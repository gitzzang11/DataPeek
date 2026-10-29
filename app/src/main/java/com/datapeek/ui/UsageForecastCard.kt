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
import com.datapeek.domain.Confidence
import com.datapeek.domain.CycleForecast
import com.datapeek.domain.ForecastStatus
import com.datapeek.domain.forecastCycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ForecastText(val headline: String, val detail: String, val warning: Boolean)

internal fun formatDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))

internal fun formatShortDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN))

internal fun Confidence.label(): String = when (this) {
    Confidence.LOW -> "낮음"
    Confidence.MEDIUM -> "보통"
    Confidence.HIGH -> "높음"
}

/** Plain-language outcome of the forecast: this cycle's exhaustion date, or reassurance for this cycle. */
internal fun CycleForecast.text(): ForecastText {
    val resetDate = cycle.endExclusive
    val reset = formatShortDay(resetDate)
    return when (status) {
        ForecastStatus.INSUFFICIENT -> ForecastText(
            "사용 패턴을 알아가는 중이에요",
            if (remainingBytes == null) "새로고침하면 이번 달 사용 기록을 시작해요."
            else "하루 이상 기록이 쌓이면 소진 예상일을 알려드려요.",
            false,
        )
        ForecastStatus.EXHAUSTED -> ForecastText(
            "이번 달 데이터를 모두 사용했어요", "${reset}에 데이터가 초기화돼요.", true,
        )
        ForecastStatus.NO_CONSUMPTION -> ForecastText(
            "이번 달은 걱정 없어요", "아직 사용량 변화가 없어요. ${reset}에 초기화돼요.", false,
        )
        ForecastStatus.RUNS_OUT -> {
            val date = exhaustionDate
            val budget = dailyBudgetBytes?.takeIf { it >= 1L shl 20 }?.let { " ${reset}까지 하루 ${formatBytes(it.toLong())} 이하로 쓰면 버틸 수 있어요." }.orEmpty()
            ForecastText(
                if (daysLeft == 0) "오늘 소진될 것으로 예상돼요" else "${date?.let(::formatDay)}에 소진될 것으로 예상돼요",
                "${daysLeft?.takeIf { it > 0 }?.let { "${it}일 남았어요." }.orEmpty()}$budget".trim(),
                true,
            )
        }
        ForecastStatus.SAFE -> ForecastText(
            "이번 달은 걱정 없어요",
            "지금 속도라면 ${reset}에 초기화될 때까지 버텨요" +
                (leftoverAtResetBytes?.let { " (약 ${formatBytes(it)} 남음)" }.orEmpty()) +
                ". 예상 소진일이 다음 달 이후예요.",
            false,
        )
    }
}

@Composable
internal fun UsageForecastCard(state: HomeUiState, onOpenHistory: () -> Unit) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000)
        }
    }
    val snapshot = state.snapshot ?: return
    val mock = state.isMockData || snapshot.source == UsageSource.MOCK
    OneUiGroup {
        if (mock) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("실제 사용 기록으로 예측해요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("테스트 데이터는 사용 기록에 포함하지 않습니다.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val forecast = forecastCycle(state.ledger, state.resetDay, now)
            val text = forecast.text()
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (text.warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (text.detail.isNotEmpty()) Text(text.detail, style = MaterialTheme.typography.bodyLarge)
                if (forecast.status == ForecastStatus.RUNS_OUT || forecast.status == ForecastStatus.SAFE) {
                    Text(
                        "이번 달 기록 ${String.format(Locale.KOREAN, "%.0f", forecast.observedDays)}일 기준 · 신뢰도 ${forecast.confidence.label()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (forecast.staleDays >= 1) {
                    Text("마지막 조회가 ${forecast.staleDays}일 전이에요. 새로고침하면 다시 계산해요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OneUiDivider()
        OneUiSettingRow("일별 사용 기록", summary = "그래프와 표로 보고, 지난 달 기록도 볼 수 있어요", onClick = onOpenHistory)
    }
}
