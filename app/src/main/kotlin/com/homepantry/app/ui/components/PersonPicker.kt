package com.homepantry.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.MAX_PERSON_LENGTH
import com.homepantry.app.data.Person
import com.homepantry.app.data.normalizePerson
import com.homepantry.app.data.personKey
import com.homepantry.app.ui.theme.Mint400

/**
 * Selector de «Para quién»: un chip «Sin asignar» y otro por cada persona conocida, Familia incluida
 * (con su color), más un campo para escribir un nombre nuevo. Lo escrito vive en [personText] (en
 * blanco es «sin asignar»); quien lo use obtiene la persona a guardar con `normalizePerson`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonPicker(
    personText: String,
    onPersonText: (String) -> Unit,
    names: List<String>,
    people: List<Person>,
    modifier: Modifier = Modifier
) {
    val person = normalizePerson(personText, names)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = person == null,
                onClick = { onPersonText("") },
                label = { Text(stringResource(R.string.menu_person_unassigned)) }
            )
            names.forEach { name ->
                FilterChip(
                    selected = person != null && personKey(person) == personKey(name),
                    onClick = { onPersonText(name) },
                    label = { Text(name) },
                    leadingIcon = { ColorDot(chosenTint(name, people) ?: Mint400) }
                )
            }
        }
        OutlinedTextField(
            value = personText,
            onValueChange = { onPersonText(it.take(MAX_PERSON_LENGTH)) },
            label = { Text(stringResource(R.string.menu_import_person_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}
