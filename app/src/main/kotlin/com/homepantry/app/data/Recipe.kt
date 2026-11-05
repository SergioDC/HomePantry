package com.homepantry.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Tipo de una entrada de recetas. Se guarda por nombre en [RecipeSource.type]. */
enum class RecipeSourceType { BOOK, ARTICLE, WEB, OTHER }

/**
 * Entrada del área Recetas: un libro (o una colección de libros), un artículo, una web…
 * households/{householdCode}/recipeSources/{sourceId}
 */
data class RecipeSource(
    @DocumentId
    val id: String = "",
    val name: String = "",
    /** Nombre de [RecipeSourceType]; texto para que un valor desconocido no rompa la lectura. */
    val type: String = RecipeSourceType.BOOK.name,
    val url: String? = null,
    val addedBy: String? = null,
    @ServerTimestamp
    val addedAt: Date? = null
)

/**
 * Receta de una entrada. No guarda ingredientes ni pasos: sirve para saber dónde encontrarla.
 * households/{householdCode}/recipes/{recipeId}
 */
data class Recipe(
    @DocumentId
    val id: String = "",
    val sourceId: String = "",
    val name: String = "",
    /** Libro dentro de la entrada (una colección como Future Life tiene varios); opcional. */
    val book: String? = null,
    val section: String? = null,
    val subsection: String? = null,
    /** «Dónde está»: texto libre (libro, página, notas…) que escribe la casa. */
    val location: String? = null,
    val url: String? = null,
    /** Orden dentro de la entrada: el del índice en las cargadas, la hora de alta en las manuales. */
    val order: Long = 0,
    @ServerTimestamp
    val addedAt: Date? = null
)
