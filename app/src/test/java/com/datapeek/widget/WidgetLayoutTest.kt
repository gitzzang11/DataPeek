package com.datapeek.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun microFormatterKeepsTheImportantValueOnOneCompactLine() {
        assertEquals("19.7G", formatWidgetMicroBytes((19.7 * 1024 * 1024 * 1024).toLong()))
        assertEquals("999M", formatWidgetMicroBytes((999.81 * 1024 * 1024).toLong()))
        assertEquals("308M", formatWidgetMicroBytes((308.33 * 1024 * 1024).toLong()))
        assertFalse(formatWidgetMicroBytes((19.7 * 1024 * 1024 * 1024).toLong()).contains("\n"))
    }

    @Test
    fun microFormatterPreservesMissingDataState() {
        assertEquals("—", formatWidgetMicroBytes(null))
        assertEquals("—", formatWidgetMicroBytes(-1L))
    }

    @Test
    fun wideGaugeUsesTrueSquareCircleBounds() {
        val geometry = calculateGaugeGeometry(500f, 128f)

        assertTrue(geometry.radius > 0f)
        assertTrue(geometry.boundsWidth < 256f)
        assertEquals(geometry.boundsWidth, geometry.boundsHeight, 0.001f)
    }

    @Test
    fun gaugeUsesTheAndroidCanvasOpenBottomRingDirection() {
        assertEquals(135f, GAUGE_OPEN_RING_START_ANGLE_DEGREES, 0.001f)
        assertEquals(270f, GAUGE_OPEN_RING_SWEEP_DEGREES, 0.001f)
        assertEquals(0f, gaugeProgressSweepDegrees(0f), 0.001f)
        assertEquals(135f, gaugeProgressSweepDegrees(0.5f), 0.001f)
        assertEquals(270f, gaugeProgressSweepDegrees(1f), 0.001f)
    }

    @Test
    fun gaugeProgressIsSafeForInvalidAndOutOfRangeFractions() {
        assertEquals(0f, gaugeProgressSweepDegrees(-1f), 0.001f)
        assertEquals(270f, gaugeProgressSweepDegrees(2f), 0.001f)
        assertEquals(0f, gaugeProgressSweepDegrees(Float.NaN), 0.001f)
        assertEquals(0f, gaugeProgressSweepDegrees(Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun gaugeSafePaddingKeepsTheVisibleTopArcInsideTheBitmap() {
        val strokeWidth = 16f
        val safePadding = strokeWidth / 2f + 2f
        val geometry = calculateGaugeGeometry(500f, 128f, safePadding = safePadding)

        assertTrue(geometry.top - strokeWidth / 2f >= 0f)
        assertTrue(geometry.centerY + strokeWidth / 2f <= 128f)
        assertTrue(geometry.left - strokeWidth / 2f >= 0f)
        assertTrue(geometry.right + strokeWidth / 2f <= 500f)
        assertEquals(geometry.boundsWidth, geometry.boundsHeight, 0.001f)
    }

    @Test
    fun widgetWidthAndHeightClassesAreIndependentOfCellCount() {
        assertEquals(WidgetWidthClass.W1, classifyWidgetWidth(82))
        assertEquals(WidgetWidthClass.W2, classifyWidgetWidth(180))
        assertEquals(WidgetWidthClass.W3, classifyWidgetWidth(280))
        assertEquals(WidgetHeightClass.H1, classifyWidgetHeight(82))
        assertEquals(WidgetHeightClass.H2, classifyWidgetHeight(180))
        assertEquals(WidgetHeightClass.H3, classifyWidgetHeight(280))
    }

    @Test
    fun widgetOrientationRecognizesWideTallAndSquareShapes() {
        assertEquals(WidgetOrientation.COMPACT_SQUARE, classifyWidgetOrientation(82, 82))
        assertEquals(WidgetOrientation.WIDE, classifyWidgetOrientation(180, 120))
        assertEquals(WidgetOrientation.ULTRA_WIDE, classifyWidgetOrientation(300, 120))
        assertEquals(WidgetOrientation.TALL, classifyWidgetOrientation(120, 180))
        assertEquals(WidgetOrientation.ULTRA_TALL, classifyWidgetOrientation(120, 300))
        assertEquals(WidgetOrientation.BALANCED, classifyWidgetOrientation(300, 300))
    }

    @Test
    fun representativeResizesSelectDensityAwareLayouts() {
        assertEquals(WidgetLayoutKind.MICRO, widgetLayoutPlan(82, 82, WidgetStyle.INFO, WidgetConfig()).kind)
        assertEquals(WidgetLayoutKind.COMPACT_WIDE_DASHBOARD, widgetLayoutPlan(180, 82, WidgetStyle.INFO, WidgetConfig()).kind)
        assertEquals(WidgetLayoutKind.TALL_DASHBOARD, widgetLayoutPlan(82, 180, WidgetStyle.INFO, WidgetConfig()).kind)
        assertEquals(WidgetLayoutKind.COMPACT_DETAIL, widgetLayoutPlan(180, 180, WidgetStyle.INFO, WidgetConfig()).kind)
        assertEquals(WidgetLayoutKind.WIDE_DASHBOARD, widgetLayoutPlan(500, 180, WidgetStyle.INFO, WidgetConfig()).kind)
        assertEquals(WidgetLayoutKind.TALL_DASHBOARD, widgetLayoutPlan(180, 500, WidgetStyle.INFO, WidgetConfig()).kind)
        assertEquals(WidgetLayoutKind.FULL_DETAIL, widgetLayoutPlan(500, 500, WidgetStyle.INFO, WidgetConfig()).kind)
    }

    @Test
    fun gaugePlacementFollowsTheSelectedShape() {
        assertEquals(
            GaugePlacement.TOP,
            widgetLayoutPlan(82, 180, WidgetStyle.GAUGE, WidgetConfig()).gaugePlacement,
        )
        assertEquals(
            GaugePlacement.START,
            widgetLayoutPlan(500, 180, WidgetStyle.GAUGE, WidgetConfig()).gaugePlacement,
        )
        assertEquals(
            GaugePlacement.TOP,
            widgetLayoutPlan(500, 500, WidgetStyle.GAUGE, WidgetConfig()).gaugePlacement,
        )
    }

    @Test
    fun representativeResizeFixturesKeepAVisibleDataPlan() {
        val representativeDp = listOf(82, 180, 280, 380, 500, 620)
        val representativeSizes = representativeDp.flatMap { width ->
            representativeDp.map { height -> width to height }
        }

        representativeSizes.forEach { (width, height) ->
            val info = widgetLayoutPlan(width, height, WidgetStyle.INFO, WidgetConfig())
            val gauge = widgetLayoutPlan(width, height, WidgetStyle.GAUGE, WidgetConfig())
            assertTrue(info.contentPlan.showRemaining)
            assertTrue(gauge.contentPlan.showRemaining)
            assertTrue(gauge.contentPlan.showGauge)
            assertTrue(gauge.gaugeDiameterDp > 0)
            assertTrue(gauge.gaugeDiameterDp <= width)
            assertTrue(gauge.gaugeDiameterDp <= height * 2)
            assertTrue(gauge.gaugePlacement == GaugePlacement.TOP || gauge.gaugePlacement == GaugePlacement.START)
        }
    }

    @Test
    fun everyRepresentativeGaugeKeepsACircularUnclippedTopArc() {
        val representativeDp = listOf(82, 180, 280, 380, 500, 620)

        representativeDp.forEach { width ->
            representativeDp.forEach { height ->
                val plan = widgetLayoutPlan(width, height, WidgetStyle.GAUGE, WidgetConfig())
                val bitmapWidth = plan.gaugeWidthDp.toFloat()
                val bitmapHeight = plan.gaugeHeightDp.toFloat()
                val strokeWidth = calculateGaugeStrokeDp(plan.gaugeDiameterDp, plan.sizeClass)
                val safePadding = strokeWidth / 2f + 2f
                val geometry = calculateGaugeGeometry(bitmapWidth, bitmapHeight, safePadding = safePadding)

                assertEquals(geometry.boundsWidth, geometry.boundsHeight, 0.001f)
                assertTrue(strokeWidth < geometry.radius)
                assertTrue(geometry.top - strokeWidth / 2f >= 0f)
                assertTrue(geometry.centerY + strokeWidth / 2f <= bitmapHeight)
                assertTrue(geometry.left - strokeWidth / 2f >= 0f)
                assertTrue(geometry.right + strokeWidth / 2f <= bitmapWidth)
            }
        }
    }

    @Test
    fun mediumAndLargeLayoutsUseTheAvailableInformationCapacity() {
        val square2x2 = widgetLayoutPlan(180, 180, WidgetStyle.INFO, WidgetConfig()).contentPlan
        assertTrue(square2x2.showUsed)
        assertTrue(square2x2.showTotal)
        assertTrue(square2x2.showUpdated)

        val wide3x2 = widgetLayoutPlan(280, 180, WidgetStyle.INFO, WidgetConfig()).contentPlan
        assertTrue(wide3x2.showPercent)
        assertTrue(wide3x2.showUpdated)

        val tall2x3 = widgetLayoutPlan(180, 280, WidgetStyle.INFO, WidgetConfig()).contentPlan
        assertTrue(tall2x3.showPercent)
        assertTrue(tall2x3.showUpdated)

        val square5x5 = widgetLayoutPlan(500, 500, WidgetStyle.INFO, WidgetConfig()).contentPlan
        assertTrue(square5x5.showCarrier)
        assertTrue(square5x5.showStatus)
        assertTrue(square5x5.showRefresh)
    }

    @Test
    fun typographyGrowsWithoutUsingTheLargestTypeInShortWidgets() {
        val oneByOne = widgetLayoutPlan(82, 82, WidgetStyle.INFO, WidgetConfig()).typography
        val sixByOne = widgetLayoutPlan(620, 82, WidgetStyle.INFO, WidgetConfig()).typography
        val sixBySix = widgetLayoutPlan(620, 620, WidgetStyle.INFO, WidgetConfig()).typography

        assertTrue(sixBySix.remainingSp > oneByOne.remainingSp)
        assertTrue(sixByOne.remainingSp < sixBySix.remainingSp)
        assertTrue(oneByOne.metricSp <= sixBySix.metricSp)
    }

    @Test
    fun tallGaugeKeepsTheCircleWithinTheAvailableWidth() {
        val geometry = calculateGaugeGeometry(200f, 500f)

        assertEquals(100f, geometry.radius, 0.001f)
        assertEquals(200f, geometry.boundsWidth, 0.001f)
        assertEquals(200f, geometry.boundsHeight, 0.001f)
    }

    @Test
    fun invalidGaugeDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { calculateGaugeGeometry(0f, 100f) }
        assertThrows(IllegalArgumentException::class.java) { calculateGaugeGeometry(100f, 0f) }
    }

    @Test
    fun observedSizeDiagnosticUsesDpAndTheSameResponsiveClass() {
        val observed = WidgetObservedSize(
            appWidgetId = 17,
            widthDp = 180,
            heightDp = 180,
            sizeClass = WidgetSizeClass.MEDIUM,
            style = WidgetStyle.GAUGE,
            theme = WidgetTheme.LIGHT_FOREGROUND,
            backgroundEnabled = true,
            transparencyPercent = 25,
            resolvedForeground = ResolvedForegroundMode.LIGHT,
            resolvedPrimaryTextArgb = 0xFFF7F9FC.toInt(),
            resolvedBackgroundAlpha = 191,
            gaugeStrokeDp = 16f,
        )

        assertEquals(
            "Widget ID: 17\n" +
                "Actual: 180 x 180 dp\n" +
                "폭 W2 · 높이 H2\n" +
                "형태 BALANCED · Layout COMPACT_DETAIL\n" +
                "Style: GAUGE · Theme: LIGHT_FOREGROUND\n" +
                "Background: ON · Transparency: 25%\n" +
                "Resolved foreground: LIGHT\n" +
                "Resolved text: #FFF7F9FC\n" +
                "Resolved background alpha: 191\n" +
                "Gauge diameter: 180 dp · stroke: 16 dp\n" +
                "Gauge arc: 135° + 270°",
            formatWidgetObservedSize(observed),
        )
        assertEquals("아직 관찰된 위젯 크기 없음", formatWidgetObservedSize(null))
    }
}
