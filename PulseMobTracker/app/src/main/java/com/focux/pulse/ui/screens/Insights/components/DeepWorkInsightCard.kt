package com.focux.pulse.ui.screens.Insights.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.pulseAppCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.focux.pulse.ui.theme.PulseAppFontLabel
import com.focux.pulse.ui.theme.PulseAppIconSizeLarge
import com.focux.pulse.ui.theme.PulseAppPaddingLarge
import androidx.compose.ui.unit.sp
import com.focux.pulse.ui.theme.PulseAppColorPrimary

@Composable
fun DeepWorkInsightCard() {
    Row(
        modifier = Modifier
            .width(com.focux.pulse.ui.theme.PulseAppCardWidth)
            .height(80.dp)
            .background(
                androidx.compose.ui.graphics.Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(com.focux.pulse.ui.theme.PulseAppCornerRadiusMedium)
            )
            .border(
                width = com.focux.pulse.ui.theme.PulseAppBorderWidthThick,
                color = PulseAppColorPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(com.focux.pulse.ui.theme.PulseAppCornerRadiusMedium)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(id = R.drawable.search_icon),
            contentDescription = "Search",
            modifier = Modifier.size(PulseAppIconSizeLarge),
            tint = PulseAppColorPrimary
        )
        Spacer(modifier = Modifier.width(PulseAppPaddingLarge))
        Text(
            text = "Deep Work is up to 20% compared to last week.",
            style = PulseAppFontLabel
        )
    }
}
