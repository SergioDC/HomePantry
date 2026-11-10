package com.homepantry.app.data

/** Valores de la clave `type` que envía el notificador del NAS (tools/notifier/src/logic.mjs). */
const val PUSH_TYPE_SPLIT_CLOSED = "sncount_closed"
const val PUSH_TYPE_SPLIT_REMINDER = "sncount_reminder"

/**
 * Notificación push de SNcount ya validada. El NAS manda solo datos y la app pone el texto con
 * sus `strings.xml`, así los textos siguen en un único sitio.
 */
sealed interface SplitPush {
    val monthId: String
    val monthName: String

    /** Otro participante ha cerrado el grupo. [byName] vacío si el NAS no encontró su nombre. */
    data class Closed(override val monthId: String, override val monthName: String, val byName: String) : SplitPush

    /** El mes está acabando (o ya acabó) y sigue abierto. */
    data class Reminder(override val monthId: String, override val monthName: String) : SplitPush
}

/**
 * Carga `data` de un mensaje FCM → [SplitPush], o null si no es de SNcount o el id no vale para
 * una ruta (ver [isValidSplitMonthId]). Un mensaje inválido se descarta sin notificar.
 */
fun parseSplitPush(data: Map<String, String>): SplitPush? {
    val monthId = data["monthId"]?.trim().orEmpty()
    if (!isValidSplitMonthId(monthId)) return null
    val monthName = data["monthName"]?.trim().orEmpty()
    return when (data["type"]) {
        PUSH_TYPE_SPLIT_CLOSED -> SplitPush.Closed(monthId, monthName, data["byName"]?.trim().orEmpty())
        PUSH_TYPE_SPLIT_REMINDER -> SplitPush.Reminder(monthId, monthName)
        else -> null
    }
}

/**
 * Id de notificación por grupo, el mismo para cierre y recordatorio: el aviso de cierre sustituye
 * al recordatorio de ese mes si seguía en la bandeja, y cerrar desde este móvil lo quita.
 */
fun splitPushNotificationId(monthId: String): Int = monthId.hashCode()
