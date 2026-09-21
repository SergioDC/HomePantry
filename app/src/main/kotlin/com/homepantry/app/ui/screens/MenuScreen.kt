package com.homepantry.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.homepantry.app.R
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.ParsedMenu
import com.homepantry.app.data.WeekShare
import com.homepantry.app.data.createReceiptCaptureUri
import com.homepantry.app.data.knownPeople
import com.homepantry.app.data.recognizeMenuWithGemini
import com.homepantry.app.data.weekDays
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.MenuViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pestaña «Menú»: calendario (semana/mes) y lista de platos. Aquí viven el día abierto, el
 * diálogo de duplicar, la importación del menú desde una foto y el Snackbar de errores y avisos;
 * el detalle está en cada componente.
 */
@OptIn(ExperimentalPermissionsApi::class)
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

    // Importar el menú desde una foto (SPEC 2026-09-21). El almacén de la clave no guarda estado en
    // memoria (lee el mismo fichero cifrado), así que basta una instancia propia en vez de pasarla
    // desde MainActivity.
    val apiKeyStore = remember { GeminiApiKeyStore(context) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var pendingCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showImportSource by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importedMenu by remember { mutableStateOf<ParsedMenu?>(null) }
    val noKeyMessage = stringResource(R.string.menu_import_no_key)
    val emptyMenuMessage = stringResource(R.string.menu_import_empty)
    val captureLostMessage = stringResource(R.string.menu_import_capture_lost)

    fun processImportPhoto(uri: Uri) {
        importing = true
        scope.launch {
            val apiKey = apiKeyStore.getApiKey()
            var menu: ParsedMenu? = null
            var failure: String? = null
            if (apiKey == null) {
                failure = noKeyMessage
            } else {
                try {
                    menu = recognizeMenuWithGemini(context, uri, apiKey, apiKeyStore.getModel())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e.message ?: e.toString()
                }
            }
            importing = false
            if (menu != null && menu.days.isNotEmpty()) {
                importedMenu = menu
            } else {
                snackbarHostState.showSnackbar(failure ?: emptyMenuMessage)
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            processImportPhoto(uri)
        } else if (success) {
            // La foto sobrevivió en cacheDir pero se perdió la Uri en memoria (actividad recreada).
            scope.launch { snackbarHostState.showSnackbar(captureLostMessage) }
        }
    }
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processImportPhoto(uri)
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            menuViewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
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
                        onDuplicate = { duplicateFrom = it },
                        onImport = { showImportSource = true }
                    )
                    else -> DishesTab(state = state, appState = appState, viewModel = menuViewModel)
                }
            }
            if (importing) {
                // Bloquea la pantalla mientras Gemini lee la foto.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
    }

    if (showImportSource) {
        AlertDialog(
            onDismissRequest = { showImportSource = false },
            title = { Text(stringResource(R.string.menu_import_action)) },
            text = null,
            confirmButton = {
                TextButton(onClick = {
                    showImportSource = false
                    if (!cameraPermissionState.status.isGranted) {
                        cameraPermissionState.launchPermissionRequest()
                    } else {
                        val uri = createReceiptCaptureUri(context)
                        pendingCaptureUri = uri
                        takePicture.launch(uri)
                    }
                }) { Text(stringResource(R.string.menu_import_dialog_camera)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportSource = false
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.menu_import_dialog_gallery)) }
            }
        )
    }

    importedMenu?.let { parsed ->
        MenuImportSheet(
            menu = parsed,
            defaultMonth = YearMonth.from(state.position.date),
            dishes = state.dishes,
            people = knownPeople(state.entries),
            viewModel = menuViewModel,
            onDismiss = { importedMenu = null },
            onDone = { plan ->
                importedMenu = null
                val summary = buildList {
                    add(context.getString(R.string.menu_import_summary_added, plan.entries.size))
                    if (plan.newDishes.isNotEmpty()) {
                        add(context.getString(R.string.menu_import_summary_new, plan.newDishes.size))
                    }
                    if (plan.alreadyPresent > 0) {
                        add(context.getString(R.string.menu_import_summary_present, plan.alreadyPresent))
                    }
                    if (plan.droppedDays > 0) {
                        add(context.getString(R.string.menu_import_summary_dropped, plan.droppedDays))
                    }
                }.joinToString(" · ")
                scope.launch { snackbarHostState.showSnackbar(summary) }
            }
        )
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
