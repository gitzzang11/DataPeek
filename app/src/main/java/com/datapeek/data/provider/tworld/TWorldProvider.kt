package com.datapeek.data.provider.tworld

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageException
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.provider.CarrierProvider
import com.datapeek.data.session.SecureSessionStorage

class TWorldProvider(
    private val sessionStorage: SecureSessionStorage,
) : CarrierProvider {
    override val carrier = Carrier.SKT_TWORLD

    override fun getLoginUrl() = LOGIN_URL

    override suspend fun isAuthenticated() = sessionStorage.isAuthenticated()

    override suspend fun markAuthenticated() = sessionStorage.markAuthenticated()

    override suspend fun fetchUsage(): Result<UsageSnapshot> {
        if (!isAuthenticated()) return Result.failure(UsageException(UsageError.NotAuthenticated))
        return Result.failure(UsageException(UsageError.UnsupportedResponse))
    }

    override suspend fun logout() = sessionStorage.clear()

    override fun supportsBackgroundRefresh() = false

    companion object {
        const val LOGIN_URL = "https://www.tworld.co.kr/"
        const val OFFICIAL_HOST = "www.tworld.co.kr"
    }
}
