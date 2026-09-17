# Interpretación de tickets con Gemini (API key propia) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user paste their own free Gemini API key into Ajustes so that receipt scans are interpreted by Gemini (image understanding, structured JSON output) instead of the ML Kit OCR + regex parser, with automatic per-scan fallback to the classic OCR path on any failure.

**Architecture:** A new `data` layer (`GeminiApiKeyStore`, `GeminiReceiptApi`/`GeminiReceiptClient`, `GeminiReceiptRecognizer`) mirrors the existing `OpenFoodFactsApi.kt` Retrofit pattern and the existing `ReceiptTextRecognizer.kt` responsibility split (pure mapping functions vs. Android/network orchestration). `PurchaseHistoryScreen.processReceiptUri` branches on whether a key is stored; `ManageZonesScreen` ("Ajustes") gets a new card to manage the key.

**Tech Stack:** Kotlin, Jetpack Compose, Retrofit + Gson (already in the project), `androidx.security:security-crypto` (new), Gemini API `v1beta/interactions` endpoint (`generativelanguage.googleapis.com`).

**Spec:** `docs/superpowers/specs/2026-09-17-gemini-receipt-ocr-design.md`

## Global Constraints

- BYOK only: no bundled/factory API key anywhere in the app or repo. Each user supplies their own.
- Free tier only: only `generativelanguage.googleapis.com` (Google AI Studio), never Vertex AI.
- When a key is present, Gemini **replaces** the classic OCR path entirely for that scan; it is not a per-scan user toggle.
- On ANY Gemini failure for a given scan (network, auth, quota, malformed response), fall back automatically to the classic OCR path for that same scan, with a short snackbar explaining why.
- The API key is a credential: stored via `EncryptedSharedPreferences`, never in the plaintext `UserPrefs` DataStore, never logged.
- The key is configured in the existing "Ajustes" screen (`ManageZonesScreen.kt`) — no new settings screen.
- Validate the key with a minimal live call when the user presses Guardar; do not save an unvalidated key.

---

### Task 1: Add the `security-crypto` dependency

**Files:**
- Modify: `app/build.gradle.kts:80-82`

**Interfaces:**
- Produces: `androidx.security:security-crypto:1.1.0` available on the `app` module classpath (provides `androidx.security.crypto.MasterKey` and `androidx.security.crypto.EncryptedSharedPreferences`, used by Task 5).

- [ ] **Step 1: Add the dependency line**

In `app/build.gradle.kts`, right after the DataStore line:

```kotlin
    // Persistencia local ligera (nombre de usuario, código de casa)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Almacenamiento cifrado (API key de Gemini que aporta cada usuario)
    implementation("androidx.security:security-crypto:1.1.0")

```

- [ ] **Step 2: Sync/build to confirm the dependency resolves**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no new source uses the library yet, this just confirms the artifact resolves).

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add security-crypto for encrypted API key storage"
```

---

### Task 2: `GeminiReceiptApi` — Retrofit interface and data classes

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptApi.kt`

**Interfaces:**
- Consumes: nothing new (only Retrofit/Gson, already a project dependency).
- Produces (used by Task 3/4 and their tests): `GeminiInputPart(type, text?, data?, mimeType?)`, `GeminiResponseFormat(type, mimeType, schema: Map<String, Any>)`, `GeminiInteractionRequest(model, input, responseFormat?)`, `GeminiStepContent(type, text?)`, `GeminiStep(type, content: List<GeminiStepContent>?)`, `GeminiInteractionResponse(status?, steps: List<GeminiStep>?)`, `GeminiReceiptApi.createInteraction(apiKey, request): Response<GeminiInteractionResponse>`, `GeminiReceiptClient.api(): GeminiReceiptApi`, `PRODUCTS_JSON_SCHEMA: Map<String, Any>`.

This mirrors `OpenFoodFactsApi.kt`'s pattern (interface + client object). The request/response shape follows Gemini's `v1beta/interactions` endpoint (confirmed against current `ai.google.dev` docs 2026-09-17): header auth `x-goog-api-key`, body `{model, input: [...], response_format: {...}}`, response `{status, steps: [{type, content: [{type, text}]}]}` where the generated text lives in the `model_output` step.

- [ ] **Step 1: Write the file**

