package com.tvhud.launcher.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvhud.launcher.data.AppEntry
import com.tvhud.launcher.data.Apps
import com.tvhud.launcher.data.Assistant
import com.tvhud.launcher.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Conversation survives leaving and returning to the screen while TV Hud is running. */
private val conversation = mutableStateListOf<Assistant.Message>()

@Composable
fun AssistantScreen(prefs: Prefs, allApps: List<AppEntry>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var askKey by remember { mutableStateOf(prefs.aiKey.isBlank()) }
    val listState = rememberLazyListState()
    val speakFocus = remember { FocusRequester() }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        if (prefs.aiKey.isBlank()) { askKey = true; return }
        conversation.add(Assistant.Message(true, q))
        input = ""
        thinking = true
        scope.launch {
            when (val r = Assistant.ask(prefs.aiKey, conversation.toList(), allApps, prefs.city, prefs.lastWeather)) {
                is Assistant.Reply.Ok -> {
                    conversation.add(Assistant.Message(false, r.text.ifBlank { "Okay." }))
                    r.openPkg?.let { pkg ->
                        allApps.firstOrNull { it.pkg == pkg }?.let { app ->
                            delay(900)
                            Apps.launch(ctx, app)
                        }
                    }
                }
                is Assistant.Reply.Error -> conversation.add(Assistant.Message(false, r.text))
            }
            thinking = false
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask TV Hud")
        runCatching { voice.launch(intent) }.onFailure {
            conversation.add(Assistant.Message(false, "Voice input isn't available on this TV — type your question instead."))
        }
    }

    LaunchedEffect(conversation.size, thinking) {
        val count = conversation.size + if (thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { speakFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 34.dp, bottom = 26.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Assistant")
                Text("Ask TV Hud", color = Hud.Ink, fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
            }
            Spacer(Modifier.weight(1f))
            Pill("Clear chat", height = 28.dp) { conversation.clear() }
            Spacer(Modifier.width(8.dp))
            Pill("API key", height = 28.dp) { askKey = true }
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (conversation.isEmpty()) {
                item {
                    Text(
                        "Try: “Open YouTube” · “What should I watch tonight?” · “Is it hot outside?” · “How do I pair a controller?”",
                        color = Hud.Muted, fontSize = 14.sp,
                    )
                }
            }
            items(conversation) { m -> Bubble(m) }
            if (thinking) item { Bubble(Assistant.Message(false, "…")) }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Speak", height = 40.dp, modifier = Modifier.width(110.dp), focusRequester = speakFocus) { listen() }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("…or type a question", fontSize = 13.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Hud.Ink, unfocusedTextColor = Hud.Ink,
                    focusedBorderColor = Hud.Ink, unfocusedBorderColor = Hud.GlassBorder,
                    focusedContainerColor = Hud.Glass, unfocusedContainerColor = Hud.Glass, cursorColor = Hud.Ink,
                ),
                modifier = Modifier.weight(1f),
            )
            Pill("Send", height = 40.dp, modifier = Modifier.width(90.dp)) { send(input) }
        }
    }

    if (askKey) {
        TextDialog(
            title = "Connect the assistant",
            hint = "sk-ant-…",
            initial = prefs.aiKey,
            note = "The assistant runs on Claude. Create an API key at console.anthropic.com (API keys › Create key) and paste " +
                "or type it here. Each question costs a fraction of a cent on your Anthropic account. The key stays on this TV.",
            onDismiss = { askKey = false },
            onDone = { prefs.aiKey = it; askKey = false },
        )
    }
}

@Composable
private fun Bubble(m: Assistant.Message) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            m.text,
            color = if (m.fromUser) Hud.Dark else Hud.Ink,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .background(if (m.fromUser) Hud.Ink else Color(0xCC1C212B), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
