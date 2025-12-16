package com.focux.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.focux.pulse.features.Insights.InsightsScreen
import com.focux.pulse.features.Summary.SummaryScreen
import com.focux.pulse.features.Timeline.TimelineScreen
import com.focux.pulse.ui.components.BottomNavBar

import com.focux.pulse.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseTheme {
                MainAppStructure()
            }
        }
    }
}

@Composable
fun MainAppStructure() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        
        val contentModifier = Modifier.padding(innerPadding)
        
        // Wraps the screen content in a Box to apply the Scaffold padding
        androidx.compose.foundation.layout.Box(modifier = contentModifier) {
            when (selectedTab) {
                0 -> SummaryScreen()
                1 -> TimelineScreen()
                2 -> InsightsScreen()
            }
        }

    }
}