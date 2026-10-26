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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.homepantry.app.ui.SplitUiState
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.IconTile
import com.homepantry.app.ui.components.NocturneFab
import com.homepantry.app.ui.components.SplitIcon
import com.homepantry.app.ui.components.SplitMonthMenu
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.groupColors
import com.homepantry.app.ui.components.icon
import com.homepantry.app.ui.components.splitMemberName
import kotlinx.coroutines.launch

/**
 * Lista de grupos de SNcount, el más reciente arriba, con alta y el menú de cada uno. Los
 * archivados no salen aquí: quedan detrás de la fila «SNcounts archivados» del final.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitMonthsScreen(
    viewModel: SplitViewModel,
    onBack: () -> Unit,
    onOpenMonth: (String) -> Unit,
    onEditMonth: (String) -> Unit,
    onOpenArchived: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }

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
                // Si todos están archivados, la lista queda solo con la fila de archivados.
                else -> SplitMonthList(
                    state = state,
                    months = state.activeMonths,
                    viewModel = viewModel,
                    onOpenMonth = onOpenMonth,
                    onEditMonth = onEditMonth,
                    onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                    footer = if (state.archivedMonths.isEmpty()) null else {
                        { ArchivedRow(state.archivedMonths.size, onOpenArchived) }
                    }
                )
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
}

/** Grupos archivados, con las mismas tarjetas y el menú (que aquí ofrece «Desarchivar»). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitArchivedScreen(
    viewModel: SplitViewModel,
    onBack: () -> Unit,
    onOpenMonth: (String) -> Unit,
    onEditMonth: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Al desarchivar o borrar el último, no queda nada que ver: se vuelve a la lista.
    var leaving by remember { mutableStateOf(false) }
    val empty = state.monthsLoaded && state.archivedMonths.isEmpty()
    LaunchedEffect(empty) {
        if (empty && !leaving) {
            leaving = true
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_archived_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!leaving) {
                            leaving = true
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SplitMonthList(
                state = state,
                months = state.archivedMonths,
                viewModel = viewModel,
                onOpenMonth = onOpenMonth,
                onEditMonth = onEditMonth,
                onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                footer = null
            )
        }
    }
}

@Composable
private fun SplitMonthList(
    state: SplitUiState,
    months: List<SplitMonth>,
    viewModel: SplitViewModel,
    onOpenMonth: (String) -> Unit,
    onEditMonth: (String) -> Unit,
    onMessage: (String) -> Unit,
    footer: (@Composable () -> Unit)?
) {
    // Colores de todos los grupos, archivados incluidos: archivar no cambia el color de nadie.
    val colors = remember(state.months) { groupColors(state.months) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(months, key = { it.id }) { month ->
            val names = month.memberIds.map { splitMemberName(state, it) }
            SplitMonthCard(
                month = month,
                color = colors[month.id] ?: MaterialTheme.colorScheme.primary,
                participants = names.joinToString(", "),
                onClick = { onOpenMonth(month.id) }
            ) {
                SplitMonthMenu(
                    month = month,
                    viewModel = viewModel,
                    onEdit = { onEditMonth(month.id) },
                    onMessage = onMessage
                )
            }
        }
        if (footer != null) item(key = "archived") { footer() }
    }
}

/** Fila del final de la lista que lleva a los archivados, con cuántos hay. */
@Composable
private fun ArchivedRow(count: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Inventory2, MaterialTheme.colorScheme.onSurfaceVariant, size = 36.dp)
            Text(
                stringResource(R.string.split_archived_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SplitMonthCard(
    month: SplitMonth,
    color: Color,
    participants: String,
    onClick: () -> Unit,
    menu: @Composable () -> Unit
) {
    StripedCard(color = color, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SplitIcon(month.emoji, month.icon(), color, size = 44.dp)
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
            menu()
        }
    }
}
