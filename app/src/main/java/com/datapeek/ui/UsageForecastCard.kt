package com.datapeek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.formatBytes
import com.datapeek.domain.ForecastStatus
import com.datapeek.domain.UsageForecast
import com.datapeek.domain.forecastUsage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun UsageForecast.headline(zone: ZoneId = ZoneId.systemDefault()): String = when (status) {
    ForecastStatus.LEARNING -> "사용 패턴을 알아가는 중"
    ForecastStatus.STALE -> "최신 사용량을 확인해 주세요"
    ForecastStatus.EXHAUSTED -> "제공 데이터를 모두 사용했어요"
    ForecastStatus.NO_CONSUMPTION -> "최근 기록에 사용량 변화가 없어요"
    ForecastStatus.LOW_CONSUMPTION -> "사용량이 적어 소진일 계산이 어려워요"
    ForecastStatus.PREDICTED -> if (runsOutBeforeReset == false) {
        "다음 초기화일까지 사용 가능 예상"
    } else {
        "${exhaustedAt?.atZone(zone)?.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))} 소진 예상"
    }
}

@Composable
internal fun UsageForecastCard(state: HomeUiState) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000)
        }
    }
    val snapshot = state.snapshot ?: return
    val mock = state.isMockData || snapshot.source == UsageSource.MOCK
    val history = state.usageHistory.takeIf { !mock && it.lastOrNull()?.fetchedAt == snapshot.fetchedAt }.orEmpty()
    val forecast = forecastUsage(history, now)
    OneUiGroup {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (mock) "실제 사용 기록으로 예측해요" else forecast.headline(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (forecast.runsOutBeforeReset == true || forecast.status == ForecastStatus.EXHAUSTED) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.onSurface,
            )
            if (forecast.dailyBytes != null || forecast.dailyBudgetBytes != null) {
                forecast.dailyBytes?.let {
                    Text("최근 하루 평균 ${formatBytes(it.toLong())}", style = MaterialTheme.typography.bodyLarge)
                }
                forecast.dailyBudgetBytes?.let {
                    Text("하루 권장량 ${formatBytes(it.toLong())}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                when {
                    mock -> "테스트 데이터는 사용 기록에 포함하지 않습니다."
                    forecast.status == ForecastStatus.LEARNING ->
                        "새로고침에 성공할 때마다 앱에 기록합니다. 최소 24시간 간격의 기록이 쌓이면 예측을 시작해요."
                    forecast.status == ForecastStatus.STALE ->
                        "기록이 하루 이상 지났거나 데이터 기간이 끝났습니다. 새로고침하면 다시 계산합니다."
                    forecast.status == ForecastStatus.NO_CONSUMPTION ->
                        "사용량이 줄어들면 소진 예상일을 계산합니다."
                    forecast.status == ForecastStatus.LOW_CONSUMPTION ->
                        "관측된 사용량이 매우 적습니다. 기록이 더 쌓이면 다시 계산합니다."
                    forecast.status == ForecastStatus.EXHAUSTED ->
                        "마지막으로 조회한 통신사 잔여량 기준입니다."
                    else -> "최근 ${String.format(Locale.KOREAN, "%.1f", forecast.observedDays)}일 기록 · 마지막 조회 기준 추정치입니다. " +
                        "사용 패턴과 통신사 반영 시차에 따라 달라질 수 있어요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
