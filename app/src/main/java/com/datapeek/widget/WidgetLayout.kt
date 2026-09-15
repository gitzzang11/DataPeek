package com.datapeek.widget

import com.datapeek.data.util.DataUnit
import com.datapeek.data.util.formatBytes
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val GAUGE_OPEN_RING_START_ANGLE_DEGREES = 135f
internal const val GAUGE_OPEN_RING_SWEEP_DEGREES = 270f
internal const val GAUGE_OPEN_RING_BITMAP_HEIGHT_RATIO = 0.88f
private val GAUGE_OPEN_RING_VERTICAL_RADIUS_FACTOR = 1f + sqrt(0.5f)

internal fun formatWidgetMicroBytes(bytes: Long?, preferredUnit: DataUnit? = null): String {
    val formatted = formatBytes(bytes, preferredUnit)
    if (formatted == "—") return formatted

    val number = formatted.substringBefore(' ')
    return when (formatted.substringAfter(' ', "")) {
        "GB" -> "${number}G"
        "MB" -> "${number.substringBefore('.')}M"
        "KB" -> "${number.substringBefore('.')}K"
        "B" -> "${number}B"
        else -> formatted.replace(" ", "")
    }
}

internal data class GaugeGeometry(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val boundsWidth: Float
        get() = right - left
    val boundsHeight: Float
        get() = bottom - top
}

internal fun calculateGaugeGeometry(
    availableWidth: Float,
    availableHeight: Float,
    gaugeTop: Float = 0f,
    safePadding: Float = 0f,
): GaugeGeometry {
    require(availableWidth > 0f) { "availableWidth must be positive" }
    require(availableHeight > 0f) { "availableHeight must be positive" }
    require(safePadding >= 0f) { "safePadding must not be negative" }
    require(safePadding * 2f < min(availableWidth, availableHeight)) {
        "safePadding leaves no drawable gauge area"
    }
    val drawableWidth = availableWidth - safePadding * 2f
    val drawableHeight = availableHeight - safePadding * 2f
    val radius = min(drawableWidth / 2f, drawableHeight / GAUGE_OPEN_RING_VERTICAL_RADIUS_FACTOR)
    val centerX = availableWidth / 2f
    val centerY = gaugeTop + safePadding + radius
    return GaugeGeometry(
        centerX = centerX,
        centerY = centerY,
        radius = radius,
        left = centerX - radius,
        top = centerY - radius,
        right = centerX + radius,
        bottom = centerY + radius,
    )
}

internal fun gaugeProgressSweepDegrees(remainingFraction: Float): Float =
    remainingFraction.takeIf { it.isFinite() }
        ?.coerceIn(0f, 1f)
        ?.times(GAUGE_OPEN_RING_SWEEP_DEGREES)
        ?: 0f

internal fun calculateGaugeRemainingFraction(remainingBytes: Long?, totalBytes: Long?): Float {
    if (remainingBytes == null || totalBytes == null || remainingBytes <= 0L || totalBytes <= 0L) return 0f
    return (remainingBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal data class GaugeCenterTextPlan(
    val label: String,
    val fontSizeSp: Int,
)

internal fun gaugeCenterTextPlan(
    remainingPercent: Float,
    gaugeDiameterDp: Int,
    sizeClass: WidgetSizeClass,
): GaugeCenterTextPlan {
    val safePercent = remainingPercent.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
    val label = "${safePercent.roundToInt()}%"
    val (preferredMinimum, maximum, readableMinimum) = when (sizeClass) {
        WidgetSizeClass.MICRO -> Triple(15, 17, 12)
        WidgetSizeClass.SMALL -> Triple(17, 20, 14)
        WidgetSizeClass.MEDIUM -> Triple(19, 23, 16)
        WidgetSizeClass.LARGE -> Triple(22, 27, 18)
        WidgetSizeClass.XLARGE -> Triple(24, 30, 20)
    }
    val proportionalSize = (gaugeDiameterDp.coerceAtLeast(1) * 0.30f)
        .roundToInt()
        .coerceIn(preferredMinimum, maximum)
    val widthAdjustment = when (label.length) {
        4 -> 2
        3 -> 1
        else -> 0
    }
    return GaugeCenterTextPlan(
        label = label,
        fontSizeSp = (proportionalSize - widthAdjustment).coerceAtLeast(readableMinimum),
    )
}
