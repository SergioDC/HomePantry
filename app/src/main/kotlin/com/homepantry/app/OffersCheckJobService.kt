package com.homepantry.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.homepantry.app.data.AppNotification
import com.homepantry.app.data.NotificationsRepository
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.nextMondayCheck
import com.homepantry.app.data.pendingOfferNotification
import com.homepantry.app.data.retryOrReschedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.ZonedDateTime

/** Extra del intent de la notificación: id del aviso que hay que abrir. */
const val EXTRA_NOTIFICATION_ID = "com.homepantry.app.NOTIFICATION_ID"

private const val OFFERS_JOB_ID = 4_201
private const val OFFERS_CHANNEL_ID = "offers"

/**
 * Programa la comprobación de ofertas para [at]. Se usa JobScheduler del sistema y no WorkManager
 * porque WorkManager arrastra Room y engordaba el APK casi 1 MB para un único trabajo semanal.
 * Con [replace] = false (arranque de la app) no se toca un trabajo ya programado.
 */
fun scheduleOffersCheck(context: Context, at: ZonedDateTime, replace: Boolean) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    if (!replace && scheduler.getPendingJob(OFFERS_JOB_ID) != null) return
    val delay = Duration.between(ZonedDateTime.now(at.zone), at).toMillis().coerceAtLeast(0)
    val job = JobInfo.Builder(OFFERS_JOB_ID, ComponentName(context, OffersCheckJobService::class.java))
        .setMinimumLatency(delay)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        // Sobrevive a reinicios del móvil (necesita RECEIVE_BOOT_COMPLETED en el manifest).
        .setPersisted(true)
        .build()
    scheduler.schedule(job)
}

fun ensureOffersChannel(context: Context) {
    val channel = NotificationChannel(
        OFFERS_CHANNEL_ID,
        context.getString(R.string.offers_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = context.getString(R.string.offers_channel_description) }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Comprobación semanal (lunes 11:00) del aviso de ofertas que escribe el script de `tools/offers`.
 * No hay FCM: si el aviso existe y este miembro no lo ha leído, lanza una notificación local.
 * Si aún no existe, reintenta cada 3 h hasta el martes (ver OffersLogic). Se reprograma él mismo
 * pase lo que pase, para no romper la cadena semanal.
 */
class OffersCheckJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            val next = check()
            // Primero se termina este job y después se programa el siguiente: programar con el mismo
            // id un job que sigue en marcha lo cancela, y onStopJob pisaría la fecha con un reintento.
            jobFinished(params, false)
            if (next != null) scheduleOffersCheck(applicationContext, next, replace = true)
        }
        return true
    }

    /** El sistema cortó el trabajo (p. ej. se perdió la red): se vuelve a programar un reintento. */
    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        scheduleOffersCheck(applicationContext, retryOrReschedule(ZonedDateTime.now()), replace = true)
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** @return cuándo volver a comprobar, o null si no hay casa (MainActivity lo programará al entrar). */
    private suspend fun check(): ZonedDateTime? {
        val prefs = UserPrefs(applicationContext)
        val code = prefs.householdCode.first()?.takeIf { it.isNotBlank() } ?: return null
        val now = ZonedDateTime.now()
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val uid = auth.currentUser?.uid.orEmpty()
            val notifications = NotificationsRepository(FirebaseFirestore.getInstance(), code).fetchNotifications()
            val pending = pendingOfferNotification(notifications, uid, prefs.notifiedOfferIds.first(), now.toInstant())
            if (pending != null) {
                notify(pending)
                prefs.addNotifiedOfferId(pending.id)
                nextMondayCheck(now)
            } else {
                retryOrReschedule(now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            retryOrReschedule(now)
        }
    }

    private fun notify(notification: AppNotification) {
        val context = applicationContext
        // Sin permiso (Android 13+) se queda en la campana de Home; no se insiste.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureOffersChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_NOTIFICATION_ID, notification.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, notification.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stores = notification.stores.map { it.store }
        val text = if (stores.size > 1) {
            context.getString(R.string.notifications_stores_join, stores.dropLast(1).joinToString(", "), stores.last())
        } else {
            stores.firstOrNull().orEmpty()
        }
        val built = NotificationCompat.Builder(context, OFFERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notification.id.hashCode(), built)
    }
}
