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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import com.focux.pulse.R
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

data class TutorialPage(
    val title: String,
    val description: String,
    val imageRes: Int? = null,
    val graphic: (@Composable () -> Unit)? = null
)

@Composable
fun MiniLimitsGraphic() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Social Media", color = PulseAppColorSecondary, style = PulseAppFontSubHeader)
            Text("45 / 60m", color = PulseAppColorPrimary, style = PulseAppFontBody)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.height(16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PulseAppColorBackground)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.75f).clip(RoundedCornerShape(8.dp)).background(PulseAppColorPrimary))
        }
    }
}

@Composable
fun WelcomeFeaturesGraphic() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        FeatureRow(
            icon = Icons.Default.Check,
            title = "Your Focus Score",
            description = "Rewards mindful mornings and penalizes endless scrolling."
        )
        FeatureRow(
            icon = Icons.Default.DateRange,
            title = "Track Your Habits",
            description = "Visualize daily screen time and Focus Score."
        )
        FeatureRow(
            icon = Icons.Default.Warning,
            title = "Stop Doomscrolling",
            description = "Pulse catches you when opening distracting apps."
        )
        FeatureRow(
            icon = Icons.Default.Info,
            title = "Mindful Interventions",
            description = "Redirect attention with breathing exercises."
        )
    }
}

@Composable
fun TimelineScreenshotGraphic() {
    Image(
        painter = painterResource(id = R.drawable.intro_timeline),
        contentDescription = "Timeline Screenshot",
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(240.dp)
            .clip(RoundedCornerShape(PulseAppCornerRadiusLarge))
            .border(1.dp, PulseAppColorPrimary.copy(alpha=0.3f), RoundedCornerShape(PulseAppCornerRadiusLarge)),
        contentScale = ContentScale.Crop,
        alignment = androidx.compose.ui.BiasAlignment(0f, -0.6f) // Shift crop higher to chop off more of the bottom
    )
}

@Composable
fun IntroPager(onNext: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        TutorialPage(
            title = "Welcome to Pulse",
            description = "",
            graphic = { WelcomeFeaturesGraphic() }
        ),
        TutorialPage(
            title = "The Timeline",
            description = "The timeline shows how you use apps throughout the day. Each app category has its own color: Green for Productive, Red for Distracting, and Grey for Neutral. These can be configured in settings.",
            graphic = null
        ),
        TutorialPage(
            title = "Configuration",
            description = "Take control by setting Mindful Limits on specific app categories or configuring app types as Productive, Distracting, or Neutral.",
            graphic = null
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground)
            .padding(PulseAppPaddingMedium)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = pages[page].title,
                    style = PulseAppFontHeader.copy(fontSize = 28.sp),
                    color = PulseAppColorPrimary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (pages[page].description.isNotEmpty()) {
                    Text(
                        text = pages[page].description,
                        style = PulseAppFontBody.copy(fontSize = 16.sp, lineHeight = 24.sp),
                        color = PulseAppColorSecondary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (pages[page].graphic != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Graphic Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        pages[page].graphic?.invoke()
                    }
                }

                if (page == pages.size - 1) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onNext() },
                        colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
                    ) {
                        Text(
                            text = "Get Started",
                            color = PulseAppColorBackground,
                            style = PulseAppFontHeader.copy(fontSize = 18.sp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) PulseAppColorPrimary else PulseAppColorSurface
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
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
    onGrantClick: () -> Unit,
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
            text = "Pulse needs Accessibility Access strictly to detect when distracting apps are opened so it can step in and help you regain focus.\n\nPulse CANNOT read your messages, view your screen content, or collect your personal data. All processing happens 100% securely on your device.",
            style = PulseAppFontBody,
            textAlign = TextAlign.Center,
            color = Color.LightGray
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        if (!isGranted) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary),
                    shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
                ) {
                    Text("Accept & Enable", color = PulseAppColorBackground, style = PulseAppFontHeader.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }

                OutlinedButton(
                    onClick = onNextClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(PulseAppCornerRadiusMedium),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseAppColorPrimary)
                ) {
                    Text("Decline", style = PulseAppFontHeader.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
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
