package com.abdulazeez.statusclone

import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager

/**
 * Snapshot of every toggle-able system state we can detect without root.
 * "Hotspot" uses an undocumented-but-widely-relied-on broadcast action,
 * since there's no public API for it - flagged in the README as the
 * most likely thing to need per-device tuning.
 */
data class SystemState(
    val mobileDataOn: Boolean = false,
    val bluetoothOn: Boolean = false,
    val hotspotOn: Boolean = false,
    val airplaneModeOn: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val onCall: Boolean = false,
    val dndOn: Boolean = false,
    val powerSaveOn: Boolean = false,
    val nfcOn: Boolean = false,
    val locationOn: Boolean = false,
    val vpnOn: Boolean = false,
    val alarmSet: Boolean = false
)

class SystemStateWatcher(
    private val context: Context,
    private val onStateChanged: (SystemState) -> Unit
) {
    private var state = SystemState()
    private var registered = false
    private var lastKnownHotspotState = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
                // extra name used across AOSP/most OEMs: "wifi_state"; value 13 == WIFI_AP_STATE_ENABLED
                val wifiApState = intent.getIntExtra("wifi_state", -1)
                lastKnownHotspotState = wifiApState == 13
            }
            refresh()
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED") // undocumented hotspot broadcast
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        registered = true
        refresh()
    }

    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
        registered = false
    }

    /** Re-reads everything from the relevant system services and reports if anything changed. */
    fun refresh() {
        val newState = SystemState(
            mobileDataOn = isMobileDataOn(),
            bluetoothOn = isBluetoothOn(),
            hotspotOn = lastKnownHotspotState,
            airplaneModeOn = isAirplaneModeOn(),
            ringerMode = ringerMode(),
            onCall = isOnCall(),
            dndOn = isDndOn(),
            powerSaveOn = isPowerSaveOn(),
            nfcOn = isNfcOn(),
            locationOn = isLocationOn(),
            vpnOn = isVpnOn(),
            alarmSet = isAlarmSet()
        )
        if (newState != state) {
            state = newState
            onStateChanged(newState)
        }
    }

    // ---------- individual checks ----------

    private fun isMobileDataOn(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun isBluetoothOn(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    } catch (_: SecurityException) {
        // Needs BLUETOOTH_CONNECT on API 31+; caller should have requested it.
        false
    }

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun ringerMode(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioManager.RINGER_MODE_NORMAL
        return am.ringerMode
    }

    private fun isOnCall(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        return try {
            @Suppress("DEPRECATION")
            tm.callState != TelephonyManager.CALL_STATE_IDLE
        } catch (_: SecurityException) {
            false
        }
    }

    private fun isDndOn(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun isPowerSaveOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isPowerSaveMode
    }

    private fun isNfcOn(): Boolean = try {
        NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    private fun isLocationOn(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    private fun isVpnOn(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun isAlarmSet(): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.nextAlarmClock != null
    }
}
