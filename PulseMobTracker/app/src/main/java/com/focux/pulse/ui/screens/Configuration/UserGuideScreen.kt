package com.focux.pulse.ui.screens.Configuration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.focux.pulse.ui.theme.*

data class FaqItem(val question: String, val answer: String)

val pulseFaqs = listOf(
    FaqItem(
        "What information is provided in the Summary?",
        "The Summary tab gives you a holistic overview of your day. It displays your Focus Score (0-100), daily screen time, unlocking habits, and longest continuous offline streak. It's the best place to check your overall progress at a glance."
    ),
    FaqItem(
        "What does the Timeline look like?",
        "The Timeline shows your exact app usage minute-by-day. \n🟢 Green = Productive apps\n🔴 Red = Distracting apps\n⚪ Grey = Neutral/System apps\nIf you see a 'fire/breathe' icon, it means Pulse caught you opening a distracting app and paused you."
    ),
    FaqItem(
        "How do I change individual session categories?",
        "Go to the Timeline screen and tap the 'Edit' button at the top right. Then, tap on any colored app session block on the timeline to manually re-categorize it as Productive, Distracting, or Neutral. Tap 'Save' when you're done!"
    ),
    FaqItem(
        "What weekly details are shown in Insights?",
        "The Insights tab tracks your long-term trends. You can view your Focus Score averaged over the week, see your most used apps categorized by type, and identify which days you were most distracted."
    ),
    FaqItem(
        "How do I configure my App Types and Limits?",
        "In the Configuration tab under 'App Category', you can assign every app on your phone to be Productive, Distracting, or Neutral. Right below that, under 'Mindful Limits', you can set a daily time allowance for distracting apps to trigger a breathing intervention when exceeded."
    ),
    FaqItem(
        "How do I export my data?",
        "Scroll down to the 'Database Management' section in the Configuration tab and tap 'Export Database'. You can then save your raw SQLite data file or directly share it via Telegram, Email, or Google Drive."
    ),
    FaqItem(
        "How is the Holistic Focus Score calculated?",
        "Your Focus Score (0-100) evaluates 5 distinct components:\n• 🕐 **Screen Time Balance (40 pts)**: Starts at 20. Grows with Productive usage; decreases with Distracting screens.\n• 📱 **Unlocking Habits (20 pts)**: Degrades based on absolute count of unlocks and locks.\n• 🌅 **Morning Habit (10 pts)**: Discarded if your first app upon waking is defined as Distracting.\n• 🧠 **Deep Work Hours (15 pts)**: Awards points for your longest continuous uninterrupted offline flow (Target: 2 hours).\n• 💤 **Sleep Quality (15 pts)**: Awarded for consistent rest duration and avoiding late-night interruptions."
    )
)

@Composable
fun UserGuideScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseAppColorBackground)
            .padding(PulseAppPaddingMedium)
            .systemBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PulseAppColorPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "User Guide & FAQs",
                style = PulseAppFontHeader,
                color = PulseAppColorPrimary
            )
        }
        
        // FAQ List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pulseFaqs) { faq ->
                FaqExpandableCard(faq)
            }
        }
    }
}

@Composable
fun FaqExpandableCard(faq: FaqItem) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
            .background(PulseAppColorSurface)
            .clickable { expanded = !expanded }
            .padding(PulseAppPaddingMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = faq.question,
                style = PulseAppFontSubHeader,
                color = PulseAppColorPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "−" else "+",
                style = PulseAppFontHeader,
                color = PulseAppColorSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = faq.answer,
                    style = PulseAppFontBody,
                    color = PulseAppColorSecondary
                )
            }
        }
    }
}
