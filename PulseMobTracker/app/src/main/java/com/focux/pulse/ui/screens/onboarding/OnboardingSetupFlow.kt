package com.focux.pulse.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import com.focux.pulse.ui.screens.components.BreathingParticleAnimation
import com.focux.pulse.ui.screens.components.generateParticles
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.data.local.entities.SystemState
import com.focux.pulse.ui.theme.*
import com.focux.pulse.utilities.NO_LIMIT
import com.focux.pulse.ui.screens.components.AppIcon
import kotlinx.coroutines.launch

enum class SetupStep {
    DISTRACTING_APPS,
    PRODUCTIVE_APPS,
    MASTER_TOGGLES,
    BREATHING_TIME,
    APP_LIMITS,
    DOOM_SCROLL,
    READY
}

@Composable
fun OnboardingSetupFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { PulseDatabase.getDatabase(context) }
    
    var currentStep by remember { mutableStateOf(SetupStep.DISTRACTING_APPS) }
    
    // Loaded App Data
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }

    // User Selections
    var distractingPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var productivePackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Toggles
    var isAppLimitsEnabled by remember { mutableStateOf(true) }
    var isDoomScrollEnabled by remember { mutableStateOf(true) }

    // Configurations
    var breathingTime by remember { mutableStateOf(4) }
    var distSessionLimit by remember { mutableStateOf<Int?>(5) }
    var distDailyLimit by remember { mutableStateOf<Int?>(30) }
    var distOpensLimit by remember { mutableStateOf<Int?>(10) }
    var doomScrollWindow by remember { mutableStateOf(10) }
    var doomScrollThreshold by remember { mutableStateOf(15) }

    LaunchedEffect(Unit) {
        var apps = db.appInfoDao().getAllApps()
        if (apps.isEmpty()) {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)
            val newApps = resolveInfoList.mapNotNull { resolveInfo ->
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == context.packageName) return@mapNotNull null
                val appName = resolveInfo.loadLabel(context.packageManager).toString()
                AppInfo(packageName = pkgName, appName = appName, category = "NEUTRAL")
            }
            db.appInfoDao().insertAllIfNotExists(newApps)
            apps = db.appInfoDao().getAllApps()
        }
        allApps = apps
        appsLoaded = true
    }

    if (!appsLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(PulseAppColorBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PulseAppColorPrimary)
        }
        return
    }

    fun finishSetup() {
        scope.launch {
            // Save App Categories
            val updatedApps = allApps.map {
                val cat = when (it.packageName) {
                    in distractingPackages -> AppCategory.DISTRACTING
                    in productivePackages -> AppCategory.PRODUCTIVE
                    else -> AppCategory.NEUTRAL
                }
                it.copy(category = cat)
            }
            db.appInfoDao().insertAllIfNotExists(updatedApps) // Wait, we need to UPDATE not insert if exists
            updatedApps.forEach { db.appInfoDao().updateCategory(it.packageName, it.category) }
            
            // Save Toggles
            db.analyticsDao().updateState(SystemState("pulse_master_enabled", "true")) // Force master to always be true if setup finishes
            db.analyticsDao().updateState(SystemState("pulse_app_limits_enabled", isAppLimitsEnabled.toString()))
            db.analyticsDao().updateState(SystemState("pulse_doomscroll_enabled", isDoomScrollEnabled.toString()))
            
            // Save Configs
            db.analyticsDao().updateState(SystemState("mindful_base_duration", breathingTime.toString()))
            db.analyticsDao().updateState(SystemState("limit_distracting_session", distSessionLimit?.toString() ?: NO_LIMIT.toString()))
            db.analyticsDao().updateState(SystemState("limit_distracting_daily", distDailyLimit?.toString() ?: NO_LIMIT.toString()))
            db.analyticsDao().updateState(SystemState("limit_distracting_opens", distOpensLimit?.toString() ?: NO_LIMIT.toString()))
            db.analyticsDao().updateState(SystemState("mindful_doomscroll_window_secs", doomScrollWindow.toString()))
            db.analyticsDao().updateState(SystemState("mindful_doomscroll_threshold", doomScrollThreshold.toString()))
            
            onFinish()
        }
    }

    BackHandler(enabled = currentStep != SetupStep.DISTRACTING_APPS) {
        when (currentStep) {
            SetupStep.PRODUCTIVE_APPS -> currentStep = SetupStep.DISTRACTING_APPS
            SetupStep.MASTER_TOGGLES -> currentStep = SetupStep.PRODUCTIVE_APPS
            SetupStep.BREATHING_TIME -> currentStep = SetupStep.MASTER_TOGGLES
            SetupStep.APP_LIMITS -> currentStep = SetupStep.BREATHING_TIME
            SetupStep.DOOM_SCROLL -> {
                if (isAppLimitsEnabled) currentStep = SetupStep.APP_LIMITS
                else currentStep = SetupStep.BREATHING_TIME
            }
            else -> {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PulseAppColorBackground) {
        Crossfade(targetState = currentStep, animationSpec = tween(500), label = "SetupFlow") { step ->
            when (step) {
                SetupStep.DISTRACTING_APPS -> {
                    AppSelectionScreen(
                        title = "Distracting Apps",
                        subtitle = "Select apps that steal your focus. Pulse will help you limit them.",
                        apps = allApps,
                        selectedPackages = distractingPackages,
                        onSelectionChanged = { distractingPackages = it },
                        onNext = { currentStep = SetupStep.PRODUCTIVE_APPS }
                    )
                }
                SetupStep.PRODUCTIVE_APPS -> {
                    AppSelectionScreen(
                        title = "Productive Apps",
                        subtitle = "Select apps that are important for your work. Distracting apps are disabled.",
                        apps = allApps,
                        selectedPackages = productivePackages,
                        disabledPackages = distractingPackages,
                        onSelectionChanged = { productivePackages = it },
                        onBack = { currentStep = SetupStep.DISTRACTING_APPS },
                        onNext = { currentStep = SetupStep.MASTER_TOGGLES }
                    )
                }
                SetupStep.MASTER_TOGGLES -> {
                    MasterTogglesScreen(
                        isAppLimitsEnabled = isAppLimitsEnabled,
                        isDoomScrollEnabled = isDoomScrollEnabled,
                        onLimitsChange = { isAppLimitsEnabled = it },
                        onDoomChange = { isDoomScrollEnabled = it },
                        onBack = { currentStep = SetupStep.PRODUCTIVE_APPS },
                        onNext = {
                            if (!isAppLimitsEnabled && !isDoomScrollEnabled) {
                                finishSetup()
                            } else if (isAppLimitsEnabled || isDoomScrollEnabled) {
                                currentStep = SetupStep.BREATHING_TIME
                            } else {
                                currentStep = SetupStep.READY
                            }
                        }
                    )
                }
                SetupStep.BREATHING_TIME -> {
                    BreathingTimeScreen(
                        time = breathingTime,
                        onTimeChange = { breathingTime = it },
                        onBack = { currentStep = SetupStep.MASTER_TOGGLES },
                        onNext = {
                            if (isAppLimitsEnabled) {
                                currentStep = SetupStep.APP_LIMITS
                            } else if (isDoomScrollEnabled) {
                                currentStep = SetupStep.DOOM_SCROLL
                            } else {
                                currentStep = SetupStep.READY
                            }
                        }
                    )
                }
                SetupStep.APP_LIMITS -> {
                    AppLimitsScreen(
                        sessionLimit = distSessionLimit,
                        dailyLimit = distDailyLimit,
                        opensLimit = distOpensLimit,
                        onSessionChange = { distSessionLimit = it },
                        onDailyChange = { distDailyLimit = it },
                        onOpensChange = { distOpensLimit = it },
                        onBack = { currentStep = SetupStep.BREATHING_TIME },
                        onNext = {
                            if (isDoomScrollEnabled) currentStep = SetupStep.DOOM_SCROLL
                            else currentStep = SetupStep.READY
                        }
                    )
                }
                SetupStep.DOOM_SCROLL -> {
                    DoomScrollScreen(
                        windowSecs = doomScrollWindow,
                        threshold = doomScrollThreshold,
                        onConfigChange = { w, t -> doomScrollWindow = w; doomScrollThreshold = t },
                        onBack = {
                            if (isAppLimitsEnabled) currentStep = SetupStep.APP_LIMITS
                            else currentStep = SetupStep.BREATHING_TIME
                        },
                        onNext = { currentStep = SetupStep.READY }
                    )
                }
                SetupStep.READY -> {
                    ReadySetupScreen(onFinish = { finishSetup() })
                }
            }
        }
    }
}

// ---------------- UI Components below ---------------- //

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    title: String,
    subtitle: String,
    apps: List<AppInfo>,
    selectedPackages: Set<String>,
    disabledPackages: Set<String> = emptySet(),
    onSelectionChanged: (Set<String>) -> Unit,
    onBack: (() -> Unit)? = null,
    onNext: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(title, style = PulseAppFontHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(subtitle, style = PulseAppFontBody, color = PulseAppColorSecondary)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PulseAppColorPrimary,
                unfocusedBorderColor = PulseAppColorSecondary,
                cursorColor = PulseAppColorPrimary
            ),
            textStyle = PulseAppFontBody.copy(color = Color.White)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        val filteredApps = remember(apps, searchQuery) {
            if (searchQuery.isBlank()) apps
            else apps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        }
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredApps) { app ->
                val isDisabled = app.packageName in disabledPackages
                val isSelected = app.packageName in selectedPackages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable(enabled = !isDisabled) {
                            if (isSelected) onSelectionChanged(selectedPackages - app.packageName)
                            else onSelectionChanged(selectedPackages + app.packageName)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(packageName = app.packageName, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        app.appName.ifEmpty { app.packageName }, 
                        style = PulseAppFontBody, 
                        color = if (isDisabled) Color.Gray else Color.White, 
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isSelected || isDisabled,
                        enabled = !isDisabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PulseAppColorBackground,
                            checkedTrackColor = if (isDisabled) Color.DarkGray else PulseAppColorPrimary,
                            uncheckedThumbColor = PulseAppColorBackground,
                            uncheckedTrackColor = PulseAppColorSecondary
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PulseAppColorPrimary)
                ) {
                    Text("Back", color = PulseAppColorPrimary)
                }
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text("Continue", color = PulseAppColorBackground)
            }
        }
    }
}

