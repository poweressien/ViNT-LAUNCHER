package com.bluemimicry.launcher.lockscreen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Weather(val tempC: Double, val code: Int, val windKmh: Double) {
    val description: String get() = when (code) {
        0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Overcast"; 45, 48 -> "Fog"
        in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"; in 95..99 -> "Thunderstorm"; else -> "—"
    }
}

/**
 * Real weather from Open-Meteo (free, no API key), and ONLY after the user enters coordinates and agrees.
 * The only data sent is those coordinates. Without configuration nothing is fetched and nothing is shown.
 */
object WeatherSource {
    private fun prefs(c: Context) = c.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun isConfigured(c: Context) = prefs(c).getBoolean("consent", false) && prefs(c).contains("lat")
    fun coords(c: Context): Pair<Double, Double>? = if (isConfigured(c)) prefs(c).getString("lat", null)!!.toDouble() to prefs(c).getString("lon", "0")!!.toDouble() else null

    fun configure(c: Context, lat: Double, lon: Double) {
        prefs(c).edit().putString("lat", lat.toString()).putString("lon", lon.toString()).putBoolean("consent", true).apply()
    }
    fun clear(c: Context) { prefs(c).edit().clear().apply() }

    suspend fun fetch(c: Context): Weather? {
        val (lat, lon) = coords(c) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val u = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,wind_speed_10m")
                val conn = (u.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
                conn.inputStream.bufferedReader().use { r ->
                    val cur = JSONObject(r.readText()).getJSONObject("current")
                    Weather(cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.getDouble("wind_speed_10m"))
                }
            }.getOrNull()
        }
    }
}
