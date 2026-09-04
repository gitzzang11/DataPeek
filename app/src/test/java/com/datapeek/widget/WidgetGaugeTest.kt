package com.datapeek.widget

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.calculateRemainingPercent
import com.datapeek.data.util.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetGaugeTest {
    @Test
    fun responsiveGaugeStrokeIsThickInEverySizeClass() {
        val fixtures = listOf(
            Triple(WidgetSizeClass.MICRO, 63, 8f..10f),
            Triple(WidgetSizeClass.SMALL, 82, 10f..12f),
            Triple(WidgetSizeClass.MEDIUM, 125, 14f..17f),
            Triple(WidgetSizeClass.LARGE, 180, 16f..20f),
            Triple(WidgetSizeClass.XLARGE, 292, 18f..24f),
        )

        fixtures.forEach { (sizeClass, diameter, expectedRange) ->
            val stroke = calculateGaugeStrokeDp(diameter, sizeClass)
            assertTrue(stroke in expectedRange)
            assertTrue(stroke < diameter / 2f)
        }
    }

    @Test
    fun twoByTwoGaugeIsSubstantiallyThickerThanTheLegacyPixelCap() {
        val diameterDp = widgetLayoutPlan(180, 180, WidgetStyle.GAUGE, WidgetConfig()).gaugeDiameterDp
        val oldStrokeOnXxhdpiDp = legacyGaugeStrokeDp(diameterDp, density = 3f)
        val newStrokeDp = calculateGaugeStrokeDp(diameterDp, WidgetSizeClass.MEDIUM)

        assertTrue(newStrokeDp >= 13f)
        assertTrue(newStrokeDp >= oldStrokeOnXxhdpiDp * 2f)
    }
    @Test
    fun gaugeSweepClampsTheFullRemainingPercentRange() {
        assertEquals(0f, gaugeSweepDegrees(0f), 0.001f)
        assertEquals(135f, gaugeSweepDegrees(50f), 0.001f)
        assertEquals(270f, gaugeSweepDegrees(100f), 0.001f)
        assertEquals(0f, gaugeSweepDegrees(-10f), 0.001f)
        assertEquals(270f, gaugeSweepDegrees(150f), 0.001f)
        assertEquals(0f, gaugeSweepDegrees(Float.NaN), 0.001f)
    }

    @Test
    fun zeroTotalDoesNotProduceAnInvalidGaugePercent() {
        val snapshot = UsageSnapshot(
            carrier = Carrier.FREET_SKT,
            totalBytes = 0L,
            remainingBytes = 0L,
            fetchedAt = Instant.EPOCH,
            source = UsageSource.CARRIER_SERVER,
        )

        assertEquals(null, calculateRemainingPercent(snapshot.remainingBytes, snapshot.totalBytes))
        assertEquals(0f, gaugeSweepDegrees(0f), 0.001f)
    }

    @Test
    fun remainingFractionUsesRawBytesAndSafelyClampsEdgeCases() {
        assertEquals(0f, calculateGaugeRemainingFraction(null, 10L), 0.001f)
        assertEquals(0f, calculateGaugeRemainingFraction(10L, 0L), 0.001f)
        assertEquals(0f, calculateGaugeRemainingFraction(-1L, 10L), 0.001f)
        assertEquals(0f, calculateGaugeRemainingFraction(0L, 10L), 0.001f)
        assertEquals(0.5f, calculateGaugeRemainingFraction(5L, 10L), 0.001f)
        assertEquals(1f, calculateGaugeRemainingFraction(10L, 10L), 0.001f)
        assertEquals(1f, calculateGaugeRemainingFraction(20L, 10L), 0.001f)
    }

    @Test
    fun widgetPercentKeepsOnlyUsefulPrecision() {
        assertEquals("98%", formatWidgetPercent(98f))
        assertEquals("98.5%", formatWidgetPercent(98.46f))
        assertEquals("100%", formatWidgetPercent(120f))
        assertEquals("0%", formatWidgetPercent(Float.NaN))
    }

    @Test
    fun widgetUsesTheSharedHomeFormatterForTheRealisticFreetSnapshot() {
        val snapshot = UsageSnapshot(
            carrier = Carrier.FREET_SKT,
            totalBytes = 20L * 1024 * 1024 * 1024,
            usedBytes = 315733L * 1024,
            remainingBytes = 20655787L * 1024,
            fetchedAt = Instant.EPOCH,
            source = UsageSource.CARRIER_SERVER,
        )

        assertEquals("20 GB", formatBytes(snapshot.totalBytes))
        assertEquals("308.33 MB", formatBytes(snapshot.usedBytes))
        assertEquals("19.7 GB", formatBytes(snapshot.remainingBytes))
        assertTrue((snapshot.usedBytes ?: 0L) + (snapshot.remainingBytes ?: 0L) == snapshot.totalBytes)
    }
}
