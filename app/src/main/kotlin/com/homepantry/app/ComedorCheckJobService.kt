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
import com.homepantry.app.data.ComedorConfig
import com.homepantry.app.data.ComedorFetch
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.data.MenuImportDraft
import com.homepantry.app.data.MenuImportDraftStore
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.comedorTargetMonth
import com.homepantry.app.data.fetchComedorMenu
import com.homepantry.app.data.hasComedorMenu
import com.homepantry.app.data.isStaleComedorMonth
import com.homepantry.app.data.menuImportDraftFile
import com.homepantry.app.data.monthLabel
import com.homepantry.app.data.nextComedorCheck
import java.time.Duration
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Extra del intent de la notificación del comedor: abrir la pantalla de Menú con la revisión. */
const val EXTRA_OPEN_MENU = "com.homepantry.app.OPEN_MENU"

private const val COMEDOR_JOB_ID = 4_202
private const val COMEDOR_CHANNEL_ID = "comedor"
private const val COMEDOR_NOTIFICATION_ID = 4_202

/** Programa la comprobación del menú del comedor para [at]; mismo esquema que [scheduleOffersCheck]. */
fun scheduleComedorCheck(context: Context, at: ZonedDateTime, replace: Boolean) {
    val scheduler = context.getSystemService(JobScheduler::class.java)
    if (!replace && scheduler.getPendingJob(COMEDOR_JOB_ID) != null) return
    val delay = Duration.between(ZonedDateTime.now(at.zone), at).toMillis().coerceAtLeast(0)
    val job = JobInfo.Builder(COMEDOR_JOB_ID, ComponentName(context, ComedorCheckJobService::class.java))
        .setMinimumLatency(delay)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setPersisted(true)
        .build()
    scheduler.schedule(job)
}

fun ensureComedorChannel(context: Context) {
    val channel = NotificationChannel(
        COMEDOR_CHANNEL_ID,
        context.getString(R.string.comedor_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = context.getString(R.string.comedor_channel_description) }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Comprobación del menú del comedor (spec 2026-10-30): una vez al día desde 3 días antes de fin de
 * mes hasta el día 5. Si Oihan ya tiene menú ese mes no descarga nada; si el PDF es nuevo y del mes
 * buscado, lo deja como borrador y avisa. Nunca importa: el usuario lo revisa. Se reprograma él
 * mismo pase lo que pase.
 */
class ComedorCheckJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            val next = check()
            // Como en ofertas: primero se termina este job y después se programa el siguiente.
            jobFinished(params, false)
            if (next != null) scheduleComedorCheck(applicationContext, next, replace = true)
        }
        return true
    }

    /** El sistema cortó el trabajo (p. ej. se perdió la red): reintento en una hora. */
    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        scheduleComedorCheck(applicationContext, ZonedDateTime.now().plusHours(1), replace = true)
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** @return cuándo volver a comprobar, o null si no hay casa (MainActivity lo programará al entrar). */
    private suspend fun check(): ZonedDateTime? {
        val context = applicationContext
        val prefs = UserPrefs(context)
        val code = prefs.householdCode.first()?.takeIf { it.isNotBlank() } ?: return null
        val now = ZonedDateTime.now(ComedorConfig.ZONE)
        val target = comedorTargetMonth(now.toLocalDate()) ?: return nextComedorCheck(now, gotTarget = false)
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val entries = MealEntriesRepository(FirebaseFirestore.getInstance(), code)
                .getRange(target.atDay(1).toString(), target.atEndOfMonth().toString())
            if (hasComedorMenu(entries)) return nextComedorCheck(now, gotTarget = true)

            // Un borrador a medio revisar (de una foto o del comedor) no se pisa.
            val draftStore = MenuImportDraftStore(menuImportDraftFile(context, code))
            if (draftStore.load() != null) return nextComedorCheck(now, gotTarget = false)

            val keyStore = GeminiApiKeyStore(context)
            val apiKey = keyStore.getApiKey() ?: return nextComedorCheck(now, gotTarget = false)
            val fetched = fetchComedorMenu(context, target, apiKey, keyStore.getModel(), prefs.comedorFingerprint.first())
            if (fetched !is ComedorFetch.Read) return nextComedorCheck(now, gotTarget = false)
            val menu = fetched.menu
            if (isStaleComedorMonth(menu.month, target) || menu.days.isEmpty()) {
                // Este PDF no sirve: no se vuelve a mandar a Gemini.
                prefs.saveComedorFingerprint(fetched.fingerprint)
                return nextComedorCheck(now, gotTarget = false)
            }
            // La lectura tarda segundos: entretanto el usuario puede haber creado otro borrador
            // (foto o botón del comedor). Ese no se pisa, y sin huella mañana se reintenta.
            if (draftStore.load() != null) return nextComedorCheck(now, gotTarget = false)
            val month = menu.month ?: target
            draftStore.save(MenuImportDraft(menu.month, month, ComedorConfig.PERSON, menu.days))
            // La huella va después del borrador: si no llegó a guardarse, mañana se vuelve a leer.
            prefs.saveComedorFingerprint(fetched.fingerprint)
            notify(month)
            nextComedorCheck(now, gotTarget = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nextComedorCheck(now, gotTarget = false)
        }
    }

    private fun notify(month: YearMonth) {
        val context = applicationContext
        // Sin permiso (Android 13+) el borrador espera igualmente en la pantalla de Menú.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureComedorChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_MENU, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, COMEDOR_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val built = NotificationCompat.Builder(context, COMEDOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.comedor_notification_title, monthLabel(month)))
            .setContentText(context.getString(R.string.comedor_notification_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(COMEDOR_NOTIFICATION_ID, built)
    }
}
