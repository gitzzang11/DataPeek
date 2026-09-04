package com.datapeek.data.session

import android.webkit.CookieManager

object WebViewCookieBridge {
    fun clearAllCookies(onComplete: () -> Unit = {}) {
        CookieManager.getInstance().removeAllCookies { onComplete() }
        CookieManager.getInstance().flush()
    }
}
