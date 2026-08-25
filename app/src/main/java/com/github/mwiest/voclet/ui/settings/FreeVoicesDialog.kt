package com.github.mwiest.voclet.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.R

/**
 * Explains that playback uses the system's voices, and links the free voice
 * packs worth installing when a language has none.
 */
@Composable
fun FreeVoicesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tts_free_voices)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_tts_info),
                    style = MaterialTheme.typography.bodyMedium,
                )
                VoiceLink(
                    name = stringResource(R.string.settings_tts_espeak),
                    url = stringResource(R.string.settings_tts_espeak_url),
                    onClick = openLink,
                )
                VoiceLink(
                    name = stringResource(R.string.settings_tts_rhvoice),
                    url = stringResource(R.string.settings_tts_rhvoice_url),
                    onClick = openLink,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/** A link out to a voice pack's project page, marked as leaving the app. */
@Composable
private fun VoiceLink(
    name: String,
    url: String,
    onClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onClick(url) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
