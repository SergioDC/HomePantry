package com.homepantry.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WeatherTest {

    private fun hoursOf(day: String) = (0..23).map { "%sT%02d:00".format(day, it) }

    private fun forecast(
        current: OpenMeteoCurrent? = OpenMeteoCurrent("2026-11-22T08:15", 13.6, 2),
        hourlyDays: List<String> = listOf("2026-11-22"),
        hourlyTemps: List<Double?>? = null,
        daily: OpenMeteoDaily? = OpenMeteoDaily(listOf("2026-11-22"), listOf(18.4), listOf(6.5), listOf(30))
    ): OpenMeteoForecast {
        val times = hourlyDays.flatMap { hoursOf(it) }
        return OpenMeteoForecast(
            current = current,
            hourly = OpenMeteoHourly(
                time = times,
                temperature2m = hourlyTemps ?: times.indices.map { 5.0 + it },
                weatherCode = times.map { 0 },
                precipitationProbability = times.map { 10 }
            ),
            daily = daily
        )
    }

    @Test
    fun `codigos WMO`() {
        val expected = mapOf(
            0 to WeatherKind.CLEAR, 1 to WeatherKind.PARTLY_CLOUDY, 2 to WeatherKind.PARTLY_CLOUDY,
            3 to WeatherKind.CLOUDY, 45 to WeatherKind.FOG, 48 to WeatherKind.FOG,
            51 to WeatherKind.DRIZZLE, 55 to WeatherKind.DRIZZLE, 57 to WeatherKind.DRIZZLE,
            61 to WeatherKind.RAIN, 65 to WeatherKind.RAIN, 67 to WeatherKind.RAIN, 80 to WeatherKind.RAIN, 82 to WeatherKind.RAIN,
            71 to WeatherKind.SNOW, 75 to WeatherKind.SNOW, 77 to WeatherKind.SNOW, 85 to WeatherKind.SNOW, 86 to WeatherKind.SNOW,
            95 to WeatherKind.STORM, 96 to WeatherKind.STORM, 99 to WeatherKind.STORM,
            42 to WeatherKind.CLOUDY
        )
        expected.forEach { (code, kind) -> assertEquals("código $code", kind, weatherKind(code)) }
    }

    @Test
    fun `instantanea completa`() {
        val snapshot = weatherSnapshot(forecast(), fetchedAtMillis = 1000L)!!
        assertEquals(14, snapshot.currentTemp)
        assertEquals(WeatherKind.PARTLY_CLOUDY, snapshot.kind)
        assertEquals(18, snapshot.maxTemp)
        assertEquals(7, snapshot.minTemp)
        assertEquals(30, snapshot.rainProbability)
        assertEquals((8..22).toList(), snapshot.hours.map { it.hour })
        assertEquals(HourWeather(8, 13, WeatherKind.CLEAR), snapshot.hours.first())
        assertEquals(1000L, snapshot.fetchedAtMillis)
    }

    @Test
    fun `horas solo del dia de hoy`() {
        val snapshot = weatherSnapshot(forecast(hourlyDays = listOf("2026-11-22", "2026-11-23")), 0L)!!
        assertEquals((8..22).toList(), snapshot.hours.map { it.hour })
        assertEquals(13, snapshot.hours.first().temp)
    }

    @Test
    fun `sin current o sin daily devuelve null`() {
        assertNull(weatherSnapshot(forecast(current = null), 0L))
        assertNull(weatherSnapshot(forecast(daily = OpenMeteoDaily(listOf("2026-11-22"), null, listOf(6.5), listOf(30))), 0L))
        assertNull(weatherSnapshot(forecast(daily = null), 0L))
    }

    @Test
    fun `huecos en las horas`() {
        val temps = (0..23).map { if (it == 9) null else 5.0 + it }
        val snapshot = weatherSnapshot(forecast(hourlyTemps = temps), 0L)!!
        assertFalse(9 in snapshot.hours.map { it.hour })
        assertEquals(14, snapshot.hours.size)
    }

    @Test
    fun `lluvia ausente es 0`() {
        val daily = OpenMeteoDaily(listOf("2026-11-22"), listOf(18.4), listOf(6.5), null)
        assertEquals(0, weatherSnapshot(forecast(daily = daily), 0L)!!.rainProbability)
    }

    @Test
    fun `manana hasta las 12`() {
        assertTrue(isMorning(LocalTime.of(11, 59)))
        assertFalse(isMorning(LocalTime.of(12, 0)))
    }

    @Test
    fun `gson de la respuesta real`() {
        val hours = hoursOf("2026-11-22").joinToString(",") { "\"$it\"" }
        val temps = (0..23).joinToString(",") { (5.0 + it).toString() }
        val zeros = (0..23).joinToString(",") { "0" }
        val tens = (0..23).joinToString(",") { "10" }
        val json = """{"latitude":43.2,"longitude":-2.9,
            "current":{"time":"2026-11-22T08:15","interval":900,"temperature_2m":13.6,"weather_code":2},
            "hourly":{"time":[$hours],"temperature_2m":[$temps],"weather_code":[$zeros],"precipitation_probability":[$tens]},
            "daily":{"time":["2026-11-22"],"temperature_2m_max":[18.4],"temperature_2m_min":[6.5],"precipitation_probability_max":[30]}}"""
        val parsed = Gson().fromJson(json, OpenMeteoForecast::class.java)
        assertEquals(weatherSnapshot(forecast(), 5L), weatherSnapshot(parsed, 5L))
    }

    @Test
    fun `la instantanea sabe de que dia es`() {
        val snapshot = weatherSnapshot(forecast(), 0L)!!
        assertEquals("2026-11-22", snapshot.date)
        assertTrue(snapshot.isFor(java.time.LocalDate.of(2026, 11, 22)))
        assertFalse(snapshot.isFor(java.time.LocalDate.of(2026, 11, 23)))
    }
}