@Composable
fun MasterTogglesScreen(
    isAppLimitsEnabled: Boolean, isDoomScrollEnabled: Boolean,
    onLimitsChange: (Boolean) -> Unit, onDoomChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = PulseAppColorBackground,
        checkedTrackColor = PulseAppColorPrimary,
        uncheckedThumbColor = PulseAppColorBackground,
        uncheckedTrackColor = PulseAppColorSecondary
    )
    
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Control Your Flow", style = PulseAppFontHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Choose which interventions Pulse should use.", style = PulseAppFontBody, color = PulseAppColorSecondary)
        
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enable App Reminders", style = PulseAppFontBody, color = PulseAppColorSecondary)
            Switch(checked = isAppLimitsEnabled, onCheckedChange = onLimitsChange, colors = switchColors)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enable Doom Scroll Detection", style = PulseAppFontBody, color = PulseAppColorSecondary)
            Switch(checked = isDoomScrollEnabled, onCheckedChange = onDoomChange, colors = switchColors)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PulseAppColorPrimary)
            ) {
                Text("Back", color = PulseAppColorPrimary)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text("Continue", color = PulseAppColorBackground)
            }
        }
    }
}

@Composable
fun BreathingTimeScreen(time: Int, onTimeChange: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    val particles = remember { generateParticles(180) }
    var rotation by remember { mutableStateOf(0f) }
    var currentPhaseScale by remember { mutableStateOf(0.5f) }
    var phaseText by remember { mutableStateOf("Inhale...") }
    
    LaunchedEffect(time) {
        val phaseDurationMs = time * 1000L
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val cycleDurationMs = phaseDurationMs * 4L
            val phaseInMs = elapsed % cycleDurationMs
            rotation = (elapsed % 30000L).toFloat() / 30000f * (2f * Math.PI.toFloat())
            val secondsLeft = ((phaseDurationMs - (phaseInMs % phaseDurationMs)) / 1000).toInt() + 1
            when {
                phaseInMs < phaseDurationMs -> {
                    phaseText = "Inhale... ${secondsLeft}s"
                    currentPhaseScale = phaseInMs.toFloat() / phaseDurationMs
                }
                phaseInMs < phaseDurationMs * 2 -> {
                    phaseText = "Hold... ${secondsLeft}s"
                    currentPhaseScale = 1f
                }
                phaseInMs < phaseDurationMs * 3 -> {
                    phaseText = "Exhale... ${secondsLeft}s"
                    currentPhaseScale = 1f - ((phaseInMs - phaseDurationMs * 2).toFloat() / phaseDurationMs)
                }
                else -> {
                    phaseText = "Hold... ${secondsLeft}s"
                    currentPhaseScale = 0f
                }
            }
            kotlinx.coroutines.delay(16)
        }
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text("Mindful Pause", style = PulseAppFontHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("How many seconds should the breathing exercise last before an app opens?", style = PulseAppFontBody, color = PulseAppColorSecondary)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        BreathingParticleAnimation(
            modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
            currentPhaseScale = currentPhaseScale,
            rotation = rotation,
            particles = particles
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = phaseText,
            style = PulseAppFontBody.copy(fontSize = 15.sp, letterSpacing = 0.5.sp),
            color = Color.LightGray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if(time > 1) onTimeChange(time-1) }) { Text("-", color = PulseAppColorPrimary, fontSize = 24.sp) }
            Text("$time s", style = PulseAppFontHeader.copy(fontSize = 32.sp), color = Color.White, modifier = Modifier.padding(horizontal = 24.dp))
            IconButton(onClick = { if(time < 10) onTimeChange(time+1) }) { Text("+", color = PulseAppColorPrimary, fontSize = 24.sp) }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PulseAppColorPrimary)
            ) {
                Text("Back", color = PulseAppColorPrimary)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text("Continue", color = PulseAppColorBackground)
            }
        }
    }
}

