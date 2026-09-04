package com.datapeek.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStatusTest {
    @Test
    fun mapsAuthenticationErrorsToLoginRequired() {
        assertEquals("로그인 필요", widgetStatusLabel("로그인이 필요합니다."))
        assertEquals("로그인 필요", widgetStatusLabel("로그인이 만료되었습니다."))
    }

    @Test
    fun mapsOtherErrorsToUpdateFailure() {
        assertEquals("업데이트 실패", widgetStatusLabel("네트워크에 연결할 수 없습니다."))
    }

    @Test
    fun expiredSessionWidgetOpensTheLoginFlow() {
        assertEquals(true, widgetRequiresLogin("프리티 로그인이 만료되었습니다."))
        assertEquals(false, widgetRequiresLogin("네트워크에 연결할 수 없습니다."))
    }

    @Test
    fun hasNoStatusLabelWhenThereIsNoError() {
        assertNull(widgetStatusLabel(null))
    }

    @Test
    fun exposesLoadingLabelWhileWidgetRefreshIsRunning() {
        assertEquals("새로고침", widgetRefreshLabel(isRefreshing = false))
        assertEquals("새로고침 중…", widgetRefreshLabel(isRefreshing = true))
    }

    @Test
    fun largeWidgetStatusIncludesNormalAndRefreshingStates() {
        assertEquals("정상", widgetStateLabel(lastError = null, isRefreshing = false))
        assertEquals("새로고침 중", widgetStateLabel(lastError = null, isRefreshing = true))
        assertEquals("업데이트 실패", widgetStateLabel(lastError = "네트워크 오류", isRefreshing = false))
    }
}
