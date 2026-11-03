package com.homepantry.app.data

import kotlin.math.roundToLong

/**
 * Peso o volumen de UNA unidad de un envase, siempre en kg o l ([unit] es "KG" o "L"), para
 * comparar precios por kilo o por litro: 300 g es `PackSize(0.3, "KG")`.
 */
data class PackSize(val amount: Double, val unit: String)

/** Nombre impreso sin el tamaño y el tamaño que llevaba, si llevaba alguno. */
data class SplitName(val name: String, val pack: PackSize?)

/**
 * Un tamaño suelto dentro del nombre ("300GR", "1,5 L", "33CL", "6X1L"). Los bordes impiden que
 * "4" en "PILAS AA 4" o una "L" de una palabra cuenten como tamaño, y que "1/2 KG" se lea como 2 kg.
 */
private val PACK_SIZE = Regex(
    """(?<![\p{L}\d/])(?:(\d+)\s*[xX]\s*)?(\d+(?:[.,]\d+)?)\s*(kgs?|grs?|g|lt|l|cl|ml)(?![\p{L}\d])""",
    RegexOption.IGNORE_CASE
)

private fun round3(value: Double): Double = (value * 1000).roundToLong() / 1000.0

/**
 * Separa el tamaño del envase del nombre impreso, para que "ARANDANO 300GR" y "ARANDANO 150GR"
 * sean el mismo producto y el peso sirva para el precio por kilo. Solo se usa la primera
 * coincidencia; un multipack ("6X1L") cuenta como el total de la unidad comprada.
 */
fun splitPackSize(rawName: String): SplitName {
    val match = PACK_SIZE.find(rawName) ?: return SplitName(rawName.trim(), null)
    val multiplier = match.groupValues[1].toIntOrNull() ?: 1
    val number = match.groupValues[2].replace(",", ".").toDoubleOrNull()
    if (number == null || number <= 0 || multiplier <= 0) return SplitName(rawName.trim(), null)
    val (factor, unit) = when (match.groupValues[3].lowercase()) {
        "kg", "kgs" -> 1.0 to "KG"
        "g", "gr", "grs" -> 0.001 to "KG"
        "l", "lt" -> 1.0 to "L"
        "cl" -> 0.01 to "L"
        else -> 0.001 to "L" // ml
    }
    val name = rawName.removeRange(match.range).replace(Regex("\\s+"), " ").trim()
    return SplitName(name, PackSize(round3(number * factor * multiplier), unit))
}

/**
 * Clave del alias de un nombre impreso: sin el tamaño (un alias sirve para todos los tamaños) y
 * normalizada. "/" y "." no valen en un id de documento de Firestore. Null si no queda nombre.
 */
fun aliasKey(rawName: String): String? =
    normalizeProductName(splitPackSize(rawName).name)
        .replace('/', '_')
        .replace('.', '_')
        .takeIf { it.isNotBlank() }

/** Envase que escribe el usuario: cantidad (admite coma) en G, KG, ML o L. Null si no es > 0. */
fun toPackSize(amountText: String, displayUnit: String): PackSize? {
    val amount = amountText.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    return when (displayUnit) {
        "G" -> PackSize(round3(amount / 1000), "KG")
        "KG" -> PackSize(round3(amount), "KG")
        "ML" -> PackSize(round3(amount / 1000), "L")
        "L" -> PackSize(round3(amount), "L")
        else -> null
    }
}

/** Texto y unidad para mostrar o editar un envase: por debajo de 1 kg o 1 l, en g o ml. */
fun packDisplay(pack: PackSize): Pair<String, String> {
    val small = pack.amount < 1.0
    val amount = round3(if (small) pack.amount * 1000 else pack.amount)
    val unit = when {
        pack.unit == "L" && small -> "ML"
        pack.unit == "L" -> "L"
        small -> "G"
        else -> "KG"
    }
    return formatQuantity(amount) to unit
}
