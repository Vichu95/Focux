package com.focux.pulse.features.Summary.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.data_manager.FirstLastAppsData
import com.focux.pulse.ui.theme.*

@Composable
fun FirstLastAppsCard(data: FirstLastAppsData) {
    // Frame First and Last App (CSS Specs)
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .height(PulseAppFirstAndLastAppCardHeight) // 171dp
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(PulseAppPaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "First & Last Apps",
            style = PulseAppFontHeader,
            color = PulseAppColorPrimary,
            modifier = Modifier.fillMaxWidth().align(Alignment.Start)
        )

        // Main Content Row (Centered vertically in remaining space)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First App (Morning) Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sun_icon),
                    contentDescription = "Morning",
                    modifier = Modifier.size(PulseAppIconSizeLarge) // 48dp approx
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = data.morningTime,
                        style = PulseAppFontSubHeader, // 16sp
                        color = PulseAppColorSecondary // White
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // Gap 16px
                    Image(
                        painter = painterResource(id = if(data.morningApp.iconName.contains("instagram")) R.drawable.instagram_logo else R.drawable.temp_icon),
                        contentDescription = data.morningApp.name,
                        modifier = Modifier.size(PulseAppIconSizeMedium) // 36dp
                    )
                }
            }

            // Vertical Divider (Rectangle 4)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(97.dp)
                    .background(PulseAppColorSurface) // #2B4555
            )

            // Last App (Night) Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.moon_icon),
                    contentDescription = "Night",
                    modifier = Modifier.size(39.dp) // CSS says 39x39 for moon
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = data.nightTime,
                        style = PulseAppFontSubHeader, // 16sp
                        color = PulseAppColorSecondary // White
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // Gap 16px
                    Image(
                        painter = painterResource(id = if(data.nightApp.iconName.contains("gmail")) R.drawable.gmail_logo else R.drawable.temp_icon),
                        contentDescription = data.nightApp.name,
                        modifier = Modifier.size(PulseAppIconSizeMedium) // 36dp
                    )
                }
            }
        }
    }
}

