package com.tvhud.launcher.ui

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tvhud.launcher.R

object Hud {
    val Ink = Color(0xFFF2F4F8)
    val Muted = Color(0xFFB9BFCC)
    val Soft = Color(0xFFC9CEDA)
    val Glass = Color(0x14FFFFFF)
    val GlassBorder = Color(0x24FFFFFF)
    val Panel = Color(0xF214181F)
    val Dark = Color(0xFF14161B)
    val Green = Color(0xFF5BE39A)
    val Gray = Color(0xFF8A90A0)
}

/**
 * Remote-friendly click: OK/Enter fires on release, holding OK (or pressing Menu) fires [onLongClick].
 * Also works with a mouse or touch.
 */
fun Modifier.tvClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    var longFired by remember { mutableStateOf(false) }
    var downSeen by remember { mutableStateOf(false) }
    this
        .onKeyEvent { e ->
            val native = e.nativeKeyEvent
            val code = native.keyCode
            if (code == KeyEvent.KEYCODE_MENU && onLongClick != null) {
                if (e.type == KeyEventType.KeyUp) onLongClick()
                return@onKeyEvent true
            }
            val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
                code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A
            if (!isOk) return@onKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        longFired = false
                        downSeen = true
                    } else if (native.isLongPress && !longFired && onLongClick != null) {
                        longFired = true
                        onLongClick()
                    }
                }
                KeyEventType.KeyUp -> {
                    // Ignore a release whose press began on another screen.
                    if (downSeen && !longFired) onClick()
                    downSeen = false
                    longFired = false
                }
            }
            true
        }
        .focusable()
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick?.invoke() })
        }
}

/** A card that lifts and gets a white ring when focused, like the design. */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    background: Color = Hud.Glass,
    focusedBackground: Color = background,
    focusScale: Float = 1.06f,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                val f = it.isFocused || it.hasFocus
                if (f != focused) {
                    focused = f
                    onFocusChange?.invoke(f)
                }
            }
            .tvClick(onLongClick, onClick)
            .shadow(if (focused) 18.dp else 6.dp, shape, clip = false)
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Hud.GlassBorder, shape),
    ) {
        content(focused)
    }
}

/** Rounded "glass" pill button, white when focused. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 30.dp,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusCard(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(50),
        background = if (selected) Hud.Ink else Hud.Glass,
        focusedBackground = Hud.Ink,
        focusScale = 1.05f,
        focusRequester = focusRequester,
        onClick = onClick,
    ) { focused ->
        val fg = if (focused || selected) Hud.Dark else Hud.Ink
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = if (text.isEmpty()) 7.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Image(rememberVectorPainter(icon), null, Modifier.size(16.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg))
            if (text.isNotEmpty()) Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier,
        color = Hud.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
    )
}

@Composable
fun NightBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF07080E))) {
        Image(
            painterResource(R.drawable.hud_night), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
        content()
    }
}

/** Text entry popup (uses the TV's on-screen keyboard). */
@Composable
fun TextDialog(
    title: String,
    hint: String,
    initial: String = "",
    note: String? = null,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF181C24)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = Hud.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (note != null) Text(note, color = Hud.Soft, fontSize = 11.sp, lineHeight = 16.sp)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(hint, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(value) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Hud.Ink, unfocusedTextColor = Hud.Ink,
                    focusedBorderColor = Hud.Ink, unfocusedBorderColor = Hud.Gray, cursorColor = Hud.Ink,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Save", Modifier.width(110.dp), onClick = { onDone(value) })
                Pill("Cancel", Modifier.width(110.dp), onClick = onDismiss)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

data class MenuOption(val label: String, val action: () -> Unit)

/** Options popup, e.g. after holding OK on an app. */
@Composable
fun OptionsDialog(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(360.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF181C24)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Hud.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            options.forEachIndexed { i, o ->
                FocusCard(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    focusedBackground = Hud.Ink,
                    focusScale = 1.02f,
                    focusRequester = if (i == 0) fr else null,
                    onClick = { onDismiss(); o.action() },
                ) { focused ->
                    Text(
                        o.label, color = if (focused) Hud.Dark else Hud.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

/** Info popup with a single OK button. */
@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF181C24)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = Hud.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Hud.Soft, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(2.dp))
            Pill("OK", Modifier.width(110.dp), focusRequester = fr, onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

/** Builds a simple outline icon from SVG-style path strings (24×24 grid). */
fun strokeIcon(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d),
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = if (filled) null else SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

object HudIcons {
    val Gear = strokeIcon(
        "gear",
        "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val Gamepad = strokeIcon(
        "gamepad",
        "M7 7h10a5 5 0 0 1 4.9 6l-.8 3.6a2.6 2.6 0 0 1-4.4 1.2L14.5 16h-5l-2.2 1.8a2.6 2.6 0 0 1-4.4-1.2L2.1 13A5 5 0 0 1 7 7z",
        "M7 10v4M5 12h4",
        "M16 10.2a.8.8 0 1 0 0 1.6a.8.8 0 1 0 0-1.6zM18 12.2a.8.8 0 1 0 0 1.6a.8.8 0 1 0 0-1.6z",
    )
    val Box = strokeIcon("box", "M4 8h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z", "M6 12.5h5M17 12.5h1", "M9 4l3 4 3-4")
    val Cast = strokeIcon("cast", "M3 8V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6", "M3 12a8 8 0 0 1 8 8M3 16a4 4 0 0 1 4 4")
    val Monitor = strokeIcon("monitor", "M5 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M8 20h8M12 16v4")
    val Play = strokeIcon("play", "M7 4.5v15l13-7.5z", filled = true)
    val Globe = strokeIcon("globe", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M3 12h18", "M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9s1.3-6.4 3.8-9z")
    val Grid = strokeIcon("grid", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z")
    val Plus = strokeIcon("plus", "M12 5v14M5 12h14")
    val Spark = strokeIcon(
        "spark",
        "M12 3c.6 4.6 2.4 6.4 7 7-4.6.6-6.4 2.4-7 7-.6-4.6-2.4-6.4-7-7 4.6-.6 6.4-2.4 7-7z",
        "M19 16.5c.2 1.4.8 2 2 2.2-1.2.2-1.8.8-2 2.2-.2-1.4-.8-2-2-2.2 1.2-.2 1.8-.8 2-2.2z",
    )
}
