package com.codexbar.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.core.domain.model.AiService
import com.codexbar.android.core.domain.model.AppError
import com.codexbar.android.core.domain.model.QuotaInfo
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.di.ClaudeRepository
import com.codexbar.android.di.CodexRepository
import com.codexbar.android.di.GeminiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ClaudeRepository private val claudeRepository: QuotaRepository,
    @CodexRepository private val codexRepository: QuotaRepository,
    @GeminiRepository private val geminiRepository: QuotaRepository,
    private val prefsManager: EncryptedPrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.value = DashboardUiState.Loading

            val repos = buildList {
                if (prefsManager.hasCredential(AiService.CLAUDE)) add(AiService.CLAUDE to claudeRepository)
                if (prefsManager.hasCredential(AiService.CODEX)) add(AiService.CODEX to codexRepository)
                if (prefsManager.hasCredential(AiService.GEMINI)) add(AiService.GEMINI to geminiRepository)
            }

            if (repos.isEmpty()) {
                _uiState.value = DashboardUiState.Success(emptyList(), Instant.now())
                _isRefreshing.value = false
                return@launch
            }

            val deferreds = repos.map { (service, repo) ->
                async { service to repo.fetchQuota() }
            }

            val results = deferreds.map { it.await() }

            val successCards = mutableListOf<ServiceCardData>()
            val errors = mutableMapOf<AiService, AppError>()

            for ((service, result) in results) {
                when (result) {
                    is Result.Success -> {
                        successCards.add(mapToCardData(result.value))
                    }
                    is Result.Failure -> {
                        errors[service] = result.error
                        successCards.add(
                            ServiceCardData(
                                service = service,
                                windows = emptyList(),
                                extraUsage = null,
                                tier = null,
                                error = result.error
                            )
                        )
                    }
                }
            }

            // Sort by highest utilization first
            val sortedCards = successCards.sortedByDescending { card ->
                card.windows.maxOfOrNull { it.utilization } ?: 0.0
            }

            _uiState.value = if (errors.isEmpty()) {
                DashboardUiState.Success(sortedCards, Instant.now())
            } else if (successCards.all { it.error != null }) {
                DashboardUiState.Error(errors.values.first())
            } else {
                DashboardUiState.PartialSuccess(sortedCards, errors)
            }

            _isRefreshing.value = false
        }
    }

    private fun mapToCardData(quotaInfo: QuotaInfo): ServiceCardData {
        return ServiceCardData(
            service = quotaInfo.service,
            windows = quotaInfo.windows.map { window ->
                UsageWindowUi(
                    label = window.label,
                    utilization = window.utilization
                )
            },
            extraUsage = quotaInfo.extraUsage?.let { extra ->
                ExtraUsageUi(
                    monthlyLimit = extra.monthlyLimit,
                    usedCredits = extra.usedCredits,
                    utilization = extra.utilization,
                    currency = extra.currency
                )
            },
            tier = quotaInfo.tier
        )
    }
}
