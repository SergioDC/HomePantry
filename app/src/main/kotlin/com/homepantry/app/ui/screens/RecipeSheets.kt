package com.homepantry.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.Recipe
import com.homepantry.app.data.RecipeSource
import com.homepantry.app.data.RecipeSourceType
import com.homepantry.app.data.trimToNull
import com.homepantry.app.ui.components.SegmentedToggle

/** Etiqueta visible de un tipo de entrada; un tipo desconocido se muestra como «Otro». */
@Composable
fun recipeSourceTypeLabel(type: String): String = when (type) {
    RecipeSourceType.BOOK.name -> stringResource(R.string.recipes_type_book)
    RecipeSourceType.ARTICLE.name -> stringResource(R.string.recipes_type_article)
    RecipeSourceType.WEB.name -> stringResource(R.string.recipes_type_web)
    else -> stringResource(R.string.recipes_type_other)
}

/** Alta o edición de una entrada: nombre, tipo y enlace opcional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSourceFormSheet(
    initial: RecipeSource?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, url: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember {
        mutableStateOf(
            RecipeSourceType.entries.firstOrNull { it.name == initial?.type } ?: RecipeSourceType.BOOK
        )
    }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (initial == null) R.string.recipes_source_create else R.string.recipes_source_edit),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.recipes_source_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            SegmentedToggle(
                options = RecipeSourceType.entries.map { it to recipeSourceTypeLabel(it.name) },
                selected = type,
                onSelect = { type = it },
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.recipes_source_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            FormButtons(
                canSave = name.isNotBlank(),
                onCancel = onDismiss,
                onSave = { onSave(name.trim(), type.name, url.trimToNull()) }
            )
        }
    }
}

/**
 * Alta o edición de una receta. Al abrir una que ya existe el foco va a «Dónde está», que es lo
 * que se viene a apuntar; en una nueva va al nombre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFormSheet(
    initial: Recipe,
    onDismiss: () -> Unit,
    onSave: (Recipe) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial.name) }
    var location by remember { mutableStateOf(initial.location.orEmpty()) }
    var book by remember { mutableStateOf(initial.book.orEmpty()) }
    var section by remember { mutableStateOf(initial.section.orEmpty()) }
    var url by remember { mutableStateOf(initial.url.orEmpty()) }
    val isNew = initial.id.isEmpty()
    val nameFocus = remember { FocusRequester() }
    val locationFocus = remember { FocusRequester() }
    val save = {
        onSave(
            initial.copy(
                name = name.trim(),
                location = location.trimToNull(),
                book = book.trimToNull(),
                section = section.trimToNull(),
                url = url.trimToNull()
            )
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (isNew) R.string.recipes_recipe_create else R.string.recipes_recipe_edit),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.recipes_recipe_name)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text(stringResource(R.string.recipes_recipe_location)) },
                placeholder = { Text(stringResource(R.string.recipes_recipe_location_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(locationFocus)
            )
            OutlinedTextField(
                value = book,
                onValueChange = { book = it },
                label = { Text(stringResource(R.string.recipes_recipe_book)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = section,
                onValueChange = { section = it },
                label = { Text(stringResource(R.string.recipes_recipe_section)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.recipes_recipe_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.recipes_recipe_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            FormButtons(canSave = name.isNotBlank(), onCancel = onDismiss, onSave = save)
        }
    }

    LaunchedEffect(Unit) {
        if (isNew) nameFocus.requestFocus() else locationFocus.requestFocus()
    }
}

@Composable
private fun FormButtons(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.recipes_cancel)) }
        Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.recipes_save)) }
    }
}

/** Confirmación de borrado, con el botón en coral (SPEC.md §6). */
@Composable
fun RecipeDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.recipes_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipes_cancel)) }
        }
    )
}
