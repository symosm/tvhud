package com.tvhud.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tvhud.launcher.data.Prefs
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ambient mode: an ad-free screensaver with a big clock, date and weather over the night sky.
 * Choose it in Quick settings › Screensaver › "TV Hud Ambient".
 */
class AmbientDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var content: LinearLayout
    private var tick = 0

    private val updater = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.hud_night)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.85f
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        clock = TextView(this).apply {
            setTextColor(Color.parseColor("#F2F4F8"))
            textSize = 110f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -0.03f
            includeFontPadding = false
        }
        date = TextView(this).apply {
            setTextColor(Color.parseColor("#C9CEDA"))
            textSize = 20f
            setPadding(px(4), px(6), 0, 0)
        }
        weather = TextView(this).apply {
            setTextColor(Color.parseColor("#B9BFCC"))
            textSize = 16f
            setPadding(px(4), px(4), 0, 0)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock)
            addView(date)
            addView(weather)
        }
        root.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(px(60), 0, 0, px(50))
            },
        )
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.post(updater)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(updater)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(updater)
        super.onDetachedFromWindow()
    }

    private fun update() {
        val now = LocalDateTime.now()
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"
        clock.text = now.format(DateTimeFormatter.ofPattern(pattern))
        date.text = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
        weather.text = Prefs(this).lastWeather
        // Drift the clock slightly every few minutes to protect the panel from burn-in.
        tick++
        if (tick % 18 == 0) {
            val d = resources.displayMetrics.density
            content.translationX = ((tick / 18) % 5 * 12 * d)
            content.translationY = -((tick / 18) % 3 * 10 * d)
        }
    }
}
