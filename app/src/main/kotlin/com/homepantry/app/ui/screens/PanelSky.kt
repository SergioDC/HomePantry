package com.homepantry.app.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.Precipitation
import com.homepantry.app.data.SceneSpec
import com.homepantry.app.data.SkyPhase
import com.homepantry.app.data.WeatherKind
import com.homepantry.app.data.sceneSpec
import com.homepantry.app.data.skyGradient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Una partícula de la escena: posición horizontal [x] (0–1), [y] (0–1, solo estrellas), tamaño
 * [size] en dp, [periodMillis] de un ciclo y [phase] (0–1) para que no vayan todas a la vez.
 */
private class Particle(val x: Float, val y: Float, val size: Float, val periodMillis: Float, val phase: Float)

private fun particles(seed: Int, count: Int, size: ClosedFloatingPointRange<Float>, period: ClosedFloatingPointRange<Float>): List<Particle> {
    val random = Random(seed)
    fun Random.between(range: ClosedFloatingPointRange<Float>) = range.start + nextFloat() * (range.endInclusive - range.start)
    return List(count) {
        Particle(random.nextFloat(), random.nextFloat(), random.between(size), random.between(period), random.nextFloat())
    }
}

// Semillas fijas: la escena es siempre la misma y no salta al recomponer.
private val RAIN = particles(11, 130, 14f..32f, 550f..900f)
private val DRIZZLE = particles(17, 45, 8f..16f, 1_100f..1_700f)
private val SNOW = particles(23, 70, 3f..9f, 6_000f..14_000f)
private val STARS = particles(31, 55, 2f..2f, 2_000f..6_000f)
private val CLOUDS = particles(41, 7, 0.6f..1.4f, 70_000f..140_000f)

private val SUN_DAY = listOf(Color(0xFFFFF6C9), Color(0xFFFFD257), Color(0xFFFFAD33))
private val SUN_DUSK = listOf(Color(0xFFFFE9C2), Color(0xFFFFAB5C), Color(0xFFF2713A))
private val MOON = Color(0xFFF4F1DE)
private val FLASH = Color(0xFFE8ECFF)
private val DROP = Color(0xE6D2E6FF)
private val SCRIM = Color(0x800A0C18)

/** Curva del relámpago de la maqueta (posición en un ciclo de 7 s → opacidad). */
private val FLASH_KEYS = listOf(0f to 0f, 0.86f to 0f, 0.87f to 0.8f, 0.885f to 0.1f, 0.9f to 0.6f, 0.92f to 0f, 1f to 0f)
private const val FLASH_CYCLE_MILLIS = 7_000L

/**
 * Cielo animado a pantalla completa detrás del panel de cocina. Todo se dibuja en un único
 * [Canvas] con un reloj propio: solo se repinta, sin recomponer. Con [animate] en false (panel
 * atenuado, app en segundo plano o animaciones desactivadas en el sistema) se queda quieto en el
 * último fotograma, para no gastar batería toda la noche.
 */
@Composable
fun PanelSky(kind: WeatherKind, phase: SkyPhase, animate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val systemAnimations = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val running = animate && systemAnimations
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        // Se sigue desde donde se paró: al despertar el panel no hay saltos.
        val start = withFrameMillis { it } - time
        while (true) withInfiniteAnimationFrameMillis { time = it - start }
    }
    val spec = remember(kind, phase) { sceneSpec(kind, phase) }
    val colors = remember(kind, phase) { skyGradient(kind, phase).map { Color(it) } }

    Canvas(modifier.fillMaxSize()) {
        val t = time.toFloat()
        drawRect(Brush.verticalGradient(colors))
        if (spec.stars) drawStars(t)
        if (spec.sun) drawSun(t, phase == SkyPhase.DUSK)
        if (spec.moon) drawMoon()
        if (spec.fog) drawFog(t)
        drawClouds(t, spec)
        when (spec.precipitation) {
            Precipitation.RAIN, Precipitation.STORM -> drawRain(t, RAIN.take(spec.particles))
            Precipitation.DRIZZLE -> drawRain(t, DRIZZLE.take(spec.particles))
            Precipitation.SNOW -> drawSnow(t, SNOW.take(spec.particles))
            Precipitation.NONE -> Unit
        }
        if (spec.lightning) drawLightning(time)
        // Velo arriba: la cabecera en blanco se lee aunque el cielo sea claro (nieve, niebla).
        val scrimHeight = 340.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(SCRIM, Color.Transparent), endY = scrimHeight), size = Size(size.width, scrimHeight))
    }
}

/** Posición (0–1) de una partícula dentro de su ciclo. */
private fun Particle.progress(t: Float): Float = (t / periodMillis + phase) % 1f

private fun DrawScope.drawStars(t: Float) {
    STARS.forEach { star ->
        val alpha = 0.15f + 0.85f * (0.5f - 0.5f * cos(2f * PI.toFloat() * star.progress(t)))
        drawCircle(Color.White.copy(alpha = alpha), 1.2.dp.toPx(), Offset(star.x * size.width, star.y * size.height * 0.6f))
    }
}

