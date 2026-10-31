package com.tvhud.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Small wrapper around SharedPreferences for everything TV Hud remembers. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("tvhud", Context.MODE_PRIVATE)

    /** Package names pinned to the Home row, in order. Null = not chosen yet (use defaults). */
    var pinned: List<String>?
        get() = sp.getString("pinned", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) = sp.edit().putString("pinned", v?.joinToString(",")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** Custom names for HDMI inputs, keyed by input id. */
    fun inputName(id: String): String? = runCatching {
        JSONObject(sp.getString("inputNames", "{}") ?: "{}").optString(id).ifBlank { null }
    }.getOrNull()

    fun setInputName(id: String, name: String) {
        val o = runCatching { JSONObject(sp.getString("inputNames", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (name.isBlank()) o.remove(id) else o.put(id, name.trim())
        sp.edit().putString("inputNames", o.toString()).apply()
    }

    // Weather location (defaults to Manila)
    var city: String
        get() = sp.getString("city", "Manila") ?: "Manila"
        set(v) = sp.edit().putString("city", v).apply()
    var lat: Double
        get() = sp.getFloat("lat", 14.5995f).toDouble()
        set(v) = sp.edit().putFloat("lat", v.toFloat()).apply()
    var lon: Double
        get() = sp.getFloat("lon", 120.9842f).toDouble()
        set(v) = sp.edit().putFloat("lon", v.toFloat()).apply()

    /** Last weather text, e.g. "31° Manila", shown by the screensaver too. */
    var lastWeather: String
        get() = sp.getString("lastWeather", "") ?: ""
        set(v) = sp.edit().putString("lastWeather", v).apply()

    // Play from PC
    var steamId: String
        get() = sp.getString("steamId", "") ?: ""
        set(v) = sp.edit().putString("steamId", v.trim()).apply()
    var steamKey: String
        get() = sp.getString("steamKey", "") ?: ""
        set(v) = sp.edit().putString("steamKey", v.trim()).apply()
    var pcMac: String
        get() = sp.getString("pcMac", "") ?: ""
        set(v) = sp.edit().putString("pcMac", v.trim()).apply()
    var pcName: String
        get() = sp.getString("pcName", "My PC") ?: "My PC"
        set(v) = sp.edit().putString("pcName", v.ifBlank { "My PC" }).apply()

    // AI assistant
    var aiKey: String
        get() = sp.getString("aiKey", "") ?: ""
        set(v) = sp.edit().putString("aiKey", v.trim()).apply()

    // Browser
    var adBlock: Boolean
        get() = sp.getBoolean("adBlock", true)
        set(v) = sp.edit().putBoolean("adBlock", v).apply()
}