@Composable
fun AppLimitsScreen(sessionLimit: Int?, dailyLimit: Int?, opensLimit: Int?, onSessionChange: (Int?) -> Unit, onDailyChange: (Int?) -> Unit, onOpensChange: (Int?) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text("Daily Goal", style = PulseAppFontHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Set default limits for your Distracting Apps.", style = PulseAppFontBody, color = PulseAppColorSecondary)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Daily Time Limit (minutes)", style = PulseAppFontBody, color = Color.White)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDailyChange(if (dailyLimit == null) 30 else maxOf(5, dailyLimit - 5)) }) { Text("-", color = PulseAppColorPrimary, fontSize = 24.sp) }
            Text(if (dailyLimit == null) "No Limit" else "$dailyLimit m", style = PulseAppFontSubHeader, color = Color.White)
            IconButton(onClick = { onDailyChange(if (dailyLimit == null) 30 else dailyLimit + 5) }) { Text("+", color = PulseAppColorPrimary, fontSize = 24.sp) }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Session Time Limit (minutes)", style = PulseAppFontBody, color = Color.White)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSessionChange(if (sessionLimit == null) 5 else maxOf(1, sessionLimit - 1)) }) { Text("-", color = PulseAppColorPrimary, fontSize = 24.sp) }
            Text(if (sessionLimit == null) "No Limit" else "$sessionLimit m", style = PulseAppFontSubHeader, color = Color.White)
            IconButton(onClick = { onSessionChange(if (sessionLimit == null) 5 else sessionLimit + 1) }) { Text("+", color = PulseAppColorPrimary, fontSize = 24.sp) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Daily Opens Limit", style = PulseAppFontBody, color = Color.White)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpensChange(if (opensLimit == null) 10 else maxOf(1, opensLimit - 1)) }) { Text("-", color = PulseAppColorPrimary, fontSize = 24.sp) }
            Text(if (opensLimit == null) "No Limit" else "$opensLimit x", style = PulseAppFontSubHeader, color = Color.White)
            IconButton(onClick = { onOpensChange(if (opensLimit == null) 10 else opensLimit + 1) }) { Text("+", color = PulseAppColorPrimary, fontSize = 24.sp) }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PulseAppColorPrimary)
            ) {
                Text("Back", color = PulseAppColorPrimary)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text("Continue", color = PulseAppColorBackground)
            }
        }
    }
}

