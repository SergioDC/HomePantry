package com.homepantry.app.data

import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Menú del comedor del Colegio Basauri, que Ausolan publica en Menuo (spec 2026-10-30). La API es
 * interna y sin documentar: los ids salen de la web y pueden cambiar sin aviso.
 */
object ComedorConfig {
    const val BASE_URL = "https://apimenuo.ausolan.com/"
    const val PK_MENU = "fce8da488dcf48bab961b36a009fcfdc"
    const val PK_CENTRO = "4cf8682f16604ae1b1dcb2e400f2972e"
    const val PK_CENTRO_ENVIO = "cef83569f1ae40cc816cb3d300cf64d9"
    /** Dieta basal. */
    const val PK_DIETA = "d12fe25a9bbd49239e3cd65eef62e7c5"
    /** Servicio de comida. */
    const val PK_SERVICIO = "6f6096586ff248189d7d535b8509f411"
    const val PK_IDIOMA_ES = "00242C08E94B10101810432101271385"
    /** Quien come en el comedor: el borrador sale ya asignado a esta persona. */
    const val PERSON = "Oihan"
    val ZONE: ZoneId = ZoneId.of("Europe/Madrid")
}

/** Un parámetro del informe de Menuo: todos los valores van como texto. */
data class ComedorParam(val nombre: String = "", val valorObjeto: String = "")

private val menuoInstant = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/**
 * `FechaIni` y `FechaFin` del mes: la medianoche local del primer y del último día, en UTC, como
 * las manda la web.
 */
fun comedorMonthRange(month: YearMonth, zone: ZoneId = ComedorConfig.ZONE): Pair<String, String> =
    menuoInstant.format(month.atDay(1).atStartOfDay(zone)) to
        menuoInstant.format(month.atEndOfMonth().atStartOfDay(zone))

/**
 * Cuerpo del POST que genera el PDF mensual. Menuo hoy ignora las fechas y devuelve el mes
 * publicado, pero se envían las del mes pedido por si lo corrigen.
 */
fun comedorRequestBody(month: YearMonth): List<ComedorParam> {
    val (start, end) = comedorMonthRange(month)
    return listOf(
        ComedorParam("FechaIni", start),
        ComedorParam("FechaFin", end),
        ComedorParam("PkMenu", ComedorConfig.PK_MENU),
        ComedorParam("PkCentro", ComedorConfig.PK_CENTRO),
        ComedorParam("PkCentroEnvio", ComedorConfig.PK_CENTRO_ENVIO),
        ComedorParam("PkDieta", ComedorConfig.PK_DIETA),
        // Menuo espera un array JSON serializado dentro del texto.
        ComedorParam("PkServicio", "[\"${ComedorConfig.PK_SERVICIO}\"]"),
        ComedorParam("PkIdioma", ComedorConfig.PK_IDIOMA_ES),
        ComedorParam("NombreArchivo", "menu"),
        ComedorParam("TipoVista", "Mensual")
    )
}

private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)

/** Si la respuesta es un PDF: por su content type o por la cabecera `%PDF` de los bytes. */
fun isPdf(contentType: String?, bytes: ByteArray): Boolean =
    contentType?.substringBefore(';')?.trim().equals("application/pdf", ignoreCase = true) ||
        (bytes.size >= PDF_MAGIC.size && PDF_MAGIC.indices.all { bytes[it] == PDF_MAGIC[it] })

/** Huella (SHA-256 en hex) del PDF, para no mandar a Gemini dos veces el mismo. */
fun pdfFingerprint(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Si entre [entries] hay alguna comida de [person]: entonces ese mes ya tiene el menú del comedor. */
fun hasComedorMenu(entries: List<MealEntry>, person: String = ComedorConfig.PERSON): Boolean {
    val key = personKey(person)
    return entries.any { it.slot == MealSlot.LUNCH.name && personKey(it.person.orEmpty()) == key }
}

/**
 * Mes al que importar al pulsar el botón: el leído en la cabecera o, si no se leyó, el siguiente.
 * Null si el PDF es de un mes ya pasado (aún no han publicado el nuevo).
 */
fun manualComedorMonth(detected: YearMonth?, today: LocalDate): YearMonth? {
    val current = YearMonth.from(today)
    return when {
        detected == null -> current.plusMonths(1)
        detected < current -> null
        else -> detected
    }
}

/** Si el PDF leído es de un mes anterior al que se busca (sigue publicado el viejo). */
fun isStaleComedorMonth(detected: YearMonth?, target: YearMonth): Boolean = detected != null && detected < target
