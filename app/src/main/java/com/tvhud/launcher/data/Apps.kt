package com.tvhud.launcher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

enum class AppCategory(val label: String) {
    STREAMING("Streaming"), MUSIC("Music"), GAMES("Games"), TOOLS("Tools")
}

data class AppEntry(
    val pkg: String,
    val label: String,
    /** The app's official 16:9 TV banner, when it has one. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val category: AppCategory,
    val launch: Intent,
)

object Apps {
    /** Popular apps placed on Home first, in this order, when they are installed. */
    private val preferredOrder = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.spotify.tv.android",
        "tv.twitch.android.app",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.wbd.stream",
        "com.hbo.hbonow",
        "com.vuclip.viu",
        "com.valvesoftware.steamlink",
    )

    private val streamingHints = listOf("netflix", "youtube", "twitch", "disney", "hbo", "viu", "amazonvideo", "iwant", "tfc", "vivamax", "plex", "kodi")
    private val musicHints = listOf("spotify", "music", "deezer", "tidal", "soundcloud")

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val infos = runCatching { pm.queryIntentActivities(query, 0) }.getOrDefault(emptyList())
            for (ri in infos) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == ctx.packageName || result.containsKey(pkg)) continue
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
                val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                result[pkg] = AppEntry(
                    pkg = pkg,
                    label = ri.loadLabel(pm)?.toString() ?: pkg,
                    banner = banner?.let { toImage(it, 320, 180) },
                    icon = icon?.let { toImage(it, 160, 160) },
                    category = categorize(ai.applicationInfo, pkg),
                    launch = launch,
                )
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    /** Home row: the user's own order if they set one, else popular apps + other streaming apps. */
    fun homeRow(all: List<AppEntry>, prefs: Prefs): List<AppEntry> {
        val byPkg = all.associateBy { it.pkg }
        prefs.pinned?.let { saved -> return saved.mapNotNull { byPkg[it] } }
        val first = preferredOrder.mapNotNull { byPkg[it] }
        val rest = all.filter { it.category == AppCategory.STREAMING && it !in first }
        return (first + rest).take(10)
    }

    private fun categorize(info: ApplicationInfo, pkg: String): AppCategory {
        val p = pkg.lowercase()
        if (musicHints.any { p.contains(it) }) return AppCategory.MUSIC
        if (streamingHints.any { p.contains(it) }) return AppCategory.STREAMING
        return when (info.category) {
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.STREAMING
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            else -> if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) AppCategory.GAMES else AppCategory.TOOLS
        }
    }

    private fun toImage(d: Drawable, maxW: Int, maxH: Int): ImageBitmap? = runCatching {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: maxW
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: maxH
        val scale = minOf(maxW.toFloat() / iw, maxH.toFloat() / ih, 1f)
        d.toBitmap((iw * scale).toInt().coerceAtLeast(1), (ih * scale).toInt().coerceAtLeast(1)).asImageBitmap()
    }.getOrNull()

    // ---- Actions ----

    fun launch(ctx: Context, app: AppEntry) = safeStart(ctx, app.launch)

    /** Opens an installed app by package name, or its Google Play page if it's missing. */
    fun openOrInstall(ctx: Context, pkg: String) {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            safeStart(ctx, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            openStore(ctx, pkg)
        }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun openStore(ctx: Context, pkg: String? = null) {
        val market = if (pkg != null) "market://details?id=$pkg" else "market://search?q=apps"
        val web = if (pkg != null) "https://play.google.com/store/apps/details?id=$pkg" else "https://play.google.com/store/apps"
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(market)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun appInfo(ctx: Context, pkg: String) = safeStart(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun uninstall(ctx: Context, pkg: String) = safeStart(
        ctx,
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun safeStart(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent); true
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't open that on this TV", Toast.LENGTH_SHORT).show(); false
    }
}
