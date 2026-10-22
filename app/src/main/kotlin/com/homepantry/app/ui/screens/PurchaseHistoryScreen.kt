package com.homepantry.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.homepantry.app.R
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.GeminiReceiptException
import com.homepantry.app.data.ParsedReceipt
import com.homepantry.app.data.StoreSpend
import com.homepantry.app.data.Ticket
import com.homepantry.app.data.createReceiptCaptureUri
import com.homepantry.app.data.detectReceiptDate
import com.homepantry.app.data.lastMonthsSpend
import com.homepantry.app.data.monthStart
import com.homepantry.app.data.monthSummary
import com.homepantry.app.data.parseReceiptLines
import com.homepantry.app.data.purchasesOfStore
import com.homepantry.app.data.recognizeReceiptTextLines
import com.homepantry.app.data.recognizeReceiptWithGemini
import com.homepantry.app.data.spendByStore
import com.homepantry.app.data.storeOverview
import com.homepantry.app.data.storeShares
import com.homepantry.app.data.ticketsByMonth
import com.homepantry.app.ui.AppViewModel
import com.homepantry.app.ui.components.ColorDot
import com.homepantry.app.ui.components.DeleteTicketDialog
import com.homepantry.app.ui.components.MonthHeader
import com.homepantry.app.ui.components.SpendHeaderCard
import com.homepantry.app.ui.components.SpendSectionTitle
import com.homepantry.app.ui.components.StripedCard
import com.homepantry.app.ui.components.colorOf
import com.homepantry.app.ui.components.euros
import com.homepantry.app.ui.components.rememberStoreColors
import com.homepantry.app.ui.components.SpendMonthBars
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val esLocale = Locale.forLanguageTag("es-ES")


/** Meses que muestra la gráfica del panel, el actual incluido. */
private const val CHART_MONTHS = 12

/** Valor de la pestaña «Todos los meses» en el estado guardado del mes elegido. */
private const val ALL_MONTHS = "all"

