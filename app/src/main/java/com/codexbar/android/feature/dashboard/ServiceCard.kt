package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.AppError

@Composable
fun ServiceCard(
    cardData: ServiceCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Icon + Name + Tier badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = cardData.service.displayName,
                    modifier = Modifier.size(32.dp),
                    tint = Color(cardData.service.brandColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = cardData.service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                cardData.tier?.let { tier ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Error state
            if (cardData.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatError(cardData.error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary gauge (first window)
            val primaryWindow = cardData.windows.firstOrNull()
            primaryWindow?.let { window ->
                QuotaGaugeBar(
                    utilization = window.utilization,
                    label = window.label,
                    showPercentage = true,
                    resetsAt = window.resetsAt
                )
            }

            // Secondary windows
            val secondaryWindows = cardData.windows.drop(1)

            if (secondaryWindows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    secondaryWindows.forEach { window ->
                        QuotaGaugeBar(
                            utilization = window.utilization,
                            label = window.label,
                            showPercentage = true,
                            resetsAt = window.resetsAt
                        )
                    }
                }
            }

            // Extra usage (Claude credits)
            cardData.extraUsage?.let { extra ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Credits: ${extra.currency} ${String.format("%.2f", extra.usedCredits)} / ${String.format("%.2f", extra.monthlyLimit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatError(error: AppError): String {
    return when (error) {
        is AppError.NetworkError -> "Network error"
        is AppError.AuthError -> if (error.isTerminal) "Re-authentication required" else "Auth error"
        is AppError.RateLimited -> "Rate limited"
        is AppError.ParseError -> "Response parse error"
        is AppError.CredentialNotFound -> "No credentials configured"
        is AppError.ServiceUnavailable -> "Service unavailable"
    }
}
