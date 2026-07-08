package com.abdulazeez.statusclone

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotificationIconPickerActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var adapter: AppPickerAdapter
    private var currentSelection: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_picker)

        prefs = PrefsManager(this)
        val tvCount = findViewById<TextView>(R.id.tvSelectedCount)

        val apps = InstalledAppsRepository.getLaunchableApps(this)
        currentSelection = prefs.selectedNotificationApps.toSet()

        adapter = AppPickerAdapter(apps, currentSelection) { selected ->
            currentSelection = selected
            tvCount.text = "${selected.size} / ${PrefsManager.MAX_FAKE_ICONS} selected"
        }
        tvCount.text = "${currentSelection.size} / ${PrefsManager.MAX_FAKE_ICONS} selected"

        findViewById<RecyclerView>(R.id.recyclerApps).apply {
            layoutManager = LinearLayoutManager(this@NotificationIconPickerActivity)
            adapter = this@NotificationIconPickerActivity.adapter
        }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.btnSaveSelection).setOnClickListener {
            prefs.selectedNotificationApps = currentSelection.toList()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Persist even if the user just navigates back without tapping Save.
        prefs.selectedNotificationApps = currentSelection.toList()
    }
}
