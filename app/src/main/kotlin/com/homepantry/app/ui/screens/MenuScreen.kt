package com.homepantry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.homepantry.app.R
import com.homepantry.app.data.WeekShare
import com.homepantry.app.data.weekDays
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.MenuViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Pestaña «Menú»: calendario (semana/mes) y lista de platos. Aquí viven el día abierto, el
 * diálogo de duplicar y el Snackbar de errores y avisos; el detalle está en cada componente.
 */
@Composable
fun MenuScreen(menuViewModel: MenuViewModel, appViewModel: AppViewModel) {
    val state by menuViewModel.state.collectAsState()
    val appState by appViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var openDay by remember { mutableStateOf<LocalDate?>(null) }
    var duplicateFrom by remember { mutableStateOf<LocalDate?>(null) }
    val duplicatedMessage = stringResource(R.string.menu_duplicate_done)
    val chooserTitle = stringResource(R.string.menu_share_chooser)

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            menuViewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.menu_tab_menu)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.menu_tab_dishes)) }
                )
            }
            when (selectedTab) {
                0 -> MenuCalendarTab(
                    state = state,
                    viewModel = menuViewModel,
                    onOpenDay = { openDay = it },
                    onShare = { weekStart ->
                        val keys = weekDays(weekStart).map { it.toString() }.toSet()
                        val image = WeekShare.render(weekStart, state.entries.filter { it.date in keys })
                        WeekShare.share(context, image, chooserTitle)
                    },
                    onDuplicate = { duplicateFrom = it }
                )
                else -> DishesTab(state = state, appState = appState, viewModel = menuViewModel)
            }
        }
    }

    openDay?.let { day ->
        DayMealsSheet(date = day, state = state, viewModel = menuViewModel, onDismiss = { openDay = null })
    }

    duplicateFrom?.let { source ->
        DuplicateWeekDialog(
            source = source,
            viewModel = menuViewModel,
            onDismiss = { duplicateFrom = null },
            onDone = {
                duplicateFrom = null
                scope.launch { snackbarHostState.showSnackbar(duplicatedMessage) }
            }
        )
    }
}
