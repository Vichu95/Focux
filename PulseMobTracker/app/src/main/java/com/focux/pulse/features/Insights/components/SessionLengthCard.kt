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

@Composable
fun SessionLengthCard(data: SessionLengthData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pulseAppCard()
            .padding(16.dp)
    ) {
        Text(
            text = "Average Session Length",
            style = Typography.titleLarge,
            color = PulseAppColorPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.clock_icon),
                contentDescription = "Time",
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = "Overall : ${data.overall}", style = Typography.labelSmall, color = PulseAppColorSecondary)
                Text(text = "Productive : ${data.productive}", style = Typography.labelSmall, color = PulseAppColorSecondary)
                Text(text = "Distracting : ${data.distracting}", style = Typography.labelSmall, color = PulseAppColorSecondary)
            }
        }
    }
}
