package com.github.mwiest.voclet.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
 *
 * The on-device row carries two markers rather than one. Since the catalog was
 * split, translation and camera import are provisioned separately, and a single
 * tick would have to mean "one of the two works" — which is the one thing a
 * user checking this screen must not be told, because it does not say which.
 * The cloud row keeps its single marker: one API key serves both features.
 */
@Composable
fun AiSettingsSection(
    cloudSummary: String,
    cloudConfigured: Boolean,
    /** Display name of the downloaded translation model, or null if there is none. */
    localTextModel: String?,
    /** Display name of the downloaded camera model, or null if there is none. */
    localVisionModel: String?,
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
            summaryContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LocalFeatureLine(R.string.settings_ai_section_text, localTextModel)
                    LocalFeatureLine(R.string.settings_ai_section_vision, localVisionModel)
                }
            },
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onLocalClick,
        )
    }
}

/**
 * One on-device feature: its marker, its name, and the model serving it.
 *
 * Names the feature rather than only the model, because a model name alone
 * ("EuroLLM 1.7B") says nothing about which half of the AI it powers.
 */
@Composable
private fun LocalFeatureLine(@StringRes featureLabel: Int, modelName: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ConfiguredMarker(modelName != null)
        Text(
            text = stringResource(
                R.string.settings_ai_local_feature_line,
                stringResource(featureLabel),
                modelName ?: stringResource(R.string.settings_ai_status_not_downloaded),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
