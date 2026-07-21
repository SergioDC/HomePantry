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
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val listViewMode: ListViewMode = ListViewMode.GROUPED,
    val loading: Boolean = true,
    val error: String? = null
) {
    val progress: String get() = progressText(items)
    val sections: List<ZoneSection> get() = groupAndSort(items, zones, selectedZoneId, sortMode)
    val flatRows: List<FlatRow> get() {
        val zoneFiltered = if (selectedZoneId == "ALL") items else items.filter { it.zone == selectedZoneId }
        return flattenAndSort(zoneFiltered, zones, sortMode)
    }
    val zoneCards: List<ZoneSummary> get() = zoneSummaries(items, zones)
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

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
    }

    fun setListViewMode(mode: ListViewMode) {
        _state.value = _state.value.copy(listViewMode = mode)
    }

    private suspend fun uploadAndAttachPhoto(itemId: String, localUri: android.net.Uri) {
        val url = storageRepository.uploadPhoto(itemId, localUri)
        itemsRepository.updatePhotoUrl(itemId, url)
    }

    /**
     * Crea el producto y, si hay foto local, la sube y adjunta -- todo en
     * viewModelScope para que sobreviva aunque AddItemSheet se cierre antes de
     * terminar (evita perder la foto al guardar offline).
     */
    fun createItem(item: Item, localPhotoUri: android.net.Uri?) = viewModelScope.launch {
        val newItemId = itemsRepository.addItem(item.copy(addedBy = userName))
        if (localPhotoUri != null) {
            runCatching { uploadAndAttachPhoto(newItemId, localPhotoUri) }
        }
    }

    /**
     * Guarda los cambios de un producto ya existente y, si hay foto nueva, la
     * sube y adjunta -- mismo motivo que createItem: todo en viewModelScope.
     */
    fun editItem(item: Item, localPhotoUri: android.net.Uri? = null) = viewModelScope.launch {
        itemsRepository.updateItem(item)
        if (localPhotoUri != null) {
            runCatching { uploadAndAttachPhoto(item.id, localPhotoUri) }
        }
    }

    /** Suma cantidad a un producto pendiente ya existente en vez de duplicarlo (cierre de huecos §3). */
    fun incrementQty(item: Item, addQty: Double) = viewModelScope.launch {
        itemsRepository.updateItem(item.copy(qty = item.qty + addQty))
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
