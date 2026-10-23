package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.monthSummary
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.SplitViewModel
import com.homepantry.app.ui.components.HomeCard
import com.homepantry.app.ui.components.euros
import com.homepantry.app.ui.theme.StoreColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de inicio: separa las tres áreas de la app (Almacén, Gastos y Reparto) en tarjetas
 * con un resumen de cada una. Solo lee datos que los ViewModels ya tienen cargados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    splitViewModel: SplitViewModel,
    userName: String,
    onOpenSettings: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSpending: () -> Unit,
    onOpenSplit: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val splitState by splitViewModel.state.collectAsState()
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale.forLanguageTag("es-ES")).format(Date()) }
    val pending = state.items.count { !it.done }
    val month = remember(state.purchases) { monthSummary(state.purchases) }
    val monthCount = splitState.activeMonths.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_settings_cd))
                    }
                }
            )
        }
    ) { padding ->
        // Con scroll por si la fuente del sistema es grande y las tarjetas no caben.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_greeting, userName),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = today.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            HomeCard(
                icon = Icons.Filled.Inventory2,
                color = StoreColors[0],
                title = stringResource(R.string.home_almacen_title),
                summary = if (pending == 0) stringResource(R.string.home_almacen_none)
                else pluralStringResource(R.plurals.home_almacen_pending, pending, pending),
                onClick = onOpenStorage
            )
            HomeCard(
                icon = Icons.Filled.Receipt,
                color = StoreColors[2],
                title = stringResource(R.string.home_gastos_title),
                summary = if (month.ticketCount == 0) stringResource(R.string.home_gastos_none)
                else stringResource(R.string.home_gastos_month, euros(month.total)),
                onClick = onOpenSpending
            )
            HomeCard(
                icon = Icons.Filled.Groups,
                color = StoreColors[1],
                title = stringResource(R.string.home_reparto_title),
                summary = if (monthCount == 0) stringResource(R.string.home_reparto_none)
                else pluralStringResource(R.plurals.home_reparto_months, monthCount, monthCount),
                onClick = onOpenSplit,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
