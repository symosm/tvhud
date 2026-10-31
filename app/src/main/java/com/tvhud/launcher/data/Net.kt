package com.tvhud.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

object Net {
    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "TVHud/1.0")
            try {
                if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    /** POST JSON. Returns (status code, body) or null if the network failed. */
    suspend fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 60000
            c.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            try {
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    suspend fun getBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 10000
            try {
                if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}

/** Free weather from Open-Meteo (no account or key needed). */
object Weather {
    data class Now(val tempC: Int, val text: String)

    suspend fun current(lat: Double, lon: Double): Now? {
        val body = Net.getText(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto",
        ) ?: return null
        return runCatching {
            val cur = JSONObject(body).getJSONObject("current")
            Now(Math.round(cur.getDouble("temperature_2m")).toInt(), describe(cur.optInt("weather_code", 0)))
        }.getOrNull()
    }

    data class Place(val name: String, val lat: Double, val lon: Double)

    suspend fun findCity(name: String): Place? {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val body = Net.getText("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1") ?: return null
        return runCatching {
            val r = JSONObject(body).getJSONArray("results").getJSONObject(0)
            Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
        }.getOrNull()
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}

/** Steam: recently played games (optional, needs the user's own free Web API key) + Wake-on-LAN. */
object Steam {
    const val STEAM_LINK = "com.valvesoftware.steamlink"
    const val MOONLIGHT = "com.limelight"

    data class Game(val appId: Int, val name: String) {
        val coverUrl get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg"
    }

    /** Returns null on failure (bad key, private profile, offline). */
    suspend fun recentGames(key: String, steamId: String): List<Game>? {
        if (key.isBlank() || steamId.isBlank()) return null
        val body = Net.getText(
            "https://api.steampowered.com/IPlayerService/GetRecentlyPlayedGames/v1/?key=${URLEncoder.encode(key, "UTF-8")}&steamid=${URLEncoder.encode(steamId, "UTF-8")}&count=12",
        ) ?: return null
        return runCatching {
            val resp = JSONObject(body).getJSONObject("response")
            val arr = resp.optJSONArray("games") ?: return@runCatching emptyList<Game>()
            (0 until arr.length()).map { i ->
                val g = arr.getJSONObject(i)
                Game(g.getInt("appid"), g.optString("name", "Game"))
            }
        }.getOrNull()
    }

    /** Sends a Wake-on-LAN "magic packet". Returns false if the MAC address is invalid. */
    suspend fun wake(mac: String): Boolean = withContext(Dispatchers.IO) {
        val parts = mac.trim().split(':', '-', '.').filter { it.isNotEmpty() }
        val hex = if (parts.size == 1 && parts[0].length == 12) parts[0].chunked(2) else parts
        if (hex.size != 6) return@withContext false
        val macBytes = runCatching { hex.map { it.toInt(16).toByte() } }.getOrNull() ?: return@withContext false
        val packet = ByteArray(6) { 0xFF.toByte() } + List(16) { macBytes }.flatten().toByteArray()
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                val addr = InetAddress.getByName("255.255.255.255")
                s.send(DatagramPacket(packet, packet.size, addr, 9))
                s.send(DatagramPacket(packet, packet.size, addr, 7))
            }
            true
        }.getOrDefault(false)
    }
}
