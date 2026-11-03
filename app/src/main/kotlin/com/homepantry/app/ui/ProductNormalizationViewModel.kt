package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.NAMER_BATCH_SIZE
import com.homepantry.app.data.NormalizationRow
import com.homepantry.app.data.ProductAlias
import com.homepantry.app.data.ProductFieldsUpdate
import com.homepantry.app.data.ProductProposal
import com.homepantry.app.data.Purchase
import com.homepantry.app.data.applyPlan
import com.homepantry.app.data.nameProductsWithGemini
import com.homepantry.app.data.pendingRawNames
import com.homepantry.app.data.rowsFromProposals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Normalización del histórico con revisión (flujo 2 del spec). Vive a nivel de actividad, como
 * la lectura de tickets, para que las propuestas de Gemini (lentas y de pago) no se pierdan al
 * girar el móvil ni al salir de la pantalla.
 */
class ProductNormalizationViewModel : ViewModel() {

    private val _rows = MutableStateFlow<List<NormalizationRow>>(emptyList())
    val rows: StateFlow<List<NormalizationRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Prepara las filas de lo que falta por normalizar: las que resuelve un alias, al momento, y
     * el resto pidiéndolas a Gemini por tandas. Si una tanda falla se para y lo recibido se queda
     * para revisar; volver a llamar solo pide los nombres que aún no tienen fila.
     */
    fun start(
        purchases: List<Purchase>,
        aliases: Map<String, ProductAlias>,
        apiKeyStore: GeminiApiKeyStore,
        needsKeyMessage: String
    ) {
        if (_loading.value || _applying.value) return
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val (aliasRows, names) = pendingRawNames(purchases, aliases)
                val knownKeys = _rows.value.map { it.key }.toSet()
                _rows.value = _rows.value + aliasRows.filter { it.key !in knownKeys }
                val requested = _rows.value.map { it.rawName }.toSet()
                val todo = names.filter { it !in requested }
                if (todo.isEmpty()) return@launch
                val apiKey = apiKeyStore.getApiKey()
                if (apiKey == null) {
                    _error.value = needsKeyMessage
                    return@launch
                }
                val model = apiKeyStore.getModel()
                todo.chunked(NAMER_BATCH_SIZE).forEach { batch ->
                    val proposals = nameProductsWithGemini(batch, apiKey, model)
                    _rows.value = _rows.value + rowsFromProposals(purchases, batch, proposals)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _loading.value = false
            }
        }
    }

    fun editRow(key: String, final: ProductProposal) {
        _rows.value = _rows.value.map { if (it.key == key) it.copy(final = final) else it }
    }

    fun toggleRow(key: String) {
        _rows.value = _rows.value.map { if (it.key == key) it.copy(selected = !it.selected) else it }
    }

    /** Guarda las filas marcadas con [save]; solo vacía la revisión si ha ido bien. */
    fun apply(save: suspend (List<ProductFieldsUpdate>, List<ProductAlias>) -> Boolean, onDone: () -> Unit) {
        if (_applying.value || _loading.value) return
        val (updates, aliases) = applyPlan(_rows.value)
        if (updates.isEmpty()) return
        _applying.value = true
        viewModelScope.launch {
            try {
                if (save(updates, aliases)) {
                    _rows.value = emptyList()
                    onDone()
                }
            } finally {
                _applying.value = false
            }
        }
    }
}
