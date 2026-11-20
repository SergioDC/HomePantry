package com.homepantry.app.ui

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.CardImportDraft
import com.homepantry.app.data.CardImportDraftStore
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.cardImportDraft
import com.homepantry.app.data.recognizeCardChargesWithGemini
import com.homepantry.app.data.toExpenses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Lectura de capturas de cargos de tarjeta para SNcount y su borrador de revisión. Como
 * [ReceiptScanViewModel], vive a nivel de actividad (la lectura sigue aunque se salga de la
 * pantalla) y el borrador se guarda en disco, porque Gemini es lento y de pago.
 */
@OptIn(FlowPreview::class)
class CardImportViewModel(private val draftStore: CardImportDraftStore) : ViewModel() {

    private val _reading = MutableStateFlow(false)
    val reading: StateFlow<Boolean> = _reading.asStateFlow()

    private val _draft = MutableStateFlow<CardImportDraft?>(null)
    val draft: StateFlow<CardImportDraft?> = _draft.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** Grupo y capturas de la última lectura que falló, para reintentarla sin volver a elegirlas. */
    private val _failedMonthId = MutableStateFlow<String?>(null)
    val failedMonthId: StateFlow<String?> = _failedMonthId.asStateFlow()
    private var failedUris: List<Uri> = emptyList()

    init {
        viewModelScope.launch {
            // Primero se recupera el borrador y después se guarda cada cambio; en este orden el
            // null inicial no lo borra.
            val saved = draftStore.load()
            if (saved != null && _draft.value == null && !_reading.value) _draft.value = saved
            _draft.debounce(300).collectLatest { draft ->
                try {
                    draftStore.save(draft)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin disco el borrador sigue en memoria; no merece la pena molestar.
                }
            }
        }
    }

    /** Hay una lectura en marcha, un borrador o un fallo pendiente de otro grupo distinto de [monthId]. */
    fun busyWithOtherMonth(monthId: String): String? =
        _draft.value?.monthId?.takeIf { it != monthId }
            ?: _failedMonthId.value?.takeIf { it != monthId }

    /**
     * Lee [uris] con Gemini y deja el resultado como borrador. Ignora la llamada si ya hay una
     * lectura en marcha o un borrador sin cerrar.
     */
    fun read(
        context: Context,
        uris: List<Uri>,
        month: SplitMonth,
        existing: List<SplitExpense>,
        currentUid: String,
        apiKeyStore: GeminiApiKeyStore,
        noKeyMessage: String
    ) {
        if (_reading.value || _draft.value != null || uris.isEmpty()) return
        _reading.value = true
        _error.value = null
        failedUris = uris
        _failedMonthId.value = month.id
        // Solo el contexto de la aplicación: el de la actividad no debe sobrevivir en el ViewModel.
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val apiKey = apiKeyStore.getApiKey()
                if (apiKey == null) {
                    _error.value = noKeyMessage
                    return@launch
                }
                val movements = recognizeCardChargesWithGemini(appContext, uris, apiKey, apiKeyStore.getModel(), month)
                _draft.value = cardImportDraft(movements, month, existing, currentUid, uris.map { it.toString() })
                _failedMonthId.value = null
                failedUris = emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _reading.value = false
            }
        }
    }

    /** Vuelve a leer las capturas de la última lectura fallida. */
    fun retry(
        context: Context,
        month: SplitMonth,
        existing: List<SplitExpense>,
        currentUid: String,
        apiKeyStore: GeminiApiKeyStore,
        noKeyMessage: String
    ) {
        val uris = failedUris.ifEmpty { _draft.value?.imageUris?.map { it.toUri() }.orEmpty() }
        if (uris.isEmpty()) return
        read(context, uris, month, existing, currentUid, apiKeyStore, noKeyMessage)
    }

    /** Cambios del usuario al revisar (casillas, pagador, filas editadas). */
    fun updateDraft(draft: CardImportDraft) {
        if (_draft.value != null) _draft.value = draft
    }

    /**
     * Escribe los gastos marcados con [write] y solo descarta el borrador si ha ido bien; si falla
     * se queda para reintentar. Corre aquí y no en la pantalla para que girar el móvil a mitad no
     * deje el borrador ofreciendo otra vez gastos ya guardados.
     */
    fun save(month: SplitMonth, write: suspend (List<SplitExpense>) -> Unit) {
        val draft = _draft.value ?: return
        if (_saving.value) return
        val expenses = draft.toExpenses(month)
        if (expenses.isEmpty()) return
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                write(expenses)
                _draft.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _saving.value = false
            }
        }
    }

    /** Se descarta la importación (borrador o lectura fallida). */
    fun discard() {
        if (_saving.value || _reading.value) return
        _draft.value = null
        _error.value = null
        _failedMonthId.value = null
        failedUris = emptyList()
    }

    fun clearError() {
        _error.value = null
    }
}
