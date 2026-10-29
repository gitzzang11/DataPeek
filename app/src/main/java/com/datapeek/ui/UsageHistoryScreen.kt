package com.datapeek.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datapeek.R
import com.datapeek.data.util.formatBytes
import com.datapeek.domain.ForecastStatus
import com.datapeek.domain.SpanKind
import com.datapeek.domain.UsageCycle
import com.datapeek.domain.UsageSpan
import com.datapeek.domain.buildCycleReport
import com.datapeek.domain.cycleFor
import com.datapeek.domain.forecastCycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun resetDayLabel(state: HomeUiState): String {
    val day = state.resetDay
    val base = if (day >= 31) "매월 말일" else "매월 ${day}일"
    return when {
        state.resetDaySetting in 1..31 -> "$base (직접 설정)"
        (state.ledger.carrierResetHint ?: state.snapshot?.cycleEnd) != null -> "$base (통신사 정보)"
        else -> "$base (달력 기준)"
    }
}

internal fun UsageCycle.title(): String =
    "${start.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))} ~ ${formatShortDay(lastDay)}"

internal fun UsageSpan.dateLabel(): String {
    val pattern = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
    return if (start == end) start.format(pattern) else "${start.format(DateTimeFormatter.ofPattern("M/d", Locale.KOREAN))} ~ ${end.format(DateTimeFormatter.ofPattern("M/d", Locale.KOREAN))}"
}

internal fun UsageSpan.note(): String? = when (kind) {
    SpanKind.MEASURED -> null
    SpanKind.ESTIMATED -> "추정 · 조회 없는 ${dayCount}일을 나눠 계산"
    SpanKind.BACKFILL -> "추정 · 기록 시작 전 ${dayCount}일 포함"
    SpanKind.TOP_UP -> "데이터 충전·변경 감지, 사용량은 계산하지 않아요"
    SpanKind.FIRST -> "첫 기록"
}

@Composable
internal fun UsageHistoryScreen(state: HomeUiState, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            value = Instant.now()
            delay(60_000)
        }
    }
    val resetDay = state.resetDay
    val today = now.atZone(zone).toLocalDate()
    val currentCycle = cycleFor(today, resetDay)
    val cycles = remember(state.ledger, resetDay, currentCycle) {
        (state.ledger.days.map { cycleFor(it.date, resetDay) } + currentCycle).distinct().sortedBy { it.start }
    }
    var selectedStart by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedIndex = cycles.indexOfFirst { it.start.toString() == selectedStart }.takeIf { it >= 0 } ?: cycles.lastIndex
    val cycle = cycles[selectedIndex]
    val isCurrent = cycle == currentCycle
    var mode by rememberSaveable { mutableStateOf(ChartMode.DAILY) }
    val report = remember(state.ledger, cycle) { buildCycleReport(state.ledger, cycle) }
    val forecast = if (isCurrent) forecastCycle(state.ledger, resetDay, now, zone) else null
    val mock = state.isMockData

    OneUiScreen(title = "사용 기록", onBack = onBack) {
        OneUiGroup {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedStart = cycles[selectedIndex - 1].start.toString() }, enabled = selectedIndex > 0) {
                    Icon(painterResource(R.drawable.ic_chevron), "이전 기간", modifier = Modifier.rotate(180f))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isCurrent) "이번 달" else "지난 기간", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cycle.title(), style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { selectedStart = cycles[selectedIndex + 1].start.toString() }, enabled = selectedIndex < cycles.lastIndex) {
                    Icon(painterResource(R.drawable.ic_chevron), "다음 기간")
                }
            }
        }

        OneUiGroup {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (forecast != null && !mock) {
                    val text = forecast.text()
                    Text(text.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (text.warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    if (text.detail.isNotEmpty()) Text(text.detail, style = MaterialTheme.typography.bodyLarge)
                    if (forecast.staleDays >= 1) {
                        Text("마지막 조회가 ${forecast.staleDays}일 전이에요. 새로고침하면 다시 계산해요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        report.usedBytes?.let { "이 기간에 ${formatBytes(it)}를 사용했어요" } ?: "이 기간의 사용 기록이 없어요",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (forecast != null && !mock &&
                (forecast.status == ForecastStatus.RUNS_OUT || forecast.status == ForecastStatus.SAFE)
            ) {
                OneUiDivider()
                forecast.dailyBytes?.let { DataValueRow("예상 하루 사용량", formatBytes(it.toLong())) }
                if (forecast.earliestDate != null && forecast.latestDate != null) {
                    DataValueRow("예상 소진 범위", "${formatShortDay(forecast.earliestDate!!)} ~ ${formatShortDay(forecast.latestDate!!)}")
                }
                DataValueRow("예측 신뢰도", forecast.confidence.label())
            }
            report.usedBytes?.let {
                OneUiDivider()
                DataValueRow("이 기간 사용량", formatBytes(it))
                report.averageDailyBytes?.let { average -> DataValueRow("하루 평균", formatBytes(average.toLong())) }
            }
            Spacer(Modifier.height(8.dp))
        }

        OneUiSectionTitle("일별 그래프")
        OneUiGroup {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ChartMode.DAILY to "일별 사용량", ChartMode.CUMULATIVE to "누적 사용량").forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = mode == value, onClick = { mode = value },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label) }
                    }
                }
                if (report.spans.isEmpty()) {
                    Text("조회에 성공할 때마다 하루의 마지막 잔여량을 기록해요. 아직 이 기간의 기록이 없어요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp))
                } else {
                    val days = remember(report) { chartDays(report) }
                    UsageChart(
                        days = days, mode = mode, today = today,
                        description = "${cycle.title()} 일별 사용량 그래프. " +
                            (report.usedBytes?.let { "총 ${formatBytes(it)} 사용." } ?: "") + " 아래 표에 같은 내용이 있어요.",
                        dailyPaceBytes = forecast?.dailyBytes?.takeIf { it > 0 },
                        limitBytes = if (isCurrent) report.totalBytes else null,
                    )
                    Text(
                        buildString {
                            if (mode == ChartMode.DAILY) {
                                append("채워진 막대는 조회로 확인한 값, 윤곽만 있는 막대는 추정이에요.")
                                if (forecast?.dailyBytes != null && forecast.dailyBytes!! > 0) append(" 빨간 점선은 예상 하루 사용량이에요.")
                            } else {
                                append("파란 선은 이번 달 누적 사용량이에요.")
                                if (forecast?.dailyBytes != null && forecast.dailyBytes!! > 0) append(" 점선은 지금 속도로 이어질 예상이고, 빨간 점선은 전체 제공량이에요.")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }

        if (report.spans.isNotEmpty()) {
            OneUiSectionTitle("날짜별 사용량")
            OneUiGroup {
                report.spans.asReversed().forEachIndexed { index, span ->
                    if (index > 0) OneUiDivider()
                    OneUiSettingRow(
                        title = span.dateLabel(), summary = span.note(),
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(span.usedBytes?.let { formatBytes(it) } ?: "—", style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                Text("잔여 ${formatBytes(span.remainingBytes)}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }

        OneUiSectionTitle("기록 기준")
        OneUiGroup {
            OneUiSettingRow("데이터 초기화일", summary = resetDayLabel(state), onClick = onOpenSettings)
        }
        Text(
            "하루의 마지막 조회값을 저장하고 전날과의 차이를 그날 사용량으로 계산해요. 조회가 없던 날은 사용량을 나눠 추정으로 표시하고, " +
                "예측에는 현재 기간의 기록만 사용해요. 통신사 반영 시차에 따라 실제와 다를 수 있어요.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
