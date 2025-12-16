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

@Composable
fun TopAppsCard(data: List<TopAppItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pulseAppCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Top Apps",
            style = Typography.titleLarge,
            color = PulseAppColorPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        data.forEach { item ->
            Text(
                text = "${item.rank}. ${item.app.name} (${item.type}) - ${item.duration}",
                style = Typography.labelSmall.copy(fontSize = 14.sp),
                color = PulseAppColorSecondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
