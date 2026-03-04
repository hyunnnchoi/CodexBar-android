package com.yourname.aiquota.core.domain.repository

import com.yourname.aiquota.core.domain.model.AppError
import com.yourname.aiquota.core.domain.model.QuotaInfo
import com.yourname.aiquota.core.domain.model.Result

interface QuotaRepository {
    suspend fun fetchQuota(): Result<QuotaInfo, AppError>
    suspend fun validateCredential(): Result<Unit, AppError>
}
