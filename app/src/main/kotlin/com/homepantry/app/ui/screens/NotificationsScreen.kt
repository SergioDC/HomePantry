package com.homepantry.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.homepantry.app.R
import com.homepantry.app.data.AppNotification
import com.homepantry.app.data.isUnread
import com.homepantry.app.ui.NotificationsViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-ES")

/** "2026-10-26" → "Lunes 26 oct". El nombre corto del mes en español lleva punto en Java; se quita. */
internal fun weekdayLabel(isoDate: String): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, spanish).replaceFirstChar { it.uppercase() }
    return "$weekday ${dayMonthLabel(isoDate)}"
}

/** "2026-11-01" → "1 nov". */
internal fun dayMonthLabel(isoDate: String): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    return "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, spanish).trimEnd('.')}"
}

@Composable
internal fun storesLabel(notification: AppNotification): String {
    val names = notification.stores.map { it.store }
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> stringResource(R.string.notifications_stores_join, names.dropLast(1).joinToString(", "), names.last())
    }
}

/** Lista de avisos de la casa, más recientes primero. Los no leídos por este miembro van marcados. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    uid: String,
    onBack: () -> Unit,
    onOpenNotification: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    // El permiso de notificaciones se pide aquí y no al arrancar: así se entiende para qué es.
    // Si se deniega, la campana sigue funcionando. Solo hace falta desde Android 13.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!permission.status.isGranted && !permission.status.shouldShowRationale) {
                permission.launchPermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zones_back_cd))
                    }
                }
            )
        }
    ) { padding ->
        if (state.notifications.isEmpty()) {
            Text(
                text = stringResource(R.string.notifications_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.notifications, key = { it.id }) { notification ->
                val unread = isUnread(notification, uid)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenNotification(notification.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = if (unread) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = stringResource(R.string.notifications_week, weekdayLabel(notification.weekStart), storesLabel(notification)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
