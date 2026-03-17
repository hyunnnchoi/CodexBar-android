package com.codexbar.android.core.workmanager

import android.content.Context
import androidx.startup.Initializer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer as AndroidWorkManagerInitializer
import java.util.concurrent.TimeUnit

class WorkManagerInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        schedulePeriodicRefresh(context)
        scheduleTokenRefresh(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(AndroidWorkManagerInitializer::class.java)
    }

    companion object {
        private const val QUOTA_WORK_NAME = "quota_periodic_refresh"
        private const val TOKEN_WORK_NAME = "token_periodic_refresh"

        fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(QUOTA_WORK_NAME)
                return
            }

            // WorkManager minimum is 15 minutes
            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<QuotaRefreshWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("quota_refresh")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                QUOTA_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun scheduleTokenRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(TOKEN_WORK_NAME)
                return
            }

            val effectiveInterval = intervalMinutes.coerceAtLeast(15)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                effectiveInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("token_refresh")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TOKEN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
