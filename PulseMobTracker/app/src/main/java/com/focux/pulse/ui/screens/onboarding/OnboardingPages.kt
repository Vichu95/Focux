package com.focux.pulse.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import com.focux.pulse.R
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashPage(onSplashComplete: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        delay(2500) // Show for 2.5 seconds
        isVisible = false
        delay(500) // allow fade out
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(800)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Using the ic_launcher_foreground as the app icon for now
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "Pulse App Icon",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(PulseAppColorSurface)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Pulse",
                    style = PulseAppFontHeader.copy(fontSize = 32.sp),
                    color = PulseAppColorPrimary
                )
            }
        }
    }
}

@Composable
fun ValuePropPage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Pulse",
            style = PulseAppFontHeader.copy(fontSize = 28.sp),
            color = PulseAppColorPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Feature 1
        FeatureRow(
            icon = Icons.Default.DateRange,
            title = "Track Your Habits",
            description = "Visualize your daily screen time and calculate your holistic focus score."
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Feature 2
        FeatureRow(
            icon = Icons.Default.Warning,
            title = "Stop Doomscrolling",
            description = "Pulse automatically catches you when rapidly switching between distracting apps."
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Feature 3
        FeatureRow(
            icon = Icons.Default.Favorite,
            title = "Mindful Interventions",
            description = "Instead of hard blocks, gently redirect attention with a short breathing exercise."
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Feature 4
        FeatureRow(
            icon = Icons.Default.Lock,
            title = "100% Private, 0% Ads",
            description = "No internet required. Your data belongs to you, stays on your device, and can be exported at any time."
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
            shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
        ) {
            Text("Let's Get Started", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PulseAppColorPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PulseAppColorPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = PulseAppFontSubHeader.copy(fontWeight = FontWeight.Bold),
                color = PulseAppColorSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = PulseAppFontBody.copy(color = Color.Gray, fontSize = 14.sp),
            )
        }
    }
}

@Composable
fun UsageAccessPage(isGranted: Boolean, onGrantClick: () -> Unit, onNextClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconTint = PulseAppColorPrimary
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(PulseAppColorPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Usage Access",
            style = PulseAppFontHeader.copy(fontSize = 28.sp),
            color = PulseAppColorPrimary,
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "To show you meaningful insights about your screen time and identify distracting patterns, Pulse needs to see which apps you use and for how long.",
            style = PulseAppFontBody,
            textAlign = TextAlign.Center,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (!isGranted) {
            Button(
                onClick = onGrantClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
            ) {
                Text("Grant Usage Access", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            Button(
                onClick = onNextClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
            ) {
                Text("Continue", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun AccessibilityAccessPage(
    isGranted: Boolean, 
    showDisclosureDialog: Boolean,
    onGrantClick: () -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val iconTint = PulseAppColorPrimary
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(PulseAppColorPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Accessibility Access",
            style = PulseAppFontHeader.copy(fontSize = 28.sp),
            color = PulseAppColorPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "To actively detect rapid app switching (doom scrolling) and help you regain focus by stepping in right when you open a distracting app, Pulse needs Accessibility Service.",
            style = PulseAppFontBody,
            textAlign = TextAlign.Center,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (!isGranted) {
            Button(
                onClick = onGrantClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
            ) {
                Text("Grant Accessibility Access", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            Button(
                onClick = onNextClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
            ) {
                Text("Finish Setup", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
    
    if (showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = onDialogDismiss,
            title = {
                Text("Accessibility Service Required", style = PulseAppFontSubHeader)
            },
            text = {
                Text(
                    text = "Pulse needs Accessibility Service access to detect when you open distracting apps and show a mindful breathing screen. Pulse does not view, collect, or transmit the content of your screen.",
                    style = PulseAppFontBody
                )
            },
            confirmButton = {
                Button(onClick = onDialogConfirm) { Text("Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = onDialogDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ReadyPage(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(PulseAppColorPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = PulseAppColorPrimary,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Enjoy your focus",
            style = PulseAppFontHeader.copy(fontSize = 32.sp),
            color = PulseAppColorSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Pulse is now active in the background. Spend less time scrolling and more time living.",
            style = PulseAppFontBody.copy(fontSize = 16.sp),
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
            shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
        ) {
            Text("Open Dashboard", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
    }
}
