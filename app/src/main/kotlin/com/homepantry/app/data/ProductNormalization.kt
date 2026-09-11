package com.homepantry.app.data

import java.text.Normalizer

/**
 * Clave de agrupación para el histórico de compras: minúsculas, sin
 * acentos, espacios colapsados, y sin una "s" final simple (plural naive).
 * No pretende ser perfecta -- el usuario puede editar el nombre en la
 * pantalla de revisión para forzar que dos líneas se agrupen igual.
 */
fun normalizeProductName(name: String): String {
    val lowerTrimmed = name.trim().lowercase()
    val collapsedSpaces = lowerTrimmed.replace(Regex("\\s+"), " ")
    val withoutAccents = Normalizer.normalize(collapsedSpaces, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return if (withoutAccents.length > 3 && withoutAccents.endsWith("s")) {
        withoutAccents.dropLast(1)
    } else {
        withoutAccents
    }
}
