package com.focux.pulse.ui.screens.Configuration

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
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.SystemState
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ConfigurationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PulseAppPaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Configuration",
            style = PulseAppFontHeader,
            color = PulseAppColorPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Database Reset Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
                .background(PulseAppColorSurface)
                .padding(PulseAppPaddingMedium)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Database Management",
                    style = PulseAppFontSubHeader,
                    color = PulseAppColorPrimary
                )
                
                Text(
                    text = "Clear all raw data and reset processing indexes. Use this if you encounter data issues after reinstalling.",
                    style = PulseAppFontBody,
                    color = PulseAppColorSecondary
                )
                
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val db = PulseDatabase.getDatabase(context)
                                
                                // Clear all tables
                                db.rawDataDao().deleteAll()
                                db.analyticsDao().deleteAllSessions()
                                db.analyticsDao().deleteAllDailyStats()
                                
                                // Reset processing indexes
                                db.analyticsDao().updateState(SystemState("last_processed_app_id", "0"))
                                db.analyticsDao().updateState(SystemState("last_processed_screen_id", "0"))
                                
                                statusMessage = "✓ Database cleared and indexes reset!"
                            } catch (e: Exception) {
                                statusMessage = "✗ Error: ${e.message}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PulseAppColorDistracting
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Clear Database & Reset Indexes",
                        color = PulseAppColorBackground
                    )
                }
                
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        style = PulseAppFontBody,
                        color = if (statusMessage.startsWith("✓")) PulseAppColorPrimary else PulseAppColorDistracting
                    )
                }
            }
        }
        
        // Ignored Apps Info Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
                .background(PulseAppColorSurface)
                .padding(PulseAppPaddingMedium)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ignored Apps",
                    style = PulseAppFontSubHeader,
                    color = PulseAppColorPrimary
                )
                
                Text(
                    text = "These apps don't trigger UNLOCK_APP detection:",
                    style = PulseAppFontBody,
                    color = PulseAppColorSecondary
                )
                
                PULSE_IGNORED_APPS.forEach { pkg ->
                    Text(
                        text = "• $pkg",
                        style = PulseAppFontBody,
                        color = PulseAppColorSecondary
                    )
                }
            }
        }
    }
}
