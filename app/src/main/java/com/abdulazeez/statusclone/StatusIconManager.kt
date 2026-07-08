package com.abdulazeez.statusclone

import android.content.Context
import android.media.AudioManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Adds/removes small icons into a row, one per active system state -
 * mirrors how the real status bar only shows an icon while that feature
 * is actually on. Caps the number shown at once (real One UI does this
 * too) so a lot of things being on at once doesn't crowd/overflow the bar -
 * lowest-priority icons are simply dropped rather than wrapping.
 */
class StatusIconManager(
    private val context: Context,
    private val row: LinearLayout,
    private val maxVisible: Int = 4
) {
    private val views = mutableMapOf<String, ImageView>()
    private var tint: Int = 0xFFFFFFFF.toInt()

    fun setTint(color: Int) {
        tint = color
        views.values.forEach { it.setColorFilter(color) }
    }

    fun render(state: SystemState) {
        // Highest priority first - matches real One UI ordering intent
        // (call/DND/alarm are things you'd want to notice immediately).
        val candidates = listOf(
            Triple("call", state.onCall, R.drawable.ic_status_call),
            Triple("dnd", state.dndOn, R.drawable.ic_status_dnd),
            Triple("alarm", state.alarmSet, R.drawable.ic_status_alarm),
            Triple(
                "ringer",
                state.ringerMode != AudioManager.RINGER_MODE_NORMAL,
                if (state.ringerMode == AudioManager.RINGER_MODE_SILENT)
                    R.drawable.ic_status_ringer_silent else R.drawable.ic_status_ringer_vibrate
            ),
            Triple(
                "bluetooth",
                state.bluetoothOn,
                if (state.bluetoothConnected) R.drawable.ic_status_bluetooth_connected
                else R.drawable.ic_status_bluetooth
            ),
            Triple("hotspot", state.hotspotOn, R.drawable.ic_status_hotspot),
            Triple("vpn", state.vpnOn, R.drawable.ic_status_vpn),
            Triple("airplane", state.airplaneModeOn, R.drawable.ic_status_airplane),
            Triple("data", state.mobileDataOn, R.drawable.ic_status_mobile_data),
            Triple("powersave", state.powerSaveOn, R.drawable.ic_status_powersave),
            Triple("nfc", state.nfcOn, R.drawable.ic_status_nfc),
            Triple("location", state.locationOn, R.drawable.ic_status_location)
        )

        val active = candidates.filter { it.second }
        val shown = active.take(maxVisible).map { it.first }.toSet()

        for ((key, isActive, iconRes) in candidates) {
            set(key, isActive && key in shown, iconRes)
        }
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
