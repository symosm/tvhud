package com.tvhud.launcher.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvhud.launcher.data.Prefs
import com.tvhud.launcher.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SettingRow(val title: String, val sub: String, val action: () -> Unit)

@Composable
fun QuickSettingsPanel(prefs: Prefs, onPrefsChanged: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var askCity by remember { mutableStateOf(false) }
    var showHomeGuide by remember { mutableStateOf(false) }

    val rows = listOf(
        SettingRow("Wi-Fi & internet", "Networks and connection") { openSettings(ctx, Settings.ACTION_WIFI_SETTINGS) },
        SettingRow("Bluetooth & controllers", "Pair game controllers, speakers, headphones") { openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS) },
        SettingRow("Picture & display", "Picture mode, brightness, resolution") { openSettings(ctx, Settings.ACTION_DISPLAY_SETTINGS) },
        SettingRow("Sound", "Sound mode and output") { openSettings(ctx, Settings.ACTION_SOUND_SETTINGS) },
        SettingRow("Screensaver", "Choose “TV Hud Ambient” for the clock screensaver") { openSettings(ctx, Settings.ACTION_DREAM_SETTINGS) },
        SettingRow("Weather city", prefs.city) { askCity = true },
        SettingRow("Free up memory", "Closes apps running in the background") {
            scope.launch {
                val n = withContext(Dispatchers.Default) { freeMemory(ctx) }
                Toast.makeText(ctx, "Freed memory from $n apps", Toast.LENGTH_SHORT).show()
            }
        },
        SettingRow("Make TV Hud your home screen", "How to replace the Google TV home screen") { showHomeGuide = true },
        SettingRow("All TV settings", "Coocaa and Google TV settings") { openSettings(ctx, Settings.ACTION_SETTINGS) },
    )

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(360.dp)
                .fillMaxHeight()
                .background(Hud.Panel)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Quick settings", color = Hud.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Back to close", color = Hud.Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            rows.forEachIndexed { i, row ->
                FocusCard(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    background = Color(0xFF1C212B),
                    focusedBackground = Hud.Ink,
                    focusScale = 1.02f,
                    focusRequester = if (i == 0) first else null,
                    onClick = row.action,
                ) { focused ->
                    Column(
                        Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(row.title, color = if (focused) Hud.Dark else Hud.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(row.sub, color = if (focused) Color(0xFF3A404C) else Hud.Muted, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (askCity) {
        TextDialog(
            title = "Weather city",
            hint = "e.g. Quezon City",
            initial = prefs.city,
            onDismiss = { askCity = false },
            onDone = { name ->
                askCity = false
                scope.launch {
                    val place = Weather.findCity(name)
                    if (place == null) {
                        Toast.makeText(ctx, "Couldn't find “$name”", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.city = place.name
                        prefs.lat = place.lat
                        prefs.lon = place.lon
                        onPrefsChanged()
                        Toast.makeText(ctx, "Weather set to ${place.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    if (showHomeGuide) {
        InfoDialog(
            title = "Make TV Hud your home screen",
            body = "Google TV always sends the Home button to its own home screen. To use TV Hud instead, follow the " +
                "“Set TV Hud as home” steps in the guide that came with this app: they use a computer once, " +
                "can be undone at any time, and don't delete anything.\n\nUntil then, open TV Hud from your apps list.",
            onDismiss = { showHomeGuide = false },
        )
    }
}

private fun openSettings(ctx: Context, action: String) {
    val ok = runCatching {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
    if (!ok) runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun freeMemory(ctx: Context): Int {
    // Android only lets apps stop *background* processes of other apps; whatever is on screen is untouched.
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    var n = 0
    val apps = runCatching { ctx.packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
    for (info in apps) {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
        if (info.packageName == ctx.packageName) continue
        if (runCatching { am.killBackgroundProcesses(info.packageName) }.isSuccess) n++
    }
    return n
}
