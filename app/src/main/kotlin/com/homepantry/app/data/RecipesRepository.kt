package com.homepantry.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Entradas y recetas de la casa: households/{code}/recipeSources y households/{code}/recipes.
 * Mismo patrón que [DishesRepository].
 */
class RecipesRepository(
    private val firestore: FirebaseFirestore,
    private val householdCode: String
) {
    private fun household() = firestore.collection("households").document(householdCode)
    private fun sources() = household().collection("recipeSources")
    private fun recipes() = household().collection("recipes")

    fun observeSources(): Flow<List<RecipeSource>> = callbackFlow {
        val registration = sources().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(RecipeSource::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    fun observeRecipes(): Flow<List<Recipe>> = callbackFlow {
        val registration = recipes().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(Recipe::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /**
     * Carga la entrada si la casa aún no la tiene. La entrada se escribe la última: si la carga se
     * corta a medias, el siguiente intento la repite. Las recetas se escriben con `mergeFields` sin
     * `location`, para que repetirla nunca borre una página que ya hubiera apuntado alguien.
     */
    suspend fun seedIfMissing(seed: RecipeSeed) {
        if (sources().document(seed.source.id).get().await().exists()) return
        seed.recipes.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { recipe ->
                batch.set(recipes().document(recipe.id), recipe, SEED_FIELDS)
            }
            batch.commit().await()
        }
        sources().document(seed.source.id).set(seed.source).await()
    }

    suspend fun addSource(source: RecipeSource): String = sources().add(source).await().id

    /** Cambia solo los campos editables, sin pisar el resto (ver ItemsRepository.setDone). */
    suspend fun updateSource(id: String, name: String, type: String, url: String?) {
        sources().document(id).update(mapOf("name" to name, "type" to type, "url" to url)).await()
    }

    /** Borra la entrada y todas sus recetas, en lotes de como mucho 500 operaciones. */
    suspend fun deleteSource(id: String) {
        val docs = recipes().whereEqualTo("sourceId", id).get().await().documents
        docs.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        sources().document(id).delete().await()
    }

    suspend fun addRecipe(recipe: Recipe) {
        recipes().add(recipe).await()
    }

    /** Cambia solo los campos del formulario, para no pisar lo que acabe de escribir otro miembro. */
    suspend fun updateRecipe(recipe: Recipe) {
        recipes().document(recipe.id).update(
            mapOf(
                "name" to recipe.name,
                "book" to recipe.book,
                "section" to recipe.section,
                "location" to recipe.location,
                "url" to recipe.url
            )
        ).await()
    }

    suspend fun deleteRecipe(id: String) {
        recipes().document(id).delete().await()
    }

    private companion object {
        val SEED_FIELDS: SetOptions =
            SetOptions.mergeFields("sourceId", "name", "book", "section", "subsection", "order", "addedAt")
    }
}
