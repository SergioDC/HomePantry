package com.homepantry.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.CalendarMode
import com.homepantry.app.data.Dish
import com.homepantry.app.data.DishesRepository
import com.homepantry.app.data.DuplicateMode
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.MenuImportDraft
import com.homepantry.app.data.MenuImportDraftStore
import com.homepantry.app.data.MenuImportPlan
import com.homepantry.app.data.MissingChoice
import com.homepantry.app.data.ParsedMenu
import com.homepantry.app.data.PeopleRepository
import com.homepantry.app.data.Person
import com.homepantry.app.data.PersonColor
import com.homepantry.app.data.assignableEntries
import com.homepantry.app.data.itemsForMissing
import com.homepantry.app.data.matchDish
import com.homepantry.app.data.nextEntryOrder
import com.homepantry.app.data.planDuplicateWeek
import com.homepantry.app.data.planMenuImport
import com.homepantry.app.data.recognizeMenuWithGemini
import com.homepantry.app.data.visibleRange
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Modo del calendario y fecha de referencia (un día de la semana o del mes visible). */
data class CalendarPosition(val mode: CalendarMode, val date: LocalDate)

data class MenuUiState(
    val dishes: List<Dish> = emptyList(),
    /** Entradas del rango visible (página actual, anterior y siguiente). */
    val entries: List<MealEntry> = emptyList(),
    val position: CalendarPosition = CalendarPosition(CalendarMode.WEEK, LocalDate.now()),
    /** Personas a las que se asignan platos, con su color (SPEC 2026-09-21). */
    val people: List<Person> = emptyList(),
    val error: String? = null
)

