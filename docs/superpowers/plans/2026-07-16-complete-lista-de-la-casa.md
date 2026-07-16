# Lista de la Casa — Completar App Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Note:** this plan is executed inline, same session, by the author of the plan (full context already loaded — no blind handoff). Steps are still kept concrete and file-exact so progress is auditable, but duplicate boilerplate explanations are trimmed relative to the canonical format.

**Goal:** Turn the scaffolded "Lista de la Casa" Android project (data models + repos already exist) into a project that compiles with `./gradlew assembleDebug`, per the checklist in `SPEC.md` §5.

**Architecture:** MVVM — one `AppViewModel` (StateFlow) wraps `ItemsRepository` + `ZonesRepository` + `UserPrefs` (DataStore) + `StorageRepository` (Firebase Storage) + `OpenFoodFactsApi` (Retrofit). Four Compose screens (`JoinHousehold`, `MainList`, `AddItemSheet`, `ManageZones`) wired via Navigation Compose in `MainActivity`. Pure/testable logic (grouping, progress text, household-code validation, OFF JSON parsing) is extracted into plain Kotlin so it can run as JVM unit tests without an emulator.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Firebase (Firestore/Auth-anonymous/Storage), CameraX + ML Kit barcode-scanning, Coil, Retrofit+Gson, DataStore Preferences, Accompanist permissions, JUnit4 (unit tests only, no instrumented tests — no emulator available in this environment).

## Global Constraints
- `applicationId` / Firebase Android app id: `com.listacasa.app` (SPEC §"Requisitos").
- minSdk 26, targetSdk/compileSdk 34 (already set in `app/build.gradle.kts`) — do not change.
- Color palette is fixed, copied verbatim from SPEC §6 into `ui/theme/Color.kt`; menta→primary, lima→secondary, coral→error only for alerts/delete (never elsewhere).
- No traditional login — Firebase Anonymous Auth + household code (SPEC §3). Household code and user name persist via DataStore, asked once.
- Zones: at least 1 must always exist; deleting a zone reassigns its items to "Otros" and requires confirming with the affected item count first (SPEC §1.3).
- Barcode flow: CameraX + ML Kit live scan → Open Food Facts lookup → fallback to an editable name field with the barcode shown if lookup fails/offline (SPEC §1.5).
- `app/google-services.json` must never be committed (already in `.gitignore`) — a structurally-valid placeholder is created locally only, to unblock compilation, and the user is told to replace it with their real Firebase file.
- Do not push to GitHub without explicit confirmation (remote `origin` already exists and is in sync with local `main`).

---

### Task 1: Gradle wrapper + local SDK config + empty proguard file

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `local.properties` (gitignored already via `/local.properties`)
- Create: `app/proguard-rules.pro` (empty, referenced by `app/build.gradle.kts:22` but missing)

- [ ] Generate the wrapper with the installed Gradle (`C:\Android\gradle-7.0` is too old for AGP 8.5 — use a `gradle wrapper` invocation via a temporary local Gradle 8.7 if not present, or `cmd /c gradle wrapper --gradle-version 8.7` if a system `gradle` is on PATH; otherwise hand-write `gradle-wrapper.properties` pointing at `https://services.gradle.org/distributions/gradle-8.7-bin.zip` and fetch the wrapper jar from a pinned AGP-compatible Gradle distribution).
- [ ] Write `local.properties` with `sdk.dir=C\:\\Android` (Windows path escaping for `.properties`).
- [ ] Create empty `app/proguard-rules.pro`.
- [ ] Verify: `./gradlew -v` (or `gradlew.bat -v` on Windows) prints a Gradle version without error.

### Task 2: Theme colors (SPEC §6)

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/listacasa/app/ui/theme/Theme.kt`

**Produces:** `ListaDeLaCasaTheme(content: @Composable () -> Unit)` composable; `MintPrimary`, `LimeSecondary`, `CoralError`, `ZoneColors: List<Color>` constants used later by `ZoneChip`.

- [ ] Add `Color.kt` with every hex from SPEC §6 as named `Color(0xFF......)` constants.
- [ ] Add `Theme.kt` building a Material3 `lightColorScheme(primary = MintPrimary, secondary = LimeSecondary, error = CoralError, background = Background, surface = Card, onBackground = Ink, ...)` wrapped in `ListaDeLaCasaTheme`.
- [ ] Verify: file compiles in isolation once Task 8 wires `ListaDeLaCasaTheme` into `MainActivity` (checked then, not standalone — no test framework needed for pure constants/theme wiring).

### Task 3: `HouseholdCode` pure logic + unit test

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/HouseholdCode.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/data/HouseholdCodeTest.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation("junit:junit:4.13.2")`)

