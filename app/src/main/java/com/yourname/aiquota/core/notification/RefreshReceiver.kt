package com.yourname.aiquota.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yourname.aiquota.core.workmanager.QuotaRefreshWorker

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == QuotaNotificationService.ACTION_REFRESH) {
            val request = OneTimeWorkRequestBuilder<QuotaRefreshWorker>()
                .addTag("quota_refresh_manual")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
