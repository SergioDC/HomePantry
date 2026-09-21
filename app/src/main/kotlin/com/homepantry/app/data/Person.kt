package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId

/**
 * La familia es una persona como cualquier otra: se guarda en la entrada con este nombre, con su
 * documento, su color y su etiqueta en el calendario. `null` en la entrada no es la familia sino
 * «sin asignar» (antes se confundían y asignar a Familia no cambiaba nada).
 */
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
 * «José» y «jose» sean el mismo documento (la familia es `"familia"`). Sin «/», que Firestore leería
 * como ruta.
 */
fun personDocId(name: String): String = personKey(name).replace('/', '-')

/**
 * El color elegido para [person], o null si no tiene documento, color o el id ya no existe. Sin
 * persona (null o en blanco) tampoco hay color: son las entradas sin asignar.
 */
fun colorFor(person: String?, people: List<Person>): PersonColor? {
    if (person.isNullOrBlank()) return null
    val id = personDocId(person)
    return personColorOf(people.firstOrNull { it.id == id }?.color)
}
