package com.tvhud.launcher.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.LruCache
import android.view.InputDevice
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvhud.launcher.data.Apps
import com.tvhud.launcher.data.Net
import com.tvhud.launcher.data.Prefs
import com.tvhud.launcher.data.Steam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val coverCache = LruCache<String, ImageBitmap>(40)

@Composable
fun rememberUrlImage(url: String): ImageBitmap? {
    val state = produceState(coverCache.get(url), url) {
        if (value == null) {
            val bytes = Net.getBytes(url)
            val bmp = bytes?.let {
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
            if (bmp != null) {
                coverCache.put(url, bmp)
                value = bmp
            }
        }
    }
    return state.value
}

private fun connectedControllers(): List<String> =
    InputDevice.getDeviceIds().toList()
        .mapNotNull { InputDevice.getDevice(it) }
        .filter { d ->
            !d.isVirtual && (
                d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                )
        }
        .map { it.name }
        .distinct()

private enum class Ask { NONE, PC_NAME, MAC, STEAM_ID, STEAM_KEY }

@Composable
fun PlayFromPcScreen(prefs: Prefs, refreshTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(Ask.NONE) }
    var accountVersion by remember { mutableStateOf(0) }
    var games by remember { mutableStateOf<List<Steam.Game>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var pcName by remember { mutableStateOf(prefs.pcName) }
    var mac by remember { mutableStateOf(prefs.pcMac) }
    val hasAccount = remember(accountVersion) { prefs.steamId.isNotBlank() && prefs.steamKey.isNotBlank() }
    val steamLinkInstalled = remember(refreshTick) { Apps.isInstalled(ctx, Steam.STEAM_LINK) }
    val moonlightInstalled = remember(refreshTick) { Apps.isInstalled(ctx, Steam.MOONLIGHT) }
    val controllers by produceState(connectedControllers()) {
        while (true) {
            value = connectedControllers()
            delay(3000)
        }
    }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(accountVersion) {
        games = null
        loadFailed = false
        if (prefs.steamId.isNotBlank() && prefs.steamKey.isNotBlank()) {
            val result = Steam.recentGames(prefs.steamKey, prefs.steamId)
            games = result
            loadFailed = result == null
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { playFocus.requestFocus() }
    }

    fun openSteamLink(gameName: String? = null) {
        if (!steamLinkInstalled) {
            Toast.makeText(ctx, "Install Steam Link first (free)", Toast.LENGTH_SHORT).show()
            Apps.openStore(ctx, Steam.STEAM_LINK)
            return
        }
        if (gameName != null) Toast.makeText(ctx, "Opening Steam Link — pick $gameName in Big Picture", Toast.LENGTH_LONG).show()
        Apps.openOrInstall(ctx, Steam.STEAM_LINK)
    }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 26.dp)) {
        // Header + PC card
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Games")
                Text("Play from your PC", color = Hud.Ink, fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .background(Hud.Glass, RoundedCornerShape(14.dp))
                    .border(1.dp, Hud.GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(rememberVectorPainter(HudIcons.Monitor), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Hud.Ink))
                Column {
                    Text(pcName, color = Hud.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (mac.isBlank()) "Set up Wake-on-LAN to turn it on from here" else "Wake-on-LAN ready",
                        color = Hud.Soft, fontSize = 10.sp,
                    )
                }
                Pill(if (mac.isBlank()) "Set up" else "Wake PC", height = 26.dp) {
                    if (mac.isBlank()) {
                        ask = Ask.MAC
                    } else {
                        scope.launch {
                            val ok = Steam.wake(mac)
                            Toast.makeText(
                                ctx,
                                if (ok) "Wake signal sent to $pcName — give it about 20 seconds" else "That MAC address doesn't look right",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                Pill("Rename", height = 26.dp) { ask = Ask.PC_NAME }
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Recently played on Steam")
        Spacer(Modifier.height(8.dp))
        when {
            !hasAccount -> WideCard(
                title = "Show your Steam games here",
                sub = "Optional. Add your Steam ID and a free Steam Web API key.",
                onClick = { ask = Ask.STEAM_ID },
            )
            loadFailed -> WideCard(
                title = "Couldn't load your games",
                sub = "Check your Steam ID and key, and that “Game details” is Public in Steam privacy settings. OK to edit.",
                onClick = { ask = Ask.STEAM_ID },
            )
            games == null -> Text("Loading your games…", color = Hud.Muted, fontSize = 13.sp, modifier = Modifier.height(130.dp))
            games!!.isEmpty() -> WideCard(
                title = "No games played in the last 2 weeks",
                sub = "Play something on Steam and it will show up here.",
                onClick = { openSteamLink() },
            )
            else -> LazyRow(
                contentPadding = PaddingValues(start = 2.dp, end = 60.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(games!!, key = { _, g -> g.appId }) { _, game ->
                    GameTile(game) { openSteamLink(game.name) }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            // Controller status
            Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Controller")
                FocusCard(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    focusScale = 1.03f,
                    onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(rememberVectorPainter(HudIcons.Gamepad), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Hud.Ink))
                        Column {
                            Text(
                                controllers.firstOrNull() ?: "No controller connected",
                                color = Hud.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (controllers.isEmpty()) "OK to pair one in Bluetooth settings"
                                else if (controllers.size > 1) "+${controllers.size - 1} more connected" else "Connected",
                                color = Hud.Soft, fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (moonlightInstalled) {
                Pill("Moonlight", height = 40.dp, modifier = Modifier.width(130.dp)) { Apps.openOrInstall(ctx, Steam.MOONLIGHT) }
            }

            // Big Play button → Steam Link
            FocusCard(
                modifier = Modifier.size(200.dp, 64.dp),
                shape = RoundedCornerShape(18.dp),
                background = Hud.Ink,
                focusedBackground = Color.White,
                focusRequester = playFocus,
                onClick = { openSteamLink() },
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(rememberVectorPainter(HudIcons.Play), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Hud.Dark))
                    Column {
                        Text(if (steamLinkInstalled) "Play" else "Get Steam Link", color = Hud.Dark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (steamLinkInstalled) "Opens Steam Link" else "Free on Google Play", color = Color(0xFF3A404C), fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Your PC needs Steam open. Best on wired internet or 5 GHz Wi-Fi.  ·  Game names and covers come from your own Steam account.",
            color = Hud.Muted, fontSize = 10.sp,
        )
    }

    when (ask) {
        Ask.NONE -> Unit
        Ask.PC_NAME -> TextDialog(
            title = "PC name", hint = "e.g. Gaming PC", initial = pcName,
            onDismiss = { ask = Ask.NONE },
            onDone = { prefs.pcName = it; pcName = prefs.pcName; ask = Ask.NONE },
        )
        Ask.MAC -> TextDialog(
            title = "Your PC's MAC address",
            hint = "e.g. 3C:7C:3F:12:AB:CD",
            initial = mac,
            note = "On your PC, open Command Prompt and type: getmac /v — use the “Physical Address” of the wired (Ethernet) adapter. " +
                "Also turn on Wake-on-LAN in your PC's BIOS and network adapter settings.",
            onDismiss = { ask = Ask.NONE },
            onDone = { prefs.pcMac = it; mac = prefs.pcMac; ask = Ask.NONE },
        )
        Ask.STEAM_ID -> TextDialog(
            title = "Your Steam ID (17 digits)",
            hint = "e.g. 7656119…",
            initial = prefs.steamId,
            note = "In Steam: click your name › Account details. Your Steam ID is shown under your account name.",
            onDismiss = { ask = Ask.NONE },
            onDone = { prefs.steamId = it; ask = Ask.STEAM_KEY },
        )
        Ask.STEAM_KEY -> TextDialog(
            title = "Steam Web API key",
            hint = "32 letters and numbers",
            initial = prefs.steamKey,
            note = "Get a free key at steamcommunity.com/dev/apikey (sign in, type any domain name such as tvhud.local). " +
                "It stays on this TV and is only used to show your recent games.",
            onDismiss = { ask = Ask.NONE },
            onDone = { prefs.steamKey = it; ask = Ask.NONE; accountVersion++ },
        )
    }
}

@Composable
private fun WideCard(title: String, sub: String, onClick: () -> Unit) {
    FocusCard(
        modifier = Modifier.width(520.dp).height(86.dp).padding(start = 2.dp),
        focusScale = 1.03f,
        onClick = onClick,
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = Hud.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Hud.Soft, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun GameTile(game: Steam.Game, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val cover = rememberUrlImage(game.coverUrl)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusCard(
            modifier = Modifier.size(184.dp, 86.dp),
            shape = RoundedCornerShape(14.dp),
            background = Color(0xFF1A1E27),
            onFocusChange = { focused = it },
            onClick = onClick,
        ) {
            if (cover != null) {
                Image(cover, game.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(game.name, color = Hud.Soft, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(10.dp))
                }
            }
        }
        Text(
            game.name, color = if (focused) Color.White else Hud.Muted, fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(184.dp).padding(start = 2.dp),
        )
    }
}
