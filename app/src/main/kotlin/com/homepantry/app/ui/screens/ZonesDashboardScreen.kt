package com.homepantry.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.zoneColorFor
import com.homepantry.app.data.zoneIconLetter
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.ZoneCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pestaña "Almacén": dashboard de zonas (Nocturne). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesDashboardScreen(
    viewModel: AppViewModel,
    userName: String,
    onManageZones: () -> Unit,
    onOpenZone: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showCreateZone by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale("es", "ES")).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onManageZones) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_cd))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.dashboard_greeting, userName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )
            Text(
                text = today.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(state.zoneCards, key = { _, summary -> summary.zone.id }) { index, summary ->
                    val summaryText = if (summary.pendingCount == 0) {
                        stringResource(R.string.dashboard_summary_up_to_date, summary.itemCount)
                    } else {
                        stringResource(R.string.dashboard_summary_pending, summary.itemCount, summary.pendingCount)
                    }
                    ZoneCard(
                        letter = zoneIconLetter(summary.zone),
                        colorHex = zoneColorFor(index),
                        name = summary.zone.name,
                        summaryText = summaryText,
                        onClick = { onOpenZone(summary.zone.id) }
                    )
                }
                item {
                    OutlinedCard(
                        onClick = { showCreateZone = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_new_zone),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateZone) {
        AlertDialog(
            onDismissRequest = { showCreateZone = false; newZoneName = "" },
            title = { Text(stringResource(R.string.dashboard_new_zone)) },
            text = {
                OutlinedTextField(
                    value = newZoneName,
                    onValueChange = { newZoneName = it },
                    label = { Text(stringResource(R.string.zones_new_zone_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newZoneName.isNotBlank()) {
                        viewModel.createZone(newZoneName.trim())
                    }
                    newZoneName = ""
                    showCreateZone = false
                }) { Text(stringResource(R.string.add_item_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateZone = false; newZoneName = "" }) { Text("Cancelar") }
            }
        )
    }
}