```kotlin
package com.homepantry.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** SPEC 2026-09-17: interpretación de tickets con Gemini (v1beta/interactions). */
data class GeminiInputPart(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    @SerializedName("mime_type") val mimeType: String? = null
)

data class GeminiResponseFormat(
    val type: String = "text",
    @SerializedName("mime_type") val mimeType: String = "application/json",
    val schema: Map<String, Any>
)

data class GeminiInteractionRequest(
    val model: String,
    val input: List<GeminiInputPart>,
    @SerializedName("response_format") val responseFormat: GeminiResponseFormat? = null
)

data class GeminiStepContent(val type: String, val text: String? = null)
data class GeminiStep(val type: String, val content: List<GeminiStepContent>? = null)
data class GeminiInteractionResponse(val status: String? = null, val steps: List<GeminiStep>? = null)

/** Esquema JSON que se le pide a Gemini como salida estructurada del ticket. */
val PRODUCTS_JSON_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
        "products" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string"),
                    "price" to mapOf("type" to "number")
                ),
                "required" to listOf("name", "price")
            )
        )
    ),
    "required" to listOf("products")
)

interface GeminiReceiptApi {
    @POST("v1beta/interactions")
    suspend fun createInteraction(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiInteractionRequest
    ): Response<GeminiInteractionResponse>
}

object GeminiReceiptClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun api(): GeminiReceiptApi = retrofit.create(GeminiReceiptApi::class.java)
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptApi.kt
git commit -m "feat: add Retrofit client for the Gemini interactions API"
```

---

### Task 3: `GeminiReceiptRecognizer` — pure mapping functions (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt` (this task writes only the pure, network-free parts; Task 4 adds the suspend network functions to the same file)
- Test: `app/src/test/kotlin/com/homepantry/app/data/GeminiReceiptRecognizerTest.kt`

**Interfaces:**
- Consumes: `ParsedReceiptLine` (from `ReceiptParsing.kt`), `GeminiInteractionResponse`/`GeminiStep`/`GeminiStepContent` (from Task 2).
- Produces (used by Task 4 and by `PurchaseHistoryScreen` in Task 8): sealed `GeminiReceiptException`, `GeminiAuthException`, `GeminiQuotaException`, `GeminiNetworkException(cause)`, `GeminiResponseException(reason)`; `internal fun classifyHttpErrorCode(code: Int): GeminiReceiptException?`; `internal fun extractOutputText(response: GeminiInteractionResponse): String`; `internal fun mapGeminiOutputTextToLines(outputText: String): List<ParsedReceiptLine>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.homepantry.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiReceiptRecognizerTest {
    @Test fun `maps a well-formed products JSON to parsed lines`() {
        val json = """{"products":[{"name":"TOMATE RAMA","price":1.5},{"name":"LECHE ENTERA","price":0.89}]}"""
        assertEquals(
            listOf(ParsedReceiptLine("TOMATE RAMA", 1.5), ParsedReceiptLine("LECHE ENTERA", 0.89)),
            mapGeminiOutputTextToLines(json)
        )
    }

    @Test fun `skips entries missing a name or price instead of crashing`() {
        val json = """{"products":[
            {"name":"TOMATE RAMA","price":1.5},
            {"name":null,"price":2.0},
            {"name":"SIN PRECIO","price":null}
        ]}"""
        assertEquals(listOf(ParsedReceiptLine("TOMATE RAMA", 1.5)), mapGeminiOutputTextToLines(json))
    }

    @Test fun `throws GeminiResponseException on malformed JSON`() {
        assertThrows(GeminiResponseException::class.java) { mapGeminiOutputTextToLines("not json") }
    }

    @Test fun `throws GeminiResponseException when the products field is missing`() {
        assertThrows(GeminiResponseException::class.java) { mapGeminiOutputTextToLines("""{"other":"field"}""") }
    }

    @Test fun `extracts the model_output text from a well-formed interaction response`() {
        val response = GeminiInteractionResponse(
            status = "completed",
            steps = listOf(
                GeminiStep(type = "tool_call", content = null),
                GeminiStep(
                    type = "model_output",
                    content = listOf(GeminiStepContent(type = "text", text = """{"products":[]}"""))
                )
            )
        )
        assertEquals("""{"products":[]}""", extractOutputText(response))
    }

    @Test fun `throws GeminiResponseException when there is no model_output step`() {
        val response = GeminiInteractionResponse(status = "failed", steps = emptyList())
        assertThrows(GeminiResponseException::class.java) { extractOutputText(response) }
    }

    @Test fun `classifies 401 and 403 as auth errors`() {
        assertTrue(classifyHttpErrorCode(401) is GeminiAuthException)
        assertTrue(classifyHttpErrorCode(403) is GeminiAuthException)
    }

    @Test fun `classifies 429 as a quota error`() {
        assertTrue(classifyHttpErrorCode(429) is GeminiQuotaException)
    }

    @Test fun `returns null for an unrelated status code`() {
        assertNull(classifyHttpErrorCode(500))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.GeminiReceiptRecognizerTest"`
