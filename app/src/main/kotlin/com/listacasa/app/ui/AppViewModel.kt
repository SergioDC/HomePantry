package com.listacasa.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listacasa.app.data.Item
import com.listacasa.app.data.ItemsRepository
import com.listacasa.app.data.StorageRepository
import com.listacasa.app.data.Zone
import com.listacasa.app.data.ZonesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val items: List<Item> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val selectedZoneId: String = "ALL",
    val loading: Boolean = true,
    val error: String? = null
) {
    val progress: String get() = progressText(items)
    val sections: List<ZoneSection> get() = groupAndSort(items, zones, selectedZoneId)
}

/**
 * Une ItemsRepository/ZonesRepository/StorageRepository en un único StateFlow
 * para las pantallas de Compose. Sin login tradicional: el nombre de usuario
 * ya viene resuelto desde DataStore antes de crear este ViewModel.
 */
class AppViewModel(
    private val itemsRepository: ItemsRepository,
    private val zonesRepository: ZonesRepository,
    val storageRepository: StorageRepository,
    private val userName: String
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { zonesRepository.seedIfEmpty() }
        }
        viewModelScope.launch {
            runCatching {
                itemsRepository.observeItems().collect { items ->
                    _state.value = _state.value.copy(items = items, loading = false, error = null)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
        viewModelScope.launch {
            runCatching {
                zonesRepository.observeZones().collect { zones ->
                    _state.value = _state.value.copy(zones = zones)
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun selectZone(zoneId: String) {
        _state.value = _state.value.copy(selectedZoneId = zoneId)
    }

    /** @return el id Firestore del producto creado, para poder subir su foto después. */
    suspend fun addItem(item: Item): String =
        itemsRepository.addItem(item.copy(addedBy = userName))

    /** Sube la foto y adjunta su URL al producto ya creado. */
    fun attachPhoto(itemId: String, localUri: android.net.Uri) = viewModelScope.launch {
        val url = storageRepository.uploadPhoto(itemId, localUri)
        itemsRepository.updatePhotoUrl(itemId, url)
    }

    fun toggleDone(item: Item) = viewModelScope.launch {
        itemsRepository.updateItem(item.copy(done = !item.done))
    }

    fun deleteItem(itemId: String) = viewModelScope.launch {
        itemsRepository.deleteItem(itemId)
    }

    fun clearDone() = viewModelScope.launch {
        val doneIds = _state.value.items.filter { it.done }.map { it.id }
        itemsRepository.clearDone(doneIds)
    }

    fun createZone(name: String) = viewModelScope.launch {
        val nextOrder = (_state.value.zones.maxOfOrNull { it.order } ?: -1) + 1
        zonesRepository.addZone(name, nextOrder)
    }

    fun renameZone(zoneId: String, newName: String) = viewModelScope.launch {
        zonesRepository.renameZone(zoneId, newName)
    }

    /** Reasigna los productos de la zona eliminada a "Otros" antes de borrarla (SPEC.md sec 1.3). */
    fun deleteZone(zoneId: String, otrosZoneId: String) = viewModelScope.launch {
        if (_state.value.zones.size <= 1) return@launch
        itemsRepository.reassignZone(zoneId, otrosZoneId)
        zonesRepository.deleteZone(zoneId)
        if (_state.value.selectedZoneId == zoneId) {
            _state.value = _state.value.copy(selectedZoneId = "ALL")
        }
    }
}
