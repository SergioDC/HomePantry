package com.homepantry.app.data

import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Fallos al bajar el menú del comedor. No llevan texto visible: la pantalla elige el string según
 * el tipo (sin red frente a «la web del comedor ha cambiado»).
 */
sealed class ComedorMenuException(cause: Throwable? = null) : Exception(cause) {
    class Network(cause: Throwable) : ComedorMenuException(cause)
    class Http(val code: Int) : ComedorMenuException()
    /** Lo recibido no es un PDF legible: Menuo ha cambiado su API. */
    class NotPdf(cause: Throwable? = null) : ComedorMenuException(cause)
}

/** Informe de Menuo que genera el PDF del menú mensual (spec 2026-10-30). Sin autenticación. */
interface ComedorMenuApi {
    @POST("plapi/Report/GeneratePdfFigma")
    suspend fun generatePdf(@Body body: List<ComedorParam>): Response<ResponseBody>
}

object ComedorMenuClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl(ComedorConfig.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun api(): ComedorMenuApi = retrofit.create(ComedorMenuApi::class.java)
}

/** Descarga el PDF del menú (en memoria, nunca a disco). Menuo devuelve el mes publicado, no [month]. */
suspend fun downloadComedorPdf(month: YearMonth): ByteArray {
    val response = try {
        ComedorMenuClient.api().generatePdf(comedorRequestBody(month))
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw ComedorMenuException.Network(e)
    }
    val body = response.body()
    if (!response.isSuccessful || body == null) {
        response.errorBody()?.close()
        throw ComedorMenuException.Http(response.code())
    }
    val bytes = try {
        body.use { it.bytes() }
    } catch (e: IOException) {
        throw ComedorMenuException.Network(e)
    }
    if (!isPdf(body.contentType()?.toString(), bytes)) throw ComedorMenuException.NotPdf()
    return bytes
}
