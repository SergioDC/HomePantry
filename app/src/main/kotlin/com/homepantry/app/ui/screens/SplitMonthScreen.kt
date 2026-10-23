package com.homepantry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.IconTile
import com.homepantry.app.ui.components.icon
import com.homepantry.app.ui.components.groupColors
import kotlinx.coroutines.launch

/**
 * Página de un grupo de SNcount (mes o actividad): cabecera con icono y nombre, pestañas
 * segmentadas Gastos, Saldos y Fotos, y el contenido de la pestaña elegida.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitMonthScreen(
    viewModel: SplitViewModel,
    monthId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expenseSheet by remember { mutableStateOf<ExpenseSheetTarget?>(null) }
    val reimbursementTitle = stringResource(R.string.split_reimbursement_title)

    // El mes lo abre la ruta con RetainSplitMonth, ligado a la entrada de navegación: así sigue
    // abierto en las pantallas de gasto y de editar que se apilan encima.

    // Si otro miembro borra el mes mientras está abierto, se vuelve a la lista.
    val monthGone = state.monthsLoaded && state.months.none { it.id == monthId }
    LaunchedEffect(monthGone) {
        if (monthGone) onBack()
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            // El nombre va en la cabecera grande, no en la barra: la barra solo lleva «atrás» a SNcount.
            TopAppBar(
                title = { Text(stringResource(R.string.split_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.openMonth?.let { month ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val color = groupColors(state.months)[month.id] ?: MaterialTheme.colorScheme.primary
                    IconTile(month.icon(), color, size = 64.dp)
                    Text(
                        month.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            SplitTabs(
                labels = listOf(R.string.split_tab_expenses, R.string.split_tab_balances, R.string.split_tab_photos)
                    .map { stringResource(it) },
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> SplitExpensesTab(
                        state = state,
                        currentUid = viewModel.currentUid,
                        onAdd = { expenseSheet = ExpenseSheetTarget.New },
                        onOpen = { expenseSheet = ExpenseSheetTarget.Edit(it) }
                    )
                    1 -> SplitBalancesTab(
                        state = state,
                        currentUid = viewModel.currentUid,
                        onSettle = { viewModel.settle(it, reimbursementTitle) }
                    )
                    else -> SplitPhotosTab(
                        state = state,
                        onUpload = { uri, note, expenseId -> viewModel.addPhoto(uri, note, expenseId) },
                        onDelete = { viewModel.deletePhoto(it) },
                        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                    )
                }
            }
        }
    }

    val month = state.openMonth
    val target = expenseSheet
    if (month != null && target != null) {
        val editing = (target as? ExpenseSheetTarget.Edit)?.expense
        // La clave hace que cambiar de gasto reinicie el estado local de la hoja.
        key(editing?.id) {
            SplitExpenseSheet(
                state = state,
                month = month,
                currentUid = viewModel.currentUid,
                expense = editing,
                onSave = { title, amountText, payerId, participants, date, reimbursement ->
                    viewModel.saveExpense(
                        editing?.id, title, amountText, payerId, participants, date, reimbursement,
                        emoji = editing?.emoji.orEmpty(), shareTexts = null
                    )
                },
                onDelete = { editing?.let { viewModel.deleteExpense(it.id) } },
                onDismiss = { expenseSheet = null }
            )
        }
    }
}

/** Pestañas como control segmentado a todo el ancho (Gastos, Saldos, Fotos). */
@Composable
private fun SplitTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

/** Qué gasto abre la hoja: uno nuevo o uno existente. */
private sealed interface ExpenseSheetTarget {
    data object New : ExpenseSheetTarget
    data class Edit(val expense: SplitExpense) : ExpenseSheetTarget
}
