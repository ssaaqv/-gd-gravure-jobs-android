package com.gdgravure.jobs.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandBlue = Color(0xFF1B4F72)
val AccentOrange = Color(0xFFD35400)
val SurfaceGray = Color(0xFFF4F6F8)
val WarnBg = Color(0xFFFFF3CD)
val WarnText = Color(0xFF856404)

private val AppColors = lightColorScheme(
    primary = BrandBlue,
    secondary = AccentOrange,
    background = SurfaceGray,
    surface = Color.White
)

@Composable
fun GdGravureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content
    )
}
