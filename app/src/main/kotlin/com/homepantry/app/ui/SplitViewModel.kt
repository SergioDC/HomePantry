package com.homepantry.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.Member
import com.homepantry.app.data.MemberBalance
import com.homepantry.app.data.MembersRepository
import com.homepantry.app.data.Settlement
import com.homepantry.app.data.SplitExpense
import com.homepantry.app.data.SplitExpenseError
import com.homepantry.app.data.SplitExpensesRepository
import com.homepantry.app.data.SplitMonth
import com.homepantry.app.data.SplitMonthsRepository
import com.homepantry.app.data.SplitPhoto
import com.homepantry.app.data.SplitPhotosRepository
import com.homepantry.app.data.StorageRepository
import com.homepantry.app.data.computeBalances
import com.homepantry.app.data.computeSettlements
import com.homepantry.app.data.monthTotalCents
import com.homepantry.app.data.parseCents
import com.homepantry.app.data.parseShares
import com.homepantry.app.data.validateSplitExpense
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SplitUiState(
    val months: List<SplitMonth> = emptyList(),
    /** Hasta el primer snapshot de meses no se sabe si la lista está vacía o aún cargando. */
    val monthsLoaded: Boolean = false,
    val members: List<Member> = emptyList(),
    val openMonthId: String? = null,
    /** Gastos del mes abierto, el más reciente primero. */
    val expenses: List<SplitExpense> = emptyList(),
    /**
     * Ya llegó el primer snapshot de gastos del mes abierto. Hasta entonces una lista vacía no
     * significa «sin gastos» (Editar no debe dejar cambiar el mes por eso).
     */
    val expensesLoaded: Boolean = false,
    /** Fotos del mes abierto. */
    val photos: List<SplitPhoto> = emptyList(),
    val uploadingPhoto: Boolean = false,
    val error: String? = null
) {
    val openMonth: SplitMonth? get() = months.firstOrNull { it.id == openMonthId }

    /** Lista principal: los no archivados, en el orden de [months]. */
    val activeMonths: List<SplitMonth> by lazy { months.filter { !it.archived } }
    val archivedMonths: List<SplitMonth> by lazy { months.filter { it.archived } }

    // Perezosas: Compose lee el estado muchas veces por fotograma y estas derivadas recorren
    // todos los gastos; cada copia del estado las recalcula una sola vez.
    val balances: List<MemberBalance> by lazy {
        openMonth?.let { computeBalances(expenses, it.memberIds) } ?: emptyList()
    }
    val settlements: List<Settlement> by lazy { computeSettlements(balances) }
    val monthTotalCents: Long by lazy { monthTotalCents(expenses) }

    /** Nombre del miembro, o null si el uid ya no está en `members` (p. ej. un móvil reinstalado). */
    fun memberName(id: String): String? = members.firstOrNull { it.id == id }?.name
}

