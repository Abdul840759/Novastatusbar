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
import android.graphics.Rect
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusBarAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private lateinit var stateWatcher: SystemStateWatcher
    private var iconManager: StatusIconManager? = null
    private var lastState: SystemState = SystemState()
    private lateinit var colorSampler: BackgroundColorSampler
    private var currentBarColor: Int = Color.BLACK

    /** Manual hide from the shade/quick-settings-open check - always wins regardless of the auto-hide setting. */
    private var shadeForcedHidden = false

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

        serviceInfo = serviceInfo.also {
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        addOverlay()
        startClockTicker()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        colorSampler = BackgroundColorSampler(this)

        stateWatcher = SystemStateWatcher(this) { state ->
            lastState = state
            if (prefs.showSystemStateIcons) iconManager?.render(state)
            applyLockState(state.isLocked)
            applyChargingIcon(state.isCharging)
        }
        stateWatcher.start()

        loadFakeNotificationIcons()
        updateCarrierAndNetworkType()
        requestColorSample()

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
            if (key == "fake_transparency_enabled" || key == "fallback_bar_color") {
                requestColorSample()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        windowManager.addView(view, params)
        overlayView = view
        layoutParams = params

        iconManager = StatusIconManager(this, view.findViewById(R.id.systemIconRow))
        applyPrefsToView()
        applyCutoutSafeMargins()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.height = currentBarHeightPx()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            // View was already detached - ignore.
        }
        applyCutoutSafeMargins()
    }

    /**
     * Uses the REAL display cutout safe insets instead of a hardcoded margin,
     * so icons never overlap the notch regardless of device/cutout shape.
     */
    private fun applyCutoutSafeMargins() {
        val view = overlayView ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        view.post {
            val insets = view.rootWindowInsets ?: return@post
            val cutout = insets.displayCutout ?: return@post
            val minMargin = dpToPx(16)
            val left = maxOf(minMargin, cutout.safeInsetLeft)
            val right = maxOf(minMargin, cutout.safeInsetRight)

            view.findViewById<View>(R.id.leftZone)?.let {
                val lp = it.layoutParams as android.widget.RelativeLayout.LayoutParams
                lp.marginStart = left
                it.layoutParams = lp
            }
            view.findViewById<View>(R.id.rightZone)?.let {
                val lp = it.layoutParams as android.widget.RelativeLayout.LayoutParams
                lp.marginEnd = right
                it.layoutParams = lp
            }
        }
    }

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

    // ---------- Lock-state swap: LEFT zone only ever shows one group ----------

    private fun applyLockState(isLocked: Boolean) {
        val view = overlayView ?: return
        view.findViewById<TextView>(R.id.tvCarrierName)?.visibility =
            if (isLocked && prefs.showCarrierName) View.VISIBLE else View.GONE
        view.findViewById<LinearLayout>(R.id.unlockedLeftGroup)?.visibility =
            if (isLocked) View.GONE else View.VISIBLE
    }

    // ---------- Styling from prefs ----------

    private fun applyPrefsToView() {
        val view = overlayView ?: return
        val color = prefs.iconColor

        view.findViewById<TextView>(R.id.tvClock)?.setTextColor(color)
        view.findViewById<TextView>(R.id.tvBatteryPct)?.setTextColor(color)
        view.findViewById<TextView>(R.id.tvCarrierName)?.setTextColor(color)
        view.findViewById<TextView>(R.id.tvNetworkType)?.setTextColor(color)
        view.findViewById<BatteryIconView>(R.id.batteryIconView)?.iconColor = color
        view.findViewById<ImageView>(R.id.ivSignal)?.setColorFilter(color)
        iconManager?.setTint(color)

        // "Fake transparency": the bar is ALWAYS opaque - it never lets the
        // real system UI show through. When sampling is unavailable/fails,
        // we use a fixed fallback color instead of ever going transparent.
        if (!prefs.fakeTransparencyEnabled) {
            animateToColor(prefs.fallbackBarColor)
        }

        val batteryPct = view.findViewById<TextView>(R.id.tvBatteryPct)
        val batteryIconView = view.findViewById<BatteryIconView>(R.id.batteryIconView)
        when (prefs.batteryStyleIndex) {
            1 -> batteryIconView?.visibility = View.GONE
            2 -> batteryPct?.visibility = View.GONE
            else -> Unit
        }

        applyLockState(lastState.isLocked)
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

    // ---------- Fake notification icons ----------

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

    // ---------- Clock ----------

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

    // ---------- Fake transparency: opaque bar smoothly matched to sampled color ----------

    private fun animateToColor(target: Int) {
        val view = overlayView ?: return
        if (target == currentBarColor) return
        val animator = android.animation.ValueAnimator.ofObject(
            android.animation.ArgbEvaluator(), currentBarColor, target
        )
        animator.duration = 350
        animator.addUpdateListener { view.setBackgroundColor(it.animatedValue as Int) }
        animator.start()
        currentBarColor = target
    }

    private fun requestColorSample() {
        if (!prefs.fakeTransparencyEnabled) return
        colorSampler.sample(currentBarHeightPx()) { result ->
            when (result) {
                is SampleResult.Success -> animateToColor(result.color)
                is SampleResult.Failed -> animateToColor(prefs.fallbackBarColor)
                is SampleResult.Throttled -> Unit // keep current color, don't flicker
            }
        }
    }

    // ---------- Battery (real charging state, not a static icon) ----------

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val pct = (level * 100) / scale
        overlayView?.findViewById<TextView>(R.id.tvBatteryPct)?.text = "$pct%"
        overlayView?.findViewById<BatteryIconView>(R.id.batteryIconView)?.levelPercent = pct
    }

    private fun applyChargingIcon(isCharging: Boolean) {
        overlayView?.findViewById<BatteryIconView>(R.id.batteryIconView)?.isCharging = isCharging
    }

    // ---------- Auto-hide: fullscreen apps, real status bar hidden, or shade/quick-settings open ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Shade/quick-settings open: always hide, independent of the auto-hide toggle.
        shadeForcedHidden = isShadeOrQuickSettingsOpen()

        if (shadeForcedHidden) {
            setOverlayVisible(false)
            return
        }

        if (!prefs.autoHideEnabled) {
            setOverlayVisible(true)
            return
        }

        val realBarHidden = isRealStatusBarWindowAbsent()
        val fullscreenApp = isForegroundAppFullscreen()
        val shouldShow = !(realBarHidden || fullscreenApp)
        setOverlayVisible(shouldShow)
        if (shouldShow) requestColorSample()
    }

    /**
     * Most reliable auto-hide signal: check whether the real system status-bar
     * window itself is still present in the window list, instead of guessing
     * from the foreground app's bounds. If it's gone, the real bar is hidden
     * (immersive mode) and ours should hide too.
     */
    private fun isRealStatusBarWindowAbsent(): Boolean {
        val windowList = windows ?: return false
        val statusBarHeight = currentBarHeightPx()
        val systemWindows = windowList.filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
        // If this device/OEM never reports any TYPE_SYSTEM window at all, we
        // can't determine anything from this signal - fail safe by assuming
        // the real bar IS visible, rather than hiding ours permanently.
        if (systemWindows.isEmpty()) return false
        val hasStatusBarWindow = systemWindows.any { w ->
            val b = Rect()
            w.getBoundsInScreen(b)
            b.top <= 0 && b.height() in 1..(statusBarHeight * 2)
        }
        return !hasStatusBarWindow
    }

    /**
     * Heuristic: a top-anchored window taller than ~3x the status bar but not
     * full screen is very likely the notification shade or quick settings
     * panel expanded down. Best-effort - some OEM dialogs could false-positive.
     */
    private val systemUiPackageHints = listOf("systemui", "phonemanager", "notificationpanel", "statusbar")

    private fun isShadeOrQuickSettingsOpen(): Boolean {
        if (!prefs.hideOnShadeOpen) return false
        val windowList = windows ?: return false
        val statusBarHeight = currentBarHeightPx()
        for (w in windowList) {
            val pkg = try {
                w.root?.packageName?.toString()?.lowercase()
            } catch (_: Exception) {
                null
            } ?: continue
            if (systemUiPackageHints.none { pkg.contains(it) }) continue
            val b = Rect()
            w.getBoundsInScreen(b)
            // The thin status bar strip itself is also a systemui window - only
            // treat it as the shade/quick-settings panel when it's clearly
            // taller than just that strip.
            if (b.top <= 0 && b.height() > statusBarHeight * 3) return true
        }
        return false
    }

    private fun isForegroundAppFullscreen(): Boolean {
        val windowList = windows ?: return false
        val displayHeight = resources.displayMetrics.heightPixels
        val statusBarHeight = currentBarHeightPx()

        for (w in windowList) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (!w.isFocused && !w.isActive) continue
            val bounds = Rect()
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
