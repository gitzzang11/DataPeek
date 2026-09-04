package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetLoginNavigationPolicyTest {
    @Test
    fun routesTheObservedGoogleOAuthAuthorizationNavigationToCustomTabs() {
        val url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&redirect_uri=https%3A%2F%2Fwww.freet.co.kr%2Foauth%2Fgoogle"

        assertEquals(
            FreetLoginNavigationAction.OPEN_CUSTOM_TAB,
            FreetLoginNavigationPolicy.classify(url),
        )
    }

    @Test
    fun classifiesObservedGoogleOAuthAsAWebViewBlockForCustomTabs() {
        assertEquals(
            FreetLoginNavigationAction.OPEN_CUSTOM_TAB,
            FreetLoginNavigationPolicy.classify(
                "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&state=redacted",
            ),
        )
    }

    @Test
    fun allowsTheObservedFreetGoogleCallback() {
        assertEquals(
            FreetLoginNavigationAction.ALLOW_OFFICIAL,
            FreetLoginNavigationPolicy.classify("https://www.freet.co.kr/oauth/google?code=redacted"),
        )
    }

    @Test
    fun blocksUnrelatedGooglePagesAndExternalHosts() {
        assertEquals(
            FreetLoginNavigationAction.BLOCK_EXTERNAL,
            FreetLoginNavigationPolicy.classify("https://accounts.google.com/signin/v2/identifier"),
        )
        assertEquals(
            FreetLoginNavigationAction.BLOCK_EXTERNAL,
            FreetLoginNavigationPolicy.classify("https://example.com/login"),
        )
        assertEquals(
            FreetLoginNavigationAction.BLOCK_EXTERNAL,
            FreetLoginNavigationPolicy.classify("http://www.freet.co.kr/login"),
        )
    }

    @Test
    fun explainsWhenTheCustomTabCannotBeOpened() {
        assertTrue(FreetLoginNavigationPolicy.GOOGLE_OAUTH_LAUNCH_FAILED_MESSAGE.contains("Google"))
        assertTrue(FreetLoginNavigationPolicy.GOOGLE_OAUTH_LAUNCH_FAILED_MESSAGE.contains("브라우저"))
    }
}
