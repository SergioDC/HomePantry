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

/** SPEC.md §1.5: autocompletar nombre de producto a partir del código de barras escaneado. */
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
