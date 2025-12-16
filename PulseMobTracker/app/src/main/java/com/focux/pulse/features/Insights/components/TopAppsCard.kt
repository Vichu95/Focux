package com.focux.pulse.features.Insights.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focux.pulse.data.TopAppItem
import com.focux.pulse.ui.theme.CyberpunkPrimary
import com.focux.pulse.ui.theme.CyberpunkSecondaryText
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.cyberpunkCard
import androidx.compose.ui.unit.sp

@Composable
fun TopAppsCard(data: List<TopAppItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberpunkCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Top Apps",
            style = Typography.titleLarge,
            color = CyberpunkPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        data.forEach { item ->
            Text(
                text = "${item.rank}. ${item.app.name} (${item.type}) - ${item.duration}",
                style = Typography.labelSmall.copy(fontSize = 14.sp),
                color = CyberpunkSecondaryText,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
