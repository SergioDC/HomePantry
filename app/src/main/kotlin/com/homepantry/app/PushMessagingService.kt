package com.homepantry.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.homepantry.app.data.PushTokensRepository
import com.homepantry.app.data.SplitPush
import com.homepantry.app.data.UserPrefs
import com.homepantry.app.data.parseSplitPush
import com.homepantry.app.data.splitPushNotificationId
import com.homepantry.app.data.splitShareUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val SNCOUNT_CHANNEL_ID = "sncount"
private const val SAVE_TOKEN_TIMEOUT_MS = 10_000L

/**
 * Push de SNcount que envía el notificador del NAS (tools/notifier): aviso de cierre de un grupo y
 * recordatorio de fin de mes. Llegan solo con datos y prioridad alta, así que [onMessageReceived]
 * se ejecuta aunque la app esté cerrada y la notificación la monta la app.
 */
class PushMessagingService : FirebaseMessagingService() {

    /**
     * FCM lo llama en un hilo propio de fondo: bloquear unos segundos aquí no congela nada. Si
     * falla, no pasa nada: [savePushToken] se repite en cada arranque de la app.
     */
    override fun onNewToken(token: String) {
        runBlocking { withTimeoutOrNull(SAVE_TOKEN_TIMEOUT_MS) { savePushToken(applicationContext, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val push = parseSplitPush(message.data) ?: return
        showSplitPushNotification(applicationContext, push)
    }
}

/**
 * Guarda el token FCM de este móvil en `pushTokens/{uid}` de la casa. Sin casa o sin sesión no
 * hace nada; los errores se tragan (sin red, sin Play Services…) porque se reintenta al arrancar.
 */
suspend fun savePushToken(context: Context, token: String? = null) {
    try {
        val code = UserPrefs(context).householdCode.first()?.takeIf { it.isNotBlank() } ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val value = token ?: FirebaseMessaging.getInstance().token.await()
        PushTokensRepository(FirebaseFirestore.getInstance(), code).save(uid, value)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Se reintenta en el próximo arranque.
    }
}

fun ensureSncountChannel(context: Context) {
    val channel = NotificationChannel(
        SNCOUNT_CHANNEL_ID,
        context.getString(R.string.sncount_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = context.getString(R.string.sncount_channel_description) }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Al tocarla se abre el grupo con el mismo camino que un enlace de SNcount: `data` es
 * [splitShareUrl] y MainActivity lo lee con `splitMonthIdFromLink` (en onCreate o onNewIntent).
 */
fun showSplitPushNotification(context: Context, push: SplitPush) {
    // Sin permiso (Android 13+) la tarjeta del grupo sigue mostrando «Cerrado por X».
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    ensureSncountChannel(context)
    val name = push.monthName.ifBlank { context.getString(R.string.sncount_fallback_name) }
    val title: String
    val text: String
    when (push) {
        is SplitPush.Closed -> {
            title = context.getString(R.string.sncount_closed_title, name)
            text = if (push.byName.isBlank()) context.getString(R.string.sncount_closed_text)
            else context.getString(R.string.sncount_closed_text_by, push.byName)
        }
        is SplitPush.Reminder -> {
            title = context.getString(R.string.sncount_reminder_title, name)
            text = context.getString(R.string.sncount_reminder_text)
        }
    }
    val notificationId = splitPushNotificationId(push.monthId)
    val intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse(splitShareUrl(push.monthId)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pendingIntent = PendingIntent.getActivity(
        context, notificationId, intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val built = NotificationCompat.Builder(context, SNCOUNT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(notificationId, built)
}

/** Quita la notificación de un grupo (al cerrarlo desde este móvil ya no hace falta recordarlo). */
fun cancelSplitPushNotification(context: Context, monthId: String) {
    NotificationManagerCompat.from(context).cancel(splitPushNotificationId(monthId))
}
