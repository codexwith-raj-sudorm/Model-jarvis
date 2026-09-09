package com.jarvis.assistant.tools

import com.jarvis.assistant.agent.Tool
import com.jarvis.assistant.agent.ToolContext

/**
 * Weather via Open-Meteo (free, key-less, no account).
 *
 *  1. geocoding-api.open-meteo.com → resolve the city name to lat/lon
 *  2. api.open-meteo.com/v1/forecast → current conditions + 2-day outlook
 *
 * Default city: the user's saved home (SharedPreferences "home_city", set by
 * "save_memory"-style facts later), else Kolkata — no location permission is
 * requested in this scaffold.
 */
class WeatherTool : Tool {

    override val name = "weather"
    override val description =
        "Current weather and a 2-day outlook for a city (fresh data from open-meteo.com)."
    override val parameters = """{"city": "string, city name, optional — defaults to the user's home city"}"""

    override suspend fun execute(args: Map<String, String>, context: ToolContext): String {
        if (!context.webEnabled || context.web == null) {
            return "[offline] weather needs the internet — web is switched off"
        }
        val web = context.web
        val city = args["city"]?.takeIf { it.isNotBlank() }
            ?: args["location"]?.takeIf { it.isNotBlank() }
            ?: homeCity(context)

        // 1) geocode
        val geo = web.getJson(
            "https://geocoding-api.open-meteo.com/v1/search?name=" +
                java.net.URLEncoder.encode(city, "UTF-8") +
                "&count=1&language=en&format=json"
        )
        val place = geo.optJSONArray("results")?.optJSONObject(0)
            ?: return "[error] no place found for \"$city\""
        val lat = place.optDouble("latitude")
        val lon = place.optDouble("longitude")
        val resolved = place.optString("name", city)
        val admin = place.optString("admin1", "")
        val country = place.optString("country", "")

        // 2) forecast
        val wx = web.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=2"
        )

        val cur = wx.optJSONObject("current") ?: return "[error] no current conditions in forecast"
        val daily = wx.optJSONObject("daily")

        val temp = cur.optDouble("temperature_2m")
        val feels = cur.optDouble("apparent_temperature")
        val humidity = cur.optInt("relative_humidity_2m")
        val wind = cur.optDouble("wind_speed_10m")
        val cond = wmoText(cur.optInt("weather_code"))

        val sb = StringBuilder()
        sb.append("Weather in $resolved")
        if (admin.isNotBlank()) sb.append(", $admin")
        if (country.isNotBlank()) sb.append(" ($country)")
        sb.append(": ")

        if (daily != null) {
            val maxs = daily.optJSONArray("temperature_2m_max")
            val mins = daily.optJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weather_code")
            if (maxs != null && mins != null && maxs.length() >= 2) {
                sb.append("today ${round(maxs.optDouble(0))}°/${round(mins.optDouble(0))}° ${wmoText(codes?.optInt(0) ?: 0)}, ")
                sb.append("tomorrow ${round(maxs.optDouble(1))}°/${round(mins.optDouble(1))}° ${wmoText(codes?.optInt(1) ?: 0)}. ")
            }
        }
        sb.append("Right now ${round(temp)}° (feels like ${round(feels)}°), $cond, ")
        sb.append("humidity $humidity%, wind ${round(wind)} km/h. (source: open-meteo.com)")
        return sb.toString()
    }

    private fun homeCity(context: ToolContext): String =
        context.appContext.getSharedPreferences("jarvis", android.content.Context.MODE_PRIVATE)
            .getString("home_city", DEFAULT_CITY) ?: DEFAULT_CITY

    private fun round(d: Double): Long = Math.round(d)

    /** WMO weather-interpretation codes → short text. */
    private fun wmoText(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "mostly clear"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61, 63, 65 -> "rain"
        66, 67 -> "freezing rain"
        71, 73, 75 -> "snow"
        77 -> "snow grains"
        80, 81, 82 -> "showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "mixed conditions"
    }

    companion object {
        const val DEFAULT_CITY = "Kolkata"
    }
}
