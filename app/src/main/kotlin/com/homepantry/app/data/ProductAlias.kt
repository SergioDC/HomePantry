package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Corrección recordada por la casa: qué nombre genérico (y, a veces, qué envase) corresponde a un
 * nombre impreso. El id es [aliasKey] del nombre impreso, que no lleva el tamaño, así que un alias
 * sirve para "ARANDANO 300GR" y para "ARANDANO 150GR".
 * households/{householdCode}/productAliases/{aliasKey}
 */
data class ProductAlias(
    @DocumentId
    val id: String = "",
    /** Un ejemplo del nombre impreso, para reconocerlo al mirar los datos. */
    val rawName: String = "",
    val productName: String = "",
    /**
     * True si el envase de abajo es una corrección que hay que aplicar, incluida la de "sin envase"
     * (`packSize` null). Solo se guarda cuando el nombre impreso no lleva tamaño: si lo lleva, el
     * envase sale del propio nombre de cada línea.
     */
    val packKnown: Boolean = false,
    val packSize: Double? = null,
    val packUnit: String? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
