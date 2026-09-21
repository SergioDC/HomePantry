package com.homepantry.app.data

/**
 * Paleta fija para colorear a las personas del menú (SPEC 2026-09-21). Cada color tiene dos tonos
 * porque los fondos son opuestos: [onDark] para la app (tema oscuro Nocturne) y [onLight], más
 * oscuro, para la imagen de compartir (fondo blanco). `PersonColorTest` comprueba que ambos se leen.
 * Son ARGB opacos; la UI de Compose los convierte a `Color`. El primero, menta, es el de por defecto.
 */
enum class PersonColor(val label: String, val onDark: Long, val onLight: Long) {
    MINT("Menta", 0xFF5DCAA5, 0xFF0F6E56),
    SKY("Cielo", 0xFF6CB4EE, 0xFF1C6FB0),
    LILAC("Lila", 0xFFB4A7F5, 0xFF5B4BC4),
    PINK("Rosa", 0xFFF29BC0, 0xFFB83A76),
    CORAL("Coral", 0xFFF28B6E, 0xFFC24A25),
    AMBER("Ámbar", 0xFFF2C063, 0xFF9A6A00),
    LIME("Lima", 0xFFB5D96B, 0xFF4E7A16),
    GRAY("Gris", 0xFFB8B8C4, 0xFF6E6F80)
}

/** El color guardado con ese id (el `name` del enum), o null si no hay o ya no existe. */
fun personColorOf(id: String?): PersonColor? = PersonColor.values().firstOrNull { it.name == id }
