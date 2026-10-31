package com.tvhud.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvhud.launcher.data.AppCategory
import com.tvhud.launcher.data.AppEntry
import com.tvhud.launcher.data.Apps
import com.tvhud.launcher.data.Prefs
import kotlinx.coroutines.delay

@Composable
fun AllAppsScreen(
    prefs: Prefs,
    allApps: List<AppEntry>,
    prefsVersion: Int,
    onPrefsChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AppCategory?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<AppEntry?>(null) }
    val hidden = remember(prefsVersion) { prefs.hidden }
    val homePkgs = remember(prefsVersion, allApps) { Apps.homeRow(allApps, prefs).map { it.pkg } }
    val firstChip = remember { FocusRequester() }

    val shown = allApps.filter { app ->
        (if (showHidden) app.pkg in hidden else app.pkg !in hidden) &&
            (category == null || app.category == category) &&
            (query.isBlank() || app.label.contains(query.trim(), ignoreCase = true))
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstChip.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 34.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("All apps", color = Hud.Ink, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps", fontSize = 12.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Hud.Ink, unfocusedTextColor = Hud.Ink,
                    focusedBorderColor = Hud.Ink, unfocusedBorderColor = Hud.GlassBorder,
                    focusedContainerColor = Hud.Glass, unfocusedContainerColor = Hud.Glass, cursorColor = Hud.Ink,
                ),
                modifier = Modifier.width(320.dp),
            )
            Spacer(Modifier.width(18.dp))
            Text("${shown.size} apps · no ads", color = Hud.Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("All", selected = category == null && !showHidden, focusRequester = firstChip) {
                category = null; showHidden = false
            }
            AppCategory.values().forEach { c ->
                Pill(c.label, selected = category == c && !showHidden) { category = c; showHidden = false }
            }
            if (hidden.isNotEmpty()) {
                Pill("Hidden (${hidden.size})", selected = showHidden) { showHidden = !showHidden; category = null }
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 30.dp, start = 4.dp, end = 4.dp),
        ) {
            items(shown, key = { it.pkg }) { app ->
                AppTile(app = app, width = 124, onLongClick = { menu = app }, onClick = { Apps.launch(ctx, app) })
            }
            if (!showHidden) {
                item(key = "store") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusCard(
                            modifier = Modifier.size(124.dp, 70.dp),
                            onClick = { Apps.openStore(ctx) },
                        ) {
                            Image(
                                rememberVectorPainter(HudIcons.Plus), null,
                                Modifier.size(24.dp).align(Alignment.Center), colorFilter = ColorFilter.tint(Hud.Ink),
                            )
                        }
                        Text("Get more apps", color = Hud.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }

    menu?.let { app ->
        val onHome = app.pkg in homePkgs
        val isHidden = app.pkg in hidden
        OptionsDialog(
            title = app.label,
            options = buildList {
                add(MenuOption("Open") { Apps.launch(ctx, app) })
                if (onHome) add(MenuOption("Remove from Home") { prefs.pinned = homePkgs - app.pkg; onPrefsChanged() })
                else add(MenuOption("Add to Home") { prefs.pinned = homePkgs + app.pkg; onPrefsChanged() })
                if (isHidden) add(MenuOption("Unhide") { prefs.hidden = hidden - app.pkg; onPrefsChanged() })
                else add(MenuOption("Hide") { prefs.hidden = hidden + app.pkg; onPrefsChanged() })
                add(MenuOption("App info") { Apps.appInfo(ctx, app.pkg) })
                add(MenuOption("Uninstall") { Apps.uninstall(ctx, app.pkg) })
            },
            onDismiss = { menu = null },
        )
    }
}
