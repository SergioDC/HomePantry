package com.homepantry.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.homepantry.app.R
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.GeminiReceiptException
import com.homepantry.app.data.ParsedReceipt
import com.homepantry.app.data.createReceiptCaptureUri
import com.homepantry.app.data.parseReceiptLines
import com.homepantry.app.data.recognizeReceiptTextLines
import com.homepantry.app.data.recognizeReceiptWithGemini
import com.homepantry.app.data.unitSuffix
import com.homepantry.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla "Historial de compras": lista de productos agrupados (spec
 * "Histórico y gasto mensual") con acceso al escaneo de ticket.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PurchaseHistoryScreen(
    viewModel: AppViewModel,
    geminiApiKeyStore: GeminiApiKeyStore,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenTickets: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var pendingCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var reviewReceipt by remember { mutableStateOf<ParsedReceipt?>(null) }
    var reviewPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var reviewNotice by remember { mutableStateOf<String?>(null) }
    var processingOcr by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }

    fun processReceiptUri(uri: Uri) {
        processingOcr = true
        scope.launch {
            val apiKey = geminiApiKeyStore.getApiKey()
            var parsed = ParsedReceipt(store = null, lines = emptyList())
            var geminiFailureReason: String? = null
            var usedClassicAfterGeminiFailure = false

            if (apiKey != null) {
                val model = geminiApiKeyStore.getModel()
                val geminiResult = runCatching { recognizeReceiptWithGemini(context, uri, apiKey, model) }
                val geminiReceipt = geminiResult.getOrNull()
                if (geminiReceipt != null) {
                    parsed = geminiReceipt
                } else {
                    val failure = geminiResult.exceptionOrNull()
                    geminiFailureReason = (failure as? GeminiReceiptException)?.message ?: failure?.message ?: "error desconocido"
                    usedClassicAfterGeminiFailure = true
                }
            }

            var classicFailed = false
            if (apiKey == null || usedClassicAfterGeminiFailure) {
                val ocrResult = runCatching { recognizeReceiptTextLines(context, uri) }
                classicFailed = ocrResult.isFailure
                parsed = ParsedReceipt(store = null, lines = parseReceiptLines(ocrResult.getOrDefault(emptyList())))
            }

            processingOcr = false

            // Estos avisos se muestran dentro de la hoja de revisión, no como
            // Snackbar: la hoja es modal y taparía (y haría caducar) el snackbar.
            reviewNotice = when {
                usedClassicAfterGeminiFailure ->
                    context.getString(R.string.purchase_history_gemini_fallback, geminiFailureReason)
                classicFailed -> context.getString(R.string.purchase_history_ocr_error)
                parsed.lines.isEmpty() -> context.getString(R.string.purchase_history_ocr_empty)
                else -> null
            }

            reviewReceipt = parsed
            reviewPhotoUri = uri
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            processReceiptUri(uri)
        } else if (success && uri == null) {
            // La captura de cámara sobrevivió (foto en cacheDir) pero el estado
            // en memoria con su Uri se perdió (recreación de actividad o proceso
            // matado en segundo plano). Avisamos al usuario en vez de fallar en
            // silencio: ver hallazgo de revisión "Lost capture URI".
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.purchase_history_capture_lost)) }
        }
    }

    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            processReceiptUri(uri)
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val dateFormat = remember { SimpleDateFormat("d MMM", Locale("es", "ES")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                },
                actions = {
                    TextButton(onClick = onOpenTickets) {
                        Text(stringResource(R.string.purchase_history_tickets))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showScanDialog = true }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.purchase_history_scan_cd))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        when {
            processingOcr -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.purchaseStoreSections.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.purchase_history_empty))
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    state.purchaseStoreSections.forEach { section ->
                        item(key = "store/${section.storeKey}") {
                            Text(
                                text = section.displayName.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(section.products, key = { "${section.storeKey}/${it.normalizedName}" }) { summary ->
                            ListItem(
                                headlineContent = { Text(summary.displayName) },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.purchase_history_last_purchase,
                                            dateFormat.format(summary.purchases.first().date),
                                            summary.purchases.size
                                        )
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        stringResource(
                                            R.string.purchase_history_unit_price,
                                            summary.latestUnitPrice,
                                            unitSuffix(summary.latestUnit)
                                        )
                                    )
                                },
                                modifier = Modifier.clickable { onOpenProduct(summary.normalizedName) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text(stringResource(R.string.purchase_history_scan_dialog_title)) },
            text = null,
            confirmButton = {
                TextButton(onClick = {
                    showScanDialog = false
                    if (!cameraPermissionState.status.isGranted) {
                        cameraPermissionState.launchPermissionRequest()
                    } else {
                        val uri = createReceiptCaptureUri(context)
                        pendingCaptureUri = uri
                        takePicture.launch(uri)
                    }
                }) {
                    Text(stringResource(R.string.purchase_history_scan_dialog_camera))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showScanDialog = false
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(stringResource(R.string.purchase_history_scan_dialog_gallery))
                }
            }
        )
    }

    val receipt = reviewReceipt
    if (receipt != null) {
        ReceiptReviewSheet(
            viewModel = viewModel,
            initialReceipt = receipt,
            ticketPhotoUri = reviewPhotoUri,
            notice = reviewNotice,
            onDismiss = {
                reviewReceipt = null
                reviewPhotoUri = null
                reviewNotice = null
            }
        )
    }
}
