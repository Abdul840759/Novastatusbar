package com.abdulazeez.statusclone

import android.content.Context
import android.graphics.Color

/**
 * Thin wrapper around SharedPreferences so the settings screen, the
 * overlay service, and the icon picker all read/write the same values.
 */
class PrefsManager(context: Context) {

    val raw = context.getSharedPreferences("nova_status_prefs", Context.MODE_PRIVATE)
    private val prefs = raw

    var clockFormat: String
        get() = prefs.getString(KEY_CLOCK_FORMAT, "HH:mm") ?: "HH:mm"
        set(value) = prefs.edit().putString(KEY_CLOCK_FORMAT, value).apply()

    var batteryStyleIndex: Int
        get() = prefs.getInt(KEY_BATTERY_STYLE, 0)
        set(value) = prefs.edit().putInt(KEY_BATTERY_STYLE, value).apply()

    var iconColor: Int
        get() = prefs.getInt(KEY_ICON_COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(KEY_ICON_COLOR, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    /** Real One UI has no solid fill behind the icons - default to true. */
    var transparentBackground: Boolean
        get() = prefs.getBoolean(KEY_TRANSPARENT_BG, true)
        set(value) = prefs.edit().putBoolean(KEY_TRANSPARENT_BG, value).apply()

    /** Hide the overlay in fullscreen/immersive apps, like the real status bar. */
    var autoHideEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HIDE, value).apply()

    /** Empty string = use the real carrier name reported by TelephonyManager. */
    var carrierNameOverride: String
        get() = prefs.getString(KEY_CARRIER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARRIER_NAME, value).apply()

    var showCarrierName: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CARRIER, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CARRIER, value).apply()

    /** "auto" = read real network type; "manual" = always show manualNetworkType. */
    var networkTypeMode: String
        get() = prefs.getString(KEY_NETWORK_MODE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_NETWORK_MODE, value).apply()

    var manualNetworkType: String
        get() = prefs.getString(KEY_MANUAL_NETWORK, "4G") ?: "4G"
        set(value) = prefs.edit().putString(KEY_MANUAL_NETWORK, value).apply()

    var showSystemStateIcons: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SYSTEM_ICONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SYSTEM_ICONS, value).apply()

    /** Up to 5 package names, comma-separated, chosen from the icon picker. */
    var selectedNotificationApps: List<String>
        get() = prefs.getString(KEY_FAKE_APPS, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_FAKE_APPS, value.take(MAX_FAKE_ICONS).joinToString(","))
            .apply()

    companion object {
        const val MAX_FAKE_ICONS = 5

        private const val KEY_CLOCK_FORMAT = "clock_format"
        private const val KEY_BATTERY_STYLE = "battery_style"
        private const val KEY_ICON_COLOR = "icon_color"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_TRANSPARENT_BG = "transparent_background"
        private const val KEY_AUTO_HIDE = "auto_hide_enabled"
        private const val KEY_CARRIER_NAME = "carrier_name_override"
        private const val KEY_SHOW_CARRIER = "show_carrier_name"
        private const val KEY_NETWORK_MODE = "network_type_mode"
        private const val KEY_MANUAL_NETWORK = "manual_network_type"
        private const val KEY_SHOW_SYSTEM_ICONS = "show_system_state_icons"
        private const val KEY_FAKE_APPS = "fake_notification_apps"
    }
}
