package com.focux.pulse.ui.screens.Breathing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.service.AppInterceptorService
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BreathingActivity : ComponentActivity() {

    /** Logs the breathing decision to app_sessions for opens tracking.
     *  Only called for per-app limit triggers, not doom scroll. */
    private fun logBreathingDecision(packageName: String, type: String) {
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
        val session = AppSession(
            packageName = packageName,
            startTime = now,
            endTime = now,
            duration = 0L,
            type = type,
            date = sdf.format(java.util.Date(now)),
            startTimeStr = timeStr,
            endTimeStr = timeStr
        )
        lifecycleScope.launch(Dispatchers.IO) {
            PulseDatabase.getDatabase(applicationContext).analyticsDao().insertSession(session)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE") ?: "Unknown App"
        val sessionLimitMins = intent.getIntExtra("SESSION_LIMIT_MINS", -1)
        val dailyLimitMins = intent.getIntExtra("DAILY_LIMIT_MINS", -1)
        val usedDailyMins = intent.getIntExtra("USED_DAILY_MINS", 0)
        val opensLimit = intent.getIntExtra("OPENS_LIMIT", -1)
        val usedOpens = intent.getIntExtra("USED_OPENS", 0)
        
        val breathingDuration = intent.getIntExtra("BREATHING_DURATION", 4)
        val isDoomScroll = intent.getBooleanExtra("IS_DOOM_SCROLL", false)
        
        setContent {
            PulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    BreathingScreen(
                        targetPackageName = targetPackageName,
                        sessionLimitMins = sessionLimitMins,
                        dailyLimitMins = dailyLimitMins,
                        usedDailyMins = usedDailyMins,
                        opensLimit = opensLimit,
                        usedOpens = usedOpens,
                        breathingDuration = breathingDuration,
                        isDoomScroll = isDoomScroll,
                        onProceed = {
                            logBreathingDecision(targetPackageName, "FOCUS_LOST")
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
                            logBreathingDecision(targetPackageName, "FOCUS_RETAINED")
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

data class Particle(
    val angle: Float,
    val radiusRatio: Float,
    val size: Float,
    val speed: Float
)

fun getOrdinal(n: Int): String {
    val suffix = if (n in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingScreen(
    targetPackageName: String,
    sessionLimitMins: Int,
    dailyLimitMins: Int,
    usedDailyMins: Int,
    opensLimit: Int,
    usedOpens: Int,
    breathingDuration: Int,
    isDoomScroll: Boolean = false,
    onProceed: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)
    
    val phaseDurationMs = breathingDuration * 1000L
    val cycleDurationMs = phaseDurationMs * 4L
    
    var isSequenceComplete by remember { mutableStateOf(false) }
    var currentPhaseText by remember { mutableStateOf("Inhale...") }
    var currentPhaseScale by remember { mutableStateOf(0f) } // 0f to 1f representing breathing expansion
    
    // We will use the already calculated `currentPhaseScale` to drive the breathing pulse.
    // For rotation, we just need a constantly increasing value.
    var rotation by remember { mutableStateOf(0f) }
    
    // Looping timer
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val phaseInMs = elapsed % cycleDurationMs
            
            // Update rotation continuously. 
            // 30 seconds for a full rotation (2 * PI) = (2 * PI) / 30000 per ms.
            rotation = (elapsed % 30000L).toFloat() / 30000f * (2f * Math.PI.toFloat())
            
            val secondsLeft = ((phaseDurationMs - (phaseInMs % phaseDurationMs)) / 1000).toInt() + 1
            
            when {
                phaseInMs < phaseDurationMs -> {
                    // Inhale
                    currentPhaseText = "Inhale... ${secondsLeft}s"
                    currentPhaseScale = (phaseInMs.toFloat() / phaseDurationMs)
                }
                phaseInMs < phaseDurationMs * 2 -> {
                    // Hold full
                    currentPhaseText = "Hold... ${secondsLeft}s"
                    currentPhaseScale = 1f
                }
                phaseInMs < phaseDurationMs * 3 -> {
                    // Exhale
                    currentPhaseText = "Exhale... ${secondsLeft}s"
                    val exElapsed = phaseInMs - phaseDurationMs * 2
                    currentPhaseScale = 1f - (exElapsed.toFloat() / phaseDurationMs)
                }
                else -> {
                    // Hold empty
                    currentPhaseText = "Hold... ${secondsLeft}s"
                    currentPhaseScale = 0f
                }
            }
            
            if (elapsed >= cycleDurationMs) {
                isSequenceComplete = true
            }
            
            delay(16)
        }
    }

    // Particle Setup
    val particleCount = 180
    val particles = remember {
        List(particleCount) {
            Particle(
                angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
                radiusRatio = Random.nextFloat() * 0.7f + 0.3f, // Avoid clump in center
                size = Random.nextFloat() * 6f + 2f,
                speed = Random.nextFloat() * 0.5f + 0.1f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Push top text down more
            Spacer(modifier = Modifier.weight(0.8f))
            
            AnimatedVisibility(
                visible = isSequenceComplete,
                enter = fadeIn(animationSpec = tween(1000))
            ) {
                Text(
                    text = if (isDoomScroll) "You're switching too fast." else "Your focus is breaking.",
                    style = PulseAppFontHeader.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.weight(0.5f))
            
            // Breathing Particle Sphere
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(300.dp)
            ) {
                val primaryColor = PulseAppColorPrimary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.width / 2
                    val minRadius = maxRadius * 0.4f
                    val currentRadius = minRadius + (maxRadius - minRadius) * (0.1f + 0.9f * currentPhaseScale)
                    
                    particles.forEach { p ->
                        val r = currentRadius * p.radiusRatio
                        val theta = p.angle + rotation * p.speed
                        val x = center.x + r * cos(theta)
                        val y = center.y + r * sin(theta)
                        
                        // slight pulsing opacity based on scale
                        val alpha = 0.3f + 0.7f * currentPhaseScale
                        drawCircle(
                            color = primaryColor.copy(alpha = alpha),
                            radius = p.size,
                            center = Offset(x, y)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Inhale/Exhale text below the animation, smaller
            Text(
                text = currentPhaseText,
                style = PulseAppFontBody.copy(fontSize = 15.sp, letterSpacing = 0.5.sp),
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.weight(1f))

            // Fade in info and buttons at the end
            androidx.compose.animation.AnimatedVisibility(
                visible = isSequenceComplete,
                enter = fadeIn(animationSpec = tween(1000))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Limits info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        if (sessionLimitMins != -1) {
                            Text("Session Limit: $sessionLimitMins mins", color = Color.Gray, style = PulseAppFontLabel.copy(fontSize = 12.sp))
                        }
                        if (dailyLimitMins != -1) {
                            val color = if (usedDailyMins >= dailyLimitMins) PulseAppColorDistracting else Color.Gray
                            Text("Daily Limit: $usedDailyMins / $dailyLimitMins mins", color = color, style = PulseAppFontLabel.copy(fontSize = 12.sp))
                        }
                        if (opensLimit != -1) {
                            val color = if (usedOpens >= opensLimit) PulseAppColorDistracting else Color.Gray
                            Text("Daily Opens: $usedOpens / $opensLimit times", color = color, style = PulseAppFontLabel.copy(fontSize = 12.sp))
                        }
                    }
                    
                    // Smaller Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f), // Don't span full width
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onExit,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp), // Smaller height
                            colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorSurface),
                            shape = RoundedCornerShape(22.dp), // More rounded
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Close App", color = Color.LightGray, style = PulseAppFontLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        }
                        
                        Button(
                            onClick = onProceed,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Continue", tint = PulseAppColorBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Continue", color = PulseAppColorBackground, style = PulseAppFontLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
