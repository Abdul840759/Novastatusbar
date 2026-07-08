package com.abdulazeez.statusclone

import android.content.Context
import android.media.AudioManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Adds/removes small icons into a row, one per active system state -
 * mirrors how the real status bar only shows an icon while that feature
 * is actually on (Bluetooth, hotspot, DND, etc.) instead of always
 * reserving space for everything.
 */
class StatusIconManager(private val context: Context, private val row: LinearLayout) {

    private val views = mutableMapOf<String, ImageView>()
    private var tint: Int = 0xFFFFFFFF.toInt()

    fun setTint(color: Int) {
        tint = color
        views.values.forEach { it.setColorFilter(color) }
    }

    fun render(state: SystemState) {
        // Order roughly matches real One UI left-to-right priority.
        set("alarm", state.alarmSet, R.drawable.ic_status_alarm)
        set("location", state.locationOn, R.drawable.ic_status_location)
        set("nfc", state.nfcOn, R.drawable.ic_status_nfc)
        set("vpn", state.vpnOn, R.drawable.ic_status_vpn)
        set("powersave", state.powerSaveOn, R.drawable.ic_status_powersave)
        set("dnd", state.dndOn, R.drawable.ic_status_dnd)
        set("call", state.onCall, R.drawable.ic_status_call)
        set(
            "ringer",
            state.ringerMode != AudioManager.RINGER_MODE_NORMAL,
            if (state.ringerMode == AudioManager.RINGER_MODE_SILENT)
                R.drawable.ic_status_ringer_silent else R.drawable.ic_status_ringer_vibrate
        )
        set("hotspot", state.hotspotOn, R.drawable.ic_status_hotspot)
        set("bluetooth", state.bluetoothOn, R.drawable.ic_status_bluetooth)
        set("data", state.mobileDataOn, R.drawable.ic_status_mobile_data)
        set("airplane", state.airplaneModeOn, R.drawable.ic_status_airplane)
    }

    private fun set(key: String, visible: Boolean, iconRes: Int) {
        if (!visible) {
            views[key]?.let { row.removeView(it); views.remove(key) }
            return
        }
        val existing = views[key]
        if (existing != null) {
            existing.setImageResource(iconRes)
            return
        }
        val iv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(44, 44).apply { marginStart = 10 }
            setImageResource(iconRes)
            setColorFilter(tint)
            visibility = View.VISIBLE
        }
        views[key] = iv
        row.addView(iv)
    }
}
