package com.tvhud.launcher.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvhud.launcher.BrowserActivity
import com.tvhud.launcher.data.AppEntry
import com.tvhud.launcher.data.Apps
import com.tvhud.launcher.data.InputEntry
import com.tvhud.launcher.data.InputKind
import com.tvhud.launcher.data.Inputs
import com.tvhud.launcher.data.Prefs
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000)
    }
}

@Composable
fun HomeScreen(
    prefs: Prefs,
    allApps: List<AppEntry>,
    inputs: List<InputEntry>,
    weather: String,
    prefsVersion: Int,
    onPrefsChanged: () -> Unit,
    onOpen: (Screen) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val homeApps = remember(allApps, prefsVersion) {
        val hidden = prefs.hidden
        Apps.homeRow(allApps, prefs).filter { it.pkg !in hidden }
    }
    var appMenu by remember { mutableStateOf<AppEntry?>(null) }
    var inputMenu by remember { mutableStateOf<InputEntry?>(null) }
    var renaming by remember { mutableStateOf<InputEntry?>(null) }
    val firstApp = remember { FocusRequester() }
    val now by rememberNow()
    val is24 = DateFormat.is24HourFormat(ctx)

    LaunchedEffect(homeApps.isNotEmpty()) {
        if (homeApps.isNotEmpty()) {
            delay(80)
            runCatching { firstApp.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, top = 40.dp, bottom = 26.dp)) {
        // Clock, date, weather + shortcuts
        Row(Modifier.fillMaxWidth().padding(end = 60.dp), verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")),
                    color = Hud.Ink, fontSize = 88.sp, fontWeight = FontWeight.Light,
                    letterSpacing = (-3).sp, lineHeight = 88.sp,
                )
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) + if (weather.isNotBlank()) "  ·  $weather" else "",
                    color = Hud.Soft, fontSize = 17.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Pill("Ask AI", icon = HudIcons.Spark) { onOpen(Screen.ASSISTANT) }
                Pill("Play from PC", icon = HudIcons.Gamepad) { onOpen(Screen.PLAY_FROM_PC) }
                Pill("Browser", icon = HudIcons.Globe) {
                    Apps.safeStart(ctx, Intent(ctx, BrowserActivity::class.java))
                }
                Pill("All apps", icon = HudIcons.Grid) { onOpen(Screen.ALL_APPS) }
                Pill("", Modifier.width(30.dp), icon = HudIcons.Gear) { onSettings() }
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Apps")
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 2.dp, end = 60.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(homeApps, key = { _, a -> a.pkg }) { i, app ->
                AppTile(
                    app = app,
                    focusRequester = if (i == 0) firstApp else null,
                    onLongClick = { appMenu = app },
                    onClick = { Apps.launch(ctx, app) },
                )
            }
            item(key = "add") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusCard(
                        modifier = Modifier.size(152.dp, 86.dp),
                        shape = RoundedCornerShape(14.dp),
                        focusRequester = if (homeApps.isEmpty()) firstApp else null,
                        onClick = { onOpen(Screen.ALL_APPS) },
                    ) {
                        Image(
                            rememberVectorPainter(HudIcons.Plus), null,
                            Modifier.size(28.dp).align(Alignment.Center), colorFilter = ColorFilter.tint(Hud.Ink),
                        )
                    }
                    Text("Add apps", color = Hud.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Consoles & inputs")
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 2.dp, end = 60.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(inputs, key = { it.id }) { input ->
                InputCard(
                    input = input,
                    onLongClick = if (input.id == Inputs.CAST_ID) null else ({ inputMenu = input }),
                    onClick = { Inputs.open(ctx, input) },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyHint("OK", "Open")
            KeyHint("Hold OK", "Options")
            KeyHint("Back", "Return home")
        }
    }

    appMenu?.let { app ->
        val pkgs = homeApps.map { it.pkg }
        val idx = pkgs.indexOf(app.pkg)
        fun save(list: List<String>) { prefs.pinned = list; onPrefsChanged() }
        OptionsDialog(
            title = app.label,
            options = buildList {
                if (idx > 0) add(MenuOption("Move left") {
                    save(pkgs.toMutableList().also { it.removeAt(idx); it.add(idx - 1, app.pkg) })
                })
                if (idx in 0 until pkgs.size - 1) add(MenuOption("Move right") {
                    save(pkgs.toMutableList().also { it.removeAt(idx); it.add(idx + 1, app.pkg) })
                })
                add(MenuOption("Remove from Home") { save(pkgs - app.pkg) })
                add(MenuOption("App info") { Apps.appInfo(ctx, app.pkg) })
                add(MenuOption("Uninstall") { Apps.uninstall(ctx, app.pkg) })
            },
            onDismiss = { appMenu = null },
        )
    }
    inputMenu?.let { input ->
        OptionsDialog(
            title = input.name,
            options = listOf(
                MenuOption("Open") { Inputs.open(ctx, input) },
                MenuOption("Rename (e.g. PS5)") { renaming = input },
                MenuOption("Reset name") { prefs.setInputName(input.id, ""); onPrefsChanged() },
            ),
            onDismiss = { inputMenu = null },
        )
    }
    renaming?.let { input ->
        TextDialog(
            title = "Rename ${input.name}",
            hint = "e.g. PlayStation 5",
            initial = prefs.inputName(input.id) ?: "",
            onDismiss = { renaming = null },
            onDone = { prefs.setInputName(input.id, it); renaming = null; onPrefsChanged() },
        )
    }
}

/** App tile: the app's official TV banner, or its icon on a dark card. */
@Composable
fun AppTile(
    app: AppEntry,
    width: Int = 152,
    focusRequester: FocusRequester? = null,
    showLabel: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusCard(
            modifier = Modifier.size(width.dp, (width * 9 / 16).dp),
            shape = RoundedCornerShape(14.dp),
            background = Color(0xFF1A1E27),
            focusRequester = focusRequester,
            onFocusChange = { focused = it },
            onLongClick = onLongClick,
            onClick = onClick,
        ) {
            when {
                app.banner != null -> Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app.icon != null -> Image(app.icon, app.label, Modifier.size((width * 0.3f).dp).align(Alignment.Center))
                else -> Text(app.label.take(1), color = Hud.Ink, fontSize = 28.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        if (showLabel) {
            Text(
                app.label, color = if (focused) Color.White else Hud.Muted,
                fontSize = 13.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(width.dp).padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun InputCard(input: InputEntry, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val tint = when (input.kind) {
        InputKind.CONSOLE -> Color(0xFF2A1430)
        InputKind.BOX -> Color(0xFF11233A)
        InputKind.CAST -> Color(0xFF0F2B26)
    }
    val icon: ImageVector = when (input.kind) {
        InputKind.CONSOLE -> HudIcons.Gamepad
        InputKind.BOX -> HudIcons.Box
        InputKind.CAST -> HudIcons.Cast
    }
    FocusCard(
        modifier = Modifier.size(270.dp, 90.dp),
        shape = RoundedCornerShape(16.dp),
        background = tint,
        focusScale = 1.04f,
        onLongClick = onLongClick,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(
                Modifier.size(42.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(rememberVectorPainter(icon), null, Modifier.size(23.dp), colorFilter = ColorFilter.tint(Color.White))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(input.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).background(if (input.connected) Hud.Green else Hud.Gray, CircleShape))
                    Text(input.detail, color = Color(0xD9FFFFFF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            key, color = Hud.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Text(label, color = Hud.Muted, fontSize = 11.sp)
    }
}
