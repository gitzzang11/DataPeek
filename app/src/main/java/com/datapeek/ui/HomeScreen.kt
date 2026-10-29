package com.datapeek.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datapeek.R
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.model.UsageSource
import com.datapeek.data.util.calculateRemainingPercent
import com.datapeek.data.util.daysUntilReset
import com.datapeek.data.util.formatBytes
import com.datapeek.data.util.resetLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HomeScreen(
    state: HomeUiState, onRefresh: () -> Unit, onLogin: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit,
) {
    val status = state.usageUiStatus()
    val needsLogin = status == UsageUiStatus.LOGIN_REQUIRED
    OneUiScreen(
        title = "데이터 사용량",
        actions = {
            IconButton(onClick = onHistory) { Icon(painterResource(R.drawable.ic_history), "사용 기록") }
            IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), "설정") }
        },
        bottomBar = {
            OneUiBottomAction(
                label = when {
                    state.isRefreshing -> "사용량 확인 중…"
                    needsLogin -> if (state.snapshot == null) "프리티 로그인" else "다시 로그인"
                    status == UsageUiStatus.UPDATE_FAILED -> "다시 시도"
                    else -> "새로고침"
                }, enabled = !state.isRefreshing,
                onClick = if (needsLogin) onLogin else onRefresh,
            )
        },
    ) {
        val snapshot = state.snapshot
        if (snapshot != null) {
            RemainingDataGroup(snapshot, state.isMockData)
            OneUiSectionTitle("소진 예측")
            UsageForecastCard(state, onOpenHistory = onHistory)
            OneUiSectionTitle("업데이트")
            OneUiGroup {
                OneUiSettingRow(
                    title = when {
                        state.isRefreshing -> "사용량을 확인하고 있어요"
                        needsLogin -> "로그인이 만료되었어요"
                        status == UsageUiStatus.UPDATE_FAILED -> "새로고침하지 못했어요"
                        state.isMockData -> "테스트 데이터 표시 중"
                        else -> "최근 사용량을 확인했어요"
                    },
                    summary = when {
                        needsLogin -> "다시 로그인해 주세요. 마지막 정상 사용량을 표시하고 있어요."
                        status == UsageUiStatus.UPDATE_FAILED -> state.errorMessage
                            ?: "연결을 확인한 후 다시 시도해 주세요."
                        else -> "마지막 조회 ${snapshot.fetchedAt.atZone(ZoneId.systemDefault()).format(
                            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN))}"
                    },
                )
            }
        } else {
            OneUiGroup {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        when {
                            state.isRefreshing || state.sessionStatus == SessionStatus.CHECKING -> "사용량을 확인하고 있어요"
                            state.isAuthenticated -> "사용량을 불러오지 못했어요"
                            else -> "내 데이터를 한눈에"
                        }, style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        when {
                            state.isRefreshing || state.sessionStatus == SessionStatus.CHECKING -> "잠시만 기다려 주세요."
                            state.isAuthenticated -> state.errorMessage ?: "연결을 확인한 후 다시 시도해 주세요."
                            else -> "프리티 계정으로 로그인하고 남은 데이터와 소진 예상일을 확인해 보세요."
                        }, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            OneUiSectionTitle("연결할 통신사")
            OneUiGroup { OneUiSettingRow(state.carrier.label, summary = "공식 통신사 사이트에서 로그인해요") }
        }
        Text(
            "DataPeek은 통신사에서 조회한 사용량을 표시합니다. 실제 사용량 반영에는 시간이 걸릴 수 있어요.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemainingDataGroup(snapshot: UsageSnapshot, mock: Boolean) {
    val percent = snapshot.remainingPercent ?: calculateRemainingPercent(snapshot.remainingBytes, snapshot.totalBytes)
    OneUiGroup {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (mock || snapshot.source == UsageSource.MOCK) "테스트 데이터" else snapshot.carrier.label,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("남은 데이터", style = MaterialTheme.typography.titleMedium)
                Text(formatBytes(snapshot.remainingBytes, snapshot.remainingUnit),
                    style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0f, 100f) / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small)
                        .semantics { contentDescription = "잔여 데이터 ${percent.toInt()} 퍼센트" },
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
        OneUiDivider()
        DataValueRow("사용한 데이터", formatBytes(snapshot.usedBytes, snapshot.usedUnit))
        DataValueRow("전체 제공량", formatBytes(snapshot.totalBytes, snapshot.totalUnit))
        DataValueRow("데이터 초기화", resetLabel(snapshot.daysUntilReset ?: daysUntilReset(snapshot.cycleEnd)))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun DataValueRow(label: String, value: String) {
    // FlowRow lets large accessibility text wrap instead of squeezing the value off-screen.
    FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
