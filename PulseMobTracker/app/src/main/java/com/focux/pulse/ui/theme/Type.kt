package com.focux.pulse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using Default fonts for now, but configured for the look
// PulseApp Typography
// Using Default fonts for now, but configured for the look
// PulseApp Typography
val PulseAppFontHeader = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = PulseAppFontSizeHeader,
    color = PulseAppColorPrimary
)

val PulseAppFontSubHeader = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = PulseAppFontSizeSubHeader,
    color = PulseAppColorSecondary
)

val PulseAppFontLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = PulseAppFontSizeLabel,
    lineHeight = 24.sp,
    color = PulseAppColorSecondary
)

val PulseAppFontBody = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = PulseAppFontSizeBody,
    color = PulseAppColorSecondary
)

val PulseAppFontBigNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = PulseAppFontSizeBigNumber,
    color = PulseAppColorSecondary
)

val Typography = Typography(
    bodyLarge = PulseAppFontBody,
    titleLarge = PulseAppFontHeader,
    labelSmall = PulseAppFontBody
)