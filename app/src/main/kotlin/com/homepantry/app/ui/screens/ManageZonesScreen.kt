package com.listacasa.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.listacasa.app.R
import com.listacasa.app.data.Zone
import com.listacasa.app.data.isProtectedZone
import com.listacasa.app.ui.AppViewModel

/** SPEC.md sec 1.6: crear, renombrar y eliminar zonas. "Otros" está protegida (cierre de huecos §4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageZonesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val zones = state.zones.sortedBy { it.order }
    var newZoneName by remember { mutableStateOf("") }
    var zoneToDelete by remember { mutableStateOf<Zone?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zones_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(zones, key = { it.id }) { zone ->
                var editedName by remember(zone.id) { mutableStateOf(zone.name) }
                val itemCount = state.items.count { it.zone == zone.id }
                val protected = isProtectedZone(zone)

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            readOnly = protected
                        )
                        Text(
                            text = "$itemCount",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        if (!protected && editedName != zone.name && editedName.isNotBlank()) {
                            TextButton(onClick = { viewModel.renameZone(zone.id, editedName.trim()) }) {
                                Text("OK")
                            }
                        }
                        if (!protected && zones.size > 1) {
                            IconButton(onClick = { zoneToDelete = zone }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.zones_delete_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (protected) {
                        Text(
                            text = stringResource(R.string.zones_protected_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newZoneName,
                        onValueChange = { newZoneName = it },
                        label = { Text(stringResource(R.string.zones_new_zone_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newZoneName.isNotBlank()) {
                                viewModel.createZone(newZoneName.trim())
                                newZoneName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.zones_add_cd))
                    }
                }
            }
        }
    }

    val pendingZone = zoneToDelete
    if (pendingZone != null) {
        val otros = zones.firstOrNull { isProtectedZone(it) && it.id != pendingZone.id }
            ?: zones.firstOrNull { it.id != pendingZone.id }
        val affectedCount = state.items.count { it.zone == pendingZone.id }
        AlertDialog(
            onDismissRequest = { zoneToDelete = null },
            title = { Text(stringResource(R.string.zones_delete_confirm_title)) },
            text = { Text(stringResource(R.string.zones_delete_confirm_message, affectedCount)) },
            confirmButton = {
                TextButton(onClick = {
                    if (otros != null) {
                        viewModel.deleteZone(pendingZone.id, otros.id)
                    }
                    zoneToDelete = null
                }) { Text(stringResource(R.string.zones_delete_confirm_title)) }
            },
            dismissButton = {
                TextButton(onClick = { zoneToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