**Produces:** `fun generateHouseholdCode(): String` (format `CASA-####`, 4 random digits), `fun isValidHouseholdCode(code: String): Boolean` (matches `^CASA-\d{4}$`, case-insensitive, trims/uppercases input).

- [ ] Add `testImplementation("junit:junit:4.13.2")` to `app/build.gradle.kts` dependencies block.
- [ ] Write failing test:
```kotlin
package com.listacasa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HouseholdCodeTest {
    @Test fun `generated code matches CASA-#### format`() {
        val code = generateHouseholdCode()
        assertTrue(code.matches(Regex("^CASA-\\d{4}$")))
    }

    @Test fun `valid code accepted, lowercase and untrimmed normalized`() {
        assertTrue(isValidHouseholdCode("CASA-4821"))
        assertTrue(isValidHouseholdCode(" casa-4821 "))
    }

    @Test fun `invalid codes rejected`() {
        assertFalse(isValidHouseholdCode("CASA-482"))
        assertFalse(isValidHouseholdCode("CASA4821"))
        assertFalse(isValidHouseholdCode(""))
    }
}
```
- [ ] Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.HouseholdCodeTest"` → expect FAIL (class doesn't exist).
- [ ] Implement `HouseholdCode.kt`:
```kotlin
package com.listacasa.app.data

import kotlin.random.Random

private val CODE_REGEX = Regex("^CASA-\\d{4}$")

fun generateHouseholdCode(): String = "CASA-" + Random.nextInt(0, 10000).toString().padStart(4, '0')

fun isValidHouseholdCode(code: String): Boolean =
    CODE_REGEX.matches(code.trim().uppercase())

fun normalizeHouseholdCode(code: String): String = code.trim().uppercase()
```
- [ ] Run same test command → expect PASS.
- [ ] Commit: `git add app/build.gradle.kts app/src/main/kotlin/com/listacasa/app/data/HouseholdCode.kt app/src/test/kotlin/com/listacasa/app/data/HouseholdCodeTest.kt && git commit -m "feat: add household code generation/validation with tests"`

### Task 4: `UserPrefs` (DataStore) — household code + user name persistence

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/UserPrefs.kt`

**Consumes:** `normalizeHouseholdCode` from Task 3.
**Produces:** `class UserPrefs(context: Context)` with `val householdCode: Flow<String?>`, `val userName: Flow<String?>`, `suspend fun saveHousehold(code: String)`, `suspend fun saveUserName(name: String)`.

- [ ] Implement using `androidx.datastore.preferences` (already a dependency):
```kotlin
package com.listacasa.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPrefs(private val context: Context) {
    private val householdCodeKey = stringPreferencesKey("household_code")
    private val userNameKey = stringPreferencesKey("user_name")

    val householdCode: Flow<String?> = context.dataStore.data.map { it[householdCodeKey] }
    val userName: Flow<String?> = context.dataStore.data.map { it[userNameKey] }

    suspend fun saveHousehold(code: String) {
        context.dataStore.edit { it[householdCodeKey] = normalizeHouseholdCode(code) }
    }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[userNameKey] = name.trim() }
    }
}
```
- [ ] No JVM unit test (needs Android `Context`) — covered by manual verification in Task 9 (JoinHouseholdScreen round-trip).
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/data/UserPrefs.kt && git commit -m "feat: persist household code and user name via DataStore"`

### Task 5: Fix repositories' `.await()` + zone seeding wiring

**Files:**
- Modify: `app/build.gradle.kts` (add `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")`)
- Modify: `app/src/main/kotlin/com/listacasa/app/data/ItemsRepository.kt` (add `import kotlinx.coroutines.tasks.await`)
- Modify: `app/src/main/kotlin/com/listacasa/app/data/ZonesRepository.kt` (add same import; add `suspend fun seedIfEmpty()` guard)

