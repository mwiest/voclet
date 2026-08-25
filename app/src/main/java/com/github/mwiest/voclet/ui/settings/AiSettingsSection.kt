package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
fun AiSettingsSection(
    cloudSummary: String,
    cloudConfigured: Boolean,
    localSummary: String,
    localConfigured: Boolean,
    onCloudClick: () -> Unit,
    onLocalClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_ai),
        onInfoClick = onInfoClick,
    ) {
        SettingsRow(
            icon = Icons.Outlined.Cloud,
            title = stringResource(R.string.settings_ai_cloud),
            summary = cloudSummary,
            summaryLeading = { ConfiguredMarker(cloudConfigured) },
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onCloudClick,
        )
        SettingsRow(
            icon = Icons.Outlined.Memory,
            title = stringResource(R.string.settings_ai_local),
            summary = localSummary,
            summaryLeading = { ConfiguredMarker(localConfigured) },
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onLocalClick,
        )
    }
}

/**
 * Green tick when this backend can serve a request, hollow ring when not.
 *
 * Sized to the summary line it sits in front of: the marker qualifies that
 * value, and at text size it cannot be mistaken for a control.
 */
@Composable
private fun ConfiguredMarker(configured: Boolean) {
    Icon(
        imageVector = if (configured) Icons.Default.CheckCircle else Icons.Outlined.Circle,
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
        modifier = Modifier.size(14.dp),
    )
}
