package com.homepantry.app.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.homepantry.app.R
import com.homepantry.app.data.OfferMatch
import com.homepantry.app.data.OfferRowState
import com.homepantry.app.data.isTrustedOfferUrl
import com.homepantry.app.data.offerRowState
import com.homepantry.app.ui.NotificationsViewModel
import com.homepantry.app.ui.components.euros
import kotlinx.coroutines.launch

/**
 * Detalle de un aviso de ofertas: ofertas por tienda con casilla, enlace al folleto y botón para
 * añadir las marcadas a la lista. Al abrirlo se marca como leído solo para este miembro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    viewModel: NotificationsViewModel,
    notificationId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val notification = state.notifications.firstOrNull { it.id == notificationId }
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Se espera a que el aviso esté cargado: markRead lo busca en el estado.
    LaunchedEffect(notificationId, notification != null) {
        if (notification != null) viewModel.markRead(notificationId)
    }

    // Por defecto van marcadas las ofertas nuevas. Se guardan solo las casillas que el usuario
    // cambia: así el valor por defecto se recalcula si la lista llega después que el aviso (o la
    // cambia otro miembro) y una oferta que ya está en la lista no sale marcada por error.
    var toggled by rememberSaveable(notificationId) { mutableStateOf(emptySet<String>()) }
    fun isChecked(offer: OfferMatch, rowState: OfferRowState) =
        rowState != OfferRowState.ADDED && ((rowState == OfferRowState.NEW) xor (offer.key in toggled))

    val validFrom = notification?.stores?.mapNotNull { it.validFrom.ifBlank { null } }?.minOrNull()
    val validUntil = notification?.stores?.mapNotNull { it.validUntil.ifBlank { null } }?.maxOrNull()
    val title = if (validFrom != null && validUntil != null) {
        stringResource(R.string.notification_title_range, dayMonthLabel(validFrom), dayMonthLabel(validUntil))
    } else {
        notification?.title.orEmpty()
    }
    val selectable = notification?.stores.orEmpty().flatMap { s -> s.offers.map { s.store to it } }
        .filter { (_, offer) -> isChecked(offer, offerRowState(offer, notification!!.addedKeys, state.items)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (notification != null) {
                Surface(tonalElevation = 3.dp) {
                    val addedMessage = { n: Int -> resources.getQuantityString(R.plurals.notification_added_snackbar, n, n) }
                    Button(
                        onClick = {
                            viewModel.addOffers(notificationId, selectable) { n ->
                                scope.launch { snackbarHostState.showSnackbar(addedMessage(n)) }
                            }
                        },
                        enabled = selectable.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(pluralStringResource(R.plurals.notification_add_button, selectable.size, selectable.size))
                    }
                }
            }
        }
    ) { padding ->
        if (notification == null) {
            Text(
                text = stringResource(R.string.notification_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            notification.stores.forEach { store ->
                item(key = "store-${store.store}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(store.store, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (isTrustedOfferUrl(store.url)) {
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, store.url.toUri())) }
                            }) {
                                Text(stringResource(if (store.store == "Lidl") R.string.notification_open_leaflet else R.string.notification_open_offers))
                            }
                        }
                    }
                }
                items(store.offers, key = { it.key }) { offer ->
                    val rowState = offerRowState(offer, notification.addedKeys, state.items)
                    OfferRow(
                        offer = offer,
                        rowState = rowState,
                        checked = isChecked(offer, rowState),
                        onCheckedChange = { toggled = if (offer.key in toggled) toggled - offer.key else toggled + offer.key }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferRow(
    offer: OfferMatch,
    rowState: OfferRowState,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val enabled = rowState != OfferRowState.ADDED
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                Text(offer.habitualLabel.ifBlank { offer.habitual }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val details = buildList {
                        add(offer.product)
                        offer.price?.let { add(euros(it)) }
                        if (offer.price == null && offer.page != null) add(stringResource(R.string.notification_see_page, offer.page))
                    }.joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    offer.oldPrice?.let {
                        Text(
                            euros(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                val extra = buildList {
                    offer.promo?.let { add(it) }
                    if (offer.price != null && offer.page != null) add(stringResource(R.string.notification_page, offer.page))
                    when (rowState) {
                        OfferRowState.IN_LIST -> add(stringResource(R.string.notification_in_list))
                        OfferRowState.ADDED -> add(stringResource(R.string.notification_added))
                        OfferRowState.NEW -> Unit
                    }
                }
                if (extra.isNotEmpty()) {
                    Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
