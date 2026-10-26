package com.homepantry.app.ui

/**
 * Qué grupo de SNcount tiene que estar abierto según las pantallas que lo necesitan. Cada
 * pantalla (entrada de navegación) es un holder: la del grupo, la de un gasto encima, la de
 * editar… Mientras quede alguno vivo, su grupo sigue abierto y sus gastos no se recargan.
 *
 * Va ligado a entradas de navegación y no a la composición porque al navegar hacia delante la
 * pantalla anterior sale de la composición: si cerrara el grupo, la pantalla nueva se quedaría
 * sin sus gastos. Retener y liberar son idempotentes para poder llamarlos en cada composición
 * y en cada evento de ciclo de vida sin llevar la cuenta fuera.
 */
class SplitMonthHolders {
    // Orden de inserción = orden de retención; el último vivo manda.
    private val holders = LinkedHashMap<String, String>()

    val openMonthId: String? get() = holders.values.lastOrNull()

    /** Si [holderId] ya está registrado: para añadir su observer de ciclo de vida una sola vez. */
    fun holds(holderId: String): Boolean = holderId in holders

    /** [holderId] necesita [monthId]. Devuelve el grupo que queda abierto. */
    fun retain(holderId: String, monthId: String): String? {
        if (holders[holderId] != monthId) {
            holders.remove(holderId)
            holders[holderId] = monthId
        }
        return openMonthId
    }

    /** [holderId] ya no existe. Devuelve el grupo que queda abierto (null si ninguno). */
    fun release(holderId: String): String? {
        holders.remove(holderId)
        return openMonthId
    }
}