@Composable
fun DoomScrollScreen(windowSecs: Int, threshold: Int, onConfigChange: (Int, Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Doom Scroll Sensitivity", style = PulseAppFontHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("How aggressively should Pulse stop you from rapidly switching between apps?", style = PulseAppFontBody, color = PulseAppColorSecondary)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        val isStrict = windowSecs >= 30 && threshold <= 10
        val isMedium = windowSecs == 15 && threshold == 12
        val isRelaxed = windowSecs <= 10 && threshold >= 15
        val isCustom = !isStrict && !isMedium && !isRelaxed
        
        Button(onClick = { onConfigChange(30, 10) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isStrict) PulseAppColorPrimary else PulseAppColorSurface)) {
            Text("Strict (10 switches in 30s)", color = if(isStrict) PulseAppColorBackground else PulseAppColorSecondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onConfigChange(15, 12) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isMedium) PulseAppColorPrimary else PulseAppColorSurface)) {
            Text("Medium (12 switches in 15s)", color = if(isMedium) PulseAppColorBackground else PulseAppColorSecondary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onConfigChange(10, 15) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isRelaxed) PulseAppColorPrimary else PulseAppColorSurface)) {
            Text("Relaxed (15 switches in 10s)", color = if(isRelaxed) PulseAppColorBackground else PulseAppColorSecondary)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PulseAppColorPrimary)
            ) {
                Text("Back", color = PulseAppColorPrimary)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text("Continue", color = PulseAppColorBackground)
            }
        }
    }
}

@Composable
fun ReadySetupScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp).clip(androidx.compose.foundation.shape.CircleShape).background(PulseAppColorPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PulseAppColorPrimary, modifier = Modifier.size(64.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("You're All Set!", style = PulseAppFontHeader.copy(fontSize = 28.sp), color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Pulse is now running in the background to protect your attention.", style = PulseAppFontBody, textAlign = TextAlign.Center, color = Color.LightGray)
        
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
        ) {
            Text("Open Dashboard", color = PulseAppColorBackground)
        }
    }
}
