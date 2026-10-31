package com.tvhud.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Built-in AI assistant, powered by Google Gemini (free tier) through the user's own API key.
 * It can answer questions, suggest what to watch, and open installed apps.
 */
object Assistant {
    const val MODEL = "gemini-2.5-flash"

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

        val contents = JSONArray()
        // Keep the last 12 turns; the conversation must start with the user.
        history.takeLast(12).dropWhile { !it.fromUser }.forEach { m ->
            contents.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text))),
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", 800)
                    // No hidden "thinking": faster answers, and it doesn't eat the free quota.
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)),
            )
            .toString()

        val resp = Net.postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent",
            mapOf("x-goog-api-key" to apiKey.trim()),
            body,
        ) ?: return Reply.Error("Can't reach the internet right now.")

        val (code, text) = resp
        val apiMsg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
        if (code == 400 && apiMsg?.contains("API key", ignoreCase = true) == true ||
            code == 401 || code == 403 && apiMsg?.contains("key", ignoreCase = true) == true
        ) {
            return Reply.Error("That API key didn't work. Press “API key” to check it.")
        }
        if (code == 429) return Reply.Error("You've reached Gemini's free limit for now — try again in a minute (or tomorrow if it's the daily limit).")
        if (code !in 200..299) {
            return Reply.Error("The assistant had a problem${if (apiMsg != null) ": $apiMsg" else "."}")
        }
        return runCatching {
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val out = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought", false)) out.append(part.optString("text"))
            }
            val raw = out.toString()
            val pkg = openTag.find(raw)?.groupValues?.get(1)?.takeIf { p -> apps.any { it.pkg == p } }
            Reply.Ok(raw.replace(openTag, "").trim(), pkg)
        }.getOrElse { Reply.Error("Couldn't read the assistant's answer.") }
    }
}
