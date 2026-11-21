package com.homepantry.app.data

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fichero del borrador de la importación de cargos de una casa (uno a la vez, de cualquier grupo). */
fun cardImportDraftFile(filesDir: File, householdCode: String): File =
    File(filesDir, "card_import_draft_$householdCode.json")

/** Guarda el borrador en [file]; guardar null lo borra. Las lecturas nunca lanzan. */
class CardImportDraftStore(private val file: File) {

    suspend fun load(): CardImportDraft? = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) cardImportDraftFromJson(file.readText()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun save(draft: CardImportDraft?) = withContext(Dispatchers.IO) {
        if (draft == null) {
            file.delete()
        } else {
            // Se escribe aparte y se renombra para no dejar un fichero a medias si la app muere.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(cardImportDraftToJson(draft))
            tmp.renameTo(file)
        }
    }
}
