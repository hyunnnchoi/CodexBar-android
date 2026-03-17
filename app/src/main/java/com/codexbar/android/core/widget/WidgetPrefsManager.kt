package com.codexbar.android.core.widget

import android.content.Context
import android.content.SharedPreferences
import com.codexbar.android.core.domain.model.AiService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-widget configuration (selected services) and cached quota data.
 * Uses plain SharedPreferences (not encrypted) since widget data is non-sensitive display info.
 */
@Singleton
class WidgetPrefsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("codexbar_widget_prefs", Context.MODE_PRIVATE)
    }

    // --- Per-widget service selection ---

    fun saveSelectedServices(appWidgetId: Int, services: Set<AiService>) {
        val key = "widget_${appWidgetId}_services"
        prefs.edit().putStringSet(key, services.map { it.name }.toSet()).commit()
    }

    fun getSelectedServices(appWidgetId: Int): Set<AiService> {
        val key = "widget_${appWidgetId}_services"
        val names = prefs.getStringSet(key, null) ?: return emptySet()
        return names.mapNotNull { name ->
            try { AiService.valueOf(name) } catch (_: Exception) { null }
        }.toSet()
    }

    fun deleteWidgetConfig(appWidgetId: Int) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("widget_${appWidgetId}_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // --- Cached quota data for widgets ---

    fun cacheQuotaData(service: AiService, label: String, utilization: Double, resetsAtEpochSecond: Long?) {
        val prefix = "cache_${service.name}"
        prefs.edit()
            .putString("${prefix}_labels", getCachedLabels(service).plus(label).joinToString(","))
            .putFloat("${prefix}_${label}_util", utilization.toFloat())
            .apply {
                if (resetsAtEpochSecond != null) {
                    putLong("${prefix}_${label}_resets", resetsAtEpochSecond)
                } else {
                    remove("${prefix}_${label}_resets")
                }
            }
            .apply()
    }

    fun cacheAllQuotaData(service: AiService, windows: List<Triple<String, Double, Long?>>) {
        val prefix = "cache_${service.name}"
        val editor = prefs.edit()
        // Clear old cache for this service
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }

        val labels = windows.map { it.first }
        editor.putString("${prefix}_labels", labels.joinToString(","))
        for ((label, utilization, resetsAt) in windows) {
            editor.putFloat("${prefix}_${label}_util", utilization.toFloat())
            if (resetsAt != null) {
                editor.putLong("${prefix}_${label}_resets", resetsAt)
            }
        }
        editor.putLong("${prefix}_updated_at", System.currentTimeMillis())
        editor.apply()
    }

    fun getCachedLabels(service: AiService): List<String> {
        val raw = prefs.getString("cache_${service.name}_labels", null) ?: return emptyList()
        return raw.split(",").filter { it.isNotEmpty() }
    }

    fun getCachedUtilization(service: AiService, label: String): Float {
        return prefs.getFloat("cache_${service.name}_${label}_util", 0f)
    }

    fun getCachedResetsAt(service: AiService, label: String): Long? {
        val value = prefs.getLong("cache_${service.name}_${label}_resets", -1L)
        return if (value > 0) value else null
    }

    fun getCachedUpdatedAt(service: AiService): Long {
        return prefs.getLong("cache_${service.name}_updated_at", 0L)
    }

    /** Returns the highest utilization across all cached windows for this service. */
    fun getMaxCachedUtilization(service: AiService): Float {
        val labels = getCachedLabels(service)
        if (labels.isEmpty()) return 0f
        return labels.maxOf { getCachedUtilization(service, it) }
    }
}
