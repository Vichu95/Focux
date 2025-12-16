package com.focux.pulse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using Default fonts for now, but configured for the look
// PulseApp Typography
val PulseAppFontHeader = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    color = PulseAppColorPrimary
)

val PulseAppFontSubHeader = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    color = PulseAppColorSecondary
)

val PulseAppFontBody = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    color = PulseAppColorSecondary
)

val PulseAppFontBigNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    color = PulseAppColorSecondary
)

val Typography = Typography(
    bodyLarge = PulseAppFontBody,
    titleLarge = PulseAppFontHeader,
    labelSmall = PulseAppFontBody
)