/**
 * Estado y acciones del menú semanal, aparte de AppViewModel (que ya tiene cinco repositorios).
 * Los productos y zonas ya cargados los toma la UI del estado de AppViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MenuViewModel(
    private val dishesRepository: DishesRepository,
    private val entriesRepository: MealEntriesRepository,
    private val itemsRepository: ItemsRepository,
    private val peopleRepository: PeopleRepository,
    private val importDraftStore: MenuImportDraftStore,
    private var userName: String
) : ViewModel() {

    private val dishes = MutableStateFlow<List<Dish>>(emptyList())
    private val entries = MutableStateFlow<List<MealEntry>>(emptyList())
    private val position = MutableStateFlow(CalendarPosition(CalendarMode.WEEK, LocalDate.now()))
    private val people = MutableStateFlow<List<Person>>(emptyList())
    private val error = MutableStateFlow<String?>(null)

    // Importación del menú desde una foto: vive aquí y no en la pantalla para que ni la lectura de
    // Gemini ni lo corregido se pierdan al cambiar de pestaña o rotar (el borrador va además a disco).
    private val _readingMenu = MutableStateFlow(false)
    val readingMenu: StateFlow<Boolean> = _readingMenu.asStateFlow()
    private val _importDraft = MutableStateFlow<MenuImportDraft?>(null)
    val importDraft: StateFlow<MenuImportDraft?> = _importDraft.asStateFlow()

    val state: StateFlow<MenuUiState> = combine(dishes, entries, position, people, error) { d, e, p, pe, err ->
        MenuUiState(dishes = d, entries = e, position = p, people = pe, error = err)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MenuUiState())

    init {
        viewModelScope.launch {
            dishesRepository.observeDishes()
                .catch { e -> error.value = e.message }
                .collect { dishes.value = it }
        }
        viewModelScope.launch {
            position
                .map { visibleRange(it.mode, it.date) }
                .distinctUntilChanged()
                .flatMapLatest { (from, to) ->
                    entriesRepository.observeRange(from, to).catch { e -> error.value = e.message }
                }
                .collect { entries.value = it }
        }
        viewModelScope.launch {
            peopleRepository.observePeople()
                .catch { e -> error.value = e.message }
                .collect { people.value = it }
        }
        viewModelScope.launch {
            // Primero se recupera el borrador que quedara en disco y después se guarda cada cambio
            // (agrupando las pulsaciones seguidas); en este orden el null inicial no lo borra.
            val saved = importDraftStore.load()
            if (saved != null && _importDraft.value == null && !_readingMenu.value) _importDraft.value = saved
            _importDraft.debounce(300).collectLatest { draft ->
                try {
                    importDraftStore.save(draft)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin disco el borrador sigue en memoria; no merece un aviso.
                }
            }
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

    fun setVisibleDate(date: LocalDate) {
        position.value = position.value.copy(date = date)
    }

    /**
     * La vista mes se asentó en [month]. Si la fecha de referencia ya cae en ese mes no se toca:
     * así, cambiar de Semana a Mes y volver conserva la misma semana. Si es otro mes, se lleva a
     * hoy cuando hoy está en él, y si no al día 1.
     */
    fun setVisibleMonth(month: YearMonth) {
        if (YearMonth.from(position.value.date) == month) return
        val today = LocalDate.now()
        val date = if (YearMonth.from(today) == month) today else month.atDay(1)
        position.value = position.value.copy(date = date)
    }

    fun setMode(mode: CalendarMode) {
        position.value = position.value.copy(mode = mode)
    }

    /** Pasa a la vista semana en la semana de [date] (al pulsar un día de la vista mes). */
    fun openWeekOf(date: LocalDate) {
        position.value = CalendarPosition(CalendarMode.WEEK, date)
    }

    fun clearError() {
        error.value = null
    }

    // ---- Platos ----

    fun saveDish(dish: Dish) = launchCatching {
        if (dish.id.isBlank()) {
            dishesRepository.addDish(dish.copy(addedBy = userName))
        } else {
            dishesRepository.updateDish(dish)
        }
    }

    /** Las entradas del menú que usaban el plato se quedan como texto libre (conservan su nombre). */
    fun deleteDish(dishId: String) = launchCatching { dishesRepository.deleteDish(dishId) }

    /** Cuántas entradas del menú usan el plato, o null si no se ha podido consultar. */
    suspend fun countEntriesForDish(dishId: String): Int? =
        try {
            entriesRepository.countByDish(dishId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    fun addMissingToShoppingList(choices: List<MissingChoice>) = launchCatching {
        itemsRepository.addItems(itemsForMissing(choices, userName))
    }

    // ---- Entradas del menú ----

    /**
     * Añade texto al menú; si coincide con un plato creado se enlaza a él, si no queda como texto
     * libre. [person] es a quién va (null = sin asignar), ya normalizado con `normalizePerson`.
     */
    fun addEntry(date: LocalDate, slot: MealSlot, text: String, person: String?) = launchCatching {
        val name = text.trim()
        if (name.isEmpty()) return@launchCatching
        val dish = matchDish(name, dishes.value)
        val key = date.toString()
        entriesRepository.addEntry(
            MealEntry(
                date = key,
                slot = slot.name,
                dishId = dish?.id,
                name = dish?.name ?: name,
                order = nextEntryOrder(entries.value, key, slot),
                addedBy = userName,
                person = person
            )
        )
        person?.let { peopleRepository.ensure(it) }
    }

    fun editEntry(entry: MealEntry, text: String, person: String?) = launchCatching {
        val name = text.trim()
        if (name.isEmpty()) return@launchCatching
        val dish = matchDish(name, dishes.value)
        entriesRepository.updateEntry(entry.copy(dishId = dish?.id, name = dish?.name ?: name, person = person))
        person?.let { peopleRepository.ensure(it) }
    }

    // ---- Personas ----

    /** Guarda el color de [person] (Familia incluida), visible para toda la casa. */
    fun setPersonColor(person: String, color: PersonColor) = launchCatching {
        peopleRepository.setColor(person, color)
    }

    /**
     * Cuántas entradas de [from] a [to] (ambos incluidos) cambiaría [assignPerson], para avisar
     * antes de aplicarlo, o null si no se ha podido consultar.
     */
    suspend fun countAssignable(from: LocalDate, to: LocalDate, person: String?, onlyUnassigned: Boolean): Int? =
        try {
            assignableEntries(entriesRepository.getRange(from.toString(), to.toString()), person, onlyUnassigned).size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /**
     * Asigna [person] (null = dejarlas sin asignar) a las entradas de [from] a [to] (ambos incluidos) y espera
     * a que termine. Lee el rango en el momento, no de la caché de la pantalla. Devuelve cuántas
     * cambió, o null si falla, con el error publicado. Corre en el scope del ViewModel: cerrar el
     * diálogo no lo deja a medias.
     */
    suspend fun assignPerson(from: LocalDate, to: LocalDate, person: String?, onlyUnassigned: Boolean): Int? =
        viewModelScope.async {
            try {
                val toChange = assignableEntries(
                    entriesRepository.getRange(from.toString(), to.toString()), person, onlyUnassigned
                )
                entriesRepository.updatePerson(toChange.map { it.id }, person)
                person?.let { peopleRepository.ensure(it) }
                toChange.size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: e.toString()
                null
            }
        }.await()

    fun deleteEntry(entry: MealEntry) = launchCatching { entriesRepository.deleteEntry(entry.id) }

    fun restoreEntry(entry: MealEntry) = launchCatching { entriesRepository.restoreEntry(entry) }

    // ---- Importar menú desde una foto ----

    /**
     * Lee con Gemini el menú de la foto [uri] y lo deja como borrador para revisarlo, con
     * [defaultMonth] como mes elegido. Si falta la clave, falla la lectura o no salen platos, publica
     * el error. Ignora la llamada si ya hay una lectura en marcha.
     */
    fun readMenuPhoto(
        context: Context,
        uri: Uri,
        apiKeyStore: GeminiApiKeyStore,
        defaultMonth: YearMonth,
        noKeyMessage: String,
        emptyMessage: String
    ) {
        if (_readingMenu.value) return
        _readingMenu.value = true
        // Solo el contexto de la aplicación: el de la actividad no debe sobrevivir en el ViewModel.
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val apiKey = apiKeyStore.getApiKey()
                if (apiKey == null) {
                    error.value = noKeyMessage
                    return@launch
                }
                val menu = recognizeMenuWithGemini(appContext, uri, apiKey, apiKeyStore.getModel())
                if (menu.days.isEmpty()) {
                    error.value = emptyMessage
                } else {
                    _importDraft.value = MenuImportDraft(menu.month, defaultMonth, "", menu.days)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: e.toString()
            } finally {
                _readingMenu.value = false
            }
        }
    }

    /** Cambios del usuario al revisar el borrador (mes, persona, platos). */
    fun updateImportDraft(draft: MenuImportDraft) {
        if (_importDraft.value != null) _importDraft.value = draft
    }

    fun discardImportDraft() {
        _importDraft.value = null
    }

    /**
     * Vuelca [menu] (ya revisado) en la comida de [month], asignado a [person] (null = sin
     * asignar), y espera a que termine. Lee las entradas
     * del mes en el momento, no de la caché de la pantalla: el mes elegido puede no estar cargado.
     * Escribe primero los platos nuevos y después las entradas, para que ninguna apunte a un plato
     * que no llegó a crearse. Devuelve el plan aplicado (para el resumen) o null si falla, con el
     * error publicado. Corre en el scope del ViewModel: salir de la pantalla no lo deja a medias.
     */
    suspend fun importMenu(menu: ParsedMenu, month: YearMonth, person: String?): MenuImportPlan? =
        viewModelScope.async {
            try {
                val existing = entriesRepository.getRange(month.atDay(1).toString(), month.atEndOfMonth().toString())
                val plan = planMenuImport(menu, month, dishes.value, existing, dishesRepository::newId, userName, person)
                dishesRepository.addDishes(plan.newDishes)
                entriesRepository.applyBatch(plan.entries, emptyList())
                person?.let { peopleRepository.ensure(it) }
                // Ya importado: el borrador sobra aunque la pantalla no llegue a enterarse.
                _importDraft.value = null
                plan
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: e.toString()
                null
            }
        }.await()

    // ---- Duplicar semana ----

    suspend fun countEntriesInWeek(weekStart: LocalDate): Int =
        runCatching {
            entriesRepository.getRange(weekStart.toString(), weekStart.plusDays(6).toString()).size
        }.getOrDefault(0)

    /**
     * Copia la semana [from] a [to] y espera a que termine. Devuelve true si se copió; si falla,
     * publica el error y devuelve false. La escritura corre en el scope del ViewModel, así que
     * cerrar el diálogo que la lanzó no la deja a medias.
     */
    suspend fun duplicateWeek(from: LocalDate, to: LocalDate, mode: DuplicateMode): Boolean =
        viewModelScope.async {
            try {
                val source = entriesRepository.getRange(from.toString(), from.plusDays(6).toString())
                val target = entriesRepository.getRange(to.toString(), to.plusDays(6).toString())
                val plan = planDuplicateWeek(source, target, from, to, mode)
                entriesRepository.applyBatch(plan.toWrite, plan.toDelete)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: e.toString()
                false
            }
        }.await()
}
