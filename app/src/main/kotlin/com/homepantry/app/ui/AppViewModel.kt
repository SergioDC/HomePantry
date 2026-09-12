package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.DefaultProduct
import com.homepantry.app.data.Item
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.Member
import com.homepantry.app.data.MembersRepository
import com.homepantry.app.data.PROTECTED_ZONE_NAME
import com.homepantry.app.data.StorageRepository
import com.homepantry.app.data.Zone
import com.homepantry.app.data.ZonesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val items: List<Item> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val selectedZoneId: String = "ALL",
    val selectedStore: String = "ALL",
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val listViewMode: ListViewMode = ListViewMode.GROUPED,
    val loading: Boolean = true,
    val error: String? = null,
    val members: List<Member> = emptyList()
) {
    val progress: String get() = progressText(items)

    // Lista de la compra: solo lo pendiente. Un producto "done" = ya lo tienes (lo compraste
    // o lo añadiste directamente a una Zona como inventario), así que no tiene sentido que
    // siga apareciendo aquí -- Zone Detail sí sigue mostrando todo (es el inventario real).
    private val pendingItems: List<Item> get() = items.filter { !it.done }

    /** Tiendas presentes entre lo pendiente, para el chip de filtro (versión ligera, sin gestión de tiendas). */
    val pendingStores: List<String> get() = pendingItems.mapNotNull { it.store }.distinct().sorted()

    private val storeFilteredPendingItems: List<Item> get() =
        if (selectedStore == "ALL") pendingItems else pendingItems.filter { it.store == selectedStore }

    val sections: List<ZoneSection> get() = groupAndSort(storeFilteredPendingItems, zones, selectedZoneId, sortMode)
    val flatRows: List<FlatRow> get() {
        val zoneFiltered = if (selectedZoneId == "ALL") storeFilteredPendingItems else storeFilteredPendingItems.filter { it.zone == selectedZoneId }
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
    private val membersRepository: MembersRepository,
    val storageRepository: StorageRepository,
    private val userName: String,
    private val uid: String
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
        viewModelScope.launch {
            runCatching { membersRepository.upsertSelf(uid, userName) }
        }
        viewModelScope.launch {
            runCatching {
                membersRepository.observeMembers().collect { members ->
                    _state.value = _state.value.copy(members = members)
                }
            }
        }
    }

    fun selectZone(zoneId: String) {
        _state.value = _state.value.copy(selectedZoneId = zoneId)
    }

    fun selectStore(store: String) {
        _state.value = _state.value.copy(selectedStore = store)
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

    fun clearError() {
        _state.value = _state.value.copy(error = null)
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
        runCatching {
            val newItemId = itemsRepository.addItem(item.copy(addedBy = userName))
            if (localPhotoUri != null) {
                runCatching { uploadAndAttachPhoto(newItemId, localPhotoUri) }
                    .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
            }
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    /**
     * Guarda los cambios de un producto ya existente y, si hay foto nueva, la
     * sube y adjunta -- mismo motivo que createItem: todo en viewModelScope.
     */
    fun editItem(item: Item, localPhotoUri: android.net.Uri? = null) = viewModelScope.launch {
        runCatching {
            itemsRepository.updateItem(item)
            if (localPhotoUri != null) {
                runCatching { uploadAndAttachPhoto(item.id, localPhotoUri) }
                    .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
            }
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    /** Suma cantidad a un producto pendiente ya existente en vez de duplicarlo (cierre de huecos §3). */
    fun incrementQty(item: Item, addQty: Double) = viewModelScope.launch {
        runCatching { itemsRepository.updateItem(item.copy(qty = item.qty + addQty)) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun toggleDone(item: Item) = viewModelScope.launch {
        runCatching { itemsRepository.updateItem(item.copy(done = !item.done)) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun deleteItem(itemId: String) = viewModelScope.launch {
        runCatching { itemsRepository.deleteItem(itemId) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    /**
     * Alta masiva desde DefaultProductsSheet. Mapea cada `zoneName` sugerido a
     * la zona real del hogar por nombre (puede no existir si el usuario la
     * renombró/eliminó), con fallback a "Otros".
     */
    fun addDefaultItems(selected: List<DefaultProduct>) = viewModelScope.launch {
        if (selected.isEmpty()) return@launch
        val zones = _state.value.zones
        val zonesByName = zones.associateBy { it.name.lowercase() }
        val fallbackZoneId = zonesByName[PROTECTED_ZONE_NAME.lowercase()]?.id ?: zones.firstOrNull()?.id
        if (fallbackZoneId == null) return@launch
        val items = selected.map { product ->
            val zoneId = zonesByName[product.zoneName.lowercase()]?.id ?: fallbackZoneId
            Item(name = product.name, qty = 1.0, unit = product.unit, zone = zoneId, addedBy = userName)
        }
        runCatching { itemsRepository.addItems(items) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun createZone(name: String, parentZoneId: String? = null) = viewModelScope.launch {
        val nextOrder = (_state.value.zones.maxOfOrNull { it.order } ?: -1) + 1
        runCatching { zonesRepository.addZone(name, nextOrder, parentZoneId) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun renameZone(zoneId: String, newName: String) = viewModelScope.launch {
        runCatching { zonesRepository.renameZone(zoneId, newName) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    fun updateZoneColor(zoneId: String, colorHex: String) = viewModelScope.launch {
        runCatching { zonesRepository.updateZoneColor(zoneId, colorHex) }
            .onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }

    /** Reasigna los productos de la zona eliminada a "Otros" antes de borrarla (SPEC.md sec 1.3). */
    fun deleteZone(zoneId: String, otrosZoneId: String) = viewModelScope.launch {
        if (_state.value.zones.size <= 1) return@launch
        runCatching {
            itemsRepository.reassignZone(zoneId, otrosZoneId)
            zonesRepository.deleteZone(zoneId)
            if (_state.value.selectedZoneId == zoneId) {
                _state.value = _state.value.copy(selectedZoneId = "ALL")
            }
        }.onFailure { e -> _state.value = _state.value.copy(error = e.message) }
    }
}