- [ ] Add the coroutines-play-services dependency (already called out as a TODO comment in `ItemsRepository.kt:63-65`).
- [ ] Add `import kotlinx.coroutines.tasks.await` to both repository files (the `.await()` calls already in the code become valid extension calls).
- [ ] In `ZonesRepository.kt`, wrap `seedDefaultZones()` with a guard so it's only called when a household has zero zones:
```kotlin
suspend fun seedIfEmpty() {
    val existing = collection().limit(1).get().await()
    if (existing.isEmpty) seedDefaultZones()
}
```
- [ ] Verify: no compile errors referencing `.await()` (checked at Task 12 full build; these files have no standalone test target since they need a live Firestore instance).
- [ ] Commit: `git add app/build.gradle.kts app/src/main/kotlin/com/listacasa/app/data/ItemsRepository.kt app/src/main/kotlin/com/listacasa/app/data/ZonesRepository.kt && git commit -m "fix: resolve Task.await() coroutine bridge and guard zone seeding"`

### Task 6: `OpenFoodFactsApi` (Retrofit) + JSON parsing unit test

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/OpenFoodFactsApi.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/data/OpenFoodFactsApiTest.kt`

**Produces:** `data class OffProduct(val productName: String?)`, `data class OffResponse(val status: Int, val product: OffProduct?)`, `object OpenFoodFactsClient { fun api(): OpenFoodFactsApi }`, `interface OpenFoodFactsApi { suspend fun getProduct(barcode: String): OffResponse }`.

- [ ] Write failing test (pure Gson parsing, no network):
```kotlin
package com.listacasa.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsApiTest {
    private val gson = Gson()

    @Test fun `parses product name when found`() {
        val json = """{"status":1,"product":{"product_name":"Leche Entera 1L"}}"""
        val response = gson.fromJson(json, OffResponse::class.java)
        assertEquals(1, response.status)
        assertEquals("Leche Entera 1L", response.product?.productName)
    }

    @Test fun `handles not-found response`() {
        val json = """{"status":0}"""
        val response = gson.fromJson(json, OffResponse::class.java)
        assertEquals(0, response.status)
        assertNull(response.product)
    }
}
```
- [ ] Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.data.OpenFoodFactsApiTest"` → expect FAIL (types don't exist).
- [ ] Implement:
```kotlin
package com.listacasa.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class OffProduct(
    @SerializedName("product_name") val productName: String?
)

data class OffResponse(
    val status: Int,
    val product: OffProduct?
)

interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): OffResponse
}

object OpenFoodFactsClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://world.openfoodfacts.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun api(): OpenFoodFactsApi = retrofit.create(OpenFoodFactsApi::class.java)
}
```
- [ ] Run same test → expect PASS.
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/data/OpenFoodFactsApi.kt app/src/test/kotlin/com/listacasa/app/data/OpenFoodFactsApiTest.kt && git commit -m "feat: add Open Food Facts Retrofit client with parsing tests"`

### Task 7: `StorageRepository` (Firebase Storage photo upload)

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/data/StorageRepository.kt`

**Produces:** `class StorageRepository(private val storage: FirebaseStorage, private val householdCode: String) { suspend fun uploadPhoto(itemId: String, localUri: Uri): String }` returning the download URL, per SPEC §2 path `households/{householdCode}/photos/{itemId}.jpg`.

- [ ] Implement:
```kotlin
package com.listacasa.app.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class StorageRepository(
    private val storage: FirebaseStorage,
    private val householdCode: String
) {
    suspend fun uploadPhoto(itemId: String, localUri: Uri): String {
        val ref = storage.reference.child("households/$householdCode/photos/$itemId.jpg")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }
}
```
- [ ] No JVM unit test (needs live Firebase Storage) — covered by manual QA in Task 10 (AddItemSheet photo flow).
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/data/StorageRepository.kt && git commit -m "feat: add Firebase Storage photo upload repository"`

### Task 8: `AppViewModel` + pure list/progress logic with unit tests

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt`
- Test: `app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt`
- Create: `app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt`

**Consumes:** `Item`, `Zone` (existing data classes), `ItemsRepository`, `ZonesRepository`, `UserPrefs`, `StorageRepository`, `OpenFoodFactsApi` (Tasks 4/5/6/7).
**Produces:**
- `fun progressText(items: List<Item>): String` → `"X de Y listos"`.
- `data class ZoneSection(val zone: Zone, val items: List<Item>)`
- `fun groupAndSort(items: List<Item>, zones: List<Zone>, filterZoneId: String?): List<ZoneSection>` — pending first then done, grouped by zone in zone `order`, optionally filtered to one zone id (`null`/`"ALL"` = no filter).
- `class AppViewModel(...) : ViewModel()` exposing `StateFlow<UiState>` with `items`, `zones`, `selectedZoneId`, `progressText`, `groupedSections`, plus actions `addItem`, `toggleDone`, `deleteItem`, `clearDone`, `selectZone`, `createZone`, `renameZone`, `deleteZone` (deleteZone first calls `itemsRepository.reassignZone(zoneId, "Otros")` then `zonesRepository.deleteZone(zoneId)`, and refuses to run if `zones.size <= 1`).

- [ ] Write failing test:
```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemListLogicTest {
    private val nevera = Zone(id = "z1", name = "Nevera", order = 0)
    private val despensa = Zone(id = "z2", name = "Despensa", order = 1)

    @Test fun `progress text counts done vs total`() {
        val items = listOf(
            Item(id = "1", name = "Leche", done = true),
            Item(id = "2", name = "Pan", done = false),
            Item(id = "3", name = "Huevos", done = true)
        )
        assertEquals("2 de 3 listos", progressText(items))
    }

    @Test fun `groups by zone order, pending before done within a zone`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1", done = true),
            Item(id = "2", name = "Pan", zone = "z1", done = false),
            Item(id = "3", name = "Arroz", zone = "z2", done = false)
        )
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = null)
        assertEquals(listOf("z1", "z2"), sections.map { it.zone.id })
        assertEquals(listOf("2", "1"), sections.first().items.map { it.id })
    }

    @Test fun `filters to a single zone when requested`() {
        val items = listOf(
            Item(id = "1", name = "Leche", zone = "z1"),
            Item(id = "2", name = "Arroz", zone = "z2")
        )
        val sections = groupAndSort(items, listOf(nevera, despensa), filterZoneId = "z2")
        assertEquals(listOf("z2"), sections.map { it.zone.id })
    }
}
```
- [ ] Run: `./gradlew testDebugUnitTest --tests "com.listacasa.app.ui.ItemListLogicTest"` → expect FAIL.
- [ ] Implement `ItemListLogic.kt`:
```kotlin
package com.listacasa.app.ui

import com.listacasa.app.data.Item
import com.listacasa.app.data.Zone

data class ZoneSection(val zone: Zone, val items: List<Item>)

fun progressText(items: List<Item>): String {
    val done = items.count { it.done }
    return "$done de ${items.size} listos"
}

fun groupAndSort(items: List<Item>, zones: List<Zone>, filterZoneId: String?): List<ZoneSection> {
    val relevantZones = zones.sortedBy { it.order }
        .filter { filterZoneId == null || filterZoneId == "ALL" || it.id == filterZoneId }
    return relevantZones.mapNotNull { zone ->
        val zoneItems = items.filter { it.zone == zone.id }
            .sortedWith(compareBy({ it.done }, { it.addedAt }))
        if (zoneItems.isEmpty() && filterZoneId != null && filterZoneId != "ALL") {
            ZoneSection(zone, zoneItems)
        } else if (zoneItems.isNotEmpty()) {
            ZoneSection(zone, zoneItems)
        } else null
    }
}
```
- [ ] Run same test → expect PASS.
- [ ] Implement `AppViewModel.kt` wiring repositories to `StateFlow`:
```kotlin
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

class AppViewModel(
    private val itemsRepository: ItemsRepository,
    private val zonesRepository: ZonesRepository,
    val storageRepository: StorageRepository,
    val userName: String
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            zonesRepository.seedIfEmpty()
        }
        viewModelScope.launch {
            itemsRepository.observeItems().collect { items ->
                _state.value = _state.value.copy(items = items, loading = false)
            }
        }
        viewModelScope.launch {
            zonesRepository.observeZones().collect { zones ->
                _state.value = _state.value.copy(zones = zones)
            }
        }
    }

    fun selectZone(zoneId: String) {
        _state.value = _state.value.copy(selectedZoneId = zoneId)
    }

    fun addItem(item: Item) = viewModelScope.launch {
        itemsRepository.addItem(item.copy(addedBy = userName))
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

    fun deleteZone(zoneId: String, otrosZoneId: String) = viewModelScope.launch {
        if (_state.value.zones.size <= 1) return@launch
        itemsRepository.reassignZone(zoneId, otrosZoneId)
        zonesRepository.deleteZone(zoneId)
    }
}
```
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/ui/ItemListLogic.kt app/src/test/kotlin/com/listacasa/app/ui/ItemListLogicTest.kt app/src/main/kotlin/com/listacasa/app/ui/AppViewModel.kt && git commit -m "feat: add AppViewModel with tested grouping/progress logic"`

### Task 9: `JoinHouseholdScreen`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/JoinHouseholdScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (add screen strings)

**Consumes:** `UserPrefs`, `generateHouseholdCode`, `isValidHouseholdCode`, `normalizeHouseholdCode` (Tasks 3/4).
**Produces:** `@Composable fun JoinHouseholdScreen(userPrefs: UserPrefs, onJoined: () -> Unit)`.

- [ ] Add strings: `join_title`, `join_name_label`, `join_code_label`, `join_create_code`, `join_enter_code`, `join_continue`, `join_code_invalid`.
- [ ] Implement the screen: two text fields (name, household code) + "crear código nuevo" button that fills the code field via `generateHouseholdCode()`, a "Continuar" button disabled until name is non-blank and `isValidHouseholdCode(code)`, calling `userPrefs.saveUserName()` + `userPrefs.saveHousehold()` then `onJoined()`.
- [ ] Manual verification (Task 12): launch app with no stored prefs → screen shows → entering a name and generating a code → tapping continue navigates to the list and prefs persist across app restarts.
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/ui/screens/JoinHouseholdScreen.kt app/src/main/res/values/strings.xml && git commit -m "feat: implement JoinHouseholdScreen"`

### Task 10: Components (`ZoneChip`, `ProductCard`, `ProgressBar`) + `MainListScreen`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/ZoneChip.kt`
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/ProductCard.kt`
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/ProgressBar.kt`
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Consumes:** `AppViewModel.state` (Task 8), `Zone.color` via `zoneColorFor` (existing `Zone.kt`).
**Produces:** `@Composable fun MainListScreen(viewModel: AppViewModel, onAddItem: () -> Unit, onManageZones: () -> Unit)`.

- [ ] `ZoneChip(zone: Zone, colorHex: String, selected: Boolean, onClick: () -> Unit)` — `FilterChip` tinted with the zone's rotated color.
- [ ] `ProductCard(item: Item, onToggleDone: () -> Unit, onDelete: () -> Unit)` — circular checkbox, name, `"${qty} ${unit.label}"`, note if present, Coil `AsyncImage` thumbnail if `photoUrl` present, `addedBy` caption, delete icon button tinted `MaterialTheme.colorScheme.error` (coral, per Global Constraints).
- [ ] `ProgressBar(items: List<Item>)` — `LinearProgressIndicator` + `progressText(items)` label.
- [ ] `MainListScreen`: header (app name + current date via `java.text.SimpleDateFormat`/`java.time.LocalDate`, settings icon → `onManageZones`), `ProgressBar`, `ScrollableTabRow` of zones + "Todos" driving `viewModel.selectZone`, `LazyColumn` rendering `state.sections` grouped with a zone header per section, `FloatingActionButton` → `onAddItem`, "vaciar comprados" action in a top bar overflow menu calling `viewModel.clearDone()`. Handle `state.loading` (progress spinner) and `state.error` (SPEC §5 "manejo de estados vacíos/errores") and an empty-list message when `state.items.isEmpty()`.
- [ ] Add strings: `main_empty_list`, `main_clear_done`, `main_all_zones_tab`, `main_error_generic`.
- [ ] Manual verification (Task 12): with two devices/emulator instances joined to the same household code, adding/checking/deleting an item on one shows up on the other in real time (Firestore listener).
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/ui/components/*.kt app/src/main/kotlin/com/listacasa/app/ui/screens/MainListScreen.kt app/src/main/res/values/strings.xml && git commit -m "feat: implement MainListScreen and shared list components"`

### Task 11: `BarcodeScannerView` (CameraX + ML Kit) + `AddItemSheet`

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/components/BarcodeScannerView.kt`
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/AddItemSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Consumes:** `OpenFoodFactsClient.api()` (Task 6), `AppViewModel.storageRepository` (Task 7), `com.google.accompanist.permissions` (existing dependency).
**Produces:** `@Composable fun BarcodeScannerView(onBarcodeDetected: (String) -> Unit, onClose: () -> Unit)`; `@Composable fun AddItemSheet(viewModel: AppViewModel, currentZoneFilter: String, onDismiss: () -> Unit)`.

- [ ] `BarcodeScannerView`: `rememberPermissionState(Manifest.permission.CAMERA)` gate → `AndroidView` hosting a CameraX `PreviewView` + `ImageAnalysis` use case, analyzer feeding frames to `com.google.mlkit.vision.barcode.BarcodeScanning.getClient()`, first successful detection calls `onBarcodeDetected(rawValue)` and stops analysis (avoid duplicate callbacks with a `AtomicBoolean` "already detected" guard released on `onClose`/dispose).
- [ ] `AddItemSheet` (Compose `ModalBottomSheet`): name field, qty (numeric) + unit dropdown (`Unit.entries`), zone chips row (existing `ZoneChip`, reusing `+ nueva zona` inline text field that calls `viewModel.createZone`), note field, "📷 Foto" button opening `ActivityResultContracts.PickVisualMedia()` (Photo Picker — no storage permission needed on API 26+ target since it's a system picker; falls back gracefully) then compresses via `Bitmap.compress(JPEG, 80, ...)` before `viewModel.storageRepository.uploadPhoto`, "📊 Escanear" button toggling a full-screen `BarcodeScannerView` overlay whose detected barcode triggers `OpenFoodFactsClient.api().getProduct(barcode)` in a `viewModelScope`-less local `LaunchedEffect`/`rememberCoroutineScope` — on `status == 1` prefill the name field (still editable), on failure/exception leave the barcode visible next to an editable name field per SPEC §1.5.2. "Guardar" button calls `viewModel.addItem(...)` with `zone = currentZoneFilter` default when set, else the selected chip, then `onDismiss()`.
- [ ] Add strings: `add_item_title`, `add_item_name`, `add_item_qty`, `add_item_note`, `add_item_photo`, `add_item_scan`, `add_item_new_zone`, `add_item_save`, `add_item_barcode_not_found`.
- [ ] Manual verification (Task 12): scanning a real product barcode with connectivity prefills the name from Open Food Facts; scanning with airplane mode on leaves the name field editable with the barcode shown; attaching a photo shows a thumbnail on the resulting `ProductCard`.
- [ ] Commit: `git add app/src/main/kotlin/com/listacasa/app/ui/components/BarcodeScannerView.kt app/src/main/kotlin/com/listacasa/app/ui/screens/AddItemSheet.kt app/src/main/res/values/strings.xml && git commit -m "feat: implement AddItemSheet with CameraX/ML Kit barcode scan and Open Food Facts lookup"`

### Task 12: `ManageZonesScreen`, `MainActivity` wiring, app icon, strings/theme cleanup, real build

**Files:**
- Create: `app/src/main/kotlin/com/listacasa/app/ui/screens/ManageZonesScreen.kt`
- Modify: `app/src/main/kotlin/com/listacasa/app/MainActivity.kt` (full rewrite of the NavHost body)
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`
- Modify: `app/src/main/res/values/strings.xml`, delete `app/src/main/kotlin/com/listacasa/app/ui/screens/TODO_SCREENS.md`
- Create (local only, gitignored): `app/google-services.json` placeholder

**Consumes:** everything from Tasks 2–11.
**Produces:** a fully wired app that builds.

- [ ] `ManageZonesScreen(viewModel: AppViewModel, onBack: () -> Unit)`: `LazyColumn` of zones with inline-editable name (`TextField` committing `onValueChange` → debounced or on-focus-lost `viewModel.renameZone`), item count per zone (`state.items.count { it.zone == zone.id }`), delete icon (coral) opening an `AlertDialog` confirming with the affected count and disabled/hidden when `state.zones.size <= 1`, and a bottom "+ nueva zona" text field + add button calling `viewModel.createZone`.
- [ ] Rewrite `MainActivity.kt`: construct `FirebaseAuth` (sign in anonymously if `currentUser == null`), `FirebaseFirestore`, `FirebaseStorage`, `UserPrefs(context)` once at the top of `setContent`; `NavHost` start destination decided by whether `userPrefs.householdCode`/`userName` flows already have values (collect as state before first composition, e.g. via `produceState`); route `mainList -> addItem` as a bottom sheet triggered by state rather than a nav route (simpler than a fifth destination) or keep it as a route per the existing TODO comments — either is acceptable, pick the bottom-sheet-as-state approach since `AddItemSheet` is a `ModalBottomSheet`, not a full screen. Wrap the whole tree in `ListaDeLaCasaTheme` (Task 2) instead of bare `MaterialTheme`.
- [ ] Delete `TODO_SCREENS.md` now that all four screens exist.
- [ ] Add adaptive icon: `ic_launcher_background.xml` = solid mint (`#0F6E56`) vector rectangle; `ic_launcher_foreground.xml` = simple basket/leaf glyph in white/lime; `mipmap-anydpi-v26/ic_launcher.xml` and `_round.xml` reference both via `<adaptive-icon>`.
- [ ] Create a structurally-valid placeholder `app/google-services.json` (fake `project_id`/`api_key`/`mobilesdk_app_id` for `com.listacasa.app`) purely so `com.google.gms.google-services` plugin doesn't fail config — confirm it's covered by the existing `.gitignore` entry (`git status` must NOT show it as untracked-to-be-added).
- [ ] Run full unit test suite: `./gradlew testDebugUnitTest` → expect all PASS (Tasks 3, 6, 8 tests).
- [ ] Run real build: `./gradlew assembleDebug` → expect `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] If the build fails, fix the reported compile errors iteratively (this is expected to take a few passes given the amount of new code) — do not mark this task done until `assembleDebug` succeeds.
- [ ] Commit: `git add -A -- ':!app/google-services.json' && git commit -m "feat: wire MainActivity navigation, add app icon, remove screen stubs"` (placeholder Firebase config must stay untracked).

### Task 13: README updates (signed APK instructions)

**Files:**
- Modify: `README.md`

- [ ] Add a "Generar APK firmado (release)" section: `keytool -genkey -v -keystore release.keystore -alias listacasa -keyalg RSA -keysize 2048 -validity 10000`, a `signingConfigs` snippet for `app/build.gradle.kts` reading the keystore path/passwords from `local.properties` (never committed), and `./gradlew assembleRelease` producing `app/build/outputs/apk/release/app-release.apk`.
- [ ] Update the "Compilar" section to mention the Gradle wrapper now exists and `local.properties` must point `sdk.dir` at the user's own SDK.
- [ ] Commit: `git add README.md && git commit -m "docs: add signed release APK instructions"`

### Task 14: Push to GitHub (confirm first)

- [ ] Ask the user for explicit go-ahead before pushing (remote `origin` → `SergioDC/HomePantry` already tracks `main`).
- [ ] `git push origin main`.

---

## Self-Review Notes
- Spec §1.1–1.6 covered by Tasks 3/4/9 (household+name), 8/10 (list+progress+zones tabs), 11 (add item, photo, barcode), 12 (manage zones).
- Spec §2 architecture covered by Tasks 2 (theme), 4 (DataStore), 5 (Firestore repos), 7 (Storage), 6 (Retrofit), 11 (CameraX/ML Kit), MainActivity (Navigation Compose).
- Spec §3 auth covered in Task 12 (anonymous sign-in in `MainActivity`).
- Spec §4 Firestore rules: already written verbatim in SPEC.md §4 — nothing to generate, just something the user pastes into the Firebase console per README; no code task needed.
- Spec §5 checklist items map 1:1 to Tasks 1 (build), 9/10/11/12 (screens), 5 (repo wiring), 12 (permissions/errors/icon/build verification), 13 (README signed APK), 14 (GitHub push).
- Spec §6 palette covered by Task 2, consumed by Tasks 10/11 (coral only for delete/error).
- No placeholders left unresolved except the two genuinely external/one-time actions (owner's real `google-services.json`, and the GitHub push confirmation) — both called out explicitly rather than silently assumed.
