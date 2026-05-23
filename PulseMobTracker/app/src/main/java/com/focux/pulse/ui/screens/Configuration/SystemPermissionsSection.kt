package com.focux.pulse.ui.screens.Configuration

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focux.pulse.ui.theme.*

@Composable
fun SystemPermissionsSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // We need to re-check permissions when the user returns to the app
    // from the Settings screen.
    var hasUsageAccess by remember { mutableStateOf(checkUsageAccess(context)) }
    var hasAccessibilityAccess by remember { mutableStateOf(checkAccessibilityAccess(context)) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = checkUsageAccess(context)
                hasAccessibilityAccess = checkAccessibilityAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
            .background(PulseAppColorSurface)
            .padding(PulseAppPaddingMedium)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "System Permissions",
                style = PulseAppFontSubHeader,
                color = PulseAppColorPrimary
            )
            
            Text(
                text = "Required permissions for Pulse to operate correctly.",
                style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall),
                color = PulseAppColorSecondary
            )
            
            // Usage Access Row
            PermissionRow(
                title = "Usage Access",
                description = "Required to track app usage time and build your focus score.",
                isGranted = hasUsageAccess,
                onGrantClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
            
            // Accessibility Access Row
            PermissionRow(
                title = "Accessibility Access",
                description = "Required to instantly intercept distracting apps and show the mindful Breathing Screen.",
                isGranted = hasAccessibilityAccess,
                onGrantClick = {
                    if (!hasAccessibilityAccess) {
                        showAccessibilityDialog = true
                    } else {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            )
        }
    }

    if (showAccessibilityDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* Enforce explicit choice */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text("Accessibility Service Consent", style = PulseAppFontSubHeader)
            },
            text = {
                Text(
                    text = "Pulse requests the Accessibility Service API permission strictly to monitor when distracting apps are opened and display a mindful breathing screen to help you pause and refocus.\n\nPulse does NOT view, collect, or transmit any screen content, personal information, or active data. All processing occurs 100% locally and offline on your device.",
                    style = PulseAppFontBody
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDialog = false
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ) {
                    Text("Accept & Enable")
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showAccessibilityDialog = false }
                ) {
                    Text("Decline")
                }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = PulseAppFontSubHeader,
                color = PulseAppColorSecondary
            )
            
            Text(
                text = if (isGranted) "Granted" else "Missing",
                style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall),
                color = if (isGranted) PulseAppColorPrimary else PulseAppColorDistracting,
                modifier = Modifier
                    .background(
                        color = if (isGranted) PulseAppColorPrimary.copy(alpha = 0.1f) else PulseAppColorDistracting.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        
        Text(
            text = description,
            style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall),
            color = PulseAppColorSecondary
        )
        
        if (!isGranted) {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PulseAppColorPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Grant Permission",
                    color = PulseAppColorBackground
                )
            }
        } else {
             Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PulseAppColorSecondary.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Manage in Settings",
                    color = PulseAppColorPrimary
                )
            }
        }
    }
}

// Helper functions (Consider moving these to a dedicated PermissionsHelper if used elsewhere)
fun checkUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun checkAccessibilityAccess(context: Context): Boolean {
    var accessibilityEnabled = 0
    val accessibilityFound = false
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) {
        // Ignore
    }
    
    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            val componentName = "${context.packageName}/${context.packageName}.service.AppInterceptorService" 
            // Note: We'll need to create this service class!. Using a generic name for now.
            return settingValue.contains(context.packageName) 
            // Simplified check: if any service in our package is enabled. 
            // A perfect check would check the exact ComponentName.
        }
    }
    return false
}
