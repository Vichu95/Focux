package com.focux.pulse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using Default fonts for now, but configured for the look
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace, // Using Monospace for that hacker/cyberpunk vibe for numbers
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = CyberpunkPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = CyberpunkPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, // Clean sans for small labels
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = CyberpunkSecondaryText
    )
)