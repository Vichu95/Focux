package com.focux.pulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import com.focux.pulse.ui.theme.PulseAppColorSecondary
import com.focux.pulse.ui.theme.Typography

@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = if (selected) PulseAppColorPrimary else PulseAppColorSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = Typography.labelSmall,
            color = if (selected) PulseAppColorPrimary else PulseAppColorSecondary
        )
    }
}
