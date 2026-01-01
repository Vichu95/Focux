package com.focux.pulse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.ui.theme.PulseAppColorBackground
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import androidx.compose.ui.unit.Dp
import com.focux.pulse.ui.theme.PulseAppBottomBarHeight
import com.focux.pulse.ui.theme.PulseAppFontSubHeader
import com.focux.pulse.ui.theme.PulseAppPaddingMedium
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.focux.pulse.ui.theme.PulseAppBottomBarBottomPadding
import com.focux.pulse.ui.theme.PulseAppIconSizeLarge
@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PulseAppBottomBarHeight)
            .background(PulseAppColorBackground)
            .padding(start = PulseAppPaddingMedium, end = PulseAppPaddingMedium, bottom = PulseAppBottomBarBottomPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Summary Tab: Icon 34dp
        NavTab(
             iconRes = R.drawable.summary_icon, 
             label = "Summary", 
             selected = selectedTab == 0,
             iconWidth = 34.dp,
             iconHeight = 34.dp,
             onClick = { onTabSelected(0) }
        )
        // Timeline Tab: Icon 44x24dp
        NavTab(
             iconRes = R.drawable.timeline_icon, 
             label = "Timeline", 
             selected = selectedTab == 1,
             iconWidth = 44.dp,
             iconHeight = 24.dp,
             onClick = { onTabSelected(1) }
        )
        // Insights Tab: Icon 45x48dp
        NavTab(
             iconRes = R.drawable.insights_icon, 
             label = "Insights", 
             selected = selectedTab == 2,
             iconWidth = 45.dp,
             iconHeight = 48.dp,
             onClick = { onTabSelected(2) }
        )
    }
}

@Composable
fun NavTab(
    iconRes: Int, 
    label: String, 
    selected: Boolean, 
    iconWidth: Dp,
    iconHeight: Dp,
    onClick: () -> Unit
) {
    val contentColor = if (selected) PulseAppColorPrimary else PulseAppColorSecondary
    
    // Standard gap from CSS (8px)
    val gap = 8.dp 

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .padding(vertical = PulseAppPaddingMedium) // Top padding only, bottom controlled by container
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Container box to ensure consistent height for alignment (24dp per CSS)
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(width = iconWidth, height = iconHeight)
            )
        }
        Text(
            text = label,
            style = PulseAppFontSubHeader.copy(fontSize = 12.sp),
            color = contentColor
        )
    }
}
