package com.datapeek.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.datapeek.BuildConfig

@Composable
internal fun SettingsScreen(
    state: HomeUiState, onBack: () -> Unit, onLogout: () -> Unit, onLogin: () -> Unit,
    onIntervalChanged: (Long) -> Unit, onAutoRefreshChanged: (Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit, onDeveloperModeChanged: (Boolean) -> Unit,
) {
    var showIntervals by rememberSaveable { mutableStateOf(false) }
    var versionTapCount by rememberSaveable { mutableIntStateOf(0) }
    var developerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.developerModeEnabled) { versionTapCount = 0; developerMessage = null }
    val intervals = listOf(30L to "30분", 60L to "1시간", 180L to "3시간", 360L to "6시간", 0L to "수동")
    OneUiScreen(title = "설정", onBack = onBack) {
        OneUiSectionTitle("통신사 계정")
        OneUiGroup {
            OneUiSettingRow(state.carrier.label,
                summary = if (state.isAuthenticated) "연결됨" else "로그인이 필요해요")
            OneUiDivider()
            OneUiSettingRow(if (state.isAuthenticated) "로그아웃" else "통신사 로그인",
                onClick = if (state.isAuthenticated) onLogout else onLogin)
        }
        OneUiSectionTitle("사용량 업데이트")
        OneUiGroup {
            OneUiSwitchRow("자동 새로고침", "앱을 열면 최신 사용량을 확인해요",
                checked = state.autoRefreshEnabled, onChange = onAutoRefreshChanged)
            OneUiDivider()
            OneUiSettingRow("새로고침 주기",
                summary = if (state.autoRefreshEnabled) intervals.firstOrNull { it.first == state.autoRefreshMinutes }?.second
                    ?: "${state.autoRefreshMinutes}분" else "사용 안 함",
                onClick = if (state.autoRefreshEnabled) ({ showIntervals = true }) else null)
        }
        Text("백그라운드에서는 갱신 필요 여부를 표시하고, 통신사 사용량은 앱을 열 때 조회해요. Android 절전 정책에 따라 갱신 시각이 달라질 수 있어요.",
            modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OneUiSectionTitle("앱 정보")
        OneUiGroup {
            OneUiSettingRow("DataPeek", summary = "버전 ${BuildConfig.VERSION_NAME}", onClick = {
                val result = DeveloperModeState(state.developerModeEnabled, versionTapCount).onVersionTapped()
                versionTapCount = result.state.versionTapCount
                developerMessage = result.message
                if (result.state.enabled && !state.developerModeEnabled) onDeveloperModeChanged(true)
            })
            if (state.developerModeEnabled) {
                OneUiDivider()
                OneUiSettingRow("개발자 옵션", onClick = onOpenDeveloperOptions)
            }
        }
        developerMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Text("비밀번호와 인증 쿠키는 공식 로그인 화면에서 관리돼요.",
            modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showIntervals) {
        AlertDialog(
            onDismissRequest = { showIntervals = false }, title = { Text("새로고침 주기") },
            text = {
                Column(Modifier.selectableGroup()) {
                    intervals.forEach { (minutes, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                            selected = state.autoRefreshMinutes == minutes,
                            role = Role.RadioButton, onClick = { onIntervalChanged(minutes); showIntervals = false },
                        ), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RadioButton(selected = state.autoRefreshMinutes == minutes, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showIntervals = false }) { Text("취소") } },
        )
    }
}

@Composable
internal fun DeveloperOptionsScreen(
    state: HomeUiState, refreshDiagnostics: FreetUsageRefreshDiagnostics, onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit, onUseMock: () -> Unit, onClearMock: () -> Unit,
    onClearCache: () -> Unit, onDisableDeveloperMode: () -> Unit,
) {
    OneUiScreen("개발자 옵션", onBack) {
        OneUiGroup {
            OneUiSwitchRow("개발자 옵션 사용", checked = true, onChange = { if (!it) onDisableDeveloperMode() })
        }
        OneUiSectionTitle("상태")
        OneUiGroup {
            OneUiSettingRow("세션", summary = state.sessionStatus.name)
            OneUiDivider()
            OneUiSettingRow("최근 API 응답", summary = refreshDiagnostics.httpStatus?.toString() ?: "기록 없음")
            OneUiDivider()
            OneUiSettingRow("캐시", summary = state.snapshot?.fetchedAt?.toString() ?: "저장된 사용량 없음")
            OneUiDivider()
            OneUiSettingRow("자동 갱신", summary = "${state.autoRefreshMinutes}분 · ${if (state.autoRefreshEnabled) "사용" else "사용 안 함"}")
            OneUiDivider()
            OneUiSettingRow("갱신 대기", summary = state.refreshDue.toString())
            OneUiDivider()
            OneUiSettingRow("최근 백그라운드 시도", summary = state.lastBackgroundAttempt?.toString() ?: "기록 없음")
            OneUiDivider()
            OneUiSettingRow("백그라운드 결과", summary = state.lastBackgroundResult ?: "기록 없음")
            OneUiDivider()
            OneUiSettingRow("최근 갱신 원인", summary = state.lastRefreshTrigger ?: "기록 없음")
            state.errorMessage?.let { OneUiDivider(); OneUiSettingRow("최근 오류", summary = it) }
        }
        OneUiSectionTitle("테스트 및 진단")
        OneUiGroup {
            OneUiSwitchRow("테스트 데이터", "실제 사용량과 예측 기록에 포함되지 않아요",
                checked = state.isMockData, onChange = { if (it) onUseMock() else onClearMock() })
            OneUiDivider()
            OneUiSettingRow("개발자 진단", summary = "응답 구조, 갱신 단계, 로그인 및 위젯", onClick = onOpenDiagnostics)
            OneUiDivider()
            OneUiSettingRow("캐시 초기화", summary = "저장된 사용량과 소진 예측 이력을 지워요", onClick = onClearCache)
        }
    }
}
