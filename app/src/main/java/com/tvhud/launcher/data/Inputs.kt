package com.tvhud.launcher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings

enum class InputKind { CONSOLE, BOX, CAST }

data class InputEntry(
    val id: String,
    val name: String,
    /** e.g. "HDMI 1 · Connected" */
    val detail: String,
    val connected: Boolean,
    val kind: InputKind,
)

object Inputs {
    const val CAST_ID = "tvhud:cast"

    /** HDMI / AV inputs the TV reports, plus a Screen cast card. */
    fun load(ctx: Context, prefs: Prefs): List<InputEntry> {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val list = runCatching { tim?.tvInputList ?: emptyList() }.getOrDefault(emptyList())
            .filter { it.isPassthroughInput && it.parentId == null }
            .map { info ->
                val original = runCatching { info.loadLabel(ctx).toString() }.getOrDefault("Input")
                val state = runCatching { tim?.getInputState(info.id) }.getOrNull()
                val connected = state == null || state == TvInputManager.INPUT_STATE_CONNECTED
                val custom = prefs.inputName(info.id)
                InputEntry(
                    id = info.id,
                    name = custom ?: original,
                    detail = (if (custom != null) "$original · " else "") + if (connected) "Connected" else "No signal",
                    connected = connected,
                    kind = if (info.type == TvInputInfo.TYPE_HDMI) InputKind.CONSOLE else InputKind.BOX,
                )
            }
            .sortedWith(compareByDescending<InputEntry> { it.connected }.thenBy { it.name })
        return list + InputEntry(CAST_ID, "Screen cast", "From your phone or PC", true, InputKind.CAST)
    }

    fun open(ctx: Context, entry: InputEntry) {
        if (entry.id == CAST_ID) {
            // Chromecast built-in is always listening on Google TV; this shows its settings/name.
            Apps.safeStart(ctx, Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(entry.id)
        Apps.safeStart(ctx, Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