Expected: FAIL to compile (the referenced functions/classes don't exist yet).

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.homepantry.app.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

sealed class GeminiReceiptException(message: String) : Exception(message)
class GeminiAuthException : GeminiReceiptException("La clave de Gemini no es válida o no tiene permisos.")
class GeminiQuotaException : GeminiReceiptException("Se ha agotado la cuota gratuita de Gemini por hoy.")
class GeminiNetworkException(cause: Throwable) : GeminiReceiptException("Sin conexión con Gemini: ${cause.message}")
class GeminiResponseException(reason: String) : GeminiReceiptException("Respuesta inesperada de Gemini: $reason")

private val geminiGson = Gson()

private data class GeminiProduct(val name: String?, val price: Double?)
private data class GeminiProductsPayload(val products: List<GeminiProduct>?)

internal fun classifyHttpErrorCode(code: Int): GeminiReceiptException? = when (code) {
    401, 403 -> GeminiAuthException()
    429 -> GeminiQuotaException()
    else -> null
}

internal fun extractOutputText(response: GeminiInteractionResponse): String {
    return response.steps
        ?.firstOrNull { it.type == "model_output" }
        ?.content
        ?.firstOrNull { it.type == "text" }
        ?.text
        ?: throw GeminiResponseException("no se encontró texto de salida en la respuesta")
}

internal fun mapGeminiOutputTextToLines(outputText: String): List<ParsedReceiptLine> {
    val payload = try {
        geminiGson.fromJson(outputText, GeminiProductsPayload::class.java)
    } catch (e: JsonSyntaxException) {
        throw GeminiResponseException("el JSON de productos no es válido")
    }
    val products = payload?.products ?: throw GeminiResponseException("falta el campo 'products'")
    return products.mapNotNull { product ->
        val name = product.name?.trim()
        val price = product.price
        if (name.isNullOrEmpty() || price == null) null else ParsedReceiptLine(name, price)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.homepantry.app.data.GeminiReceiptRecognizerTest"`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt app/src/test/kotlin/com/homepantry/app/data/GeminiReceiptRecognizerTest.kt
git commit -m "feat: parse Gemini interaction responses into receipt lines"
```

---

### Task 4: `GeminiReceiptRecognizer` — network orchestration

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt` (append to the file from Task 3)

**Interfaces:**
- Consumes: `ImageCompressor.compress(context, uri): Uri` (existing, `ImageCompression.kt`), `GeminiReceiptClient.api()`, `GeminiInteractionRequest`/`GeminiInputPart`/`GeminiResponseFormat`/`PRODUCTS_JSON_SCHEMA` (Task 2), `classifyHttpErrorCode`/`extractOutputText`/`mapGeminiOutputTextToLines`/exception types (Task 3).
- Produces (used by Task 7 and Task 8): `suspend fun recognizeReceiptWithGemini(context: Context, imageUri: Uri, apiKey: String): List<ParsedReceiptLine>`, `suspend fun validateGeminiApiKey(apiKey: String)` (returns normally on success, throws a `GeminiReceiptException` subtype otherwise).

No unit test for this step: it depends on `android.content.Context`, `android.net.Uri` and a live network call, the same reason `ReceiptTextRecognizer.kt` and `ImageCompression.kt` have none — verified manually on-device in Task 10.

- [ ] **Step 1: Append the orchestration code**

Add to the end of `GeminiReceiptRecognizer.kt` (new imports go at the top of the file alongside the existing ones):

```kotlin
import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.IOException
```

```kotlin
private const val GEMINI_MODEL = "gemini-3.8-flash"

private val RECEIPT_PROMPT = """
    Eres un asistente que extrae la lista de la compra de la foto de un
    ticket de supermercado español. Devuelve TODOS los productos comprados
    con el precio final de esa línea (el que aparece impreso, ya con
    descuentos de esa línea aplicados si los hay). Ignora totales,
    subtotales, líneas de IVA, cambio, tarjeta/efectivo, datos de la
    tienda, promociones sueltas y cualquier línea que no sea un producto
    comprado.
""".trimIndent()

private suspend fun callGemini(apiKey: String, request: GeminiInteractionRequest): GeminiInteractionResponse {
    val response = try {
        GeminiReceiptClient.api().createInteraction(apiKey, request)
    } catch (e: IOException) {
        throw GeminiNetworkException(e)
    }
    classifyHttpErrorCode(response.code())?.let { throw it }
    if (!response.isSuccessful) {
        throw GeminiResponseException("HTTP ${response.code()}")
    }
    return response.body() ?: throw GeminiResponseException("cuerpo de respuesta vacío")
}

/** Manda una foto de ticket a Gemini y devuelve las líneas ya estructuradas (sustituye a ReceiptTextRecognizer + parseReceiptLines para este escaneo). */
suspend fun recognizeReceiptWithGemini(context: Context, imageUri: Uri, apiKey: String): List<ParsedReceiptLine> {
    val compressedUri = ImageCompressor.compress(context, imageUri)
    val imageBytes = context.contentResolver.openInputStream(compressedUri)?.use { it.readBytes() }
        ?: throw GeminiResponseException("no se pudo leer la foto comprimida del ticket")
    val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

    val request = GeminiInteractionRequest(
        model = GEMINI_MODEL,
        input = listOf(
            GeminiInputPart(type = "text", text = RECEIPT_PROMPT),
            GeminiInputPart(type = "image", data = base64Image, mimeType = "image/jpeg")
        ),
        responseFormat = GeminiResponseFormat(schema = PRODUCTS_JSON_SCHEMA)
    )

    val body = callGemini(apiKey, request)
    val outputText = extractOutputText(body)
    return mapGeminiOutputTextToLines(outputText)
}

/** Llamada mínima para confirmar que una API key es válida antes de guardarla. Lanza GeminiReceiptException si no lo es. */
suspend fun validateGeminiApiKey(apiKey: String) {
    val request = GeminiInteractionRequest(model = GEMINI_MODEL, input = listOf(GeminiInputPart(type = "text", text = "OK")))
    callGemini(apiKey, request)
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the full unit test suite (confirm nothing broke)**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/GeminiReceiptRecognizer.kt
git commit -m "feat: call Gemini to interpret a receipt photo end to end"
```

---

### Task 5: `GeminiApiKeyStore` — encrypted key storage

**Files:**
- Create: `app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt`

**Interfaces:**
- Consumes: `androidx.security.crypto.MasterKey`/`EncryptedSharedPreferences` (Task 1's dependency).
- Produces (used by Task 7, Task 8, Task 9): `class GeminiApiKeyStore(context: Context)` with `suspend fun getApiKey(): String?`, `suspend fun save(key: String)`, `suspend fun clear()`.

No unit test: `EncryptedSharedPreferences` needs a real Android Keystore, unavailable in plain JVM unit tests — same constraint noted in `ImageCompression.kt`. Verified manually in Task 10 (save a key, kill and reopen the app, confirm Ajustes still shows it saved).

- [ ] **Step 1: Write the file**

```kotlin
package com.homepantry.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_FILE_NAME = "gemini_api_key_store"
private const val KEY_API_KEY = "api_key"

/**
 * Almacén cifrado (Android Keystore vía EncryptedSharedPreferences) de la
 * API key de Gemini que cada usuario aporta -- a diferencia de [UserPrefs]
 * (DataStore en texto plano para nombre/código de casa), esto es una
 * credencial real y nunca se sincroniza entre miembros del hogar.
 */
class GeminiApiKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    suspend fun save(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API_KEY).apply()
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/data/GeminiApiKeyStore.kt
git commit -m "feat: store the Gemini API key encrypted on-device"
```

---

### Task 6: New strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:64` (after `zones_members_title`, before the members list) — actually insert right after `zones_household_code_label`'s block, i.e. after line 61's string and before line 64; see step 1 for exact anchor.
- Modify: `app/src/main/res/values/strings.xml:130-131` (purchase history section)

**Interfaces:**
- Produces: string resources consumed by Task 7 (`ManageZonesScreen`) and Task 8 (`PurchaseHistoryScreen`).

- [ ] **Step 1: Add the Ajustes strings**

In `strings.xml`, right after the line `<string name="zones_household_code_label">Código de tu casa</string>` (line 61) and before `zones_members_title` (line 64), insert:

```xml
    <string name="zones_gemini_api_key_label">Interpretación de tickets con IA (opcional)</string>
    <string name="zones_gemini_api_key_description">Pega aquí tu propia clave gratuita de Gemini para que los tickets se interpreten con IA en vez del lector de texto clásico. La foto del ticket se envía a la API de Google al usar esta opción.</string>
    <string name="zones_gemini_api_key_toggle_visibility_cd">Mostrar/ocultar clave</string>
    <string name="zones_gemini_api_key_help_link">¿Cómo consigo mi clave gratuita?</string>
    <string name="zones_gemini_api_key_save">Guardar</string>
    <string name="zones_gemini_api_key_remove">Quitar</string>
    <string name="zones_gemini_api_key_saved">Clave guardada</string>
    <string name="zones_gemini_api_key_removed">Clave eliminada</string>
```

- [ ] **Step 2: Add the purchase history fallback string**

Right after `<string name="purchase_history_ocr_error">No se pudo procesar la foto. Inténtalo de nuevo.</string>` (line 131), insert:

```xml
    <string name="purchase_history_gemini_fallback">No se pudo usar IA (%1$s); se ha usado el modo clásico.</string>
```

- [ ] **Step 3: Build to confirm the XML is well-formed**

Run: `./gradlew.bat :app:processDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add strings for the Gemini API key setting"
```

---

### Task 7: API key card in Ajustes (`ManageZonesScreen`)

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/ManageZonesScreen.kt`

**Interfaces:**
- Consumes: `GeminiApiKeyStore` (Task 5), `validateGeminiApiKey`/`GeminiReceiptException` (Task 4/3), string resources (Task 6).
- Produces: `ManageZonesScreen(..., geminiApiKeyStore: GeminiApiKeyStore, ...)` — new required parameter, wired in Task 9.

No unit test: this is a Compose screen, same as the rest of this file (no existing Compose UI tests in the project) — verified manually in Task 10.

- [ ] **Step 1: Add the new parameter and imports**

In `ManageZonesScreen.kt`, change the function signature:

```kotlin
@Composable
fun ManageZonesScreen(
    viewModel: AppViewModel,
    householdCode: String,
    userName: String,
    userPrefs: UserPrefs,
    geminiApiKeyStore: GeminiApiKeyStore,
    onBack: () -> Unit,
    onEditName: () -> Unit
) {
```

Add these imports alongside the existing ones (keep the existing import list, just add):

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.GeminiReceiptException
import com.homepantry.app.data.validateGeminiApiKey
```

(`androidx.compose.ui.unit.dp` may already be imported — keep only one copy.)

- [ ] **Step 2: Insert the new card in the LazyColumn**

Right after the household code `item { Card(...) }` block (ends at line 146, right before `item { Text(...zones_members_title...) }`), insert:

```kotlin
            item {
                GeminiApiKeyCard(
                    geminiApiKeyStore = geminiApiKeyStore,
                    snackbarHostState = snackbarHostState,
                    scope = scope
                )
            }
```

- [ ] **Step 3: Add the card composable at the bottom of the file**

Add before the final closing of the file (after the existing `ManageZonesScreen` function's closing brace):

```kotlin
@Composable
private fun GeminiApiKeyCard(
    geminiApiKeyStore: GeminiApiKeyStore,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var storedKey by remember { mutableStateOf<String?>(null) }
    var inputValue by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val savedMessage = stringResource(R.string.zones_gemini_api_key_saved)
    val removedMessage = stringResource(R.string.zones_gemini_api_key_removed)

    LaunchedEffect(Unit) {
        val existing = geminiApiKeyStore.getApiKey()
        storedKey = existing
        inputValue = existing ?: ""
    }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.zones_gemini_api_key_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.zones_gemini_api_key_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.zones_gemini_api_key_toggle_visibility_cd)
                        )
                    }
                }
            )
            TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
                Text(stringResource(R.string.zones_gemini_api_key_help_link))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (storedKey != null) {
                    TextButton(onClick = {
                        scope.launch {
                            geminiApiKeyStore.clear()
                            storedKey = null
                            inputValue = ""
                            snackbarHostState.showSnackbar(removedMessage)
                        }
                    }) {
                        Text(stringResource(R.string.zones_gemini_api_key_remove))
                    }
                }
                Button(
                    enabled = inputValue.isNotBlank() && !validating,
                    onClick = {
                        val candidate = inputValue.trim()
                        validating = true
                        scope.launch {
                            try {
                                validateGeminiApiKey(candidate)
                                geminiApiKeyStore.save(candidate)
                                storedKey = candidate
                                snackbarHostState.showSnackbar(savedMessage)
                            } catch (e: GeminiReceiptException) {
                                snackbarHostState.showSnackbar(e.message ?: "Error")
                            } finally {
                                validating = false
                            }
                        }
                    }
                ) {
                    if (validating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.zones_gemini_api_key_save))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Compile**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (MainActivity will fail to compile until Task 9 updates the call site — that's expected and fixed next; if you want a green build at this exact point, temporarily is not necessary, proceed to Task 9 before running a full build)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/ManageZonesScreen.kt
git commit -m "feat: add Gemini API key card to Ajustes"
```

---

### Task 8: Branch on the API key in `PurchaseHistoryScreen`

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt:75-93`

**Interfaces:**
- Consumes: `GeminiApiKeyStore.getApiKey()` (Task 5), `recognizeReceiptWithGemini`/`GeminiReceiptException` (Task 4/3), `recognizeReceiptTextLines`/`parseReceiptLines` (existing).
- Produces: `PurchaseHistoryScreen(..., geminiApiKeyStore: GeminiApiKeyStore, ...)` — new required parameter, wired in Task 9.

This also removes the temporary `android.util.Log.d("ReceiptOCRDebug", ...)` line added during the OCR investigation earlier this session — it was diagnostic-only and should not ship.

No unit test (Compose screen + network + Android Context, same as Task 7) — verified manually in Task 10.

- [ ] **Step 1: Add the new parameter**

Change the function signature to add `geminiApiKeyStore: GeminiApiKeyStore` (keep every existing parameter):

```kotlin
@Composable
fun PurchaseHistoryScreen(
    viewModel: AppViewModel,
    geminiApiKeyStore: GeminiApiKeyStore,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit
) {
```

- [ ] **Step 2: Replace `processReceiptUri`**

Replace the whole current function body (the one containing the temporary `Log.d("ReceiptOCRDebug", ...)` line) with:

```kotlin
    fun processReceiptUri(uri: Uri) {
        processingOcr = true
        scope.launch {
            val apiKey = geminiApiKeyStore.getApiKey()
            var parsed: List<ParsedReceiptLine> = emptyList()
            var geminiFailureReason: String? = null
            var usedClassicAfterGeminiFailure = false

            if (apiKey != null) {
                val geminiResult = runCatching { recognizeReceiptWithGemini(context, uri, apiKey) }
                val geminiLines = geminiResult.getOrNull()
                if (geminiLines != null) {
                    parsed = geminiLines
                } else {
                    val failure = geminiResult.exceptionOrNull()
                    geminiFailureReason = (failure as? GeminiReceiptException)?.message ?: failure?.message ?: "error desconocido"
                    usedClassicAfterGeminiFailure = true
                }
            }

            var classicFailed = false
            if (apiKey == null || usedClassicAfterGeminiFailure) {
                val ocrResult = runCatching { recognizeReceiptTextLines(context, uri) }
                classicFailed = ocrResult.isFailure
                parsed = parseReceiptLines(ocrResult.getOrDefault(emptyList()))
            }

            processingOcr = false

            when {
                usedClassicAfterGeminiFailure && geminiFailureReason != null -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.purchase_history_gemini_fallback, geminiFailureReason)
                        )
                    }
                }
                classicFailed -> {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.purchase_history_ocr_error)) }
                }
                parsed.isEmpty() -> {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.purchase_history_ocr_empty)) }
                }
            }

            reviewLines = parsed
            reviewPhotoUri = uri
        }
    }
