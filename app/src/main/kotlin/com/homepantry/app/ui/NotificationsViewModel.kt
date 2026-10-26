package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.AppNotification
import com.homepantry.app.data.Item
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.NotificationsRepository
import com.homepantry.app.data.OfferMatch
import com.homepantry.app.data.Zone
import com.homepantry.app.data.ZonesRepository
import com.homepantry.app.data.isUnread
import com.homepantry.app.data.planOfferAdds
import com.homepantry.app.data.unreadCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<AppNotification> = emptyList(),
    val items: List<Item> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val unreadCount: Int = 0,
    val error: String? = null
)

/**
 * Avisos de ofertas: lista, leído por miembro y alta de ofertas en la lista de la compra.
 * Va aparte de AppViewModel (como Menu y Split); lee productos y zonas por su cuenta porque los
 * necesita para saber qué está ya en la lista y en qué zona poner lo nuevo.
 */
class NotificationsViewModel(
    private val notificationsRepository: NotificationsRepository,
    private val itemsRepository: ItemsRepository,
    zonesRepository: ZonesRepository,
    private val uid: String,
    private var userName: String
) : ViewModel() {

    private val notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    private val items = MutableStateFlow<List<Item>>(emptyList())
    private val zones = MutableStateFlow<List<Zone>>(emptyList())
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<NotificationsUiState> = combine(notifications, items, zones, error) { n, i, z, err ->
        NotificationsUiState(notifications = n, items = i, zones = z, unreadCount = unreadCount(n, uid), error = err)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState())

    init {
        viewModelScope.launch {
            notificationsRepository.observeNotifications()
                .catch { e -> error.value = e.message }
                .collect { notifications.value = it }
        }
        viewModelScope.launch {
            itemsRepository.observeItems()
                .catch { e -> error.value = e.message }
                .collect { items.value = it }
        }
        viewModelScope.launch {
            zonesRepository.observeZones()
                .catch { e -> error.value = e.message }
                .collect { zones.value = it }
        }
    }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message ?: e.toString()
        }
    }

    /** Nombre nuevo tras cambiarlo en Ajustes (mismo motivo que `AppViewModel.updateUserName`). */
    fun updateUserName(name: String) {
        userName = name
    }

    fun clearError() {
        error.value = null
    }

    /** Solo escribe si hace falta: abrir un aviso ya leído no genera escrituras. */
    fun markRead(id: String) {
        val notification = notifications.value.firstOrNull { it.id == id } ?: return
        if (!isUnread(notification, uid)) return
        launchCatching { notificationsRepository.markRead(id, uid) }
    }

    /**
     * Añade a la lista las ofertas marcadas ([selected]: pares tienda–oferta) y las apunta en el
     * aviso para que salgan como "añadida". [onDone] recibe cuántas se añadieron (para el snackbar).
     */
    fun addOffers(notificationId: String, selected: List<Pair<String, OfferMatch>>, onDone: (Int) -> Unit) =
        launchCatching {
            if (selected.isEmpty()) return@launchCatching
            val plan = planOfferAdds(selected, items.value, zones.value, userName)
            itemsRepository.addItems(plan.newItems)
            plan.reactivateIds.forEach { itemsRepository.setDone(it, false) }
            notificationsRepository.markAdded(notificationId, selected.map { it.second.key })
            onDone(selected.size)
        }
}
