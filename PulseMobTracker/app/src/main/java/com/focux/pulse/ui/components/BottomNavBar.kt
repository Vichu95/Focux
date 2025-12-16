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
import com.focux.pulse.ui.theme.PulseAppBottomBarHeight
import com.focux.pulse.ui.theme.PulseAppFontSubHeader
import com.focux.pulse.ui.theme.PulseAppIconSizeLarge
import com.focux.pulse.ui.theme.PulseAppPaddingMedium
import com.focux.pulse.ui.theme.PulseAppPaddingTiny

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
            .padding(horizontal = PulseAppPaddingMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab(
             iconRes = R.drawable.summary_icon, 
             label = "Summary", 
             selected = selectedTab == 0, 
             onClick = { onTabSelected(0) }
        )
        NavTab(
             iconRes = R.drawable.timeline_icon, 
             label = "Timeline", 
             selected = selectedTab == 1, 
             onClick = { onTabSelected(1) }
        )
        NavTab(
             iconRes = R.drawable.insights_icon, 
             label = "Insights", 
             selected = selectedTab == 2, 
             onClick = { onTabSelected(2) }
        )
    }
}

@Composable
fun NavTab(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val contentColor = if (selected) PulseAppColorPrimary else PulseAppColorSecondary
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PulseAppPaddingTiny),
        modifier = Modifier
            .padding(vertical = PulseAppPaddingMedium)
            .clickable { onClick() }
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(PulseAppIconSizeLarge)
        )
        Text(
            text = label,
            style = PulseAppFontSubHeader,
            color = contentColor
        )
    }
}
