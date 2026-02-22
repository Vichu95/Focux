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
import com.focux.pulse.service.AppInterceptorService
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.delay

class BreathingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE") ?: "Unknown App"
        val sessionLimitMins = intent.getIntExtra("SESSION_LIMIT_MINS", -1)
        val dailyLimitMins = intent.getIntExtra("DAILY_LIMIT_MINS", -1)
        val usedDailyMins = intent.getIntExtra("USED_DAILY_MINS", 0)
        val opensLimit = intent.getIntExtra("OPENS_LIMIT", -1)
        val usedOpens = intent.getIntExtra("USED_OPENS", 0)
        
        val breathingDuration = intent.getIntExtra("BREATHING_DURATION", 4)
        
        setContent {
            PulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PulseAppColorBackground
                ) {
                    BreathingScreen(
                        targetPackageName = targetPackageName,
                        sessionLimitMins = sessionLimitMins,
                        dailyLimitMins = dailyLimitMins,
                        usedDailyMins = usedDailyMins,
                        opensLimit = opensLimit,
                        usedOpens = usedOpens,
                        breathingDuration = breathingDuration,
                        onProceed = {
                            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackageName)
                            if (launchIntent != null) {
                                val isDailyExceeded = dailyLimitMins != -1 && usedDailyMins >= dailyLimitMins
                                val isOpensExceeded = opensLimit != -1 && usedOpens >= opensLimit
                                
                                val defaultBypassMs = 5 * 60 * 1000L
                                val durationMs = if (sessionLimitMins != -1) {
                                    sessionLimitMins * 60 * 1000L
                                } else if (isDailyExceeded || isOpensExceeded) {
                                    defaultBypassMs
                                } else {
                                    -1L
                                }
                                AppInterceptorService.allowAppContinuance(targetPackageName, durationMs)
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
    sessionLimitMins: Int,
    dailyLimitMins: Int,
    usedDailyMins: Int,
    opensLimit: Int,
    usedOpens: Int,
    breathingDuration: Int,
    onProceed: () -> Unit,
    onExit: () -> Unit
) {
    // 1. Inhale (duration), 2. Hold (duration), 3. Exhale (duration), 4. Hold (duration)
    val phaseDurationMs = breathingDuration * 1000L
    val cycleDurationMs = phaseDurationMs * 4L
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
                phaseInMs < phaseDurationMs -> "Inhale..."
                phaseInMs < phaseDurationMs * 2 -> "Hold."
                phaseInMs < phaseDurationMs * 3 -> "Exhale..."
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
        
        val isDailyExceeded = dailyLimitMins != -1 && usedDailyMins >= dailyLimitMins
        val isOpensExceeded = opensLimit != -1 && usedOpens >= opensLimit
        
        val headerText = when {
            isDailyExceeded -> "Daily Limit Exceeded"
            isOpensExceeded -> "App Opens Limit Reached"
            else -> "Mindful Interception"
        }
        
        val bodyText = when {
            isDailyExceeded -> "You've exceeded your daily allowance for this app. Take a breath and reconsider."
            isOpensExceeded -> "You've opened this app too many times today. Take a breath and reconsider."
            else -> "You are trying to open this app. Take a moment to breathe before proceeding."
        }

        Text(
            text = headerText,
            style = PulseAppFontHeader,
            color = if (isDailyExceeded || isOpensExceeded) PulseAppColorDistracting else PulseAppColorPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = bodyText,
            style = PulseAppFontBody,
            color = PulseAppColorSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (sessionLimitMins != -1) {
                Text(text = "Session Limit: $sessionLimitMins mins", style = PulseAppFontLabel, color = PulseAppColorSecondary)
            }
            if (dailyLimitMins != -1) {
                val color = if (usedDailyMins >= dailyLimitMins) PulseAppColorDistracting else PulseAppColorSecondary
                Text(text = "Daily Limit: $usedDailyMins / $dailyLimitMins mins", style = PulseAppFontLabel, color = color)
            }
            if (opensLimit != -1) {
                val color = if (usedOpens >= opensLimit) PulseAppColorDistracting else PulseAppColorSecondary
                Text(text = "Daily Opens: $usedOpens / $opensLimit times", style = PulseAppFontLabel, color = color)
            }
            if (sessionLimitMins == -1 && dailyLimitMins == -1 && opensLimit == -1) {
                Text(text = "No Explicit Limits set for this category", style = PulseAppFontLabel, color = PulseAppColorSecondary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
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
