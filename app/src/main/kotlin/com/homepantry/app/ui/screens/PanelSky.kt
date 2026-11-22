package com.homepantry.app.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.homepantry.app.data.Precipitation
import com.homepantry.app.data.SceneSpec
import com.homepantry.app.data.SkyPhase
import com.homepantry.app.data.WeatherKind
import com.homepantry.app.data.cycleProgress
import com.homepantry.app.data.sceneSpec
import com.homepantry.app.data.skyGradient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Una partícula de la escena: posición horizontal [x] (0–1), [y] (0–1, estrellas y nubes; en
 * gotas y copos, su opacidad), tamaño [size] en dp, [periodMillis] de un ciclo y [phase] (0–1)
 * para que no vayan todas a la vez.
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
private val DROP = Color(0xFFD2E6FF)
private val SCRIM = Color(0x800A0C18)

/** Curva del relámpago de la maqueta: posición en un ciclo de 7 s y opacidad en ese punto. */
private val FLASH_AT = floatArrayOf(0f, 0.86f, 0.87f, 0.885f, 0.9f, 0.92f, 1f)
private val FLASH_ALPHA = floatArrayOf(0f, 0f, 0.8f, 0.1f, 0.6f, 0f, 0f)
private const val FLASH_CYCLE_MILLIS = 7_000f

private const val TWO_PI = (2 * PI).toFloat()

/**
 * Cielo animado a pantalla completa detrás del panel de cocina. Todo se dibuja en una sola capa
 * con un reloj propio: solo se repinta, sin recomponer. Con [animate] en false (panel atenuado,
 * app en segundo plano o animaciones desactivadas en el sistema) se queda quieto en el último
 * fotograma, para no gastar batería toda la noche.
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
    val clouds = remember(spec) { CLOUDS.take(spec.clouds) }
    val drops = remember(spec) {
        when (spec.precipitation) {
            Precipitation.RAIN, Precipitation.STORM -> RAIN.take(spec.particles)
            Precipitation.DRIZZLE -> DRIZZLE.take(spec.particles)
            Precipitation.SNOW, Precipitation.NONE -> emptyList()
        }
    }
    val flakes = remember(spec) { if (spec.precipitation == Precipitation.SNOW) SNOW.take(spec.particles) else emptyList() }

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                // Lo que no cambia de un fotograma a otro se prepara una vez por tamaño y escena.
                val sky = Brush.verticalGradient(colors)
                val scrimHeight = 340.dp.toPx()
                val scrim = Brush.verticalGradient(listOf(SCRIM, Color.Transparent), endY = scrimHeight)
                val moon = if (spec.moon) moonPath(size) else null
                onDrawBehind {
                    val t = time
                    drawRect(sky)
                    if (spec.stars) drawStars(t)
                    if (spec.sun) drawSun(t, phase == SkyPhase.DUSK)
                    if (moon != null) drawMoon(moon)
                    if (spec.fog) drawFog(t)
                    drawClouds(t, clouds, Color(spec.cloudColor))
                    if (drops.isNotEmpty()) drawRain(t, drops)
                    if (flakes.isNotEmpty()) drawSnow(t, flakes)
                    if (spec.lightning) drawLightning(t)
                    // Velo arriba: la cabecera en blanco se lee aunque el cielo sea claro (nieve, niebla).
                    drawRect(scrim, size = Size(size.width, scrimHeight))
                }
            }
    )
}

private fun Particle.progress(t: Long): Float = cycleProgress(t, periodMillis, phase)

private fun DrawScope.drawStars(t: Long) {
    val radius = 1.2.dp.toPx()
    STARS.forEach { star ->
        val alpha = 0.15f + 0.85f * (0.5f - 0.5f * cos(TWO_PI * star.progress(t)))
        drawCircle(Color.White, radius, Offset(star.x * size.width, star.y * size.height * 0.6f), alpha = alpha)
    }
}

private fun DrawScope.drawSun(t: Long, dusk: Boolean) {
    val radius = 55.dp.toPx()
    val center = Offset(size.width - 445.dp.toPx(), (if (dusk) 190.dp else 70.dp).toPx() + radius)
    // Rayos: una vuelta cada 80 s.
    rotate(cycleProgress(t, 80_000f, 0f) * 360f, center) {
        repeat(24) { i ->
            val angle = i * TWO_PI / 24
            val dir = Offset(cos(angle), sin(angle))
            drawLine(
                Color(0x5CFFE28C), center + dir * (radius * 1.25f), center + dir * (radius * 2.1f),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
    val glow = if (dusk) Color(0x73FF8C46) else Color(0x73FFCD5A)
    drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center, radius * 2.4f), radius * 2.4f, center)
    val core = radius * (1f + 0.05f * sin(TWO_PI * cycleProgress(t, 5_000f, 0f)))
    drawCircle(
        Brush.radialGradient(if (dusk) SUN_DUSK else SUN_DAY, center - Offset(core * 0.2f, core * 0.2f), core * 1.2f),
        core, center
    )
}

private fun Density.moonCenter(size: Size): Offset = Offset(size.width - 445.dp.toPx(), 80.dp.toPx() + 45.dp.toPx())

/** Creciente: la luna menos ella misma desplazada a la izquierda y algo abajo. */
private fun Density.moonPath(size: Size): Path {
    val radius = 45.dp.toPx()
    val center = moonCenter(size)
    val full = Path().apply { addOval(Rect(center, radius)) }
    val shadow = Path().apply { addOval(Rect(center + Offset(-22.dp.toPx(), 8.dp.toPx()), radius)) }
    return Path().apply { op(full, shadow, PathOperation.Difference) }
}

