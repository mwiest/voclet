package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.ui.theme.LocalExtendedColors

/**
 * The "AI Assistant" settings section: one row per backend, each opening its
 * own detail screen and carrying a marker for whether it is set up.
 *
 * There is no backend preference to make here. Requests route on what is
 * actually available (see `AiBackendResolver`), so the job of this section is
 * to show, at a glance, whether *anything* is set up — a fresh install has
 * neither an API key nor a downloaded model, and both rows say so.
 */
@Composable
fun AiSettingsOverview(
    cloudSummary: String,
    cloudConfigured: Boolean,
    localSummary: String,
    localConfigured: Boolean,
    onCloudClick: () -> Unit,
    onLocalClick: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.settings_ai),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_ai_info),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiBackendRow(
                title = stringResource(R.string.settings_ai_cloud),
                summary = cloudSummary,
                configured = cloudConfigured,
                onClick = onCloudClick,
            )
            AiBackendRow(
                title = stringResource(R.string.settings_ai_local),
                summary = localSummary,
                configured = localConfigured,
                onClick = onLocalClick,
            )
        }

        // Only worth explaining once both are set up; until then the rows above
        // are asking the user to set up anything at all.
        if (cloudConfigured && localConfigured) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_ai_routing_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiBackendRow(
    title: String,
    summary: String,
    configured: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConfiguredMarker(configured)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Green tick when this backend can serve a request, hollow grey circle when not. */
@Composable
private fun ConfiguredMarker(configured: Boolean) {
    Icon(
        imageVector = if (configured) {
            Icons.Default.CheckCircle
        } else {
            Icons.Default.RadioButtonUnchecked
        },
        contentDescription = stringResource(
            if (configured) {
                R.string.settings_ai_status_configured_description
            } else {
                R.string.settings_ai_status_missing_description
            },
        ),
        tint = if (configured) {
            LocalExtendedColors.current.success.color
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(24.dp),
    )
}
