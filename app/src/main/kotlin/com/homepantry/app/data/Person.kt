package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId

/** Nombre con el que se guarda y se muestra a la familia, que es la «persona» sin nombre (null en la entrada). */
const val FAMILY_NAME = "Familia"

/**
 * Una persona a la que se asignan platos, con el color elegido para ella. Existe para guardar el
 * color y como lista de atajos que no depende de lo que el calendario tenga cargado; la entrada
 * sigue guardando solo el nombre (`MealEntry.person`).
 * households/{householdCode}/people/{personDocId}
 */
data class Person(
    @DocumentId
    val id: String = "",
    val name: String = "",
    /** Nombre del [PersonColor]; null = el de por defecto. */
    val color: String? = null
)

/**
 * Id del documento de una persona: su clave de comparación (sin mayúsculas ni acentos), para que
 * «José» y «jose» sean el mismo documento. En blanco o null es la familia (`"familia"`). Sin «/»,
 * que Firestore leería como ruta.
 */
fun personDocId(name: String?): String {
    val key = personKey(name.orEmpty())
    return if (key.isEmpty()) personKey(FAMILY_NAME) else key.replace('/', '-')
}

/** El color elegido para [person] (null = la familia), o null si no tiene documento, color o el id ya no existe. */
fun colorFor(person: String?, people: List<Person>): PersonColor? {
    val id = personDocId(person)
    return personColorOf(people.firstOrNull { it.id == id }?.color)
}
