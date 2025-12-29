package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.data.RoutineData
import com.focux.pulse.ui.theme.*
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun RoutineCard(data: RoutineData) {
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(
                horizontal = PulseAppPaddingMedium, // 16dp
                vertical = PulseAppPaddingSmall // 8dp
            )
    ) {
        Text(
            text = "Routine",
            style = PulseAppFontLabel.copy(
                color = PulseAppColorPrimary,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(PulseAppGapSmall)) // 4dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Morning
            RoutineSection(
                title = "Morning\nHabit",
                iconRes = R.drawable.sun_icon,
                iconSize = PulseAppIconSizeLarge, // 48dp
                appUsage = data.morningHabit.first,
                count = data.morningHabit.second,
                modifier = Modifier.weight(1f)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(PulseAppDividerHeight) // 97dp
                    .background(PulseAppColorSurface)
            )

            // Night
            RoutineSection(
                title = "Night\nHabit",
                iconRes = R.drawable.moon_icon,
                iconSize = 39.dp, // Explicit 39dp
                appUsage = data.nightHabit.first,
                count = data.nightHabit.second,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoutineSection(
    title: String,
    iconRes: Int,
    iconSize: Dp,
    appUsage: com.focux.pulse.data.AppUsage,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top Row: Icon + Habit Text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(PulseAppGapTiny)) // 5dp
            Text(
                text = title,
                style = PulseAppFontSubHeader, // 16sp
                textAlign = TextAlign.Center,
                color = PulseAppColorSecondary // White
            )
        }

        Spacer(modifier = Modifier.height(PulseAppPaddingSmall)) // 8dp



        // Bottom Row: App Icon + Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = if (appUsage.iconName.contains("instagram")) R.drawable.instagram_logo else if (appUsage.iconName.contains("gmail")) R.drawable.gmail_logo else R.drawable.temp_icon),
                contentDescription = appUsage.name,
                modifier = Modifier.size(PulseAppIconSizeMedium) // 36dp
            )
            Spacer(modifier = Modifier.width(PulseAppPaddingSmall)) // 8dp
            Text(
                text = "${appUsage.name} (${count}x)",
                style = PulseAppFontBody, // 14sp
                textAlign = TextAlign.Center,
                color = PulseAppColorSecondary, // White
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(PulseAppRoutineAppTextWidth) // 110dp
            )
        }
    }
}