private fun DrawScope.drawSun(t: Float, dusk: Boolean) {
    val radius = 55.dp.toPx()
    val center = Offset(size.width - 445.dp.toPx(), (if (dusk) 190.dp else 70.dp).toPx() + radius)
    // Rayos: una vuelta cada 80 s.
    rotate(t / 80_000f * 360f, center) {
        repeat(24) { i ->
            val angle = i * 2 * PI / 24
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                Color(0x5CFFE28C), center + dir * (radius * 1.25f), center + dir * (radius * 2.1f),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
    val glow = if (dusk) Color(0x73FF8C46) else Color(0x73FFCD5A)
    drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center, radius * 2.4f), radius * 2.4f, center)
    val pulse = 1f + 0.05f * sin(2f * PI.toFloat() * t / 5_000f)
    val core = radius * pulse
    drawCircle(
        Brush.radialGradient(if (dusk) SUN_DUSK else SUN_DAY, center - Offset(core * 0.2f, core * 0.2f), core * 1.2f),
        core, center
    )
}

private fun DrawScope.drawMoon() {
    val radius = 45.dp.toPx()
    val center = Offset(size.width - 445.dp.toPx(), 80.dp.toPx() + radius)
    drawCircle(Brush.radialGradient(listOf(MOON.copy(alpha = 0.18f), Color.Transparent), center, radius * 2f), radius * 2f, center)
    // Creciente: la luna menos ella misma desplazada a la izquierda y algo abajo.
    val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    val shadow = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center + Offset(-22.dp.toPx(), 8.dp.toPx()), radius))
    }
    drawPath(Path().apply { op(full, shadow, PathOperation.Difference) }, MOON)
}

private fun DrawScope.drawFog(t: Float) {
    repeat(4) { i ->
        val period = (16 + i * 5) * 1_000f
        // Vaivén suave de ida y vuelta, como el «alternate» de la maqueta.
        val offset = 0.24f * size.width * sin(2f * PI.toFloat() * (t / (2 * period) + i * 0.25f))
        val center = Offset(size.width / 2 + offset, size.height * (0.08f + i * 0.22f) + 65.dp.toPx())
        val bandHeight = 130.dp.toPx()
        withTransform({ scale(1f, bandHeight / size.width, center) }) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x99ECEFF3), Color.Transparent), center, size.width),
                size.width, center
            )
        }
    }
}

private fun DrawScope.drawClouds(t: Float, spec: SceneSpec) {
    if (spec.clouds == 0) return
    val color = Color(spec.cloudColor)
    val unit = 1.dp.toPx()
    CLOUDS.take(spec.clouds).forEach { cloud ->
        val left = (-0.35f + 1.45f * cloud.progress(t)) * size.width
        val top = size.height * (0.02f + cloud.y * 0.34f)
        val scale = cloud.size
        val alpha = 0.75f + cloud.x * 0.25f
        val pivot = Offset(left + 110 * unit, top + 33 * unit)
        withTransform({ scale(scale, scale, pivot) }) {
            val paint = color.copy(alpha = alpha)
            // Misma silueta que la maqueta: una cápsula y dos círculos encima.
            drawRoundRect(paint, Offset(left, top), Size(220 * unit, 66 * unit), CornerRadius(33 * unit))
            drawCircle(paint, 48 * unit, Offset(left + 82 * unit, top))
            drawCircle(paint, 63 * unit, Offset(left + 155 * unit, top - 7 * unit))
        }
    }
}

private fun DrawScope.drawRain(t: Float, drops: List<Particle>) {
    val w = size.width * 1.2f
    val h = size.height * 1.2f
    rotate(9f) {
        drops.forEach { drop ->
            val x = -0.1f * size.width + drop.x * w
            val y = -0.1f * size.height + (-0.15f + 1.25f * drop.progress(t)) * h
            val length = drop.size.dp.toPx()
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, DROP.copy(alpha = DROP.alpha * (0.4f + 0.6f * drop.y))), y, y + length),
                Offset(x, y), Offset(x, y + length), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawSnow(t: Float, flakes: List<Particle>) {
    flakes.forEach { flake ->
        val p = flake.progress(t)
        val sway = 18.dp.toPx() * sin(2f * PI.toFloat() * p)
        drawCircle(
            Color.White.copy(alpha = 0.5f + 0.5f * flake.y),
            flake.size.dp.toPx() / 2,
            Offset(flake.x * size.width + sway, (-0.05f + 1.1f * p) * size.height)
        )
    }
}

private fun DrawScope.drawLightning(time: Long) {
    val p = (time % FLASH_CYCLE_MILLIS).toFloat() / FLASH_CYCLE_MILLIS
    val (a, b) = FLASH_KEYS.zipWithNext().first { (_, end) -> p <= end.first }
    val alpha = a.second + (b.second - a.second) * ((p - a.first) / (b.first - a.first))
    if (alpha <= 0f) return
    drawRect(FLASH.copy(alpha = alpha))
    val unit = 1.dp.toPx()
    val left = size.width * 0.22f
    val bolt = Path().apply {
        moveTo(left + 55 * unit, 0f)
        lineTo(left + 30 * unit, 95 * unit)
        lineTo(left + 58 * unit, 95 * unit)
        lineTo(left + 26 * unit, 220 * unit)
    }
    drawPath(bolt, Color(0xFFF4F1FF).copy(alpha = alpha), style = Stroke(5 * unit, join = StrokeJoin.Round))
}
