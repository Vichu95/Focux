package com.focux.pulse.ui.screens.Breathing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.delay

class BreathingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE") ?: "Unknown App"
        
        setContent {
            PulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PulseAppColorBackground
                ) {
                    BreathingScreen(
                        targetPackageName = targetPackageName,
                        onProceed = {
                            // Proceed to the app by simulating a home press then launching intent
                            // Or simpler: just let them proceed implicitly? We'll launch it.
                            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackageName)
                            if (launchIntent != null) {
                                // Important: We MUST flag this so our service doesn't re-intercept immediately
                                // This requires keeping state. For Phase 1, we might end in an intercept loop
                                // unless we temporarily whitelist. We will figure this out in Phase 2.
                                // For now, just exit BreathingActivity.
                                startActivity(launchIntent)
                            }
                            finish()
                        },
                        onExit = {
                            // Go back home
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BreathingScreen(
    targetPackageName: String,
    onProceed: () -> Unit,
    onExit: () -> Unit
) {
    // 1. Inhale (4s), 2. Hold (4s), 3. Exhale (4s), 4. Hold (4s)
    val cycleDurationMs = 16000L
    var isSequenceComplete by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf("Inhale...") }
    var progress by remember { mutableStateOf(0f) }

    // Animation configuration
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    // Animate the circle size
    val circleScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Phase timer
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < cycleDurationMs) {
            val elapsed = System.currentTimeMillis() - startTime
            progress = elapsed.toFloat() / cycleDurationMs
            
            val phaseInMs = elapsed % cycleDurationMs
            currentPhase = when {
                phaseInMs < 4000 -> "Inhale..."
                phaseInMs < 8000 -> "Hold."
                phaseInMs < 12000 -> "Exhale..."
                else -> "Hold."
            }
            delay(16) // ~60fps
        }
        progress = 1f
        currentPhase = "Mindful Pause Complete."
        isSequenceComplete = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PulseAppPaddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        
        Text(
            text = "Mindful Interception",
            style = PulseAppFontHeader,
            color = PulseAppColorPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "You are trying to open this app. Take a moment to breathe before proceeding.",
            style = PulseAppFontBody,
            color = PulseAppColorSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(64.dp))
        
        // Breathing Circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp)
        ) {
            // Background circle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(PulseAppColorSurface)
            )
            
            // Animated breathing circle
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction = if(isSequenceComplete) 1f else circleScale)
                    .clip(CircleShape)
                    .background(PulseAppColorPrimary.copy(alpha = 0.3f))
            )

            Text(
                text = currentPhase,
                style = PulseAppFontHeader.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                color = PulseAppColorPrimary,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        // Progress Bar (Timer)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = PulseAppColorPrimary,
            trackColor = PulseAppColorSurface,
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(64.dp))

        // Actions
        if (isSequenceComplete) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
                ) {
                    Text("Exit. I don't need this app right now.", color = PulseAppColorBackground)
                }
                
                OutlinedButton(
                    onClick = onProceed,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseAppColorSecondary)
                ) {
                    Text("Proceed intentionally")
                }
            }
        }
    }
}