```

- [ ] **Step 3: Add the new imports**

Alongside the existing imports in this file, add:

```kotlin
import com.homepantry.app.data.GeminiApiKeyStore
import com.homepantry.app.data.GeminiReceiptException
import com.homepantry.app.data.recognizeReceiptWithGemini
```

(`ParsedReceiptLine`, `parseReceiptLines`, `recognizeReceiptTextLines` should already be imported from the earlier receipt-history feature — leave those as-is.)

- [ ] **Step 4: Compile**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: fails only at the `MainActivity.kt` call site (fixed in Task 9) — confirm the error is limited to that file, not to `PurchaseHistoryScreen.kt` itself.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/ui/screens/PurchaseHistoryScreen.kt
git commit -m "feat: use Gemini for receipt scans when a key is configured, with OCR fallback"
```

---

### Task 9: Wire `GeminiApiKeyStore` into `MainActivity`

**Files:**
- Modify: `app/src/main/kotlin/com/homepantry/app/MainActivity.kt:88` and the `manageZones`/`purchaseHistory` composable blocks (around lines 228-246)

**Interfaces:**
- Consumes: `GeminiApiKeyStore(context)` (Task 5), the new `geminiApiKeyStore` parameters added in Task 7/Task 8.

- [ ] **Step 1: Instantiate the store**

