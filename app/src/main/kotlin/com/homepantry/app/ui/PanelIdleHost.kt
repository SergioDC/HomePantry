package com.homepantry.app.ui

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.homepantry.app.data.IdleAction
import com.homepantry.app.data.PANEL_ROUTES
import com.homepantry.app.data.idleAction
import kotlinx.coroutines.delay

/** Brillo de la ventana con el panel atenuado: se ve que está encendido sin deslumbrar de noche. */
private const val DIMMED_BRIGHTNESS = 0.05f

/** Si el panel está atenuado: la escena del cielo se congela para no animar con nadie mirando. */
val LocalPanelDimmed = compositionLocalOf { false }

/** Cada cuánto se comprueba la inactividad; basta con que el panel reaccione en unos segundos. */
private const val IDLE_CHECK_MILLIS = 5_000L

/**
 * Envuelve la navegación de la app para el modo panel de cocina: en el panel la pantalla no se
 * apaga y, tras [com.homepantry.app.data.PANEL_IDLE_MILLIS] sin tocar, baja el brillo; en otra
 * pantalla, vuelve al panel con [onReturnToPanel]. El primer toque con el panel atenuado solo
 * recupera el brillo, para no tachar ni abrir nada a ciegas. Con el modo apagado no hace nada.
 */
@Composable
fun PanelIdleHost(
    panelMode: Boolean,
    currentRoute: String?,
    /** Hay una hoja o un diálogo de la app abierto: sus toques no llegan aquí, pero alguien lo usa. */
    suspended: Boolean,
    onReturnToPanel: () -> Unit,
    content: @Composable () -> Unit
) {
    val activity = LocalActivity.current
    var lastTouch by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var dimmed by remember { mutableStateOf(false) }
    val route by rememberUpdatedState(currentRoute)
    val isSuspended by rememberUpdatedState(suspended)
    val returnToPanel by rememberUpdatedState(onReturnToPanel)
    val onPanel = panelMode && currentRoute in PANEL_ROUTES

    DisposableEffect(onPanel, activity) {
        val window = activity?.window
        if (onPanel) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(dimmed, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = if (dimmed) DIMMED_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    LaunchedEffect(panelMode) {
        if (!panelMode) {
            dimmed = false
            return@LaunchedEffect
        }
        lastTouch = SystemClock.uptimeMillis()
        while (true) {
            delay(IDLE_CHECK_MILLIS)
            // Al cerrar la hoja se empieza a contar de nuevo, no desde el último toque de antes.
            if (isSuspended) lastTouch = SystemClock.uptimeMillis()
            when (idleAction(panelMode, route, SystemClock.uptimeMillis() - lastTouch, isSuspended)) {
                IdleAction.NONE -> Unit
                IdleAction.DIM -> dimmed = true
                IdleAction.RETURN_TO_PANEL -> {
                    lastTouch = SystemClock.uptimeMillis()
                    returnToPanel()
                }
            }
        }
    }

    Box(
        Modifier.pointerInput(panelMode) {
            if (!panelMode) return@pointerInput
            awaitPointerEventScope {
                // Se tragan todos los eventos del gesto que despierta el panel, no solo el primero:
                // si el «soltar» llegara a los hijos, un botón podría activarse igualmente.
                var swallowing = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    lastTouch = SystemClock.uptimeMillis()
                    if (event.type == PointerEventType.Press && dimmed) {
                        dimmed = false
                        swallowing = true
                    }
                    if (swallowing) {
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) swallowing = false
                    }
                }
            }
        }
    ) {
        CompositionLocalProvider(LocalPanelDimmed provides dimmed) { content() }
    }
}
