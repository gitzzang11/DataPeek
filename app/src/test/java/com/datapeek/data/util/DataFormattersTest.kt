package com.datapeek.data.util

import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DataFormattersTest {
    @Test
    fun formatsMegabytesAndGigabytesWithoutNoise() {
        assertEquals("500 MB", formatBytes(500L * 1024 * 1024))
        assertEquals("999.81 MB", formatBytes((999.81 * 1024 * 1024).toLong()))
        assertEquals("1 GB", formatBytes(1024L * 1024 * 1024))
        assertEquals("1 GB", formatBytes(1L * 1024 * 1024 * 1024))
        assertEquals("22.7 GB", formatBytes((22.7 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun parsesFreetUnitsExplicitlyAndDoesNotTreatGigabytesAsMegabytes() {
        val twentyGigabytes = 20L * 1024 * 1024 * 1024
        val nineteenPointZeroTwoGigabytes = parseDataSize("19.02G")
        val nineHundredNinetyNinePointEightyOneMegabytes = parseDataSize("999.81M")

        assertEquals(twentyGigabytes, parseDataSize("20G"))
        assertEquals(twentyGigabytes, parseDataSize("20GB"))
        assertEquals(twentyGigabytes, parseDataSize("20 GB"))
        assertEquals(twentyGigabytes, parseDataSize("20g"))
        assertEquals(twentyGigabytes, parseDataSize(" 20G "))
        assertEquals(19.02f, formatBytes(nineteenPointZeroTwoGigabytes).removeSuffix(" GB").toFloat(), 0.01f)
        assertEquals("999.81 MB", formatBytes(nineHundredNinetyNinePointEightyOneMegabytes))
        assertTrue(twentyGigabytes > nineHundredNinetyNinePointEightyOneMegabytes!!)
    }

    @Test
    fun supportsShortAndLongMegabyteFormsAndUsesBinaryConversion() {
        val nineHundredNinetyNineMegabytes = 999L * 1024 * 1024

        assertEquals(nineHundredNinetyNineMegabytes, parseDataSize("999MB"))
        assertEquals(nineHundredNinetyNineMegabytes, parseDataSize("999 MB"))
        assertEquals(nineHundredNinetyNineMegabytes, parseDataSize("999m"))
        assertEquals(parseDataSize("1G"), parseDataSize("1024M"))
        assertEquals("20 GB", formatBytes(parseDataSize("20G")))
        assertEquals("19.02 GB", formatBytes(parseDataSize("19.02 G")))
    }

    @Test
    fun parsesCompoundFreetDisplayValues() {
        val used = parseDataSizeCompound("308M 341K")
        val remaining = parseDataSizeCompound("19G 715M")
        val mixed = parseDataSizeCompound("1G 20M 10K")

        assertEquals(308L * 1024 * 1024 + 341L * 1024, used?.bytes)
        assertEquals(19L * 1024 * 1024 * 1024 + 715L * 1024 * 1024, remaining?.bytes)
        assertEquals(1L * 1024 * 1024 * 1024 + 20L * 1024 * 1024 + 10L * 1024, mixed?.bytes)
        assertEquals(DataUnit.MIB, used?.unit)
        assertEquals(DataUnit.GIB, remaining?.unit)
    }

    @Test
    fun rejectsCompoundValuesWithMissingOrUnknownUnits() {
        assertNull(parseDataSizeCompound("308M 341"))
        assertNull(parseDataSizeCompound("20G unknown"))
        assertNull(parseDataSizeCompound("0.3"))
    }

    @Test
    fun usesExplicitFallbackUnitOnlyForUnitlessValues() {
        val pointThreeGigabytes = (BigDecimal("0.3") * BigDecimal.valueOf(1024L * 1024 * 1024))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        val nineteenPointSevenGigabytes = (BigDecimal("19.7") * BigDecimal.valueOf(1024L * 1024 * 1024))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

        assertEquals(pointThreeGigabytes, parseDataSize("0.3", DataUnit.GIB))
        assertEquals(nineteenPointSevenGigabytes, parseDataSize("19.7", DataUnit.GIB))
        assertEquals("0.3 GB", formatBytes(parseDataSize("0.3", DataUnit.GIB), DataUnit.GIB))
        assertNull(parseDataSize("0.3"))
    }

    @Test
    fun anExplicitSuffixOverridesFallbackUnit() {
        assertEquals(parseDataSize("0.3G"), parseDataSize("0.3G", DataUnit.MIB))
        assertEquals(parseDataSize("999.81M"), parseDataSize("999.81", DataUnit.MIB))
    }

    @Test
    fun rejectsInvalidBytes() {
        assertEquals("—", formatBytes(null))
        assertEquals("—", formatBytes(-1))
    }

    @Test
    fun clampsRemainingPercent() {
        assertEquals(0f, calculateRemainingPercent(0, 100))
        assertEquals(50f, calculateRemainingPercent(50, 100))
        assertEquals(100f, calculateRemainingPercent(100, 100))
        assertEquals(100f, calculateRemainingPercent(200, 100))
        assertEquals(0f, calculateRemainingPercent(-1, 100))
        assertNull(calculateRemainingPercent(1, 0))
    }

    @Test
    fun computesResetLabels() {
        val today = LocalDate.of(2026, 8, 29)
        assertEquals("오늘 초기화", resetLabel(daysUntilReset(today, today)))
        assertEquals("내일 초기화", resetLabel(daysUntilReset(today.plusDays(1), today)))
        assertEquals("5일 후 초기화", resetLabel(daysUntilReset(today.plusDays(5), today)))
        assertEquals("초기화일 정보 없음", resetLabel(daysUntilReset(null, today)))
        assertTrue(daysUntilReset(today.minusDays(1), today) == 0)
    }
}
