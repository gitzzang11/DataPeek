package com.datapeek.widget

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetOpenRingTest {
    @Test
    fun gaugeUsesAnOpenBottomCircularRing() {
        assertEquals(135f, GAUGE_OPEN_RING_START_ANGLE_DEGREES, 0.001f)
        assertEquals(270f, GAUGE_OPEN_RING_SWEEP_DEGREES, 0.001f)
        assertTrue(GAUGE_OPEN_RING_SWEEP_DEGREES > 180f)
        assertTrue(GAUGE_OPEN_RING_SWEEP_DEGREES < 360f)
        assertEquals(0f, gaugeProgressSweepDegrees(0f), 0.001f)
        assertEquals(135f, gaugeProgressSweepDegrees(0.5f), 0.001f)
        assertEquals(270f, gaugeProgressSweepDegrees(1f), 0.001f)
    }

    @Test
    fun openRingStartsAndEndsBelowCenterWhilePassingThroughTheTop() {
        val start = pointAtAngle(GAUGE_OPEN_RING_START_ANGLE_DEGREES)
        val end = pointAtAngle(GAUGE_OPEN_RING_START_ANGLE_DEGREES + GAUGE_OPEN_RING_SWEEP_DEGREES)

        assertTrue(start.second > 0f)
        assertTrue(end.second > 0f)
        assertTrue(angleIsInClockwiseSweep(270f, GAUGE_OPEN_RING_START_ANGLE_DEGREES, GAUGE_OPEN_RING_SWEEP_DEGREES))
        assertFalse(angleIsInClockwiseSweep(90f, GAUGE_OPEN_RING_START_ANGLE_DEGREES, GAUGE_OPEN_RING_SWEEP_DEGREES))
    }

    @Test
    fun openRingGeometryIsCircularAndNeverClipsItsVisibleArc() {
        val width = 60f
        val height = 54f
        val stroke = 10f
        val geometry = calculateGaugeGeometry(width, height, safePadding = stroke / 2f + 2f)

        assertEquals(geometry.boundsWidth, geometry.boundsHeight, 0.001f)
        for (angle in 135..405) {
            val radians = Math.toRadians(angle.toDouble())
            val x = geometry.centerX + geometry.radius * cos(radians).toFloat()
            val y = geometry.centerY + geometry.radius * sin(radians).toFloat()
            assertTrue("left crop at $angle degrees", x - stroke / 2f >= 0f)
            assertTrue("right crop at $angle degrees", x + stroke / 2f <= width)
            assertTrue("top crop at $angle degrees", y - stroke / 2f >= 0f)
            assertTrue("bottom crop at $angle degrees", y + stroke / 2f <= height)
        }
    }

    @Test
    fun everyRepresentativeResizeKeepsTheOpenRingCircularAndVisible() {
        val representativeDp = listOf(82, 180, 280, 380, 500, 620)

        representativeDp.forEach { width ->
            representativeDp.forEach { height ->
                val plan = widgetLayoutPlan(width, height, WidgetStyle.GAUGE, WidgetConfig())
                val stroke = calculateGaugeStrokeDp(plan.gaugeDiameterDp, plan.sizeClass)
                val safePadding = stroke / 2f + 2f
                val geometry = calculateGaugeGeometry(
                    plan.gaugeWidthDp.toFloat(),
                    plan.gaugeHeightDp.toFloat(),
                    safePadding = safePadding,
                )

                assertEquals(geometry.boundsWidth, geometry.boundsHeight, 0.001f)
                for (angle in 135..405) {
                    val radians = Math.toRadians(angle.toDouble())
                    val x = geometry.centerX + geometry.radius * cos(radians).toFloat()
                    val y = geometry.centerY + geometry.radius * sin(radians).toFloat()
                    assertTrue("left crop at ${width}x$height, $angle°", x - stroke / 2f >= 0f)
                    assertTrue("right crop at ${width}x$height, $angle°", x + stroke / 2f <= plan.gaugeWidthDp)
                    assertTrue("top crop at ${width}x$height, $angle°", y - stroke / 2f >= 0f)
                    assertTrue("bottom crop at ${width}x$height, $angle°", y + stroke / 2f <= plan.gaugeHeightDp)
                }
            }
        }
    }

    @Test
    fun twoByOneHasADedicatedDenseDashboardForInfoAndGauge() {
        val info = widgetLayoutPlan(180, 82, WidgetStyle.INFO, WidgetConfig())
        val gauge = widgetLayoutPlan(180, 82, WidgetStyle.GAUGE, WidgetConfig())

        assertEquals(WidgetLayoutKind.COMPACT_WIDE_DASHBOARD, info.kind)
        assertTrue(info.compactValues)
        assertTrue(info.contentPlan.showRemaining)
        assertTrue(info.contentPlan.showUsed)
        assertTrue(info.contentPlan.showTotal)
        assertTrue(info.typography.remainingSp >= 20)

        assertEquals(WidgetLayoutKind.COMPACT_WIDE_DASHBOARD, gauge.kind)
        assertEquals(GaugePlacement.START, gauge.gaugePlacement)
        assertTrue(gauge.contentPlan.showUsed)
        assertTrue(gauge.contentPlan.showTotal)
        assertTrue(gauge.gaugeWidthDp <= 62)
        assertTrue(gauge.gaugeHeightDp <= 62)
        assertTrue(gauge.gaugeWidthDp < 180 * 0.45f)
        assertTrue(calculateGaugeStrokeDp(gauge.gaugeDiameterDp, gauge.sizeClass) >= 10f)
    }

    @Test
    fun oneByOneNoWrapAndTwoByTwoDensityRemainIntact() {
        val oneByOne = widgetLayoutPlan(82, 82, WidgetStyle.GAUGE, WidgetConfig())
        val twoByTwo = widgetLayoutPlan(180, 180, WidgetStyle.GAUGE, WidgetConfig())

        assertEquals(WidgetLayoutKind.MICRO, oneByOne.kind)
        assertTrue(oneByOne.compactValues)
        assertFalse(formatWidgetMicroBytes(19L * 1024 * 1024 * 1024).contains("\n"))
        assertTrue(twoByTwo.contentPlan.showUsed)
        assertTrue(twoByTwo.contentPlan.showTotal)
    }

    private fun pointAtAngle(angle: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angle.toDouble())
        return cos(radians).toFloat() to sin(radians).toFloat()
    }

    private fun angleIsInClockwiseSweep(angle: Float, start: Float, sweep: Float): Boolean {
        val normalized = ((angle - start) % 360f + 360f) % 360f
        return normalized <= sweep
    }
}
