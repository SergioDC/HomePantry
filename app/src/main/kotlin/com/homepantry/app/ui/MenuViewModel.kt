package com.homepantry.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homepantry.app.data.CalendarMode
import com.homepantry.app.data.Dish
import com.homepantry.app.data.DishesRepository
import com.homepantry.app.data.DuplicateMode
import com.homepantry.app.data.ItemsRepository
import com.homepantry.app.data.MealEntriesRepository
import com.homepantry.app.data.MealEntry
import com.homepantry.app.data.MealSlot
import com.homepantry.app.data.MenuImportPlan
import com.homepantry.app.data.MissingChoice
import com.homepantry.app.data.ParsedMenu
import com.homepantry.app.data.itemsForMissing
import com.homepantry.app.data.matchDish
import com.homepantry.app.data.nextEntryOrder
import com.homepantry.app.data.planDuplicateWeek
import com.homepantry.app.data.planMenuImport
import com.homepantry.app.data.visibleRange
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val error: String? = null
)

/**
 * Estado y acciones del menú semanal, aparte de AppViewModel (que ya tiene cinco repositorios).
 * Los productos y zonas ya cargados los toma la UI del estado de AppViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModel(
    private val dishesRepository: DishesRepository,
    private val entriesRepository: MealEntriesRepository,
    private val itemsRepository: ItemsRepository,
    private val userName: String
) : ViewModel() {

    private val dishes = MutableStateFlow<List<Dish>>(emptyList())
    private val entries = MutableStateFlow<List<MealEntry>>(emptyList())
    private val position = MutableStateFlow(CalendarPosition(CalendarMode.WEEK, LocalDate.now()))
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<MenuUiState> = combine(dishes, entries, position, error) { d, e, p, err ->
        MenuUiState(dishes = d, entries = e, position = p, error = err)
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

    /** Añade texto al menú; si coincide con un plato creado se enlaza a él, si no queda como texto libre. */
    fun addEntry(date: LocalDate, slot: MealSlot, text: String) = launchCatching {
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
                addedBy = userName
            )
        )
    }

    fun editEntry(entry: MealEntry, text: String) = launchCatching {
        val name = text.trim()
        if (name.isEmpty()) return@launchCatching
        val dish = matchDish(name, dishes.value)
        entriesRepository.updateEntry(entry.copy(dishId = dish?.id, name = dish?.name ?: name))
    }

    fun deleteEntry(entry: MealEntry) = launchCatching { entriesRepository.deleteEntry(entry.id) }

    fun restoreEntry(entry: MealEntry) = launchCatching { entriesRepository.restoreEntry(entry) }

    // ---- Importar menú desde una foto ----

    /**
     * Vuelca [menu] (ya revisado) en la comida de [month] y espera a que termine. Lee las entradas
     * del mes en el momento, no de la caché de la pantalla: el mes elegido puede no estar cargado.
     * Escribe primero los platos nuevos y después las entradas, para que ninguna apunte a un plato
     * que no llegó a crearse. Devuelve el plan aplicado (para el resumen) o null si falla, con el
     * error publicado. Corre en el scope del ViewModel: cerrar la hoja no lo deja a medias.
     */
    suspend fun importMenu(menu: ParsedMenu, month: YearMonth): MenuImportPlan? =
        viewModelScope.async {
            try {
                val existing = entriesRepository.getRange(month.atDay(1).toString(), month.atEndOfMonth().toString())
                val plan = planMenuImport(menu, month, dishes.value, existing, dishesRepository::newId, userName)
                dishesRepository.addDishes(plan.newDishes)
                entriesRepository.applyBatch(plan.entries, emptyList())
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
