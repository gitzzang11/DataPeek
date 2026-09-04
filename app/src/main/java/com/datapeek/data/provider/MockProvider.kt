package com.datapeek.data.provider

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import java.time.Instant

class MockProvider : CarrierProvider {
    override val carrier = Carrier.FREET_SKT

    override fun getLoginUrl() = "https://www.freet.co.kr/login"

    override suspend fun isAuthenticated() = true

    override suspend fun fetchUsage(): Result<UsageSnapshot> = Result.success(
        UsageSnapshot(
            carrier = carrier,
            lineName = "개발용 Mock 회선",
            totalBytes = 24_200_000_000L,
            usedBytes = 1_500_000_000L,
            remainingBytes = 22_700_000_000L,
            remainingPercent = 93.8f,
            cycleStart = java.time.LocalDate.now().withDayOfMonth(1),
            cycleEnd = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1),
            fetchedAt = Instant.now(),
            source = UsageSource.MOCK,
        ),
    )

    override suspend fun logout() = Unit

    override fun supportsBackgroundRefresh() = true
}
