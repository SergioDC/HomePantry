package com.homepantry.app.data

/**
 * Valida el campo de cantidad de AddItemSheet (cierre de huecos §9): vacío se
 * asume 1, y cualquier valor debe ser estrictamente mayor que 0.
 * @return la cantidad parseada, o null si el texto no es un número > 0.
 */
fun parseQtyOrDefault(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return 1.0
    val value = trimmed.toDoubleOrNull() ?: return null
    return if (value > 0) value else null
}