/**
 * Reparto de gastos por mes, aparte de AppViewModel: no comparte datos con los tickets. Meses y
 * miembros se observan siempre (Home muestra cuántos meses hay); gastos y fotos, solo los del mes
 * abierto, cambiando de mes con `flatMapLatest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplitViewModel(
    private val monthsRepository: SplitMonthsRepository,
    private val expensesRepository: SplitExpensesRepository,
    private val photosRepository: SplitPhotosRepository,
    membersRepository: MembersRepository,
    private val storageRepository: StorageRepository,
    /** Uid de este dispositivo: pagador por defecto y fila resaltada en Saldos. */
    val currentUid: String
) : ViewModel() {

    private val _state = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = _state.asStateFlow()

    private val openMonthId = MutableStateFlow<String?>(null)
    private val holders = SplitMonthHolders()

    init {
        viewModelScope.launch {
            monthsRepository.observeMonths()
                .catch { e ->
                    // Sin esto, un error en el primer snapshot dejaba la lista cargando para siempre.
                    _state.update { it.copy(monthsLoaded = true) }
                    setError(e)
                }
                .collect { months -> _state.update { it.copy(months = months, monthsLoaded = true) } }
        }
        viewModelScope.launch {
            membersRepository.observeMembers()
                .catch { e -> setError(e) }
                .collect { members -> _state.update { it.copy(members = members) } }
        }
        viewModelScope.launch {
            openMonthId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList())
                    else expensesRepository.observeExpenses(id).catch { e -> setError(e) }
                }
                .collect { expenses ->
                    // El flowOf(emptyList()) de «ningún mes» no cuenta como carga.
                    _state.update { it.copy(expenses = expenses, expensesLoaded = it.openMonthId != null) }
                }
        }
        viewModelScope.launch {
            openMonthId
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList())
                    else photosRepository.observePhotos(id).catch { e -> setError(e) }
                }
                .collect { photos -> _state.update { it.copy(photos = photos) } }
        }
    }

    private fun setError(e: Throwable) {
        _state.update { it.copy(error = e.message ?: e.toString()) }
    }

    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setError(e)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** La pantalla [holderId] (una entrada de navegación) necesita el grupo [monthId] abierto. */
    fun retainMonth(holderId: String, monthId: String) = setOpenMonth(holders.retain(holderId, monthId))

    /** Si la pantalla [holderId] ya tiene el grupo retenido (ver `RetainSplitMonth`). */
    fun holdsMonth(holderId: String): Boolean = holders.holds(holderId)

    /** La pantalla [holderId] ya no existe; si nadie más lo necesita, el grupo se cierra. */
    fun releaseMonth(holderId: String) = setOpenMonth(holders.release(holderId))

    /** Cambia el grupo abierto. Se vacían gastos y fotos al momento para no enseñar los del anterior. */
    private fun setOpenMonth(id: String?) {
        if (openMonthId.value == id) return
        _state.update {
            it.copy(openMonthId = id, expenses = emptyList(), expensesLoaded = false, photos = emptyList())
        }
        openMonthId.value = id
    }

    fun createMonth(name: String, monthKey: String, memberIds: List<String>, activity: Boolean) = launchCatching {
        monthsRepository.createMonth(name.trim(), monthKey, memberIds.distinct(), activity)
    }

    /**
     * Borra el mes con sus gastos y fotos. Los objetos de Storage van primero (ver
     * [SplitMonthsRepository.delete]); si alguno falla, no se borra nada más y el mes sigue
     * visible para reintentar. El grupo se cierra solo al salir de sus pantallas.
     */
    fun deleteMonth(monthId: String) = launchCatching {
        monthsRepository.delete(monthId) { path -> storageRepository.deleteSplitPhoto(path) }
    }

    fun setArchived(monthId: String, archived: Boolean) = launchCatching {
        monthsRepository.setArchived(monthId, archived)
    }

    fun editMonth(monthId: String, name: String, monthKey: String, emoji: String, addedMemberIds: List<String>) =
        launchCatching {
            monthsRepository.updateMonth(monthId, name.trim(), monthKey, emoji, addedMemberIds.distinct())
        }

    /**
     * Valida y guarda un gasto del mes abierto ([expenseId] null = alta). Devuelve los errores
     * para que el formulario los pinte; si está vacío, el guardado ya está en marcha.
     *
     * [shareTexts] null = a partes iguales entre [participants]. Si no, el importe escrito para
     * cada participante posible, y [participants] da solo el orden en que se guardan.
     */
    fun saveExpense(
        monthId: String,
        expenseId: String?,
        title: String,
        amountText: String,
        payerId: String,
        participants: List<String>,
        date: Date?,
        reimbursement: Boolean,
        emoji: String,
        shareTexts: Map<String, String>?
    ): Set<SplitExpenseError> {
        // Se busca en todos los grupos y no en el abierto: tras un cambio de configuración el grupo
        // se cierra y se reabre, y en ese hueco el guardado se perdería sin avisar.
        val month = _state.value.months.firstOrNull { it.id == monthId } ?: return emptySet()
        val errors = validateSplitExpense(
            title, amountText, payerId, participants, date, reimbursement, month, shareTexts = shareTexts
        )
        if (errors.isNotEmpty()) return errors
        val shares = shareTexts?.let { parseShares(it)!! } ?: emptyMap()
        val expense = SplitExpense(
            id = expenseId.orEmpty(),
            title = title.trim(),
            amountCents = parseCents(amountText)!!,
            payerId = payerId,
            participants = if (shareTexts == null) participants.distinct() else participants.distinct().filter { it in shares },
            date = date,
            reimbursement = reimbursement,
            emoji = emoji,
            shares = shares
        )
        launchCatching {
            if (expenseId == null) expensesRepository.add(month.id, expense)
            else expensesRepository.update(month.id, expense)
        }
        return emptySet()
    }

    fun deleteExpense(expenseId: String) {
        val monthId = openMonthId.value ?: return
        launchCatching { expensesRepository.delete(monthId, expenseId) }
    }

    /**
     * Salda un pago sugerido creando un reembolso. [title] llega de la UI porque el ViewModel no
     * tiene acceso a los recursos de texto.
     */
    fun settle(settlement: Settlement, title: String) {
        val monthId = openMonthId.value ?: return
        launchCatching {
            expensesRepository.add(
                monthId,
                SplitExpense(
                    title = title,
                    amountCents = settlement.amountCents,
                    payerId = settlement.fromId,
                    participants = listOf(settlement.toId),
                    date = Date(),
                    reimbursement = true
                )
            )
        }
    }

    /**
     * Sube la foto y solo después crea su documento: si falla la subida no queda un documento
     * apuntando a una imagen que no existe. Si falla el documento, se borra el objeto subido.
     */
    fun addPhoto(uri: Uri, note: String, expenseId: String?) {
        val monthId = openMonthId.value ?: return
        _state.update { it.copy(uploadingPhoto = true) }
        launchCatching {
            try {
                val photoId = photosRepository.newPhotoId(monthId)
                val upload = storageRepository.uploadSplitPhoto(monthId, photoId, uri)
                try {
                    photosRepository.add(
                        monthId,
                        SplitPhoto(
                            id = photoId,
                            url = upload.url,
                            storagePath = upload.storagePath,
                            note = note.trim(),
                            expenseId = expenseId
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    runCatching { storageRepository.deleteSplitPhoto(upload.storagePath) }
                    throw e
                }
            } finally {
                _state.update { it.copy(uploadingPhoto = false) }
            }
        }
    }

    /**
     * Borra primero el objeto de Storage y después el documento: si lo segundo falla, la foto
     * sigue visible y se puede volver a borrar (el objeto ya borrado se ignora); al revés, el
     * objeto quedaría huérfano sin forma de encontrarlo.
     */
    fun deletePhoto(photo: SplitPhoto) {
        val monthId = openMonthId.value ?: return
        launchCatching {
            if (photo.storagePath.isNotBlank()) storageRepository.deleteSplitPhoto(photo.storagePath)
            photosRepository.delete(monthId, photo.id)
        }
    }
}
