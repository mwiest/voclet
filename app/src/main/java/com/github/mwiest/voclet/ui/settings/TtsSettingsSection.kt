package com.github.mwiest.voclet.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.mwiest.voclet.R

/**
 * The "Text-to-Speech" settings section.
 *
 * Voclet speaks through whatever engine the system provides, so most of what
 * there is to say here points elsewhere: the system's own TTS settings, and
 * the free voice packs worth installing when a language has no voice. Those
 * live behind their own rows rather than as prose in the section, which leaves
 * the section itself readable as a list of settings.
 */
@Composable
fun TtsSettingsSection(
    enabledByDefault: Boolean,
    onEnabledByDefaultChange: (Boolean) -> Unit,
    variantsSummary: String,
    engineName: String?,
    onVariantsClick: () -> Unit,
    onSystemSettingsClick: () -> Unit,
    onFreeVoicesClick: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_tts)) {
        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = stringResource(R.string.settings_tts_enabled_by_default),
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
            icon = Icons.Outlined.Settings,
            title = stringResource(R.string.settings_tts_open_system_settings),
            summary = engineName?.let { stringResource(R.string.settings_tts_active_engine, it) },
            trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
            onClick = onSystemSettingsClick,
        )
        SettingsRow(
            icon = Icons.Outlined.RecordVoiceOver,
            title = stringResource(R.string.settings_tts_free_voices),
            summary = stringResource(R.string.settings_tts_free_voices_summary),
            onClick = onFreeVoicesClick,
        )
    }
}
