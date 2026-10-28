package com.homepantry.app.data

import com.google.gson.Gson
import java.io.File
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menú leído de una foto y aún sin importar, con lo que el usuario lleva corregido. Vive en el
 * ViewModel y se guarda en disco para que la lectura de Gemini (lenta y de pago) no se pierda al
 * cambiar de pantalla, rotar o si Android cierra la app.
 */
data class MenuImportDraft(
    /** Mes que Gemini leyó en la cabecera, si lo hizo. */
    val detectedMonth: YearMonth?,
    /** Mes elegido para importar. */
    val month: YearMonth,
    /** Lo escrito para «Para quién»; en blanco es «sin asignar». */
    val personText: String,
    val days: List<ParsedMenuDay>
)

// Forma en JSON: YearMonth como texto y valores por defecto para que Gson no deje nulos.
private data class StoredMenuDay(val day: Int = 0, val dishes: List<String> = emptyList())

private data class StoredMenuImportDraft(
    val detectedMonth: String? = null,
    val month: String = "",
    val personText: String = "",
    val days: List<StoredMenuDay> = emptyList()
)

private val gson = Gson()

fun menuImportDraftToJson(draft: MenuImportDraft): String = gson.toJson(
    StoredMenuImportDraft(
        detectedMonth = draft.detectedMonth?.toString(),
        month = draft.month.toString(),
        personText = draft.personText,
        days = draft.days.map { StoredMenuDay(it.day, it.dishes) }
    )
)

/** Null si el texto no es un borrador válido (fichero a medio escribir, formato antiguo…). */
fun menuImportDraftFromJson(json: String): MenuImportDraft? = try {
    val stored = gson.fromJson(json, StoredMenuImportDraft::class.java)
    MenuImportDraft(
        detectedMonth = stored.detectedMonth?.let(YearMonth::parse),
        month = YearMonth.parse(stored.month),
        personText = stored.personText.orEmpty(),
        days = stored.days.orEmpty().map { ParsedMenuDay(it.day, it.dishes.orEmpty()) }
    ).takeIf { it.days.isNotEmpty() }
} catch (e: Exception) {
    null
}

/** Guarda el borrador en [file]; guardar null lo borra. Las lecturas nunca lanzan. */
class MenuImportDraftStore(private val file: File) {

    suspend fun load(): MenuImportDraft? = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) menuImportDraftFromJson(file.readText()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun save(draft: MenuImportDraft?) = withContext(Dispatchers.IO) {
        if (draft == null) {
            file.delete()
        } else {
            // Se escribe aparte y se renombra para no dejar un fichero a medias si la app muere.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(menuImportDraftToJson(draft))
            tmp.renameTo(file)
        }
    }
}
