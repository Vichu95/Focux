package com.focux.pulse.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focux.pulse.ui.screens.Configuration.checkAccessibilityAccess
import com.focux.pulse.ui.screens.Configuration.checkUsageAccess
import com.focux.pulse.ui.theme.PulseAppColorBackground
import com.focux.pulse.ui.theme.PulseTheme

enum class OnboardingStep {
    SPLASH,
    VALUE_PROP,
    USAGE_ACCESS,
    ACCESSIBILITY_ACCESS,
    READY
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    var currentStep by remember { mutableStateOf(OnboardingStep.SPLASH) }
    var hasUsageAccess by remember { mutableStateOf(checkUsageAccess(context)) }
    var hasAccessibilityAccess by remember { mutableStateOf(checkAccessibilityAccess(context)) }
    
    // Check permissions when returning from settings

    // Check permissions when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = checkUsageAccess(context)
                hasAccessibilityAccess = checkAccessibilityAccess(context)

                // Auto-advance if sitting on a permission screen and it was granted
                if (currentStep == OnboardingStep.USAGE_ACCESS && hasUsageAccess) {
                    currentStep = OnboardingStep.ACCESSIBILITY_ACCESS
                } else if (currentStep == OnboardingStep.ACCESSIBILITY_ACCESS && hasAccessibilityAccess) {
                    currentStep = OnboardingStep.READY
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PulseTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PulseAppColorBackground
        ) {
            Crossfade(
                targetState = currentStep,
                animationSpec = tween(500),
                label = "Onboarding Flow"
            ) { step ->
                when (step) {
                    OnboardingStep.SPLASH -> SplashPage {
                        currentStep = OnboardingStep.VALUE_PROP
                    }
                    
                    OnboardingStep.VALUE_PROP -> IntroPager {
                        // Skip steps if already granted (e.g., re-installing)
                        currentStep = when {
                            !hasUsageAccess -> OnboardingStep.USAGE_ACCESS
                            !hasAccessibilityAccess -> OnboardingStep.ACCESSIBILITY_ACCESS
                            else -> OnboardingStep.READY
                        }
                    }
                    
                    OnboardingStep.USAGE_ACCESS -> UsageAccessPage(
                        isGranted = hasUsageAccess,
                        onGrantClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onNextClick = { currentStep = OnboardingStep.ACCESSIBILITY_ACCESS }
                    )
                    
                    OnboardingStep.ACCESSIBILITY_ACCESS -> AccessibilityAccessPage(
                        isGranted = hasAccessibilityAccess,
                        onGrantClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onNextClick = { currentStep = OnboardingStep.READY }
                    )
                    
                    OnboardingStep.READY -> ReadyPage {
                        onFinish()
                    }
                }
            }
        }
    }
}
