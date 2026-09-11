package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Miembro de la casa: un dispositivo/usuario anónimo que se ha unido con el código de
 * casa. Base mínima para saber quién tiene acceso (y, más adelante, para repartir gastos).
 *
 * households/{householdCode}/members/{uid}
 */
data class Member(
    @DocumentId
    val id: String = "",
    val name: String = "",
    @ServerTimestamp
    val lastSeen: Date? = null
)
