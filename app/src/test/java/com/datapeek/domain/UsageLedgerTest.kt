package com.datapeek.domain

import com.datapeek.data.model.UsageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UsageLedgerTest {
    @Test fun keepsOnlyTheLastReadingOfEachDay() {
        val ledger = ledgerOf(
            reading("2026-10-10T01:00:00Z", 20_000),
            reading("2026-10-10T20:00:00Z", 18_000),
            reading("2026-10-11T08:00:00Z", 17_000),
        )
        assertEquals(2, ledger.days.size)
        assertEquals(18_000, ledger.days[0].remainingBytes)
        assertEquals(17_000, ledger.days[1].remainingBytes)
    }

    @Test fun ignoresTestDataFailedReadingsAndOutOfOrderReadings() {
        val first = reading("2026-10-10T10:00:00Z", 20_000)
        val ledger = ledgerOf(first)
        assertEquals(ledger, recordUsage(ledger, reading("2026-10-10T11:00:00Z", 1, source = UsageSource.MOCK)))
        assertEquals(ledger, recordUsage(ledger, first.copy(remainingBytes = null)))
        assertEquals(ledger, recordUsage(ledger, reading("2026-10-09T10:00:00Z", 25_000)))
        assertEquals(ledger, recordUsage(ledger, reading("2026-10-11T10:00:00Z", 40_000, total = 30_000)))
    }

    @Test fun groupsDaysByTheUsersLocalCalendarDay() {
        val seoul = java.time.ZoneId.of("Asia/Seoul")
        val ledger = recordUsage(UsageLedger(), reading("2026-10-10T16:00:00Z", 20_000), seoul)
        assertEquals(LocalDate.parse("2026-10-11"), ledger.days.single().date)
    }

    @Test fun anotherLineStartsAFreshLedger() {
        val ledger = ledgerOf(reading("2026-10-10T10:00:00Z", 20_000), reading("2026-10-11T10:00:00Z", 19_000, line = "other"))
        assertEquals(1, ledger.days.size)
        assertEquals(19_000, ledger.days.single().remainingBytes)
    }

    @Test fun remembersATopUpWithinTheSameDay() {
        val ledger = ledgerOf(reading("2026-10-10T08:00:00Z", 10_000), reading("2026-10-10T18:00:00Z", 20_000))
        assertTrue(ledger.days.single().topUpSeen)
        assertFalse(ledgerOf(reading("2026-10-10T08:00:00Z", 10_000), reading("2026-10-10T18:00:00Z", 9_000)).days.single().topUpSeen)
    }

    @Test fun carrierResetDateComesFromTheReportedCycleEndOrDaysLeft() {
        val withEnd = ledgerOf(reading("2026-10-10T08:00:00Z", 10_000).copy(cycleEnd = LocalDate.parse("2026-11-05")))
        assertEquals(LocalDate.parse("2026-11-05"), withEnd.carrierResetHint)
        val withDays = ledgerOf(reading("2026-10-10T08:00:00Z", 10_000).copy(daysUntilReset = 5))
        assertEquals(LocalDate.parse("2026-10-15"), withDays.carrierResetHint)
        assertNull(ledgerOf(reading("2026-10-10T08:00:00Z", 10_000)).carrierResetHint)
    }

    @Test fun savedLedgerSurvivesEncodingAndRejectsDamagedData() {
        val ledger = ledgerOf(
            reading("2026-10-10T08:00:00Z", 10_000).copy(cycleEnd = LocalDate.parse("2026-11-01")),
            reading("2026-10-11T08:00:00Z", 20_000),
        )
        assertEquals(ledger, UsageLedgerCodec.decode(UsageLedgerCodec.encode(ledger)))
        assertNull(UsageLedgerCodec.decode("not json"))
        assertNull(UsageLedgerCodec.decode(null))
    }

    @Test fun oldReadingListIsConvertedToDailyEntries() {
        fun old(at: String, remaining: Long) = UsageObservation(
            scope = "scope", totalBytes = 30_000, remainingBytes = remaining,
            fetchedAt = Instant.parse(at), cycleStart = null, resetDate = null,
        )
        val ledger = UsageLedgerCodec.fromLegacy(
            listOf(old("2026-10-10T01:00:00Z", 20_000), old("2026-10-10T20:00:00Z", 19_000), old("2026-10-11T20:00:00Z", 18_000)),
            ZoneOffset.UTC,
        )
        assertEquals("scope", ledger.scope)
        assertEquals(listOf(19_000L, 18_000L), ledger.days.map { it.remainingBytes })
    }

    @Test fun keepsAtMostFourHundredDays() {
        var ledger = UsageLedger()
        var at = Instant.parse("2025-01-01T12:00:00Z")
        repeat(LEDGER_MAX_DAYS + 20) {
            ledger = recordUsage(ledger, reading(at.toString(), 10_000), ZoneOffset.UTC)
            at = at.plusSeconds(86_400)
        }
        assertEquals(LEDGER_MAX_DAYS, ledger.days.size)
    }
}
