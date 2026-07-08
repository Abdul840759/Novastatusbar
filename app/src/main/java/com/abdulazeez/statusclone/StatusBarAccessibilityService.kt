package com.abdulazeez.statusclone

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws a custom "One UI style" status bar as a system overlay window.
 *
 * Responsibilities:
 *  - Recomputes bar height/width on orientation change (landscape support).
 *  - Shows on the lock screen too (FLAG_SHOW_WHEN_LOCKED), aligned to the
 *    real status bar's actual height/cutout.
 *  - Hides itself when the foreground app is fullscreen/immersive.
 *  - Transparent background by default.
 *  - Carrier name (real or user override), auto/manual network-type label,
 *    live system-state icons (bluetooth, hotspot, DND, etc. via
 *    SystemStateWatcher + StatusIconManager), and up to 5 fake
 *    notification icons pulled from the user's own installed apps.
 */
class StatusBarAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private lateinit var stateWatcher: SystemStateWatcher
    private var iconManager: StatusIconManager? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockRunnable: Runnable

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBattery(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Needed so we can inspect the foreground window's bounds to detect
        // fullscreen/immersive apps and auto-hide, same as the real status bar.
        serviceInfo = serviceInfo.also {
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        addOverlay()
        startClockTicker()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        stateWatcher = SystemStateWatcher(this) { state ->
            if (prefs.showSystemStateIcons) iconManager?.render(state)
        }
        stateWatcher.start()

        loadFakeNotificationIcons()
        updateCarrierAndNetworkType()

        // Live-apply any change made in the settings screen (color, battery
        // style, transparency, auto-hide, carrier, network, icons) without
        // needing to restart the service.
        prefs.raw.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            applyPrefsToView()
            if (key == "fake_notification_apps") loadFakeNotificationIcons()
            if (key == "carrier_name_override" || key == "show_carrier_name" ||
                key == "network_type_mode" || key == "manual_network_type"
            ) {
                updateCarrierAndNetworkType()
            }
        }

    // ---------- Overlay creation / orientation / lock screen ----------

    private fun addOverlay() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_status_bar, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            currentBarHeightPx(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP

        windowManager.addView(view, params)
        overlayView = view
        layoutParams = params

        iconManager = StatusIconManager(this, view.findViewById(R.id.systemIconRow))
        applyPrefsToView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Screen rotated (portrait <-> landscape): recompute bar height and re-layout.
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.height = currentBarHeightPx()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            // View was already detached (e.g. service restarting) - ignore.
        }
    }

    /**
     * Status bar height differs between portrait and landscape on many
     * OEM skins, and devices with a punch-hole/cutout report a taller
     * value than the plain dimen. Try the landscape-specific dimen first,
     * then fall back to the standard one.
     */
    private fun currentBarHeightPx(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val name = if (isLandscape) "status_bar_height_landscape" else "status_bar_height"
        var resId = resources.getIdentifier(name, "dimen", "android")
        if (resId <= 0) resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(24)
    }

    private fun dpToPx(dp: Int): Int {
        val metrics: DisplayMetrics = resources.displayMetrics
        return (dp * metrics.density).toInt()
    }

    // ---------- Styling from prefs (color, battery style, transparency) ----------

    private fun applyPrefsToView() {
        val view = overlayView ?: return
        val color = prefs.iconColor

        view.findViewById<TextView>(R.id.tvClock)?.setTextColor(color)
        view.findViewById<TextView>(R.id.tvBatteryPct)?.setTextColor(color)
        view.findViewById<TextView>(R.id.tvCarrierName)?.setTextColor(color)
        view.findViewById<TextView>(R.id.tvNetworkType)?.setTextColor(color)
        view.findViewById<ImageView>(R.id.ivBatteryIcon)?.setColorFilter(color)
        view.findViewById<ImageView>(R.id.ivSignal)?.setColorFilter(color)
        iconManager?.setTint(color)

        // Real One UI has no solid bar fill - icons/text float over the
        // wallpaper or app content. Transparent is the default; solid black
        // is offered as a fallback for readability over busy backgrounds.
        view.setBackgroundColor(
            if (prefs.transparentBackground) Color.TRANSPARENT else resources.getColor(R.color.bar_black, theme)
        )

        val batteryPct = view.findViewById<TextView>(R.id.tvBatteryPct)
        val batteryIcon = view.findViewById<ImageView>(R.id.ivBatteryIcon)
        when (prefs.batteryStyleIndex) {
            1 -> batteryIcon?.visibility = View.GONE
            2 -> batteryPct?.visibility = View.GONE
            else -> Unit
        }

        view.findViewById<TextView>(R.id.tvCarrierName)?.visibility =
            if (prefs.showCarrierName) View.VISIBLE else View.GONE
    }

    // ---------- Carrier name + network type ----------

    private fun updateCarrierAndNetworkType() {
        val view = overlayView ?: return
        val name = prefs.carrierNameOverride.ifBlank { NetworkTypeHelper.realCarrierName(this) }
        view.findViewById<TextView>(R.id.tvCarrierName)?.text = name

        val networkLabel = if (prefs.networkTypeMode == "auto") {
            NetworkTypeHelper.currentGeneration(this) ?: prefs.manualNetworkType
        } else {
            prefs.manualNetworkType
        }
        view.findViewById<TextView>(R.id.tvNetworkType)?.text = networkLabel
    }

    // ---------- Fake notification icons (real installed-app icons, max 5) ----------

    private fun loadFakeNotificationIcons() {
        val row = overlayView?.findViewById<LinearLayout>(R.id.notificationIconRow) ?: return
        row.removeAllViews()

        val pm = packageManager
        for (pkg in prefs.selectedNotificationApps.take(PrefsManager.MAX_FAKE_ICONS)) {
            val icon = try {
                pm.getApplicationIcon(pkg)
            } catch (_: Exception) {
                continue
            }
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply { marginEnd = 8 }
                setImageDrawable(icon)
            }
            row.addView(iv)
        }
    }

    // ---------- Clock (per-minute unless the format includes seconds - saves battery) ----------

    private fun startClockTicker() {
        val tickMillis = if (prefs.clockFormat.contains("s")) 1000L else 60_000L
        clockRunnable = object : Runnable {
            override fun run() {
                updateClock()
                clockHandler.postDelayed(this, tickMillis)
            }
        }
        clockHandler.post(clockRunnable)
    }

    private fun updateClock() {
        val format = SimpleDateFormat(prefs.clockFormat, Locale.getDefault())
        overlayView?.findViewById<TextView>(R.id.tvClock)?.text = format.format(Date())
    }

    // ---------- Battery ----------

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val pct = (level * 100) / scale
        overlayView?.findViewById<TextView>(R.id.tvBatteryPct)?.text = "$pct%"
    }

    // ---------- Auto-hide in fullscreen / immersive apps ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!prefs.autoHideEnabled) {
            setOverlayVisible(true)
            return
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            setOverlayVisible(!isForegroundAppFullscreen())
        }
    }

    /**
     * Heuristic: if the topmost application window's bounds start at
     * y = 0 (drawing into the area the status bar would normally occupy)
     * and cover the full screen height, treat the app as fullscreen/
     * immersive and hide our overlay - exactly when the real system
     * status bar would also hide.
     */
    private fun isForegroundAppFullscreen(): Boolean {
        val windowList = windows ?: return false
        val displayHeight = resources.displayMetrics.heightPixels
        val statusBarHeight = currentBarHeightPx()

        for (w in windowList) {
            if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (!w.isFocused && !w.isActive) continue
            val bounds = android.graphics.Rect()
            w.getBoundsInScreen(bounds)
            val coversStatusBarArea = bounds.top <= 0
            val coversFullHeight = bounds.height() >= displayHeight - statusBarHeight / 2
            return coversStatusBarArea && coversFullHeight
        }
        return false
    }

    private fun setOverlayVisible(visible: Boolean) {
        overlayView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        unregisterReceiver(batteryReceiver)
        stateWatcher.stop()
        prefs.raw.unregisterOnSharedPreferenceChangeListener(prefsListener)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
                // Already removed.
            }
        }
    }
}
