package com.homepantry.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Crea un fichero temporal en cacheDir y devuelve su URI vía FileProvider
 * (content://), el formato que exige ActivityResultContracts.TakePicture
 * para escribir la foto capturada por la cámara del sistema.
 */
fun createReceiptCaptureUri(context: Context): Uri {
    val file = File.createTempFile("receipt_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
