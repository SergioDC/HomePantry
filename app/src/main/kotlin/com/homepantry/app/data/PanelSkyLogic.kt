package com.homepantry.app.data

import java.time.LocalTime

// Qué cielo se dibuja detrás del panel de cocina (spec 2026-11-22-panel-cielo-animado). Todo
// puro para poder testearlo; los colores y cantidades salen de la maqueta validada.

enum class SkyPhase { DAY, DUSK, NIGHT }

/** Sin datos del sol se supone un día de 7:00 a 19:00: el cielo no puede quedarse sin fase. */
private const val DEFAULT_SUNRISE = 7 * 60
private const val DEFAULT_SUNSET = 19 * 60

/** El atardecer dura los 90 minutos antes del ocaso. */
private const val DUSK_MINUTES = 90

/** Minutos desde medianoche, hora local, como vienen [sunriseMinute] y [sunsetMinute]. */
fun skyPhase(now: LocalTime, sunriseMinute: Int?, sunsetMinute: Int?): SkyPhase {
    val rise = sunriseMinute ?: DEFAULT_SUNRISE
    val set = sunsetMinute ?: DEFAULT_SUNSET
    val minute = now.hour * 60 + now.minute
    // Con un día más corto que el atardecer, el atardecer empieza al amanecer y no antes.
    val duskStart = maxOf(set - DUSK_MINUTES, rise)
    return when {
        minute < rise || minute >= set -> SkyPhase.NIGHT
        minute >= duskStart -> SkyPhase.DUSK
        else -> SkyPhase.DAY
    }
}

private fun gradients(vararg colors: Long): Map<WeatherKind, List<Long>> =
    WeatherKind.entries.withIndex().associate { (i, kind) ->
        kind to colors.slice(i * 3 until i * 3 + 3).map { it or 0xFF000000 }
    }

// Orden de WeatherKind: CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM.
private val SKY = mapOf(
    SkyPhase.DAY to gradients(
        0x2F7FD9, 0x6FB4F0, 0xB9E0FF, 0x3B82C9, 0x7FB3E0, 0xC3DCF0, 0x5D6F84, 0x8797A8, 0xB3BFCB,
        0x8E979F, 0xB4BBC1, 0xD5D9DC, 0x56677A, 0x7B8B9C, 0xA4B0BC, 0x3D4A5C, 0x5D6B7D, 0x808D9C,
        0x7F93A8, 0xB3C2D0, 0xE4EBF1, 0x1F2433, 0x343B4F, 0x4B5367
    ),
    SkyPhase.DUSK to gradients(
        0x3C3F8F, 0xC0629A, 0xFFAD6B, 0x45488F, 0xB56C95, 0xF3A877, 0x5A5872, 0x8D7F8F, 0xC09A8F,
        0x8A8590, 0xB3A9A8, 0xD8C8BD, 0x4F5068, 0x776F80, 0xA48E8E, 0x383C52, 0x585A6E, 0x7A7482,
        0x7C7F9E, 0xB3B0C4, 0xE6D9D8, 0x1D1F2E, 0x33334A, 0x4D4658
    ),
    SkyPhase.NIGHT to gradients(
        0x070A1F, 0x121A3D, 0x24305E, 0x0A0D24, 0x18204A, 0x2A3460, 0x14161F, 0x232633, 0x33374A,
        0x1D2028, 0x2E323C, 0x454A55, 0x12151F, 0x1F2431, 0x2F3545, 0x0E1119, 0x1A1F2B, 0x283040,
        0x1C2233, 0x2E3750, 0x4A5570, 0x08090F, 0x141622, 0x202435
    )
)

/** Tres colores ARGB del degradado vertical, de arriba abajo. */
fun skyGradient(kind: WeatherKind, phase: SkyPhase): List<Long> = SKY.getValue(phase).getValue(kind)

enum class Precipitation { NONE, DRIZZLE, RAIN, STORM, SNOW }

/** Qué lleva la escena; [particles] son gotas o copos según [precipitation]. */
data class SceneSpec(
    val sun: Boolean = false,
    val moon: Boolean = false,
    val stars: Boolean = false,
    val clouds: Int = 0,
    val cloudColor: Long = 0,
    val precipitation: Precipitation = Precipitation.NONE,
    val particles: Int = 0,
    val lightning: Boolean = false,
    val fog: Boolean = false
)

private val CLOUDS = mapOf(
    WeatherKind.CLEAR to 0, WeatherKind.PARTLY_CLOUDY to 3, WeatherKind.CLOUDY to 7, WeatherKind.FOG to 2,
    WeatherKind.DRIZZLE to 5, WeatherKind.RAIN to 6, WeatherKind.SNOW to 4, WeatherKind.STORM to 7
)

private val CLOUD_DAY = mapOf(
    WeatherKind.CLEAR to 0xFFF4F7FB, WeatherKind.PARTLY_CLOUDY to 0xFFF4F7FB, WeatherKind.CLOUDY to 0xFFD5DDE6,
    WeatherKind.FOG to 0xFFE2E6EA, WeatherKind.DRIZZLE to 0xFFB9C3CE, WeatherKind.RAIN to 0xFF9AA6B4,
    WeatherKind.SNOW to 0xFFE9EEF3, WeatherKind.STORM to 0xFF5C6476
)

fun sceneSpec(kind: WeatherKind, phase: SkyPhase): SceneSpec {
    val sunny = kind == WeatherKind.CLEAR || kind == WeatherKind.PARTLY_CLOUDY
    val night = phase == SkyPhase.NIGHT
    val cloudColor = when {
        night -> if (kind == WeatherKind.STORM) 0xFF2A2E3D else 0xFF454B63
        phase == SkyPhase.DUSK && sunny -> 0xFFF6D6CF
        else -> CLOUD_DAY.getValue(kind)
    }
    val (precipitation, particles) = when (kind) {
        WeatherKind.DRIZZLE -> Precipitation.DRIZZLE to 45
        WeatherKind.RAIN -> Precipitation.RAIN to 90
        WeatherKind.STORM -> Precipitation.STORM to 130
        WeatherKind.SNOW -> Precipitation.SNOW to 70
        else -> Precipitation.NONE to 0
    }
    return SceneSpec(
        sun = sunny && !night,
        moon = sunny && night,
        stars = night && (sunny || kind == WeatherKind.SNOW),
        clouds = CLOUDS.getValue(kind),
        cloudColor = cloudColor,
        precipitation = precipitation,
        particles = particles,
        lightning = kind == WeatherKind.STORM,
        fog = kind == WeatherKind.FOG
    )
}

enum class Greeting { MORNING, AFTERNOON, NIGHT }

/** «Buenos días» de 6 a 14, «Buenas tardes» de 14 a 21 y «Buenas noches» el resto. */
fun greeting(time: LocalTime): Greeting = when (time.hour) {
    in 6..13 -> Greeting.MORNING
    in 14..20 -> Greeting.AFTERNOON
    else -> Greeting.NIGHT
}
