package com.abdulazeez.statusclone

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppPickerAdapter(
    private val allApps: List<AppEntry>,
    initiallySelected: Set<String>,
    private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<AppPickerAdapter.VH>() {

    private var filtered: List<AppEntry> = allApps
    private val selected = initiallySelected.toMutableSet()

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val label: TextView = view.findViewById(R.id.tvAppLabel)
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_picker, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = filtered[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selected.contains(app.packageName)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (selected.size >= PrefsManager.MAX_FAKE_ICONS) {
                    holder.checkbox.isChecked = false
                    return@setOnCheckedChangeListener
                }
                selected.add(app.packageName)
            } else {
                selected.remove(app.packageName)
            }
            onSelectionChanged(selected)
        }
    }

    override fun getItemCount() = filtered.size

    fun filter(query: String) {
        filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    fun selectedCount() = selected.size
}
