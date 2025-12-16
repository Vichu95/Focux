package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.data.RoutineData
import com.focux.pulse.ui.theme.CyberpunkPrimary
import com.focux.pulse.ui.theme.CyberpunkSecondaryText
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.cyberpunkCard

@Composable
fun RoutineCard(data: RoutineData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberpunkCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Routine",
            style = Typography.titleLarge,
            color = CyberpunkPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Morning
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.sun_icon),
                        contentDescription = "Morning",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Morning", style = Typography.labelSmall, color = CyberpunkSecondaryText)
                        Text(text = "Habit", style = Typography.labelSmall, color = CyberpunkSecondaryText)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.instagram_logo),
                        contentDescription = "App",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${data.morningHabit.first.name} (${data.morningHabit.second}x)",
                        style = Typography.labelSmall,
                        color = CyberpunkSecondaryText
                    )
                }
            }
            
            // Should be a vertical divider here really
            
            // Night
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.moon_icon),
                        contentDescription = "Night",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Night", style = Typography.labelSmall, color = CyberpunkSecondaryText)
                        Text(text = "Habit", style = Typography.labelSmall, color = CyberpunkSecondaryText)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.gmail_logo),
                        contentDescription = "App",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${data.nightHabit.first.name} (${data.nightHabit.second}x)",
                        style = Typography.labelSmall,
                        color = CyberpunkSecondaryText
                    )
                }
            }
        }
    }
}
