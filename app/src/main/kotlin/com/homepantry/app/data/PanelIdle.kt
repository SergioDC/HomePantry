package com.homepantry.app.data

/** Qué hacer cuando nadie toca la tablet del panel de cocina. */
enum class IdleAction { NONE, DIM, RETURN_TO_PANEL }

/** Tiempo sin tocar tras el que se atenúa el panel o se vuelve a él. */
const val PANEL_IDLE_MILLIS = 120_000L

/** Pantallas que forman el panel: en ellas la pantalla no se apaga y se atenúa sin tocar. */
val PANEL_ROUTES = setOf("panel", "panelRestock")

/**
 * Con el modo panel activado y [idleMillis] sin tocar: en el panel se baja el brillo; en
 * cualquier otra pantalla se vuelve al panel, para que la tablet no se quede en Ajustes. Con el
 * modo apagado (los móviles) nunca se hace nada.
 */
fun idleAction(panelMode: Boolean, route: String?, idleMillis: Long): IdleAction = when {
    !panelMode || idleMillis < PANEL_IDLE_MILLIS -> IdleAction.NONE
    route in PANEL_ROUTES -> IdleAction.DIM
    else -> IdleAction.RETURN_TO_PANEL
}
