package com.codexbar.android.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.AiService
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

class QuotaGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetPrefs = WidgetPrefsManager(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val selectedServices = widgetPrefs.getSelectedServices(appWidgetId)

        provideContent {
            GlanceTheme {
                WidgetContent(selectedServices.toList(), widgetPrefs)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        services: List<AiService>,
        widgetPrefs: WidgetPrefsManager
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp)
        ) {
            when {
                services.isEmpty() -> EmptyState()
                services.size == 1 -> SingleServiceLayout(services.first(), widgetPrefs)
                else -> MultiServiceLayout(services, widgetPrefs)
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No services configured",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp
                )
            )
        }
    }

    @Composable
    private fun SingleServiceLayout(
        service: AiService,
        widgetPrefs: WidgetPrefsManager
    ) {
        val labels = widgetPrefs.getCachedLabels(service)
        val maxUtil = widgetPrefs.getMaxCachedUtilization(service)
        val remaining = ((1f - maxUtil) * 100).toInt()

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = service.displayName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                RefreshButton()
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            Text(
                text = "${remaining}% left",
                style = TextStyle(
                    color = utilizationColor(maxUtil),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            SegmentedProgressBar(maxUtil)

            Spacer(modifier = GlanceModifier.height(6.dp))

            for (label in labels.take(3)) {
                val util = widgetPrefs.getCachedUtilization(service, label)
                val resetsAt = widgetPrefs.getCachedResetsAt(service, label)
                val resetText = resetsAt?.let { formatResetTime(it) } ?: ""
                val windowRemaining = ((1f - util) * 100).toInt()

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$label: ${windowRemaining}%",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    if (resetText.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = resetText,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MultiServiceLayout(
        services: List<AiService>,
        widgetPrefs: WidgetPrefsManager
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CodexBar",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                RefreshButton()
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            for ((index, service) in services.take(3).withIndex()) {
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                ServiceRow(service, widgetPrefs)
            }
        }
    }

    @Composable
    private fun ServiceRow(service: AiService, widgetPrefs: WidgetPrefsManager) {
        val maxUtil = widgetPrefs.getMaxCachedUtilization(service)
        val remaining = ((1f - maxUtil) * 100).toInt()

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = service.displayName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "${remaining}%",
                    style = TextStyle(
                        color = utilizationColor(maxUtil),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(3.dp))
            SegmentedProgressBar(maxUtil)
        }
    }

    @Composable
    private fun RefreshButton() {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Refresh",
            modifier = GlanceModifier
                .size(20.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>()),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
        )
    }

    /**
     * Segmented progress bar: 20 segments, filled count based on remaining (1 - utilization).
     */
    @Composable
    private fun SegmentedProgressBar(utilization: Float) {
        val totalSegments = 20
        val filledSegments = ((1f - utilization) * totalSegments).roundToInt().coerceIn(0, totalSegments)
        val fillColor = utilizationColor(utilization)
        val trackColor = ColorProvider(androidx.compose.ui.graphics.Color(0xFFE0E0E0))

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(6.dp)
        ) {
            for (i in 0 until totalSegments) {
                val color = if (i < filledSegments) fillColor else trackColor
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(6.dp)
                        .background(color)
                ) {}
                if (i < totalSegments - 1) {
                    Spacer(modifier = GlanceModifier.width(1.dp))
                }
            }
        }
    }

    companion object {
        fun utilizationColor(utilization: Float): ColorProvider {
            val color = when {
                utilization >= 0.85f -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
                utilization >= 0.60f -> androidx.compose.ui.graphics.Color(0xFFF57F17)
                else -> androidx.compose.ui.graphics.Color(0xFF388E3C)
            }
            return ColorProvider(color)
        }

        fun formatResetTime(epochSecond: Long): String {
            val now = Instant.now()
            val resetAt = Instant.ofEpochSecond(epochSecond)
            if (resetAt.isBefore(now)) return ""
            val duration = Duration.between(now, resetAt)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
            return when {
                hours >= 24 -> "${hours / 24}d${hours % 24}h"
                hours > 0 -> "${hours}h${minutes}m"
                else -> "${minutes}m"
            }
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent("com.codexbar.android.ACTION_REFRESH")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
        QuotaGlanceWidget().update(context, glanceId)
    }
}
