package com.homepantry.app.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Lógica pura de los avisos de ofertas: qué está sin leer, cuándo comprueba el worker y qué se
// añade a la lista. Sin Android para poder probarla con JUnit.

/** Hora del lunes a la que el worker busca el aviso: deja margen al script (07:00 UTC). */
private val CHECK_TIME: LocalTime = LocalTime.of(11, 0)
private val RETRY_DELAY: Duration = Duration.ofHours(3)
/** Un aviso más antiguo ya no merece notificación del sistema (sigue en la campana). */
private val NOTIFY_MAX_AGE: Duration = Duration.ofDays(7)

fun isUnread(notification: AppNotification, uid: String): Boolean = uid !in notification.readBy

fun unreadCount(notifications: List<AppNotification>, uid: String): Int = notifications.count { isUnread(it, uid) }

/** El próximo lunes a las 11:00 en la zona de [now], estrictamente posterior a [now]. */
fun nextMondayCheck(now: ZonedDateTime): ZonedDateTime {
    val thisWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(CHECK_TIME).withSecond(0).withNano(0)
    return if (thisWeek.isAfter(now)) thisWeek else thisWeek.plusWeeks(1)
}

/** Ventana de reintentos: del lunes a las 11:00 (incluido) hasta el final del martes. */
fun inOffersRetryWindow(now: ZonedDateTime): Boolean = when (now.dayOfWeek) {
    DayOfWeek.MONDAY -> !now.toLocalTime().isBefore(CHECK_TIME)
    DayOfWeek.TUESDAY -> true
    else -> false
}

/** Primera programación: si ya estamos en la ventana (p. ej. instalación un lunes por la tarde), ahora. */
fun initialOffersCheck(now: ZonedDateTime): ZonedDateTime =
    if (inOffersRetryWindow(now)) now else nextMondayCheck(now)

/** Sin aviso todavía: reintento en 3 h mientras siga la ventana; si no, hasta el lunes siguiente. */
fun retryOrReschedule(now: ZonedDateTime): ZonedDateTime {
    val retry = now.plus(RETRY_DELAY)
    return if (inOffersRetryWindow(retry)) retry else nextMondayCheck(now)
}

/** El aviso de ofertas más reciente de la última semana que [uid] no ha leído ni se le ha notificado. */
fun pendingOfferNotification(
    notifications: List<AppNotification>,
    uid: String,
    notifiedIds: Set<String>,
    now: Instant
): AppNotification? {
    val since = now.minus(NOTIFY_MAX_AGE)
    return notifications
        .filter { it.type == NOTIFICATION_TYPE_OFFERS && isUnread(it, uid) && it.id !in notifiedIds }
        .filter { it.createdAt?.toInstant()?.isAfter(since) == true }
        .maxByOrNull { it.createdAt!!.time }
}

/** Dominios de las tiendas cuyos folletos se pueden abrir desde un aviso. */
private val OFFER_HOSTS = listOf("lidl.es", "aldi.es")

/**
 * Cualquiera que conozca el código de casa puede escribir un aviso, así que el enlace no es de
 * fiar: solo se abre si es https y de una de las tiendas (o un subdominio suyo).
 */
fun isTrustedOfferUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        OFFER_HOSTS.any { host == it || host.endsWith(".$it") }
}

enum class OfferRowState { NEW, IN_LIST, ADDED }

/** Producto existente (despensa o lista) que corresponde a la oferta, por nombre normalizado. */
private fun matchingItem(offer: OfferMatch, items: List<Item>, pendingOnly: Boolean): Item? {
    val names = setOf(offer.habitual, normalizeProductName(offer.habitualLabel)).filter { it.isNotBlank() }
    return items.firstOrNull { (!pendingOnly || !it.done) && normalizeProductName(it.name) in names }
}

fun offerRowState(offer: OfferMatch, addedKeys: List<String>, items: List<Item>): OfferRowState = when {
    offer.key in addedKeys -> OfferRowState.ADDED
    matchingItem(offer, items, pendingOnly = true) != null -> OfferRowState.IN_LIST
    else -> OfferRowState.NEW
}

private val spanish = Locale("es", "ES")

private fun euros(value: Double) = String.format(spanish, "%.2f €", value)

/** "2026-11-01" → "1/11"; null si no tiene ese formato. */
private fun shortDate(isoDate: String?): String? =
    isoDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { "${it.dayOfMonth}/${it.monthValue}" }

/** Nota del producto en la lista: "Oferta 2,49 € (antes 2,85 €) hasta 1/11", con lo que se sepa. */
fun offerNote(offer: OfferMatch): String {
    if (offer.price == null) {
        return if (offer.page != null) "Oferta en el folleto, pág. ${offer.page}" else "Oferta"
    }
    val old = offer.oldPrice?.let { " (antes ${euros(it)})" }.orEmpty()
    val until = shortDate(offer.validUntil)?.let { " hasta $it" }.orEmpty()
    return "Oferta ${euros(offer.price)}$old$until"
}

/**
 * Lo que hay que escribir para añadir ofertas a la lista. En esta app la despensa y la lista
 * son el mismo producto (`done = true` = lo tenemos), así que si el producto ya existe en la
 * despensa se devuelve a la lista ([reactivateIds]) en vez de duplicarlo.
 */
data class OfferAddPlan(val newItems: List<Item>, val reactivateIds: List<String>)

/** @param selected pares (tienda, oferta) marcados por el usuario. */
fun planOfferAdds(
    selected: List<Pair<String, OfferMatch>>,
    items: List<Item>,
    zones: List<Zone>,
    addedBy: String
): OfferAddPlan {
    // Mismo criterio que AddItemSheet cuando no hay zona elegida: la primera zona raíz.
    val defaultZone = zones.firstOrNull { it.parentZoneId == null }?.id.orEmpty()
    val newItems = mutableListOf<Item>()
    val reactivateIds = mutableListOf<String>()
    for ((store, offer) in selected) {
        val existing = matchingItem(offer, items, pendingOnly = false)
        if (existing != null && existing.done) {
            if (existing.id !in reactivateIds) reactivateIds += existing.id
            continue
        }
        newItems += Item(
            name = offer.habitualLabel.ifBlank { offer.habitual },
            qty = 1.0,
            unit = Unit.UD.name,
            note = offerNote(offer),
            zone = existing?.zone ?: defaultZone,
            store = store,
            done = false,
            addedBy = addedBy
        )
    }
    return OfferAddPlan(newItems, reactivateIds)
}
