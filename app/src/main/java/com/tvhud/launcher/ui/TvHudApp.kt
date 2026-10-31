package com.tvhud.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tvhud.launcher.MainActivity
import com.tvhud.launcher.data.AppEntry
import com.tvhud.launcher.data.Apps
import com.tvhud.launcher.data.InputEntry
import com.tvhud.launcher.data.Inputs
import com.tvhud.launcher.data.Prefs
import com.tvhud.launcher.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Screen { HOME, ALL_APPS, PLAY_FROM_PC, ASSISTANT }

@Composable
fun TvHudApp() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var settingsOpen by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<InputEntry>>(emptyList()) }
    var weather by remember { mutableStateOf(prefs.lastWeather) }
    /** Bumped whenever pins/hidden apps/names change so lists recompute. */
    var prefsVersion by remember { mutableStateOf(0) }

    val refresh by MainActivity.refreshes.collectAsState()
    val home by MainActivity.homePresses.collectAsState()

    LaunchedEffect(refresh) {
        allApps = withContext(Dispatchers.Default) { Apps.load(ctx) }
        inputs = withContext(Dispatchers.Default) { Inputs.load(ctx, prefs) }
    }
    LaunchedEffect(home) {
        screen = Screen.HOME
        settingsOpen = false
    }
    // Weather every 30 minutes; re-runs when the city changes (prefsVersion).
    LaunchedEffect(prefsVersion) {
        while (true) {
            Weather.current(prefs.lat, prefs.lon)?.let {
                weather = "${it.tempC}° ${prefs.city}"
                prefs.lastWeather = weather
            }
            delay(30 * 60 * 1000L)
        }
    }

    val bumpPrefs: () -> Unit = {
        prefsVersion++
        inputs = Inputs.load(ctx, prefs)
    }

    // Back goes to Home; on Home it does nothing (a home screen never closes).
    BackHandler(enabled = true) {
        when {
            settingsOpen -> settingsOpen = false
            screen != Screen.HOME -> screen = Screen.HOME
        }
    }

    NightBackground {
        if (settingsOpen) {
            // Shown on its own so the remote can't wander behind the panel.
            QuickSettingsPanel(
                prefs = prefs,
                onPrefsChanged = bumpPrefs,
                onClose = { settingsOpen = false },
            )
        } else when (screen) {
            Screen.HOME -> HomeScreen(
                prefs = prefs,
                allApps = allApps,
                inputs = inputs,
                weather = weather,
                prefsVersion = prefsVersion,
                onPrefsChanged = bumpPrefs,
                onOpen = { screen = it },
                onSettings = { settingsOpen = true },
            )
            Screen.ALL_APPS -> AllAppsScreen(
                prefs = prefs,
                allApps = allApps,
                prefsVersion = prefsVersion,
                onPrefsChanged = bumpPrefs,
            )
            Screen.PLAY_FROM_PC -> PlayFromPcScreen(prefs = prefs, refreshTick = refresh)
            Screen.ASSISTANT -> AssistantScreen(prefs = prefs, allApps = allApps)
        }
    }
}
