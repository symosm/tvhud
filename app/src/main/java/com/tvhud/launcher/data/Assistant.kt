package com.tvhud.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Built-in AI assistant, powered by Claude through the user's own Anthropic API key.
 * It can answer questions, suggest what to watch, and open installed apps.
 */
object Assistant {
    const val MODEL = "claude-haiku-5-5"

    data class Message(val fromUser: Boolean, val text: String)

    sealed class Reply {
        /** [openPkg] is set when the assistant decided to open an app. */
        data class Ok(val text: String, val openPkg: String?) : Reply()
        data class Error(val text: String) : Reply()
    }

    private val openTag = Regex("""\[\[open:([A-Za-z0-9_.]+)]]""")

    suspend fun ask(apiKey: String, history: List<Message>, apps: List<AppEntry>, city: String, weatherNow: String): Reply {
        val appList = apps.joinToString("\n") { "- ${it.label} (${it.pkg})" }
        val weatherLine = if (weatherNow.isNotBlank()) "Current weather there: $weatherNow. You only know current conditions, not forecasts." else "You don't have live weather data."
        val system = """
            You are TV Hud, the assistant built into the user's TV home screen. The user is on the couch reading your
            answer on a TV from a few metres away, so keep answers short: 1–4 short sentences, no markdown, no lists
            longer than 4 items. Their weather city is $city. $weatherLine
            You can open apps installed on this TV. If the user asks to open, launch, play or watch something in a
            specific app, reply with one short sentence and add the tag [[open:PACKAGE]] using the exact package
            name from this list (only these apps exist):
            $appList
            If they ask for a show or movie, suggest which installed app is likely to have it, but say you can't be
            sure what each service currently offers. Never invent apps that aren't in the list.
        """.trimIndent()

        val messages = JSONArray()
        // Keep the last 12 turns; the API needs the conversation to start with the user.
        history.takeLast(12).dropWhile { !it.fromUser }.forEach { m ->
            messages.put(JSONObject().put("role", if (m.fromUser) "user" else "assistant").put("content", m.text))
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 400)
            .put("system", system)
            .put("messages", messages)
            .toString()

        val resp = Net.postJson(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to "2023-06-01"),
            body,
        ) ?: return Reply.Error("Can't reach the internet right now.")

        val (code, text) = resp
        if (code == 401) return Reply.Error("That API key didn't work. Check it in the assistant's settings.")
        if (code == 429) return Reply.Error("Too many questions at once — try again in a moment.")
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
            return Reply.Error("The assistant had a problem${if (msg != null) ": $msg" else "."}")
        }
        return runCatching {
            val content = JSONObject(text).getJSONArray("content")
            val out = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") out.append(block.getString("text"))
            }
            val raw = out.toString()
            val pkg = openTag.find(raw)?.groupValues?.get(1)?.takeIf { p -> apps.any { it.pkg == p } }
            Reply.Ok(raw.replace(openTag, "").trim(), pkg)
        }.getOrElse { Reply.Error("Couldn't read the assistant's answer.") }
    }
}
