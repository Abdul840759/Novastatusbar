package com.abdulazeez.statusclone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * Reads real installed apps (and their real, current icons) via
 * PackageManager. No trademarked assets are bundled by us - we're just
 * referencing what's already installed and licensed on the user's own
 * device, the same way a launcher does.
 */
object InstalledAppsRepository {

    fun getLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        return resolved
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { ri ->
                try {
                    AppEntry(
                        packageName = ri.activityInfo.packageName,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }
}
