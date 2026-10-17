package com.datapeek.ui

import com.datapeek.domain.ForecastStatus
import com.datapeek.domain.UsageForecast
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class UsageForecastPresentationTest {
    @Test fun showsLocalExhaustionDateWhenDataRunsOutBeforeReset() {
        val forecast = UsageForecast(
            status = ForecastStatus.PREDICTED,
            exhaustedAt = Instant.parse("2026-10-20T16:00:00Z"),
            runsOutBeforeReset = true,
        )
        assertEquals("10월 21일 소진 예상", forecast.headline(ZoneId.of("Asia/Seoul")))
    }

    @Test fun doesNotPresentADateBeyondTheNextAllowanceAsExhaustion() {
        assertEquals("다음 초기화일까지 사용 가능 예상", UsageForecast(
            status = ForecastStatus.PREDICTED,
            runsOutBeforeReset = false,
        ).headline())
    }

    @Test fun distinguishesMissingHistoryStaleHistoryAndZeroConsumption() {
        assertEquals("사용 패턴을 알아가는 중", UsageForecast(ForecastStatus.LEARNING).headline())
        assertEquals("최신 사용량을 확인해 주세요", UsageForecast(ForecastStatus.STALE).headline())
        assertEquals("최근 기록에 사용량 변화가 없어요", UsageForecast(ForecastStatus.NO_CONSUMPTION).headline())
        assertEquals("제공 데이터를 모두 사용했어요", UsageForecast(ForecastStatus.EXHAUSTED).headline())
    }
}
