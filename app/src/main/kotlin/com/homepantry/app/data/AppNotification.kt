package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Tipo de los avisos de ofertas de Lidl y Aldi (los escribe el script de tools/offers). */
const val NOTIFICATION_TYPE_OFFERS = "offers"

/**
 * Aviso para toda la casa. Lo crea el script semanal de `tools/offers`, no la app.
 * Cada miembro lo marca como leído añadiendo su uid a [readBy]: un miembro que lo lee no lo
 * marca como leído para los demás.
 *
 * households/{householdCode}/notifications/{id}
 */
data class AppNotification(
    @DocumentId
    val id: String = "",
    val type: String = "",
    /** Texto ya formado por el script ("3 ofertas en cosas que compráis"). */
    val title: String = "",
    /** Lunes de la semana del aviso ("YYYY-MM-DD", hora de Madrid). */
    val weekStart: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    val readBy: List<String> = emptyList(),
    val stores: List<OfferStore> = emptyList(),
    /** Claves ([OfferMatch.key]) de las ofertas que ya se añadieron a la lista. */
    val addedKeys: List<String> = emptyList()
)

/** Ofertas de una tienda dentro de un aviso, con el enlace a su folleto o página de ofertas. */
data class OfferStore(
    val store: String = "",
    val name: String = "",
    val url: String = "",
    val validFrom: String = "",
    val validUntil: String = "",
    val offers: List<OfferMatch> = emptyList()
)

/**
 * Una oferta que coincide con un producto habitual. [habitual] es el nombre normalizado de los
 * tickets (sin tildes) y [habitualLabel] el nombre para mostrar y para la lista ("Plátanos").
 * En Lidl el precio puede faltar: solo se sabe la [page] del folleto.
 */
data class OfferMatch(
    val key: String = "",
    val habitual: String = "",
    val habitualLabel: String = "",
    val product: String = "",
    val price: Double? = null,
    val oldPrice: Double? = null,
    val promo: String? = null,
    val page: Int? = null,
    val validFrom: String? = null,
    val validUntil: String? = null
)
