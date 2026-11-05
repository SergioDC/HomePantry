package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.Recipe
import com.homepantry.app.data.RecipeSeed
import com.homepantry.app.data.RecipeSource
import com.homepantry.app.data.RecipesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipesUiState(
    val sources: List<RecipeSource> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null
) {
    /** Entradas por nombre, para la lista de Recetas. */
    val sortedSources: List<RecipeSource>
        get() = sources.sortedBy { it.name.lowercase() }

    fun recipesOf(sourceId: String): List<Recipe> = recipes.filter { it.sourceId == sourceId }
}

/**
 * Área Recetas: entradas (libros, artículos, webs…) y sus recetas con «Dónde está». Se crea a
 * nivel de actividad como los demás, pero no escucha Firestore hasta que se abre el área
 * ([start]): son cientos de documentos que la mayoría de arranques no necesitan.
 */
class RecipesViewModel(
    private val repository: RecipesRepository,
    /** Lee las entradas que vienen con la app; se llama una vez, al abrir el área. */
    private val loadSeeds: () -> List<RecipeSeed>,
    private var userName: String
) : ViewModel() {

    private val sources = MutableStateFlow<List<RecipeSource>?>(null)
    private val recipes = MutableStateFlow<List<Recipe>?>(null)
    private val error = MutableStateFlow<String?>(null)
    private var started = false

    val state: StateFlow<RecipesUiState> = combine(sources, recipes, error) { s, r, e ->
        RecipesUiState(
            sources = s.orEmpty(),
            recipes = r.orEmpty(),
            loaded = s != null && r != null,
            error = e
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipesUiState())

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            repository.observeSources().catch { e -> error.value = e.message }.collect { sources.value = it }
        }
        viewModelScope.launch {
            repository.observeRecipes().catch { e -> error.value = e.message }.collect { recipes.value = it }
        }
        launchCatching {
            val seeds = withContext(Dispatchers.IO) { loadSeeds() }
            seeds.forEach { repository.seedIfMissing(it) }
        }
    }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message ?: e.toString()
        }
    }

    fun updateUserName(name: String) {
        userName = name
    }

    fun clearError() {
        error.value = null
    }

    fun addSource(name: String, type: String, url: String?) = launchCatching {
        repository.addSource(RecipeSource(name = name, type = type, url = url, addedBy = userName))
    }

    fun updateSource(id: String, name: String, type: String, url: String?) = launchCatching {
        repository.updateSource(id, name, type, url)
    }

    fun deleteSource(id: String) = launchCatching { repository.deleteSource(id) }

    /** Crea la receta si no tiene id; si lo tiene, guarda los cambios del formulario. */
    fun saveRecipe(recipe: Recipe) = launchCatching {
        if (recipe.id.isEmpty()) {
            repository.addRecipe(recipe.copy(order = System.currentTimeMillis()))
        } else {
            repository.updateRecipe(recipe)
        }
    }

    fun deleteRecipe(id: String) = launchCatching { repository.deleteRecipe(id) }
}
