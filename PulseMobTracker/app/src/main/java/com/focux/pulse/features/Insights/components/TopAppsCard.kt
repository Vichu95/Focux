package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focux.pulse.data.TopAppItem
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border

@Composable
fun TopAppsCard(data: List<TopAppItem>) {
    Column(
        modifier = Modifier
            .width(com.focux.pulse.ui.theme.PulseAppCardWidth)
            .background(
                androidx.compose.ui.graphics.Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(com.focux.pulse.ui.theme.PulseAppCornerRadiusMedium)
            )
            .border(
                width = com.focux.pulse.ui.theme.PulseAppBorderWidthThick,
                color = PulseAppColorPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(com.focux.pulse.ui.theme.PulseAppCornerRadiusMedium)
            )
            .padding(com.focux.pulse.ui.theme.PulseAppPaddingSmall)
    ) {
        Text(
            text = "Top Apps",
            style = com.focux.pulse.ui.theme.PulseAppFontLabel.copy(color = PulseAppColorPrimary)
        )

        Spacer(modifier = Modifier.height(com.focux.pulse.ui.theme.PulseAppPaddingMedium))

        data.forEach { item ->
            Text(
                text = "${item.rank}. ${item.app.name} (${item.type}) - ${item.duration}",
                style = com.focux.pulse.ui.theme.PulseAppFontFocus,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
