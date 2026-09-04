package com.datapeek

import android.app.Application
import com.datapeek.data.provider.CarrierProvider
import com.datapeek.data.provider.freet.FreetSktProvider
import com.datapeek.data.provider.tworld.TWorldProvider
import com.datapeek.data.repository.UsageRepository
import com.datapeek.data.session.SecureSessionStorage
import com.datapeek.worker.UsageRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DataPeekApplication : Application() {
    val repository: UsageRepository by lazy {
        UsageRepository(
            context = this,
            providers = mapOf(
                CarrierProvider.CARRIER_FREET to FreetSktProvider(
                    SecureSessionStorage(this, CarrierProvider.CARRIER_FREET),
                ),
                CarrierProvider.CARRIER_TWORLD to TWorldProvider(
                    SecureSessionStorage(this, CarrierProvider.CARRIER_TWORLD),
                ),
            ),
        )
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            UsageRefreshScheduler.ensureConfigured(this@DataPeekApplication)
        }
    }
}
