package com.codexbar.android.feature.dashboard

import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import java.time.Instant

sealed class DashboardUiState {
    data object Loading : DashboardUiState()

    data class Success(
        val cards: List<ServiceCardData>,
        val lastUpdated: Instant
    ) : DashboardUiState()

    data class PartialSuccess(
        val cards: List<ServiceCardData>,
        val errors: Map<AiService, AppError>
    ) : DashboardUiState()

    data class Error(val error: AppError) : DashboardUiState()
}

data class ServiceCardData(
    val service: AiService,
    val windows: List<UsageWindowUi>,
    val extraUsage: ExtraUsageUi?,
    val tier: String?,
    val isLoading: Boolean = false,
    val error: AppError? = null
)

data class UsageWindowUi(
    val label: String,
    val utilization: Double
)

data class ExtraUsageUi(
    val monthlyLimit: Double,
    val usedCredits: Double,
    val utilization: Double,
    val currency: String
)
