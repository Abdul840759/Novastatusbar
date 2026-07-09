package com.abdulazeez.statusclone

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abdulazeez.statusclone.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private val clockFormats = arrayOf("HH:mm", "hh:mm a", "HH:mm:ss")
    private val networkTypes = arrayOf("1G", "2G", "3G", "4G", "5G")
    private val batteryStyles = arrayOf("Percentage + Icon", "Percentage Only", "Icon Only", "Circle")
    private val swatchColors = intArrayOf(
        Color.WHITE,
        Color.parseColor("#1A73E8"),
        Color.parseColor("#34C759"),
        Color.parseColor("#8E5AF7"),
        Color.parseColor("#FF9500")
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op - we just re-check state where needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        requestRuntimePermissions()

        setupEnableSwitch()
        setupPermissionButtons()
        setupTransparencySwitch()
        setupAutoHideSwitch()
        setupClockFormatSpinner()
        setupBatteryStyleSpinner()
        setupColorSwatches()
        setupCarrierName()
        setupNetworkType()
        setupSystemIconsSwitch()
        setupNotificationIconPicker()
        setupBatteryAndAutostartButtons()
    }

    override fun onResume() {
        super.onResume()
        binding.switchEnable.isChecked = isAccessibilityServiceEnabled()
    }

    // ---------- Runtime permissions ----------

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.READ_PHONE_STATE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    // ---------- Enable switch / permissions ----------

    private fun setupEnableSwitch() {
        binding.switchEnable.isChecked = isAccessibilityServiceEnabled()
        binding.switchEnable.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                binding.switchEnable.isChecked = false
                openAccessibilitySettings()
            }
        }
    }

    private fun setupPermissionButtons() {
        binding.btnOverlayPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        }
        binding.btnAccessibilityPermission.setOnClickListener { openAccessibilitySettings() }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    // ---------- Simple toggles ----------

    private fun setupTransparencySwitch() {
        binding.switchTransparent.isChecked = prefs.fakeTransparencyEnabled
        binding.switchTransparent.setOnCheckedChangeListener { _, isChecked ->
            prefs.fakeTransparencyEnabled = isChecked
        }
        setupFallbackColorSwatches()
    }

    private fun setupFallbackColorSwatches() {
        binding.fallbackColorSwatchRow.removeAllViews()
        val fallbackColors = intArrayOf(
            Color.BLACK,
            Color.parseColor("#1A1A1A"),
            Color.WHITE,
            Color.parseColor("#1428A0"),
            Color.parseColor("#34C759")
        )
        for (color in fallbackColors) {
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 24 }
                setBackgroundColor(color)
                setOnClickListener { prefs.fallbackBarColor = color }
            }
            binding.fallbackColorSwatchRow.addView(swatch)
        }
    }

    private fun setupAutoHideSwitch() {
        binding.switchAutoHide.isChecked = prefs.autoHideEnabled
        binding.switchAutoHide.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoHideEnabled = isChecked
        }
        binding.switchHideOnShade.isChecked = prefs.hideOnShadeOpen
        binding.switchHideOnShade.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideOnShadeOpen = isChecked
        }
    }

    private fun setupSystemIconsSwitch() {
        binding.switchSystemIcons.isChecked = prefs.showSystemStateIcons
        binding.switchSystemIcons.setOnCheckedChangeListener { _, isChecked ->
            prefs.showSystemStateIcons = isChecked
        }
    }

    // ---------- Clock / battery style spinners ----------

    private fun setupClockFormatSpinner() {
        binding.spinnerClockFormat.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, clockFormats)
        binding.spinnerClockFormat.setSelection(clockFormats.indexOf(prefs.clockFormat).coerceAtLeast(0))
        binding.spinnerClockFormat.post {
            binding.spinnerClockFormat.onItemSelectedListener = simpleSelectListener { pos ->
                prefs.clockFormat = clockFormats[pos]
            }
        }
    }

    private fun setupBatteryStyleSpinner() {
        binding.spinnerBatteryStyle.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, batteryStyles)
        binding.spinnerBatteryStyle.setSelection(prefs.batteryStyleIndex)
        binding.spinnerBatteryStyle.post {
            binding.spinnerBatteryStyle.onItemSelectedListener = simpleSelectListener { pos ->
                prefs.batteryStyleIndex = pos
            }
        }
    }

    private fun setupColorSwatches() {
        binding.colorSwatchRow.removeAllViews()
        for (color in swatchColors) {
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 24 }
                setBackgroundColor(color)
                setOnClickListener { prefs.iconColor = color }
            }
            binding.colorSwatchRow.addView(swatch)
        }
    }

    // ---------- Carrier name ----------

    private fun setupCarrierName() {
        binding.etCarrierName.setText(prefs.carrierNameOverride)
        binding.etCarrierName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) prefs.carrierNameOverride = binding.etCarrierName.text.toString()
        }
        binding.switchShowCarrier.isChecked = prefs.showCarrierName
        binding.switchShowCarrier.setOnCheckedChangeListener { _, isChecked ->
            prefs.showCarrierName = isChecked
        }
    }

    // ---------- Network type ----------

    private fun setupNetworkType() {
        binding.switchNetworkAuto.isChecked = prefs.networkTypeMode == "auto"
        binding.switchNetworkAuto.setOnCheckedChangeListener { _, isChecked ->
            prefs.networkTypeMode = if (isChecked) "auto" else "manual"
            if (isChecked && !NetworkTypeHelper.hasPermission(this)) {
                Toast.makeText(
                    this,
                    "Grant Phone permission for auto-detect to work - falling back to manual for now",
                    Toast.LENGTH_LONG
                ).show()
                requestRuntimePermissions()
            }
        }

        binding.spinnerManualNetwork.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, networkTypes)
        binding.spinnerManualNetwork.setSelection(
            networkTypes.indexOf(prefs.manualNetworkType).coerceAtLeast(3)
        )
        binding.spinnerManualNetwork.post {
            binding.spinnerManualNetwork.onItemSelectedListener = simpleSelectListener { pos ->
                prefs.manualNetworkType = networkTypes[pos]
            }
        }
    }

    // ---------- Notification icon picker ----------

    private fun setupNotificationIconPicker() {
        binding.btnPickNotificationIcons.setOnClickListener {
            startActivity(Intent(this, NotificationIconPickerActivity::class.java))
        }
    }

    // ---------- Battery optimization + XOS autostart ----------

    private fun setupBatteryAndAutostartButtons() {
        binding.btnIgnoreBatteryOptimization.setOnClickListener {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            } else {
                Toast.makeText(this, "Already exempted from battery optimization", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAutostartSettings.setOnClickListener {
            openXosAutostartSettings()
        }
    }

    /**
     * XOS (Infinix's skin) hides its autostart/"protected apps" manager behind
     * an OEM-specific activity that isn't documented publicly and can vary by
     * XOS version. We try the known component names for Infinix/Transsion
     * devices first, then fall back to the plain App Info screen where the
     * user can find battery/autostart settings manually.
     */
    private fun openXosAutostartSettings() {
        val candidates = listOf(
            ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.autostart.AutoStartActivity"),
            ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.ui.MainActivity"),
            ComponentName("com.transsion.batterymanager", "com.transsion.batterymanager.ui.BatteryMainActivity")
        )
        for (component in candidates) {
            try {
                startActivity(Intent().apply { setComponent(component) })
                return
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
        Toast.makeText(
            this,
            "Couldn't find XOS's autostart screen directly - opening App Info instead. " +
                "Look for 'Autostart' or 'Battery' there.",
            Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun simpleSelectListener(onSelected: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) = onSelected(position)

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
}
