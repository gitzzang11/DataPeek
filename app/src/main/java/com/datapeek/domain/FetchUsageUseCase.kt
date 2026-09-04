package com.datapeek.domain

import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.repository.UsageRepository

class FetchUsageUseCase(private val repository: UsageRepository) {
    suspend operator fun invoke(): Result<UsageSnapshot> = repository.refresh()
}
