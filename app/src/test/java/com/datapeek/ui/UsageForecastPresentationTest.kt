package com.datapeek.ui

import com.datapeek.domain.Confidence
import com.datapeek.domain.CycleForecast
import com.datapeek.domain.ForecastStatus
import com.datapeek.domain.cycleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageForecastPresentationTest {
    private val cycle = cycleFor(LocalDate.parse("2026-10-15"), 1)

    @Test fun announcesTheExhaustionDayWhenItIsInsideThisMonth() {
        val text = CycleForecast(
            status = ForecastStatus.RUNS_OUT, cycle = cycle, remainingBytes = 20L shl 30,
            exhaustionDate = LocalDate.parse("2026-10-31"), daysLeft = 16, dailyBudgetBytes = (1L shl 30).toDouble(),
            confidence = Confidence.MEDIUM,
        ).text()
        assertEquals("10월 31일 (토)에 소진될 것으로 예상돼요", text.headline)
        assertTrue(text.detail.contains("16일 남았어요"))
        assertTrue(text.detail.contains("11월 1일까지 하루 1 GB 이하"))
        assertTrue(text.warning)
    }

    @Test fun saysTodayWhenTheDataRunsOutToday() {
        val text = CycleForecast(
            status = ForecastStatus.RUNS_OUT, cycle = cycle, exhaustionDate = LocalDate.parse("2026-10-15"), daysLeft = 0,
        ).text()
        assertEquals("오늘 소진될 것으로 예상돼요", text.headline)
    }

    @Test fun reassuresWhenTheProjectedDateFallsInALaterMonth() {
        val text = CycleForecast(
            status = ForecastStatus.SAFE, cycle = cycle, exhaustionDate = LocalDate.parse("2026-12-20"),
            leftoverAtResetBytes = 5L shl 30,
        ).text()
        assertEquals("이번 달은 걱정 없어요", text.headline)
        assertTrue(text.detail.contains("11월 1일에 초기화될 때까지 버텨요"))
        assertTrue(text.detail.contains("약 5 GB 남음"))
        assertFalse(text.warning)
    }

    @Test fun distinguishesMissingHistoryExhaustedAndUnusedStates() {
        assertEquals("사용 패턴을 알아가는 중이에요", CycleForecast(ForecastStatus.INSUFFICIENT, cycle).text().headline)
        assertEquals("새로고침하면 이번 달 사용 기록을 시작해요.", CycleForecast(ForecastStatus.INSUFFICIENT, cycle).text().detail)
        assertEquals("이번 달 데이터를 모두 사용했어요", CycleForecast(ForecastStatus.EXHAUSTED, cycle).text().headline)
        assertEquals("이번 달은 걱정 없어요", CycleForecast(ForecastStatus.NO_CONSUMPTION, cycle).text().headline)
    }
}
