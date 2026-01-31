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
fun DeepWorkInsightCard(duration: String) {
    Row(
        modifier = Modifier
            .width(com.focux.pulse.ui.theme.PulseAppCardWidth)
            .heightIn(min = 86.dp)
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
            contentDescription = "Deep Work",
            modifier = Modifier.size(PulseAppIconSizeLarge), // 48dp
            tint = PulseAppColorPrimary
        )
        Spacer(modifier = Modifier.width(20.dp)) // Gap 20px
        Column(
            modifier = Modifier
                .width(280.dp), // Width 280px from CSS
            verticalArrangement = Arrangement.Center // Center content vertically within the text container
        ) {
            Text(
                text = duration,
                style = com.focux.pulse.ui.theme.PulseAppFontBody.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    color = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    }
}
