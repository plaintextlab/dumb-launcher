package com.plaintextlab.dumblauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Current weather for the Weather live tile, from the free Open-Meteo service (no account).
 * Uses the phone's approximate location (rounded to 2 decimals, about 1 km) and keeps the
 * last result, so the tile shows something offline and refreshes at most every 30 minutes.
 */
object Weather {
    data class Now(val temp: Int, val high: Int, val low: Int, val code: Int, val place: String, val at: Long) {
        /** A short description of the WMO weather code. */
        val text get() = when (code) {
            0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Cloudy"
            45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67, in 80..82 -> "Rain"
            in 71..77, 85, 86 -> "Snow"; in 95..99 -> "Thunderstorm"; else -> ""
        }

        /** Which symbol to draw. */
        val icon get() = when (code) {
            0, 1 -> R.drawable.w_sun; 2 -> R.drawable.w_partly; 3, 45, 48 -> R.drawable.w_cloud
            in 51..67, in 80..82 -> R.drawable.w_rain; in 71..77, 85, 86 -> R.drawable.w_snow
            in 95..99 -> R.drawable.w_storm; else -> R.drawable.w_cloud
        }
    }

    private const val KEY = "weather_last"
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    fun enabled(c: Context) = Prefs.getBool(c, Prefs.SHOW_WEATHER, true)
    fun fahrenheit(c: Context) = Prefs.getBool(c, Prefs.WEATHER_F, false)

    fun hasPermission(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The last weather we fetched, if any. */
    fun cached(c: Context): Now? = Prefs.get(c, KEY)?.let { s ->
        try {
            val o = JSONObject(s)
            Now(o.getInt("t"), o.getInt("h"), o.getInt("l"), o.getInt("c"), o.optString("p"), o.getLong("at"))
        } catch (e: Exception) { null }
    }

    /** Fetch fresh weather if the cached one is old (background thread). Returns the newest. */
    fun refresh(c: Context, force: Boolean = false): Now? {
        val old = cached(c)
        if (!force && old != null && System.currentTimeMillis() - old.at < MAX_AGE_MS) return old
        val loc = location(c) ?: return old
        val lat = String.format(Locale.US, "%.2f", loc.latitude)
        val lon = String.format(Locale.US, "%.2f", loc.longitude)
        val unit = if (fahrenheit(c)) "&temperature_unit=fahrenheit" else ""
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=1&timezone=auto$unit"
        val json = try { get(url) } catch (e: Exception) { return old }
        return try {
            val o = JSONObject(json)
            val cur = o.getJSONObject("current")
            val day = o.getJSONObject("daily")
            val now = Now(
                temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
                high = Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                low = Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                code = cur.getInt("weather_code"),
                place = placeName(c, loc) ?: old?.place.orEmpty(),
                at = System.currentTimeMillis(),
            )
            Prefs.set(c, KEY, JSONObject().put("t", now.temp).put("h", now.high).put("l", now.low)
                .put("c", now.code).put("p", now.place).put("at", now.at).toString())
            now
        } catch (e: Exception) { old }
    }

    /** Forget the cached weather (after changing units). */
    fun clear(c: Context) = Prefs.set(c, KEY, null)

    /** The newest approximate location any provider has, without turning GPS on. */
    @Suppress("MissingPermission") // checked by hasPermission
    private fun location(c: Context): Location? {
        if (!hasPermission(c)) return null
        val lm = c.getSystemService(LocationManager::class.java) ?: return null
        return try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
                ?: fresh(lm)
        } catch (e: Exception) { null }
    }

    /** No location known yet: ask the network provider once and wait up to 15 s (background thread). */
    @Suppress("MissingPermission", "DEPRECATION")
    private fun fresh(lm: LocationManager): Location? {
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) } ?: return null
        val latch = java.util.concurrent.CountDownLatch(1)
        var got: Location? = null
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            lm.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(l: Location) { got = l; latch.countDown() }
                @Deprecated("old API") override fun onStatusChanged(p: String?, s: Int, b: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) { latch.countDown() }
            }, android.os.Looper.getMainLooper())
        }
        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
        return got
    }

    /** Town name for the tile, if the phone can work it out. */
    @Suppress("DEPRECATION") // the listener form is API 33+
    private fun placeName(c: Context, loc: Location): String? = try {
        if (!android.location.Geocoder.isPresent()) null
        else android.location.Geocoder(c).getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    } catch (e: Exception) { null }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "dumb-launcher")
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
}
