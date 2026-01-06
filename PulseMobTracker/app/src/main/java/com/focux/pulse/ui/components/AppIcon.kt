package com.focux.pulse.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.focux.pulse.R
import com.focux.pulse.ui.theme.PulseAppIconSizeMedium
import com.focux.pulse.utils.AppInfoHelper

/**
 * Displays an app icon from the package manager.
 * Falls back to a default icon if the package is not found.
 * 
 * @param packageName The package name to fetch icon for
 * @param size The size of the icon
 * @param contentDescription Accessibility description
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = PulseAppIconSizeMedium,
    contentDescription: String = "App icon"
) {
    val context = LocalContext.current
    
    // Handle empty or invalid package names
    if (packageName.isBlank() || packageName == "app_icon" || packageName == "unknown") {
        Image(
            painter = painterResource(id = R.drawable.temp_icon),
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
        return
    }
    
    // Remember the bitmap to avoid re-fetching on recomposition
    val bitmap = remember(packageName) {
        try {
            val drawable = AppInfoHelper.getAppIcon(context, packageName)
            drawable?.toBitmap()
        } catch (e: Exception) {
            null
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    } else {
        // Fallback to default icon
        Image(
            painter = painterResource(id = R.drawable.temp_icon),
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    }
}
