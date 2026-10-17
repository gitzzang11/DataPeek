package com.datapeek.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0066CC), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF), onPrimaryContainer = Color(0xFF004C99),
    secondary = Color(0xFF626262), secondaryContainer = Color(0xFFE9E9E9),
    onSecondaryContainer = Color(0xFF171717),
    background = Color(0xFFF2F2F2), onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFEAEAEA), onSurfaceVariant = Color(0xFF626262),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEAEAEA), surfaceTint = Color.Transparent,
    outline = Color(0xFF808080), outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFCEBE9), onErrorContainer = Color(0xFF8C1D18),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF80B5FF), onPrimary = Color(0xFF002F60),
    primaryContainer = Color(0xFF143354), onPrimaryContainer = Color(0xFFCFE3FF),
    secondary = Color(0xFFB5B5B5), secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color(0xFFF7F7F7),
    background = Color.Black, onBackground = Color(0xFFF7F7F7),
    surface = Color(0xFF171717), onSurface = Color(0xFFF7F7F7),
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceContainer = Color(0xFF171717), surfaceContainerLow = Color(0xFF171717),
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF303030), surfaceTint = Color.Transparent,
    outline = Color(0xFF858585), outlineVariant = Color(0xFF353535),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF502622), onErrorContainer = Color(0xFFFFDAD6),
)
private val OneUiTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
internal fun DataPeekTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = OneUiTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
