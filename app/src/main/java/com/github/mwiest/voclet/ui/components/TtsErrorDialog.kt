package com.github.mwiest.voclet.ui.components

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.tts.TtsResult

@Composable
fun TtsErrorDialog(
    error: TtsResult,
    onDismiss: () -> Unit,
    onFix: (Intent) -> Unit
) {
    val (message, actionLabel, intent) = when (error) {
        is TtsResult.EngineNotInstalled -> Triple(
            stringResource(R.string.tts_error_engine_not_installed),
            stringResource(R.string.tts_action_open_settings),
            error.settingsIntent
        )
        is TtsResult.LanguageMissing -> Triple(
            stringResource(R.string.tts_error_language_missing),
            stringResource(R.string.tts_action_install_data),
            error.installDataIntent
        )
        is TtsResult.LanguageNotSupported -> Triple(
            stringResource(R.string.tts_error_language_not_supported),
            stringResource(R.string.tts_action_open_settings),
            error.settingsIntent
        )
        else -> return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tts_error_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onFix(intent) }) {
                Text(actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
