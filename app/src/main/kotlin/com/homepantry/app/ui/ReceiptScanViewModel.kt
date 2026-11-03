package com.homepantry.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.ParsedReceipt
import com.homepantry.app.data.ReceiptScanDraft
import com.homepantry.app.data.ReceiptScanDraftStore
import com.homepantry.app.data.detectReceiptDate
import com.homepantry.app.data.parseReceiptLines
import com.homepantry.app.data.receiptScanDraft
import com.homepantry.app.data.recognizeReceiptTextLines
import com.homepantry.app.data.ProductAlias
import com.homepantry.app.data.recognizeReceiptWithGemini
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Textos (ya resueltos) de los avisos de la lectura del ticket. */
data class ReceiptScanMessages(
    /** Gemini falló y se usó el OCR clásico; recibe el motivo. */
    val geminiFallback: (String) -> String,
    val ocrError: String,
    val ocrEmpty: String
)

/**
 * Lectura de un ticket y su borrador de revisión. Antes vivían en el estado de la pantalla de
 * Gastos y se perdían al girar el móvil o salir de ella. Aquí la lectura corre en el scope del
 * ViewModel (sigue aunque se salga de la pantalla) y el borrador se guarda en disco, como el
 * del menú, porque Gemini es lento y de pago.
 */
@OptIn(FlowPreview::class)
class ReceiptScanViewModel(private val draftStore: ReceiptScanDraftStore) : ViewModel() {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _draft = MutableStateFlow<ReceiptScanDraft?>(null)
    val draft: StateFlow<ReceiptScanDraft?> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            // Primero se recupera el borrador del disco y después se guarda cada cambio (agrupando
            // las pulsaciones seguidas); en este orden el null inicial no lo borra.
            val saved = draftStore.load()
            if (saved != null && _draft.value == null && !_scanning.value) _draft.value = saved
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

    /**
     * Lee el ticket de [uri] con Gemini (si hay clave) y, si falla o no hay clave, con el OCR
     * clásico. El resultado queda como borrador para revisarlo. Ignora la llamada si ya hay una
     * lectura en marcha o un borrador sin cerrar.
     */
    fun scanReceipt(
        context: Context,
        uri: Uri,
        apiKeyStore: GeminiApiKeyStore,
        messages: ReceiptScanMessages,
        aliases: Map<String, ProductAlias> = emptyMap()
    ) {
        if (_scanning.value || _draft.value != null) return
        _scanning.value = true
        // Solo el contexto de la aplicación: el de la actividad no debe sobrevivir en el ViewModel.
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val apiKey = apiKeyStore.getApiKey()
                var parsed = ParsedReceipt(store = null, lines = emptyList())
                var geminiFailureReason: String? = null

                if (apiKey != null) {
                    try {
                        parsed = recognizeReceiptWithGemini(appContext, uri, apiKey, apiKeyStore.getModel())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        geminiFailureReason = e.message ?: "error desconocido"
                    }
                }

                var classicFailed = false
                if (apiKey == null || geminiFailureReason != null) {
                    val textLines = try {
                        recognizeReceiptTextLines(appContext, uri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        classicFailed = true
                        emptyList()
                    }
                    parsed = ParsedReceipt(
                        store = null,
                        lines = parseReceiptLines(textLines),
                        date = detectReceiptDate(textLines, Date())
                    )
                }

                // El aviso va dentro de la hoja de revisión: un Snackbar quedaría detrás de ella.
                val notice = when {
                    geminiFailureReason != null -> messages.geminiFallback(geminiFailureReason)
                    classicFailed -> messages.ocrError
                    parsed.lines.isEmpty() -> messages.ocrEmpty
                    else -> null
                }
                _draft.value = receiptScanDraft(parsed, uri.toString(), notice, aliases)
            } finally {
                _scanning.value = false
            }
        }
    }

    /** Cambios del usuario al revisar (supermercado, fecha, líneas). */
    fun updateDraft(draft: ReceiptScanDraft) {
        if (_draft.value != null) _draft.value = draft
    }

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    /**
     * Guarda el borrador con [save] y solo lo descarta si ha ido bien; si falla se queda para
     * reintentar. Corre aquí y no en la hoja: girar el móvil a mitad no deja el ticket a medias
     * ni lo vuelve a ofrecer ya guardado (lo que acabaría duplicándolo).
     */
    fun saveDraft(save: suspend (ParsedReceipt, Uri?, List<ProductAlias>) -> Boolean) {
        val draft = _draft.value ?: return
        if (_saving.value) return
        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                if (save(draft.toParsedReceipt(), draft.photoUri?.let(Uri::parse), draft.aliasesToSave())) {
                    _draft.value = null
                } else {
                    _saveFailed.value = true
                }
            } finally {
                _saving.value = false
            }
        }
    }

    /** Se descarta (o ya se ha guardado): el borrador sobra. */
    fun discardDraft() {
        if (_saving.value) return
        _draft.value = null
        _saveFailed.value = false
    }
}
