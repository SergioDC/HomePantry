package com.homepantry.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.PersonColor
import com.homepantry.app.data.colorFor
import com.homepantry.app.data.knownPeople
import com.homepantry.app.ui.MenuUiState
import com.homepantry.app.ui.MenuViewModel
import com.homepantry.app.ui.components.chosenTint
import com.homepantry.app.ui.theme.Mint400

/**
 * Colores de las personas del menú (Familia incluida): una fila por persona con la paleta fija. Se
 * guardan para toda la casa, así que todos los móviles ven a cada persona del mismo color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleColorsSheet(state: MenuUiState, viewModel: MenuViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val names = remember(state.entries, state.people) { knownPeople(state.entries, state.people) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.menu_colors_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.menu_colors_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                // Familia va la primera de la lista y se colorea como cualquier otra persona.
                items(names, key = { "person/$it" }) { name ->
                    PersonColorRow(person = name, state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonColorRow(person: String, state: MenuUiState, viewModel: MenuViewModel) {
    val selected = colorFor(person, state.people)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = person,
            style = MaterialTheme.typography.titleMedium,
            color = chosenTint(person, state.people) ?: Mint400
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PersonColor.values().forEach { color ->
                ColorSwatch(color = color, selected = color == selected) { viewModel.setPersonColor(person, color) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: PersonColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color(color.onDark), CircleShape)
            .border(
                border = BorderStroke(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = color.label },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
        }
    }
}
