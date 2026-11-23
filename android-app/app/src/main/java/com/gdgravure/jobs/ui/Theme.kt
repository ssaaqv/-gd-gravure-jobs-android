package com.gdgravure.jobs.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HeaderDarkBlue = Color(0xFF0F3D5E)
val HeaderMidBlue = Color(0xFF1B5E8A)
val BrandBlue = Color(0xFF1565C0)
val BrandBlueSoft = Color(0xFFE8F1FA)
val AccentOrange = Color(0xFFE65100)
val AccentOrangeSoft = Color(0xFFFFF3E0)
val SuccessGreen = Color(0xFF2E7D32)
val SuccessGreenSoft = Color(0xFFE8F5E9)
val SurfaceGray = Color(0xFFF2F4F7)
val TextPrimary = Color(0xFF1A1F2C)
val TextSecondary = Color(0xFF64748B)
val WarnBg = Color(0xFFFFF8E1)
val WarnText = Color(0xFF8D5B00)

private val AppColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = AccentOrange,
    background = SurfaceGray,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9)
)

@Composable
fun GdGravureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content
    )
}
