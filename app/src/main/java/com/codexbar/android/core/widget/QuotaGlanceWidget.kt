package com.codexbar.android.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
                WidgetContent(selectedServices.toList().sortedBy { it.ordinal }, widgetPrefs)
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
                .cornerRadius(20.dp)
                .background(ColorProvider(Color(0xB01C1B1F)))
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp)
        ) {
            if (services.isEmpty()) {
                EmptyState()
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    for ((index, service) in services.withIndex()) {
                        if (index > 0) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Divider()
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                        ServiceSection(service, widgetPrefs, showRefresh = index == 0)
                    }
                }
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
                style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.6f)), fontSize = 14.sp)
            )
        }
    }

    @Composable
    private fun Divider() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(Color.White.copy(alpha = 0.1f)))
        ) {}
    }

    @Composable
    private fun ServiceSection(
        service: AiService,
        widgetPrefs: WidgetPrefsManager,
        showRefresh: Boolean
    ) {
        val labels = widgetPrefs.getCachedLabels(service)
        val tier = widgetPrefs.getCachedTier(service)

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Header: service name + tier + refresh button
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Service icon dot
                Box(
                    modifier = GlanceModifier
                        .size(10.dp)
                        .cornerRadius(5.dp)
                        .background(ColorProvider(Color(service.brandColor)))
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))

                Text(
                    text = service.displayName,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (tier != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(4.dp)
                            .background(ColorProvider(Color.White.copy(alpha = 0.15f)))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tier,
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                if (showRefresh) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier
                            .size(18.dp)
                            .clickable(actionRunCallback<RefreshWidgetAction>()),
                        colorFilter = ColorFilter.tint(ColorProvider(Color.White.copy(alpha = 0.5f)))
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Each usage window — same layout as app dashboard
            for ((index, label) in labels.withIndex()) {
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                WindowRow(service, label, widgetPrefs)
            }

            // Show placeholder if no cached data yet
            if (labels.isEmpty()) {
                Text(
                    text = "Waiting for data...",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun WindowRow(
        service: AiService,
        label: String,
        widgetPrefs: WidgetPrefsManager
    ) {
        val utilization = widgetPrefs.getCachedUtilization(service, label)
        val remaining = ((1f - utilization) * 100).toInt()
        val resetsAt = widgetPrefs.getCachedResetsAt(service, label)
        val resetText = resetsAt?.let { formatResetTime(it) } ?: ""

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Label + percentage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "${remaining}% left",
                    style = TextStyle(
                        color = utilizationColor(utilization),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // Progress bar
            SegmentedProgressBar(utilization)

            // Reset time
            if (resetText.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "Resets in $resetText",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.4f)),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun SegmentedProgressBar(utilization: Float) {
        val totalSegments = 24
        val filledSegments = ((1f - utilization) * totalSegments).roundToInt().coerceIn(0, totalSegments)
        val fillColor = utilizationColor(utilization)
        val trackColor = ColorProvider(Color.White.copy(alpha = 0.1f))

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(4.dp)
        ) {
            for (i in 0 until totalSegments) {
                val color = if (i < filledSegments) fillColor else trackColor
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(4.dp)
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
                utilization >= 0.85f -> Color(0xFFEF5350)
                utilization >= 0.60f -> Color(0xFFFFB74D)
                else -> Color(0xFF81C784)
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
                hours >= 24 -> "${hours / 24}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes}m"
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
