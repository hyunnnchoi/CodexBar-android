package com.yourname.aiquota.core.workmanager

import android.content.Context
import androidx.startup.Initializer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer as AndroidWorkManagerInitializer
import com.yourname.aiquota.core.security.EncryptedPrefsManager
import java.util.concurrent.TimeUnit

class WorkManagerInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        schedulePeriodicRefresh(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(AndroidWorkManagerInitializer::class.java)
    }

    companion object {
        private const val WORK_NAME = "quota_periodic_refresh"

        fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long = 30) {
            if (intervalMinutes <= 0) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
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
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
