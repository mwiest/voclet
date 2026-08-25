package com.github.mwiest.voclet.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.mwiest.voclet.R

/**
 * The "Text-to-Speech" settings section.
 *
 * Voclet speaks through whatever engine the system provides, so what there is
 * to say about voices — which engine serves them, where to get free ones, the
 * way out to the system's own settings — is gathered behind the last row
 * rather than laid out as prose here.
 */
@Composable
fun TtsSettingsSection(
    enabledByDefault: Boolean,
    onEnabledByDefaultChange: (Boolean) -> Unit,
    variantsSummary: String,
    engineName: String?,
    onVariantsClick: () -> Unit,
    onSystemTtsClick: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_tts)) {
        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = stringResource(R.string.settings_tts_enabled_by_default),
            summary = stringResource(R.string.settings_tts_enabled_by_default_summary),
            checked = enabledByDefault,
            onCheckedChange = onEnabledByDefaultChange,
        )
        SettingsRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_tts_language_overrides),
            summary = variantsSummary,
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onVariantsClick,
        )
        SettingsRow(
            icon = Icons.Outlined.RecordVoiceOver,
            title = stringResource(R.string.settings_tts_system),
            // The engine's own name where we can read it; the setting it comes
            // from is unset on devices where nobody ever picked one.
            summary = engineName ?: stringResource(R.string.settings_tts_system_summary_unknown),
            onClick = onSystemTtsClick,
        )
    }
}
