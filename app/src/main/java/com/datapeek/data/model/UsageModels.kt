package com.datapeek.data.model

import java.time.Instant
import java.time.LocalDate
import com.datapeek.data.util.DataUnit

enum class Carrier(val label: String) {
    FREET_SKT("프리티 · SKT망"),
    SKT_TWORLD("SK텔레콤"),
}

enum class UsageSource(val label: String) {
    CARRIER_SERVER("통신사 서버"),
    WEB_PAGE("공식 웹페이지"),
    DEVICE_ESTIMATE("기기 기준 예상치"),
    MOCK("개발용 테스트 데이터"),
}

data class UsageSnapshot(
    val carrier: Carrier,
    val lineId: String? = null,
    val lineName: String? = null,
    val phoneNumberMasked: String? = null,
    val totalBytes: Long? = null,
    val usedBytes: Long? = null,
    val remainingBytes: Long? = null,
    val totalUnit: DataUnit? = null,
    val usedUnit: DataUnit? = null,
    val remainingUnit: DataUnit? = null,
    val remainingPercent: Float? = null,
    val cycleStart: LocalDate? = null,
    val cycleEnd: LocalDate? = null,
    val daysUntilReset: Int? = null,
    val fetchedAt: Instant,
    val source: UsageSource,
)

sealed interface UsageError {
    data object NotAuthenticated : UsageError
    data object SessionExpired : UsageError
    data object NetworkUnavailable : UsageError
    data class HttpError(val code: Int) : UsageError
    data class ParseError(val reason: String) : UsageError
    data object ApiTimeout : UsageError
    data object ApiBodyReadFailed : UsageError
    data object ApiRequestFailed : UsageError
    data object UnsupportedResponse : UsageError
    data object RateLimited : UsageError
}

class UsageException(val error: UsageError) : Exception()
