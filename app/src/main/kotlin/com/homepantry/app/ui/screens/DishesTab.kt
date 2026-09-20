package com.homepantry.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Dish
import com.homepantry.app.data.Ingredient
import com.homepantry.app.data.missingIngredients
import com.homepantry.app.data.normalizeProductName
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.UiState

/**
 * Lista de platos con buscador. Al guardar un plato (nuevo o editado) se comprueba qué
 * ingredientes no están en ninguna zona ni en la lista de la compra y, si falta alguno, se
 * ofrece añadirlos con `MissingIngredientsSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishesTab(state: MenuUiState, appState: UiState, viewModel: MenuViewModel) {
    val resources = LocalContext.current.resources
    var query by rememberSaveable { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var dishToDelete by remember { mutableStateOf<Dish?>(null) }
    var deleteUsage by remember { mutableIntStateOf(0) }
    var missing by remember { mutableStateOf<List<Ingredient>>(emptyList()) }

    LaunchedEffect(dishToDelete) {
        deleteUsage = dishToDelete?.let { viewModel.countEntriesForDish(it.id) } ?: 0
    }

    val normalizedQuery = normalizeProductName(query)
    val visible = state.dishes
        .filter { normalizedQuery.isEmpty() || normalizeProductName(it.name).contains(normalizedQuery) }
        .sortedBy { it.name.lowercase() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.menu_dishes_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            when {
                state.dishes.isEmpty() -> EmptyDishesText(stringResource(R.string.menu_dishes_empty))
                visible.isEmpty() -> EmptyDishesText(stringResource(R.string.menu_dishes_no_results))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { dish ->
                        Card(
                            onClick = {
                                editingDish = dish
                                showForm = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dish.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = resources.getQuantityString(
                                            R.plurals.menu_dish_ingredient_count,
                                            dish.ingredients.size,
                                            dish.ingredients.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { dishToDelete = dish }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.menu_dish_delete_cd),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editingDish = null
                showForm = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.menu_dish_add_cd))
        }
    }

    if (showForm) {
        DishFormSheet(
            initial = editingDish,
            onDismiss = { showForm = false },
            onSave = { dish ->
                viewModel.saveDish(dish)
                missing = missingIngredients(dish, appState.items)
                showForm = false
            }
        )
    }

    if (missing.isNotEmpty()) {
        MissingIngredientsSheet(
            missing = missing,
            zones = appState.zones,
            onAdd = { choices -> viewModel.addMissingToShoppingList(choices) },
            onDismiss = { missing = emptyList() }
        )
    }

    dishToDelete?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToDelete = null },
            title = { Text(stringResource(R.string.menu_dish_delete_title, dish.name)) },
            text = {
                Text(
                    if (deleteUsage == 0) stringResource(R.string.menu_dish_delete_message_unused)
                    else resources.getQuantityString(R.plurals.menu_dish_delete_message_used, deleteUsage, deleteUsage)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDish(dish.id)
                    dishToDelete = null
                }) {
                    Text(
                        stringResource(R.string.menu_dish_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dishToDelete = null }) { Text(stringResource(R.string.menu_cancel)) }
            }
        )
    }
}

@Composable
private fun EmptyDishesText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