/**
 * Pantalla «Gastos» (spec «Gastos: panel único»): chips de supermercado que filtran todo,
 * pestañas de meses con el gasto de cada uno (más «Todos los meses»), la tarjeta del periodo
 * con su gráfica de 12 meses (tocar una barra cambia de mes), el reparto por súper, los
 * tickets y enlaces a productos y estadísticas. Al elegir un súper se ven todos sus meses,
 * cada uno con su cabecera, para compararlos sin ir uno a uno; el mes elegido se mantiene
 * al cambiar de súper.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PurchaseHistoryScreen(
    viewModel: AppViewModel,
    geminiApiKeyStore: GeminiApiKeyStore,
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onOpenProducts: () -> Unit,
    onOpenStats: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
                val textLines = ocrResult.getOrDefault(emptyList())
                parsed = ParsedReceipt(
                    store = null,
                    lines = parseReceiptLines(textLines),
                    date = detectReceiptDate(textLines, Date())
                )
            }

            processingOcr = false

            // Estos avisos se muestran dentro de la hoja de revisión, no como
            // Snackbar: la hoja es modal y taparía (y haría caducar) el snackbar.
            reviewNotice = when {
                usedClassicAfterGeminiFailure ->
                    resources.getString(R.string.purchase_history_gemini_fallback, geminiFailureReason)
                classicFailed -> resources.getString(R.string.purchase_history_ocr_error)
                parsed.lines.isEmpty() -> resources.getString(R.string.purchase_history_ocr_empty)
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
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.purchase_history_capture_lost)) }
        }
    }

    fun launchCamera() {
        val uri = createReceiptCaptureUri(context)
        pendingCaptureUri = uri
        takePicture.launch(uri)
    }

    // Al conceder el permiso se abre la cámara sin tener que volver a pulsar; si se deniega se avisa
    // (con el permiso denegado para siempre, pedirlo no muestra nada y el botón parecía no hacer nada).
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA) { granted ->
        if (granted) {
            launchCamera()
        } else {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.camera_permission_denied)) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.purchase_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                processingOcr -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.purchases.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.purchase_history_empty))
                    }
                }
                else -> SpendPanel(
                    viewModel = viewModel,
                    onOpenTicket = onOpenTicket,
                    onOpenProducts = onOpenProducts,
                    onOpenStats = onOpenStats
                )
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
                    if (cameraPermissionState.status.isGranted) {
                        launchCamera()
                    } else {
                        cameraPermissionState.launchPermissionRequest()
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

@Composable
private fun SpendPanel(
    viewModel: AppViewModel,
    onOpenTicket: (String) -> Unit,
    onOpenProducts: () -> Unit,
    onOpenStats: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val now = remember { Date() }
    var selectedStoreKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Un mes "yyyy-MM", ALL_MONTHS o null (sin elegir). El mes se mantiene al cambiar de súper,
    // para comparar súpers en el mismo mes sin volver a buscarlo.
    var selectedMonthKey by rememberSaveable { mutableStateOf<String?>(null) }
    var ticketPendingDelete by remember { mutableStateOf<Ticket?>(null) }

    val allTickets = state.purchaseTickets
    // Supermercados del de compra más reciente al más antiguo, con «sin supermercado» al final.
    val stores = remember(allTickets) {
        allTickets.distinctBy { it.storeKey }.map { it.storeKey to it.store }.sortedBy { it.first.isEmpty() }
    }
    val storeColors = rememberStoreColors(allTickets)
    // Si el súper elegido ya no tiene tickets (se borraron todos), se vuelve a «Todas».
    val selectedStore = selectedStoreKey?.takeIf { key -> stores.any { it.first == key } }

    val filtered = remember(state.purchases, selectedStore) { purchasesOfStore(state.purchases, selectedStore) }
    val sections = remember(allTickets, selectedStore) {
        ticketsByMonth(allTickets.filter { selectedStore == null || it.storeKey == selectedStore })
    }
    val chart = remember(filtered, now) { lastMonthsSpend(filtered, CHART_MONTHS, now) }
    // null = todos los meses. Sin elegir: con un súper, todos sus meses (para compararlos); con
    // «Todas», el mes más reciente. Un mes que ya no existe con este filtro cae en lo mismo.
    val month: String? = when (val key = selectedMonthKey) {
        ALL_MONTHS -> null
        else -> key?.takeIf { k -> sections.any { it.yearMonth == k } || chart.any { it.yearMonth == k } }
            ?: if (selectedStore != null) null else sections.firstOrNull()?.yearMonth
    }
    val summary = remember(filtered, month) { month?.let { monthSummary(filtered, monthStart(it)) } }
    val overview = remember(filtered, now) { storeOverview(filtered, now) }
    val shares = remember(state.purchases, month) {
        if (month == null) storeShares(state.purchases) else spendByStore(state.purchases, monthStart(month))
    }
    val visibleSections = if (month == null) sections else sections.filter { it.yearMonth == month }
    val dayFormat = remember { SimpleDateFormat("d MMM", esLocale) }
    val monthTitle = remember { SimpleDateFormat("LLLL yyyy", esLocale) }
    val monthChip = remember { SimpleDateFormat("LLL yyyy", esLocale) }
    val monthsRowState = rememberLazyListState()
    // Si el mes se elige desde la gráfica, su pestaña puede estar fuera de la vista: se lleva a ella.
    LaunchedEffect(month, sections) {
        val index = if (month == null) 0 else sections.indexOfFirst { it.yearMonth == month } + 1
        if (index >= 0) monthsRowState.animateScrollToItem(index)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Abajo, hueco para que el botón de la cámara no tape la última fila.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "stores") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedStore == null,
                    onClick = { selectedStoreKey = null },
                    label = { Text(stringResource(R.string.purchase_tickets_all)) }
                )
                stores.forEach { (key, name) ->
                    FilterChip(
                        selected = selectedStore == key,
                        onClick = { selectedStoreKey = key },
                        leadingIcon = { ColorDot(storeColors.colorOf(key)) },
                        label = { Text(name.ifEmpty { stringResource(R.string.purchase_history_no_store) }) }
                    )
                }
            }
        }
        // Pestañas de meses (las de antes, ahora con el gasto de cada mes para este filtro).
        item(key = "months") {
            LazyRow(state = monthsRowState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = ALL_MONTHS) {
                    FilterChip(
                        selected = month == null,
                        onClick = { selectedMonthKey = ALL_MONTHS },
                        label = { Text(stringResource(R.string.spend_all_months)) }
                    )
                }
                items(sections, key = { it.yearMonth }) { section ->
                    FilterChip(
                        selected = section.yearMonth == month,
                        onClick = { selectedMonthKey = section.yearMonth },
                        label = {
                            Text(
                                monthChip.format(monthStart(section.yearMonth)).replaceFirstChar { it.uppercase() } +
                                    " · " + String.format(esLocale, "%,.0f €", section.total)
                            )
                        }
                    )
                }
            }
        }
        item(key = "summary") {
            val title = month?.let { monthTitle.format(monthStart(it)).replaceFirstChar { c -> c.uppercase() } }
                ?: stringResource(R.string.spend_all_months)
            SummaryCard(
                title = title,
                total = summary?.total ?: overview.total,
                previousTotal = summary?.previousTotal,
                lines = buildList {
                    if (summary != null) {
                        add(
                            pluralStringResource(
                                R.plurals.stats_tickets_avg, summary.ticketCount, summary.ticketCount, euros(summary.averagePerTicket)
                            )
                        )
                        if (selectedStore != null) {
                            add(
                                pluralStringResource(
                                    R.plurals.spend_store_overview, overview.ticketCount,
                                    euros(overview.total), overview.ticketCount, euros(overview.monthlyAverage)
                                )
                            )
                        }
                    } else {
                        add(
                            pluralStringResource(
                                R.plurals.spend_all_overview, overview.ticketCount,
                                overview.ticketCount, euros(overview.monthlyAverage)
                            )
                        )
                    }
                },
                bars = {
                    SpendMonthBars(
                        months = chart,
                        selectedYearMonth = month,
                        onSelect = { selectedMonthKey = it },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            )
        }
        if (selectedStore == null && shares.size > 1) {
            item(key = "shares") {
                StoreShares(
                    shares = shares,
                    colors = storeColors,
                    onSelect = { selectedStoreKey = it }
                )
            }
        }
        if (month != null) {
            item(key = "ticketsHeader") {
                SpendSectionTitle(
                    stringResource(R.string.spend_month_tickets, SimpleDateFormat("LLLL", esLocale).format(monthStart(month)))
                )
            }
            if (visibleSections.isEmpty()) {
                item(key = "noTickets") {
                    Text(
                        stringResource(R.string.spend_month_no_tickets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        visibleSections.forEach { section ->
            // Con todos los meses, cada mes lleva su cabecera (tocarla abre ese mes).
            if (month == null) {
                item(key = "header/${section.yearMonth}") {
                    MonthHeader(
                        title = monthTitle.format(monthStart(section.yearMonth)).uppercase(esLocale),
                        detail = pluralStringResource(
                            R.plurals.spend_month_header, section.tickets.size, section.tickets.size, euros(section.total)
                        ),
                        onClick = { selectedMonthKey = section.yearMonth }
                    )
                }
            }
            items(section.tickets, key = { it.key }) { ticket ->
                TicketCard(
                    ticket = ticket,
                    color = storeColors.colorOf(ticket.storeKey),
                    dateText = dayFormat.format(ticket.date),
                    onOpen = { onOpenTicket(ticket.key) },
                    onDelete = { ticketPendingDelete = ticket }
                )
            }
        }
        item(key = "moreHeader") { SpendSectionTitle(stringResource(R.string.spend_more)) }
        item(key = "products") {
            MoreRow(
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                title = stringResource(R.string.spend_all_products),
                subtitle = stringResource(R.string.spend_all_products_hint),
                onClick = onOpenProducts
            )
        }
        item(key = "stats") {
            MoreRow(
                icon = { Icon(Icons.Filled.Insights, contentDescription = null) },
                title = stringResource(R.string.stats_title),
                subtitle = stringResource(R.string.spend_stats_hint),
                onClick = onOpenStats
            )
        }
    }

    val pending = ticketPendingDelete
    if (pending != null) {
        DeleteTicketDialog(
            ticket = pending,
            onConfirm = {
                viewModel.deleteTicket(pending.key)
                ticketPendingDelete = null
            },
            onDismiss = { ticketPendingDelete = null }
        )
    }
}

/** Resumen del periodo elegido: total, comparación con el mes anterior (si es un mes) y la gráfica. */
@Composable
private fun SummaryCard(
    title: String,
    total: Double,
    previousTotal: Double?,
    lines: List<String>,
    bars: @Composable () -> Unit
) {
    SpendHeaderCard {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(euros(total), style = MaterialTheme.typography.headlineMedium)
            previousTotal?.let { previous ->
                val diff = total - previous
                val arrow = if (diff > 0) "▲" else "▼"
                val sign = if (diff >= 0) "+" else "−"
                val pct = if (previous > 0) " ($sign${(abs(diff) / previous * 100).roundToInt()} %)" else ""
                Text(
                    "$arrow $sign${euros(abs(diff))}$pct",
                    style = MaterialTheme.typography.labelLarge,
                    // Gastar más es la alerta (coral); gastar menos, buena noticia.
                    color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        lines.forEachIndexed { index, line ->
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = if (index == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp)
            )
        }
        bars()
    }
}

/** Reparto del gasto (del mes o de siempre) entre supermercados: barra apilada y una fila por súper (tocar = filtrar). */
@Composable
private fun StoreShares(
    shares: List<StoreSpend>,
    colors: Map<String, Color>,
    onSelect: (String) -> Unit
) {
    val fallback = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SpendSectionTitle(stringResource(R.string.spend_by_store))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp))
        ) {
            shares.filter { it.fraction > 0 }.forEach { share ->
                Box(
                    Modifier.weight(share.fraction.toFloat()).fillMaxHeight()
                        .background(colors[share.storeKey] ?: fallback)
                )
            }
        }
        shares.forEach { share ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(share.storeKey) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorDot(colors[share.storeKey] ?: fallback)
                Text(
                    share.displayName.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Text(
                    "${euros(share.total)} · ${(share.fraction * 100).roundToInt()} %",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TicketCard(
    ticket: Ticket,
    color: Color,
    dateText: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    StripedCard(color = color, onClick = onOpen) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ticket.store.ifEmpty { stringResource(R.string.purchase_history_no_store) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(text = euros(ticket.total), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = pluralStringResource(R.plurals.spend_ticket_line, ticket.purchases.size, dateText, ticket.purchases.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (ticket.possibleDuplicate) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.purchase_tickets_duplicate),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.purchase_tickets_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MoreRow(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = icon,
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
