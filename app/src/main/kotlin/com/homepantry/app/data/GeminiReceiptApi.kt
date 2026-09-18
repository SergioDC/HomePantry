package com.homepantry.app.data

import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
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
    /**
     * Los 10s por defecto de Retrofit/OkHttp no dan para subir la foto de un
     * ticket y esperar a un modelo con visión (sobre todo en datos móviles);
     * cada timeout se convertiría en un fallback silencioso al OCR clásico.
     */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun api(): GeminiReceiptApi = retrofit.create(GeminiReceiptApi::class.java)
}
