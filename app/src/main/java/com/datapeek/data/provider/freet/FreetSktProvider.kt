package com.datapeek.data.provider.freet

import android.webkit.CookieManager
import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.provider.CarrierProvider
import com.datapeek.data.session.SecureSessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.net.URL
import java.time.Instant

class FreetSktProvider(
    private val sessionStorage: SecureSessionStorage,
) : CarrierProvider {
    override val carrier = Carrier.FREET_SKT

    override fun getLoginUrl() = LOGIN_URL

    override suspend fun isAuthenticated(): Boolean = sessionStorage.isAuthenticated() && withContext(Dispatchers.Main.immediate) {
        !CookieManager.getInstance().getCookie(USAGE_URL).isNullOrBlank()
    }

    override suspend fun markAuthenticated() = sessionStorage.markAuthenticated()

    override suspend fun fetchUsage(): Result<UsageSnapshot> {
        if (!sessionStorage.isAuthenticated()) {
            return Result.failure(UsageException(UsageError.NotAuthenticated))
        }

        val cookie = withContext(Dispatchers.Main.immediate) {
            CookieManager.getInstance().getCookie(USAGE_URL)
        }
        if (cookie.isNullOrBlank()) {
            return Result.failure(UsageException(UsageError.UnsupportedResponse))
        }

        return withContext(Dispatchers.IO) {
            val connection = (URL(USAGE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Cookie", cookie)
            }
            try {
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                    return@withContext Result.failure(UsageException(UsageError.SessionExpired))
                }
                if (status !in 200..299) {
                    return@withContext Result.failure(UsageException(UsageError.HttpError(status)))
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                if (connection.url.path == "/login") {
                    return@withContext Result.failure(UsageException(UsageError.SessionExpired))
                }
                Result.success(FreetHtmlUsageParser().parse(body, Instant.now()))
            } catch (error: UsageException) {
                Result.failure(error)
            } catch (error: UnknownHostException) {
                Result.failure(UsageException(UsageError.NetworkUnavailable))
            } catch (error: IOException) {
                Result.failure(UsageException(UsageError.NetworkUnavailable))
            } catch (error: Exception) {
                Result.failure(UsageException(UsageError.ParseError("공식 사용량 페이지를 해석하지 못했습니다.")))
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun logout() = sessionStorage.clear()

    // Freet authentication is hosted by the Activity's WebView pipeline. A headless
    // Worker cannot safely provide the same WebView lifecycle and session boundary.
    override fun supportsBackgroundRefresh() = false

    companion object {
        const val LOGIN_URL = "https://www.freet.co.kr/login"
        const val USAGE_URL = "https://www.freet.co.kr/mypage/usage"
        const val USAGE_API_URL = "https://api.freet.co.kr/mypage/usage"
        const val OFFICIAL_HOST = "www.freet.co.kr"
    }
}