Right after the existing line `val userPrefs = remember { UserPrefs(context) }` (line 88), add:

```kotlin
    val geminiApiKeyStore = remember { GeminiApiKeyStore(context) }
```

Add the import alongside the other `com.homepantry.app.data` imports in this file:

```kotlin
import com.homepantry.app.data.GeminiApiKeyStore
```

- [ ] **Step 2: Pass it to `ManageZonesScreen`**

In the `composable("manageZones") { ... }` block, add the parameter:

```kotlin
            composable("manageZones") {
                ManageZonesScreen(
                    viewModel = viewModel,
                    householdCode = code,
                    userName = name,
                    userPrefs = userPrefs,
                    geminiApiKeyStore = geminiApiKeyStore,
                    onBack = { navController.popBackStack() },
                    onEditName = { showEditName = true }
                )
            }
```

- [ ] **Step 3: Pass it to `PurchaseHistoryScreen`**

In the `composable("purchaseHistory") { ... }` block, add the parameter:

```kotlin
            composable("purchaseHistory") {
                PurchaseHistoryScreen(
                    viewModel = viewModel,
                    geminiApiKeyStore = geminiApiKeyStore,
                    onBack = { navController.popBackStack() },
                    onOpenProduct = { normalizedName ->
                        navController.navigate("purchaseDetail/${Uri.encode(normalizedName)}")
                    }
                )
            }
```

