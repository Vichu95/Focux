package com.focux.pulse.ui.screens.Insights.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focux.pulse.R
import com.focux.pulse.utilities.AppInfoHelper
import com.focux.pulse.utilities.AppUsage
import com.focux.pulse.utilities.RoutineData
import com.focux.pulse.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = packageName) {
        withContext(Dispatchers.IO) {
            val drawable = AppInfoHelper.getAppIcon(context, packageName)
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: (drawable as? android.graphics.drawable.AdaptiveIconDrawable)?.let {
                    val bmp = android.graphics.Bitmap.createBitmap(
                        it.intrinsicWidth.takeIf { w -> w > 0 } ?: 100,
                        it.intrinsicHeight.takeIf { h -> h > 0 } ?: 100,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    it.setBounds(0, 0, canvas.width, canvas.height)
                    it.draw(canvas)
                    bmp
                } ?: run {
                    if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                         val bmp = android.graphics.Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    } else {
                        null
                    }
                }

            value = bitmap?.asImageBitmap()
        }
    }

    if (iconBitmap.value != null) {
        Image(
            bitmap = iconBitmap.value!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(Color.Gray, CircleShape))
    }
}

@Composable
fun RoutineCard(data: RoutineData) {
    Column(
        modifier = Modifier
            .width(PulseAppCardWidth)
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(
                horizontal = PulseAppPaddingMedium,
                vertical = PulseAppPaddingSmall
            )
    ) {
        Text(
            text = "Routine",
            style = PulseAppFontLabel.copy(
                color = PulseAppColorPrimary,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(PulseAppGapSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Morning
            RoutineSection(
                title = "Morning\nHabit",
                iconRes = R.drawable.sun_icon,
                iconSize = PulseAppIconSizeLarge,
                appUsage = data.morningHabit.first,
                count = data.morningHabit.second,
                modifier = Modifier.weight(1f)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(PulseAppDividerHeight)
                    .background(PulseAppColorSurface)
            )

            // Night
            RoutineSection(
                title = "Night\nHabit",
                iconRes = R.drawable.moon_icon,
                iconSize = 39.dp,
                appUsage = data.nightHabit.first,
                count = data.nightHabit.second,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoutineSection(
    title: String,
    iconRes: Int,
    iconSize: Dp,
    appUsage: AppUsage,
    count: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top Row: Icon + Habit Text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(PulseAppGapTiny))
            Text(
                text = title,
                style = PulseAppFontSubHeader,
                textAlign = TextAlign.Center,
                color = PulseAppColorSecondary
            )
        }

        Spacer(modifier = Modifier.height(PulseAppPaddingSmall))

        // Bottom Row: App Icon + Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val iconName = appUsage.iconName
            if (!iconName.isNullOrEmpty()) {
                AppIconImage(
                    packageName = iconName,
                    modifier = Modifier.size(PulseAppIconSizeMedium)
                )
            } else {
                 Image(
                    painter = painterResource(id = R.drawable.temp_icon),
                    contentDescription = null,
                    modifier = Modifier.size(PulseAppIconSizeMedium)
                )
            }

            Spacer(modifier = Modifier.width(PulseAppPaddingSmall))
            Text(
                text = "${appUsage.name} (${count}x)",
                style = PulseAppFontBody,
                textAlign = TextAlign.Center,
                color = PulseAppColorSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(PulseAppRoutineAppTextWidth)
            )
        }
    }
}
