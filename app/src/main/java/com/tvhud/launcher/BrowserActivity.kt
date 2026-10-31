package com.tvhud.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tvhud.launcher.data.Prefs
import java.io.ByteArrayInputStream
import java.net.URLEncoder

/** Ad-blocking web browser built for the TV remote. */
class BrowserActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var shield: Button
    private lateinit var counter: TextView
    private lateinit var desktopBtn: Button
    private lateinit var root: FrameLayout
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    @Volatile private var adBlockOn = true
    private var blockedOnPage = 0
    private var desktopMode = false
    private var mobileUserAgent = ""

    private val blockHosts: Set<String> by lazy {
        runCatching {
            assets.open("adhosts.txt").bufferedReader().readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toHashSet()
        }.getOrDefault(hashSetOf())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        adBlockOn = prefs.adBlock

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0E1116")) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Toolbar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#14181F"))
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        val back = button("‹ Back") { if (web.canGoBack()) web.goBack() else finish() }
        val reload = button("Reload") { web.reload() }
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.parseColor("#F2F4F8"))
            setHintTextColor(Color.parseColor("#8A90A0"))
            hint = "Search or type a website"
            imeOptions = EditorInfo.IME_ACTION_GO
            background = pill("#1C212B", "#F2F4F8", stroke = true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    go(text.toString()); true
                } else false
            }
        }
        shield = button("") { toggleAdBlock() }
        counter = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#B9BFCC"))
            setPadding(dp(8), 0, dp(4), 0)
        }
        val zoomOut = button("Zoom −") { web.zoomOut() }
        val zoomIn = button("Zoom +") { web.zoomIn() }
        desktopBtn = button("Desktop site") { toggleDesktop() }
        val home = button("Start page") { showStartPage() }

        bar.addView(back, lp())
        bar.addView(reload, lp())
        bar.addView(address, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        bar.addView(shield, lp())
        bar.addView(counter, lp())
        bar.addView(zoomOut, lp())
        bar.addView(zoomIn, lp())
        bar.addView(desktopBtn, lp())
        bar.addView(home, lp(0))

        web = WebView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.parseColor("#0E1116"))
        }
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 125
        }
        mobileUserAgent = web.settings.userAgentString
        web.webViewClient = AdBlockingClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                column.visibility = View.GONE
            }

            override fun onHideCustomView() {
                fullscreenView?.let { root.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
                column.visibility = View.VISIBLE
            }
        }

        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column)
        setContentView(root)

        updateShield()
        val start = intent?.dataString
        if (start != null) web.loadUrl(start) else showStartPage()
        address.requestFocus()
    }

    private inner class AdBlockingClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host ?: return null
            if (adBlockOn && isBlocked(host) && !request.isForMainFrame) {
                runOnUiThread { blockedOnPage++; updateCounter() }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val scheme = url.scheme?.lowercase()
            // Block redirects to ad/pop-under sites and app-store/intent hijacks.
            if (scheme != "http" && scheme != "https") return true
            if (adBlockOn && url.host?.let { isBlocked(it) } == true) {
                blockedOnPage++; updateCounter(); return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            blockedOnPage = 0
            updateCounter()
            if (url != null && !url.startsWith("data:") && !url.startsWith("about:")) address.setText(url)
        }
    }

    private fun isBlocked(host: String): Boolean {
        var h = host.lowercase()
        while (true) {
            if (h in blockHosts) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.length - 1) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return false
        }
    }

    private fun go(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(' ') || !t.contains('.') -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
            else -> "https://$t"
        }
        web.loadUrl(url)
        web.requestFocus()
    }

    private fun toggleAdBlock() {
        adBlockOn = !adBlockOn
        prefs.adBlock = adBlockOn
        updateShield()
        web.reload()
    }

    private fun toggleDesktop() {
        desktopMode = !desktopMode
        web.settings.userAgentString = if (desktopMode) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        } else mobileUserAgent
        desktopBtn.text = if (desktopMode) "Mobile site" else "Desktop site"
        web.reload()
    }

    private fun updateShield() {
        shield.text = if (adBlockOn) "Ad blocker ON" else "Ad blocker OFF"
        updateCounter()
    }

    private fun updateCounter() {
        counter.text = if (adBlockOn) "$blockedOnPage blocked" else ""
    }

    private fun showStartPage() {
        val sites = listOf(
            "Google" to "https://www.google.com",
            "YouTube" to "https://www.youtube.com",
            "Facebook" to "https://www.facebook.com",
            "Twitch" to "https://www.twitch.tv",
            "Kick" to "https://kick.com",
            "Wikipedia" to "https://www.wikipedia.org",
        )
        val tiles = sites.joinToString("") { (name, url) ->
            "<a href=\"$url\"><span class=\"i\">${name.first()}</span>$name</a>"
        }
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body{margin:0;background:#0E1116;color:#F2F4F8;font-family:sans-serif;padding:48px 64px}
              h1{font-weight:300;font-size:44px;margin:0 0 8px}
              p{color:#B9BFCC;font-size:18px;margin:0 0 36px}
              .g{display:grid;grid-template-columns:repeat(6,1fr);gap:20px}
              a{display:flex;flex-direction:column;gap:14px;padding:22px;border-radius:18px;background:#181C24;color:#F2F4F8;
                text-decoration:none;font-size:20px;font-weight:600;border:3px solid transparent;outline:none}
              a:focus{border-color:#fff;transform:scale(1.05)}
              .i{width:48px;height:48px;border-radius:14px;background:#2C3442;display:flex;align-items:center;justify-content:center;font-weight:800}
            </style></head><body>
            <h1>New tab</h1><p>Ads, trackers and pop-ups are blocked. Use the address bar above to search.</p>
            <div class="g">$tiles</div></body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://tvhud.local/", html, "text/html", "utf-8", null)
        address.setText("")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when {
                fullscreenView != null -> web.webChromeClient?.onHideCustomView()
                web.canGoBack() -> web.goBack()
                else -> finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    // ---- small view helpers ----

    private fun lp(end: Int = 8) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = dp(end) }

    private fun pill(fill: String, focusFill: String, stroke: Boolean = false): StateListDrawable {
        fun shape(color: String, border: Boolean) = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor(color))
            if (border) setStroke(dp(2), Color.parseColor("#F2F4F8"))
        }
        return StateListDrawable().apply {
            if (stroke) {
                addState(intArrayOf(android.R.attr.state_focused), shape(fill, true))
            } else {
                addState(intArrayOf(android.R.attr.state_focused), shape(focusFill, false))
            }
            addState(intArrayOf(), shape(fill, false))
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(40)
        minimumHeight = dp(40)
        setPadding(dp(16), 0, dp(16), 0)
        stateListAnimator = null
        background = pill("#1C212B", "#F2F4F8")
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(Color.parseColor("#14161B"), Color.parseColor("#F2F4F8")),
            ),
        )
        setOnClickListener { onClick() }
    }
}