- [ ] **Step 4: Full build**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Full unit test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (existing `ReceiptParsingTest` + new `GeminiReceiptRecognizerTest`)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/homepantry/app/MainActivity.kt
git commit -m "feat: wire the Gemini API key store into Ajustes and receipt scanning"
```

---

### Task 10: Manual on-device verification

**Files:** none (verification only)

- [ ] **Step 1: Install on the connected device**

```bash
"C:/Android/platform-tools/adb.exe" install -r "C:/Programacion/Proyectos/HomePantry/app/build/outputs/apk/debug/app-debug.apk"
```

- [ ] **Step 2: Verify the app still works with no key configured**

Open Historial de compras, scan a receipt without having configured any Gemini key. Confirm it behaves exactly as before (classic OCR + review screen).

- [ ] **Step 3: Verify saving an invalid key is rejected**

In Ajustes, paste a made-up string (e.g. `not-a-real-key`) into the new card and press Guardar. Confirm it shows an error and does NOT save (reopen the screen / re-navigate to confirm the field is empty again or the card shows no stored key).

- [ ] **Step 4: Verify saving a real free key succeeds**

Get a real free key from `https://aistudio.google.com/apikey`, paste it, press Guardar. Confirm the "Clave guardada" snackbar appears.

- [ ] **Step 5: Verify a real scan uses Gemini**

Scan one of the real receipts used earlier in this session (Lidl or Aldi photo). Confirm the review screen shows correctly paired name/price lines (this is the actual fix for the row-misalignment bug found earlier).

- [ ] **Step 6: Verify the fallback path**

Temporarily turn off the device's WiFi/mobile data, scan a receipt again with the key still configured. Confirm it shows the "No se pudo usar IA (...); se ha usado el modo clásico" snackbar and still produces a (classic-OCR) result. Turn connectivity back on afterward.

- [ ] **Step 7: Verify "Quitar"**

In Ajustes, press Quitar. Confirm the key field clears and a subsequent scan goes back to the classic OCR path (no Gemini call).
