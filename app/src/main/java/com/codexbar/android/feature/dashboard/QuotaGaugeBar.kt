package com.codexbar.android.feature.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant

private val AmberWarning = Color(0xFFFFC107)

/**
 * Displays a gauge bar showing remaining quota.
 * @param utilization 0.0-1.0 representing how much has been USED
 * The bar fills with the REMAINING amount and text shows "X% left"
 */
@Composable
fun QuotaGaugeBar(
    utilization: Double,
    label: String? = null,
    showPercentage: Boolean = true,
    resetsAt: Instant? = null,
    modifier: Modifier = Modifier
) {
    val remaining = (1.0 - utilization).coerceIn(0.0, 1.0)

    val animatedProgress by animateFloatAsState(
        targetValue = remaining.toFloat(),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "gauge_progress"
    )

    // Color based on how much is used (danger when high utilization)
    val gaugeColor = when {
        utilization >= 0.85 -> MaterialTheme.colorScheme.error
        utilization >= 0.60 -> AmberWarning
        else -> MaterialTheme.colorScheme.primary
    }

    val animatedColor by animateColorAsState(
        targetValue = gaugeColor,
        animationSpec = tween(durationMillis = 400),
        label = "gauge_color"
    )

    val resetText = resetsAt?.let { formatResetTime(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(50))
                        .background(animatedColor)
                )
            }

            if (showPercentage) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(remaining * 100).toInt()}% left",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = animatedColor
                )
            }
        }

        if (resetText != null) {
            Text(
                text = resetText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatResetTime(resetsAt: Instant): String? {
    val now = Instant.now()
    if (resetsAt.isBefore(now)) return null
    val duration = Duration.between(now, resetsAt)
    val totalMinutes = duration.toMinutes()
    val hours = duration.toHours()
    val days = duration.toDays()
    return when {
        days >= 1 -> "Resets in ${days}d ${hours % 24}h"
        hours >= 1 -> "Resets in ${hours}h ${totalMinutes % 60}m"
        else -> "Resets in ${totalMinutes}m"
    }
}
