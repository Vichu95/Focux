package com.focux.pulse.utilities

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.focux.pulse.R
import java.util.ArrayList

/**
 * Helper to fetch app names and icons from PackageManager.
 * Uses package name to retrieve real app info from the device.
 */
object AppInfoHelper {

    /**
     * Returns the human-readable app name (e.g., "Instagram")
     * Falls back to package name if app not found.
     */
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback to last part of package name
            packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
        }
    }

    /**
     * Returns the App Icon as a Drawable.
     * Returns a default icon if the app is not found.
     */
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            val pm = context.packageManager
            pm.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // Return default launcher icon
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        }
    }

    /**
     * Resolves ALL packages that claim to be a Home application (Launchers).
     * Filters out system fallback (Settings) to return only actual launchers.
     */
    fun getLauncherPackages(context: Context): List<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }

        // Match all activities that have this category
        val list = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        
        return list
            .map { it.activityInfo.packageName }
            .filter { packageName ->
                packageName != "com.android.settings" && 
                packageName != "com.android.tv.settings"
            }
            .distinct()
    }
}