private fun DrawScope.drawMoon(crescent: Path) {
    val radius = 45.dp.toPx()
    val center = moonCenter(size)
    drawCircle(Brush.radialGradient(listOf(MOON.copy(alpha = 0.18f), Color.Transparent), center, radius * 2f), radius * 2f, center)
    drawPath(crescent, MOON)
}

private fun DrawScope.drawFog(t: Long) {
    val bandHeight = 130.dp.toPx()
    repeat(4) { i ->
        val period = (16 + i * 5) * 2_000f
        // Vaivén suave de ida y vuelta, como el «alternate» de la maqueta.
        val offset = 0.24f * size.width * sin(TWO_PI * cycleProgress(t, period, i * 0.25f))
        val center = Offset(size.width / 2 + offset, size.height * (0.08f + i * 0.22f) + bandHeight / 2)
        withTransform({ scale(1f, bandHeight / size.width, center) }) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0x99ECEFF3), Color.Transparent), center, size.width),
                size.width, center
            )
        }
    }
}

private fun DrawScope.drawClouds(t: Long, clouds: List<Particle>, color: Color) {
    val unit = 1.dp.toPx()
    clouds.forEach { cloud ->
        val left = (-0.35f + 1.45f * cloud.progress(t)) * size.width
        val top = size.height * (0.02f + cloud.y * 0.34f)
        val scale = cloud.size
        val alpha = 0.75f + cloud.x * 0.25f
        withTransform({ scale(scale, scale, Offset(left + 110 * unit, top + 33 * unit)) }) {
            // Misma silueta que la maqueta: una cápsula y dos círculos encima.
            drawRoundRect(color, Offset(left, top), Size(220 * unit, 66 * unit), CornerRadius(33 * unit), alpha = alpha)
            drawCircle(color, 48 * unit, Offset(left + 82 * unit, top), alpha = alpha)
            drawCircle(color, 63 * unit, Offset(left + 155 * unit, top - 7 * unit), alpha = alpha)
        }
    }
}

private fun DrawScope.drawRain(t: Long, drops: List<Particle>) {
    val w = size.width * 1.2f
    val h = size.height * 1.2f
    val stroke = 2.dp.toPx()
    rotate(9f) {
        drops.forEach { drop ->
            val x = -0.1f * size.width + drop.x * w
            val y = -0.1f * size.height + (-0.15f + 1.25f * drop.progress(t)) * h
            // Color liso con opacidad por gota: un degradado por gota serían 130 shaders por fotograma.
            drawLine(
                DROP, Offset(x, y), Offset(x, y + drop.size.dp.toPx()),
                strokeWidth = stroke, cap = StrokeCap.Round, alpha = 0.35f + 0.5f * drop.y
            )
        }
    }
}

private fun DrawScope.drawSnow(t: Long, flakes: List<Particle>) {
    val sway = 18.dp.toPx()
    flakes.forEach { flake ->
        val p = flake.progress(t)
        drawCircle(
            Color.White,
            flake.size.dp.toPx() / 2,
            Offset(flake.x * size.width + sway * sin(TWO_PI * p), (-0.05f + 1.1f * p) * size.height),
            alpha = 0.5f + 0.5f * flake.y
        )
    }
}

private fun DrawScope.drawLightning(t: Long) {
    val p = cycleProgress(t, FLASH_CYCLE_MILLIS, 0f)
    var i = 1
    while (i < FLASH_AT.size - 1 && p > FLASH_AT[i]) i++
    val from = FLASH_AT[i - 1]
    val alpha = FLASH_ALPHA[i - 1] + (FLASH_ALPHA[i] - FLASH_ALPHA[i - 1]) * ((p - from) / (FLASH_AT[i] - from))
    if (alpha <= 0f) return
    drawRect(FLASH, alpha = alpha)
    val unit = 1.dp.toPx()
    val left = size.width * 0.22f
    val bolt = Path().apply {
        moveTo(left + 55 * unit, 0f)
        lineTo(left + 30 * unit, 95 * unit)
        lineTo(left + 58 * unit, 95 * unit)
        lineTo(left + 26 * unit, 220 * unit)
    }
    drawPath(bolt, Color(0xFFF4F1FF), alpha = alpha, style = Stroke(5 * unit, join = StrokeJoin.Round))
}
