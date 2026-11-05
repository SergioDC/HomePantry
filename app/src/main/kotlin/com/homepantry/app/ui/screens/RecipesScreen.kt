package com.homepantry.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Recipe
import com.homepantry.app.data.RecipeSource
import com.homepantry.app.data.groupRecipes
import com.homepantry.app.data.safeRecipeUrl
import com.homepantry.app.data.searchRecipes
import com.homepantry.app.ui.RecipesUiState
import com.homepantry.app.ui.RecipesViewModel
import com.homepantry.app.ui.components.NocturneFab

/**
 * Área Recetas: buscador sobre todas las entradas y, sin búsqueda, la lista de entradas.
 * Tocar una receta abre su ficha para apuntar «Dónde está».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Recipe?>(null) }

    LaunchedEffect(Unit) { viewModel.start() }
    RecipesErrorEffect(state, viewModel, snackbarHostState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            if (query.isBlank()) {
                NocturneFab(onClick = { showCreate = true }, contentDescription = stringResource(R.string.recipes_source_create))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RecipeSearchField(query = query, onQueryChange = { query = it })
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                query.isNotBlank() -> SearchResults(state, query, onOpen = { editing = it })
                state.sources.isEmpty() -> EmptyMessage(stringResource(R.string.recipes_sources_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.sortedSources, key = { it.id }) { source ->
                        SourceCard(source, state.recipesOf(source.id), onClick = { onOpenSource(source.id) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        RecipeSourceFormSheet(
            initial = null,
            onDismiss = { showCreate = false },
            onSave = { name, type, url ->
                viewModel.addSource(name, type, url)
                showCreate = false
            }
        )
    }
    editing?.let { recipe -> RecipeEditor(recipe, viewModel, onClose = { editing = null }) }
}

@Composable
private fun SearchResults(state: RecipesUiState, query: String, onOpen: (Recipe) -> Unit) {
    val results = remember(state.recipes, query) { searchRecipes(state.recipes, query) }
    val sourceNames = remember(state.sources) { state.sources.associate { it.id to it.name } }
    if (results.isEmpty()) {
        EmptyMessage(stringResource(R.string.recipes_search_empty, query.trim()))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(results, key = { it.id }) { recipe ->
            val origin = listOfNotNull(sourceNames[recipe.sourceId], recipe.book, recipe.section)
                .distinct()
                .joinToString(" · ")
            RecipeRow(recipe, subtitle = origin.ifBlank { null }, onClick = { onOpen(recipe) })
            HorizontalDivider()
        }
    }
}

/**
 * Recetas de una entrada agrupadas por libro y sección, con un filtro propio y alta manual.
 * Si la entrada desaparece (la borra otro miembro), vuelve atrás sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSourceScreen(
    viewModel: RecipesViewModel,
    sourceId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Recipe?>(null) }
    val source = state.sources.firstOrNull { it.id == sourceId }
    val recipes = remember(state.recipes, sourceId) { state.recipesOf(sourceId) }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.loaded, source) { if (state.loaded && source == null) onBack() }
    RecipesErrorEffect(state, viewModel, snackbarHostState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    source?.url?.let(::safeRecipeUrl)?.let { url ->
                        IconButton(onClick = { openLink(uriHandler, url) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.recipes_open_link_cd))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.recipes_more_cd))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_source_edit)) },
                                onClick = { menuOpen = false; showEdit = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.recipes_source_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmDelete = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(
                onClick = { editing = Recipe(sourceId = sourceId) },
                contentDescription = stringResource(R.string.recipes_recipe_create)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (recipes.isNotEmpty()) RecipeSearchField(query = query, onQueryChange = { query = it })
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                recipes.isEmpty() -> EmptyMessage(stringResource(R.string.recipes_source_empty))
                query.isNotBlank() -> {
                    val results = remember(recipes, query) { searchRecipes(recipes, query) }
                    if (results.isEmpty()) {
                        EmptyMessage(stringResource(R.string.recipes_search_empty, query.trim()))
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                            items(results, key = { it.id }) { recipe ->
                                val origin = listOfNotNull(recipe.book, recipe.section).joinToString(" · ")
                                RecipeRow(recipe, subtitle = origin.ifBlank { null }, onClick = { editing = recipe })
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> {
                    val groups = remember(recipes) { groupRecipes(recipes) }
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        groups.forEachIndexed { index, group ->
                            item(key = "header-$index") { GroupHeader(group.book, group.section) }
                            items(group.recipes, key = { it.id }) { recipe ->
                                RecipeRow(recipe, subtitle = null, onClick = { editing = recipe })
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEdit && source != null) {
        RecipeSourceFormSheet(
            initial = source,
            onDismiss = { showEdit = false },
            onSave = { name, type, url ->
                viewModel.updateSource(source.id, name, type, url)
                showEdit = false
            }
        )
    }
    if (confirmDelete && source != null) {
        RecipeDeleteDialog(
            title = stringResource(R.string.recipes_source_delete),
            message = pluralStringResource(R.plurals.recipes_source_delete_message, recipes.size, recipes.size, source.name),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSource(source.id)
            }
        )
    }
    editing?.let { recipe -> RecipeEditor(recipe, viewModel, onClose = { editing = null }) }
}

/** Ficha de la receta y, si se pide, la confirmación de borrarla. */
@Composable
private fun RecipeEditor(recipe: Recipe, viewModel: RecipesViewModel, onClose: () -> Unit) {
    var confirmDelete by remember(recipe.id) { mutableStateOf(false) }
    if (confirmDelete) {
        RecipeDeleteDialog(
            title = stringResource(R.string.recipes_recipe_delete),
            message = stringResource(R.string.recipes_recipe_delete_message, recipe.name),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                viewModel.deleteRecipe(recipe.id)
                onClose()
            }
        )
    } else {
        RecipeFormSheet(
            initial = recipe,
            onDismiss = onClose,
            onSave = {
                viewModel.saveRecipe(it)
                onClose()
            },
            onDelete = if (recipe.id.isEmpty()) null else ({ confirmDelete = true })
        )
    }
}

@Composable
private fun RecipesErrorEffect(state: RecipesUiState, viewModel: RecipesViewModel, host: SnackbarHostState) {
    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            host.showSnackbar(message)
            viewModel.clearError()
        }
    }
}

@Composable
private fun RecipeSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recipes_search_clear_cd))
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SourceCard(source: RecipeSource, recipes: List<Recipe>, onClick: () -> Unit) {
    val located = recipes.count { !it.location.isNullOrBlank() }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = recipeSourceTypeLabel(source.type) + " · " +
                        pluralStringResource(R.plurals.recipes_source_count, recipes.size, recipes.size, located),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun GroupHeader(book: String?, section: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    ) {
        book?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        section?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Fila de receta: nombre, «Dónde está» (o «Sin ubicar») y, si se pasa, de dónde viene. */
@Composable
private fun RecipeRow(recipe: Recipe, subtitle: String?, onClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
            val location = recipe.location
            if (location.isNullOrBlank()) {
                Text(
                    stringResource(R.string.recipes_no_location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        recipe.url?.let(::safeRecipeUrl)?.let { url ->
            IconButton(onClick = { openLink(uriHandler, url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.recipes_open_link_cd))
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Abre solo enlaces web (ver [safeRecipeUrl]); si no hay app que lo abra, no pasa nada. */
private fun openLink(uriHandler: androidx.compose.ui.platform.UriHandler, url: String) {
    val safe = safeRecipeUrl(url) ?: return
    runCatching { uriHandler.openUri(safe) }
}
