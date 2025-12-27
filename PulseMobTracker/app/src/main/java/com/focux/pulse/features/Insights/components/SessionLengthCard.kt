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
import com.focux.pulse.data.SessionLengthData
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border

@Composable
fun SessionLengthCard(data: SessionLengthData) {
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
            .padding(com.focux.pulse.ui.theme.PulseAppPaddingMedium)
    ) {
        Text(
            text = "Average Session Length",
            style = com.focux.pulse.ui.theme.PulseAppFontLabel.copy(color = PulseAppColorPrimary)
        )

        Spacer(modifier = Modifier.height(com.focux.pulse.ui.theme.PulseAppPaddingMedium))

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.drawable.clock_icon),
                contentDescription = "Time",
                modifier = Modifier.size(com.focux.pulse.ui.theme.PulseAppIconSizeLarge),
                tint = PulseAppColorPrimary
            )
            Spacer(modifier = Modifier.width(com.focux.pulse.ui.theme.PulseAppGapMedium))
            Column {
                Text(text = "Overall : ${data.overall}", style = com.focux.pulse.ui.theme.PulseAppFontFocus)
                Text(text = "Productive : ${data.productive}", style = com.focux.pulse.ui.theme.PulseAppFontFocus)
                Text(text = "Distracting : ${data.distracting}", style = com.focux.pulse.ui.theme.PulseAppFontFocus)
            }
        }
    }
}
