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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.IconTile
import com.homepantry.app.ui.components.NocturneFab
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.groupColors
import com.homepantry.app.ui.components.icon
import com.homepantry.app.ui.components.splitMemberName

/** Lista de meses de reparto, el más reciente arriba, con alta, renombrado y borrado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitMonthsScreen(
    viewModel: SplitViewModel,
    onBack: () -> Unit,
    onOpenMonth: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val colors = remember(state.months) { groupColors(state.months) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SplitMonth?>(null) }
    var deleting by remember { mutableStateOf<SplitMonth?>(null) }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        floatingActionButton = {
            NocturneFab(onClick = { showCreate = true }, contentDescription = stringResource(R.string.split_month_create))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.monthsLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.months.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.split_months_empty), textAlign = TextAlign.Center)
                    TextButton(onClick = { showCreate = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.split_month_create))
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.months, key = { it.id }) { month ->
                        val names = month.memberIds.map { splitMemberName(state, it) }
                        SplitMonthCard(
                            month = month,
                            color = colors[month.id] ?: MaterialTheme.colorScheme.primary,
                            participants = names.joinToString(", "),
                            onClick = { onOpenMonth(month.id) },
                            onRename = { renaming = month },
                            onDelete = { deleting = month }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        SplitMonthFormSheet(
            members = state.members,
            unknownMemberName = stringResource(R.string.split_unknown_member),
            onCreate = { name, monthKey, memberIds, activity ->
                viewModel.createMonth(name, monthKey, memberIds, activity)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }

    renaming?.let { month ->
        var newName by remember(month.id) { mutableStateOf(month.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.split_month_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.split_month_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameMonth(month.id, newName)
                        renaming = null
                    },
                    enabled = newName.isNotBlank()
                ) { Text(stringResource(R.string.split_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }

    deleting?.let { month ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.split_month_delete_title)) },
            text = { Text(stringResource(R.string.split_month_delete_message, month.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMonth(month.id)
                    deleting = null
                }) {
                    Text(stringResource(R.string.split_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.split_cancel)) }
            }
        )
    }
}

@Composable
private fun SplitMonthCard(
    month: SplitMonth,
    color: Color,
    participants: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    StripedCard(color = color, onClick = onClick) {
        Row(
            Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(month.icon(), color, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(month.name, style = MaterialTheme.typography.titleMedium)
                val count = pluralStringResource(R.plurals.split_participants_count, month.memberIds.size, month.memberIds.size)
                Text(
                    if (month.activity) "${stringResource(R.string.split_kind_activity)} · $count" else count,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    participants,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.split_month_menu_cd))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.split_month_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.split_month_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
