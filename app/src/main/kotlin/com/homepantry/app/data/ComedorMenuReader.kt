package com.homepantry.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de buscar el menú del comedor. */
sealed interface ComedorFetch {
    /** El PDF es el mismo que la última vez: no se ha llamado a Gemini. */
    data object Unchanged : ComedorFetch
    data class Read(val menu: ParsedMenu, val fingerprint: String) : ComedorFetch
}

/**
 * Pasa la primera página del PDF a imagen, con el lado mayor a [MAX_OCR_SIDE] como las fotos.
 * `PdfRenderer` solo lee de un descriptor con seek, así que el PDF pasa por un fichero temporal
 * que se borra al terminar: no queda nada en el móvil.
 */
suspend fun renderPdfFirstPage(context: Context, pdf: ByteArray): Bitmap = withContext(Dispatchers.IO) {
    val file = File.createTempFile("comedor", ".pdf", context.cacheDir)
    try {
        file.writeBytes(pdf)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) throw ComedorMenuException.NotPdf()
                renderer.openPage(0).use { page ->
                    val scale = MAX_OCR_SIDE.toFloat() / maxOf(page.width, page.height)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    // Un PDF puede tener fondo transparente, y en JPEG saldría negro.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    } catch (e: IOException) {
        throw ComedorMenuException.NotPdf(e)
    } catch (e: SecurityException) {
        throw ComedorMenuException.NotPdf(e)
    } finally {
        file.delete()
    }
}

/**
 * Baja el PDF del comedor y lo lee con Gemini. Si su huella coincide con [skipIfFingerprint] no
 * llama a Gemini (lento y de pago) y devuelve [ComedorFetch.Unchanged]. Lanza
 * [ComedorMenuException] o [GeminiReceiptException].
 */
suspend fun fetchComedorMenu(
    context: Context,
    month: YearMonth,
    apiKey: String,
    model: String,
    skipIfFingerprint: String?
): ComedorFetch {
    val pdf = downloadComedorPdf(month)
    val fingerprint = pdfFingerprint(pdf)
    if (fingerprint == skipIfFingerprint) return ComedorFetch.Unchanged
    val bitmap = renderPdfFirstPage(context, pdf)
    val menu = try {
        recognizeMenuBitmapWithGemini(bitmap, apiKey, model)
    } finally {
        bitmap.recycle()
    }
    return ComedorFetch.Read(menu, fingerprint)
}
