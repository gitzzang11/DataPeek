package com.datapeek.data.provider

import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot

interface CarrierProvider {
    val carrier: Carrier

    fun getLoginUrl(): String

    suspend fun isAuthenticated(): Boolean

    suspend fun fetchUsage(): Result<UsageSnapshot>

    suspend fun markAuthenticated() = Unit

    suspend fun logout()

    fun supportsBackgroundRefresh(): Boolean

    companion object {
        const val CARRIER_FREET = "FREET_SKT"
        const val CARRIER_TWORLD = "SKT_TWORLD"
    }
}
