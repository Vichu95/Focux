package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.data.FirstLastAppsData
import com.focux.pulse.ui.theme.CyberpunkPrimary
import com.focux.pulse.ui.theme.CyberpunkSecondaryText
import com.focux.pulse.ui.theme.Typography
import com.focux.pulse.ui.theme.cyberpunkCard

@Composable
fun FirstLastAppsCard(data: FirstLastAppsData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberpunkCard()
            .padding(16.dp)
    ) {
        Text(
            text = "First & Last Apps",
            style = Typography.titleLarge,
            color = CyberpunkPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // First App (Morning)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.sun_icon),
                    contentDescription = "Morning",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.morningTime,
                        style = Typography.labelSmall,
                        color = CyberpunkSecondaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        painter = painterResource(id = if(data.morningApp.iconName.contains("instagram")) R.drawable.instagram_logo else R.drawable.temp_icon),
                        contentDescription = data.morningApp.name,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Last App (Night)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.moon_icon),
                    contentDescription = "Night",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data.nightTime,
                        style = Typography.labelSmall,
                        color = CyberpunkSecondaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        painter = painterResource(id = if(data.nightApp.iconName.contains("gmail")) R.drawable.gmail_logo else R.drawable.temp_icon),
                        contentDescription = data.nightApp.name,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
