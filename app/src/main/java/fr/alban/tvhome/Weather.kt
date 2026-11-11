package fr.alban.tvhome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class Weather(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hourly: List<HourForecast> = emptyList(),
    val sunrise: LocalDateTime? = null,
    val sunset: LocalDateTime? = null,
) {
    val kind get() = WeatherKind.of(code)

    /** Nuit selon le vrai lever et coucher du soleil du jour, sinon selon ce que dit Open-Meteo. */
    fun isNight(now: LocalDateTime): Boolean =
        if (sunrise != null && sunset != null) now.isBefore(sunrise) || !now.isBefore(sunset) else !isDay
}

data class HourForecast(val time: LocalDateTime, val weather: Weather)

enum class WeatherKind(val label: String) {
    CLEAR("Dégagé"), PARTLY("Éclaircies"), CLOUDY("Couvert"), FOG("Brouillard"),
    RAIN("Pluie"), SNOW("Neige"), STORM("Orage");

    companion object {
        // Codes météo WMO renvoyés par Open-Meteo
        fun of(code: Int) = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            in 51..67, in 80..82 -> RAIN
            else -> CLOUDY
        }
    }
}

private suspend fun fetchWeather(): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${Config.LATITUDE}" +
                "&longitude=${Config.LONGITUDE}&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day&daily=sunrise,sunset&forecast_days=2&timezone=auto"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 8_000
        }
        val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
        val json = JSONObject(body)
        val cur = json.getJSONObject("current")
        val hourly = runCatching { parseHourly(json, cur.getString("time")) }.getOrDefault(emptyList())
        val daily = json.optJSONObject("daily")
        Weather(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            hourly = hourly,
            sunrise = runCatching { LocalDateTime.parse(daily!!.getJSONArray("sunrise").getString(0)) }.getOrNull(),
            sunset = runCatching { LocalDateTime.parse(daily!!.getJSONArray("sunset").getString(0)) }.getOrNull(),
        )
    }.getOrNull()
}

/** Les heures qui suivent l'heure en cours (on en garde un peu plus, l'écran filtre selon l'heure réelle). */
private fun parseHourly(json: JSONObject, currentTime: String): List<HourForecast> {
    val h = json.getJSONObject("hourly")
    val times = h.getJSONArray("time")
    val temps = h.getJSONArray("temperature_2m")
    val codes = h.getJSONArray("weather_code")
    val days = h.getJSONArray("is_day")
    val currentHour = currentTime.take(13) + ":00"
    var start = 0
    for (i in 0 until times.length()) if (times.getString(i) == currentHour) { start = i; break }
    return (start + 1 until minOf(start + 9, times.length())).map { i ->
        HourForecast(
            time = LocalDateTime.parse(times.getString(i)),
            weather = Weather(temps.getDouble(i).roundToInt(), codes.getInt(i), days.optInt(i, 1) == 1),
        )
    }
}

@Composable
fun rememberWeather(): State<Weather?> = produceState<Weather?>(null) {
    while (true) {
        fetchWeather()?.let { value = it }
        delay(if (value == null) 60_000 else 30 * 60_000L)
    }
}

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(60_000 - System.currentTimeMillis() % 60_000)
    }
}

/** Pictos météo dessinés à la main, monochromes, dans l'esprit des SF Symbols. */
@Composable
fun WeatherGlyph(weather: Weather, size: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2, s / 2)
        when (weather.kind) {
            WeatherKind.CLEAR -> if (weather.isDay) sun(c, s * 0.2f, color) else moon(c, s * 0.3f, color)
            WeatherKind.PARTLY -> {
                if (weather.isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.14f, color)
                else moon(Offset(s * 0.38f, s * 0.36f), s * 0.2f, color)
                cloud(Offset(s * 0.58f, s * 0.6f), s * 0.62f, color)
            }
            WeatherKind.CLOUDY -> cloud(Offset(s * 0.5f, s * 0.52f), s * 0.8f, color)
            WeatherKind.FOG -> for (k in 0..2) {
                val y = s * (0.32f + k * 0.18f)
                drawLine(color, Offset(s * 0.15f, y), Offset(s * 0.85f, y), s * 0.08f, StrokeCap.Round)
            }
            WeatherKind.RAIN -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.8f, color)
                for (k in 0..2) {
                    val x = s * (0.32f + k * 0.18f)
                    drawLine(color, Offset(x, s * 0.7f), Offset(x - s * 0.06f, s * 0.88f), s * 0.07f, StrokeCap.Round)
                }
            }
            WeatherKind.SNOW -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.8f, color)
                for (k in 0..2) drawCircle(color, s * 0.05f, Offset(s * (0.32f + k * 0.18f), s * 0.8f))
            }
            WeatherKind.STORM -> {
                cloud(Offset(s * 0.5f, s * 0.36f), s * 0.8f, color)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.6f); lineTo(s * 0.4f, s * 0.8f); lineTo(s * 0.52f, s * 0.8f)
                    lineTo(s * 0.44f, s * 0.97f); lineTo(s * 0.64f, s * 0.72f); lineTo(s * 0.52f, s * 0.72f); close()
                }
                drawPath(bolt, color)
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, color: Color) {
    val w = r * 0.38f
    drawCircle(color, r, c, style = Stroke(width = w))
    for (k in 0 until 8) {
        val a = Math.PI * k / 4
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(color, c + dir * (r * 1.5f), c + dir * (r * 2.0f), w, StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, r: Float, color: Color) {
    val full = Path().apply { addOval(Rect(c, r)) }
    val bite = Path().apply { addOval(Rect(c + Offset(r * 0.55f, -r * 0.35f), r * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, full, bite), color)
}

private fun DrawScope.cloud(c: Offset, w: Float, color: Color) {
    val h = w * 0.3f
    drawRoundRect(color, Offset(c.x - w / 2, c.y), Size(w, h), CornerRadius(h / 2))
    drawCircle(color, w * 0.2f, Offset(c.x - w * 0.18f, c.y + h * 0.15f))
    drawCircle(color, w * 0.27f, Offset(c.x + w * 0.1f, c.y))
}
