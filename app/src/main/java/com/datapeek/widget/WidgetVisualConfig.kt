package com.datapeek.widget

import kotlin.math.min
import kotlin.math.roundToInt

internal enum class ResolvedForegroundMode {
    LIGHT,
    DARK,
}

internal data class WidgetVisualConfig(
    val foregroundMode: ResolvedForegroundMode,
    val primaryTextArgb: Int,
    val secondaryTextArgb: Int,
    val gaugeProgressArgb: Int,
    val gaugeTrackArgb: Int,
    val renderBackground: Boolean,
    val backgroundArgb: Int?,
    val backgroundAlpha: Int,
    val transparencyPercent: Int,
    val gaugeStrokeDp: Float,
)

internal fun resolveWidgetVisualConfig(
    config: WidgetConfig,
    systemDark: Boolean,
    gaugeDiameterDp: Int,
    sizeClass: WidgetSizeClass,
): WidgetVisualConfig {
    val foregroundMode = when (config.theme) {
        WidgetTheme.SYSTEM -> if (systemDark) ResolvedForegroundMode.LIGHT else ResolvedForegroundMode.DARK
        WidgetTheme.LIGHT_FOREGROUND -> ResolvedForegroundMode.LIGHT
        WidgetTheme.DARK_FOREGROUND -> ResolvedForegroundMode.DARK
    }
    val colors = when (foregroundMode) {
        ResolvedForegroundMode.LIGHT -> ForegroundColors(
            primary = 0xFFF7F9FC.toInt(),
            secondary = 0xFFD8DFEA.toInt(),
            progress = 0xFF9DB5FF.toInt(),
            track = 0xFF8793A6.toInt(),
            background = 0xFF161A22.toInt(),
        )
        ResolvedForegroundMode.DARK -> ForegroundColors(
            primary = 0xFF11151C.toInt(),
            secondary = 0xFF3D4756.toInt(),
            progress = 0xFF2857D6.toInt(),
            track = 0xFF697587.toInt(),
            background = 0xFFE9EDF4.toInt(),
        )
    }
    val backgroundAlpha = if (config.backgroundEnabled) {
        resolveBackgroundAlpha(config.safeOpacityPercent)
    } else {
        0
    }
    return WidgetVisualConfig(
        foregroundMode = foregroundMode,
        primaryTextArgb = colors.primary,
        secondaryTextArgb = colors.secondary,
        gaugeProgressArgb = colors.progress,
        gaugeTrackArgb = colors.track,
        renderBackground = config.backgroundEnabled,
        backgroundArgb = if (config.backgroundEnabled) colors.background.withAlpha(backgroundAlpha) else null,
        backgroundAlpha = backgroundAlpha,
        transparencyPercent = config.safeOpacityPercent,
        gaugeStrokeDp = calculateGaugeStrokeDp(gaugeDiameterDp, sizeClass),
    )
}

internal fun resolveBackgroundAlpha(transparencyPercent: Int): Int =
    (255f * (1f - transparencyPercent.coerceIn(0, 100) / 100f)).roundToInt()

internal fun calculateGaugeStrokeDp(gaugeDiameterDp: Int, sizeClass: WidgetSizeClass): Float {
    val diameter = gaugeDiameterDp.coerceAtLeast(1).toFloat()
    val range = when (sizeClass) {
        WidgetSizeClass.MICRO -> 8f..10f
        WidgetSizeClass.SMALL -> 10f..12f
        WidgetSizeClass.MEDIUM -> 14f..17f
        WidgetSizeClass.LARGE -> 16f..20f
        WidgetSizeClass.XLARGE -> 18f..24f
    }
    return (diameter * 0.16f)
        .coerceIn(range.start, range.endInclusive)
        .coerceAtMost((diameter / 2f - 0.5f).coerceAtLeast(0.25f))
}

internal fun legacyGaugeStrokeDp(gaugeDiameterDp: Int, density: Float): Float {
    if (density <= 0f || !density.isFinite()) return 0f
    val diameterPx = gaugeDiameterDp.coerceAtLeast(1) * density
    return min(diameterPx * 0.12f, 18f) / density
}

private data class ForegroundColors(
    val primary: Int,
    val secondary: Int,
    val progress: Int,
    val track: Int,
    val background: Int,
)

private fun Int.withAlpha(alpha: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or (this and 0x00FFFFFF)
