package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.ai.AiBackend
import com.github.mwiest.voclet.data.ai.local.AiModel
import com.github.mwiest.voclet.data.ai.local.AiModelViewModel
import com.github.mwiest.voclet.data.ai.local.ModelStatus
import com.github.mwiest.voclet.data.ai.local.ModelTier
import java.util.Locale

/**
 * "AI Assistant" settings section: shows the detected device tier and a card
 * per downloadable model with status and download/delete controls. Only one
 * model is kept at a time; downloading a second one prompts to replace.
 */
@Composable
fun AiAssistantSection(
    backend: AiBackend,
    onBackendChange: (AiBackend) -> Unit,
    viewModel: AiModelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // The model the user wants to download but which needs confirmation first
    // (because another model is already downloaded and/or the file is large).
    var pendingDownload by remember { mutableStateOf<AiModel?>(null) }

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

        // Backend selector: Auto / Cloud / On-device
        val backends = listOf(AiBackend.AUTO, AiBackend.CLOUD, AiBackend.LOCAL)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            backends.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = backend == option,
                    onClick = { onBackendChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = backends.size),
                ) { Text(stringResource(backendLabel(option))) }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(backendInfo(backend)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(
                R.string.settings_ai_device_info,
                stringResource(tierLabel(uiState.suggestedTier)),
                formatSize(uiState.totalRamBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.cards.forEach { card ->
                ModelTierCard(
                    model = card.model,
                    status = card.status,
                    isRecommended = card.isRecommended,
                    onDownload = {
                        val needsConfirm = uiState.downloadedModelId != null &&
                            uiState.downloadedModelId != card.model.id
                        val isLarge = card.model.approxSizeBytes >= LARGE_DOWNLOAD_BYTES
                        if (needsConfirm || isLarge) {
                            pendingDownload = card.model
                        } else {
                            viewModel.download(card.model)
                        }
                    },
                    onCancel = { viewModel.cancelDownload(card.model) },
                    onDelete = { viewModel.delete(card.model) },
                )
            }
        }
    }

    pendingDownload?.let { model ->
        val currentlyDownloaded = uiState.cards
            .firstOrNull { it.model.id == uiState.downloadedModelId }?.model
        ReplaceModelDialog(
            target = model,
            currentlyDownloaded = currentlyDownloaded,
            onConfirm = {
                currentlyDownloaded?.let { viewModel.delete(it) }
                viewModel.download(model)
                pendingDownload = null
            },
            onDismiss = { pendingDownload = null },
        )
    }
}

@Composable
private fun ModelTierCard(
    model: AiModel,
    status: ModelStatus,
    isRecommended: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isRecommended) {
                            Spacer(modifier = Modifier.size(8.dp))
                            RecommendedBadge()
                        }
                    }
                    Text(
                        text = formatSize(model.approxSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (status) {
                    is ModelStatus.Ready -> OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete))
                    }
                    is ModelStatus.Downloading -> OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                    else -> OutlinedButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp),
                        )
                        Text(stringResource(R.string.settings_ai_download))
                    }
                }
            }

            StatusLine(status)
        }
    }
}

@Composable
private fun StatusLine(status: ModelStatus) {
    when (status) {
        is ModelStatus.Ready -> StatusRow(
            icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
            text = stringResource(R.string.settings_ai_status_ready),
            color = MaterialTheme.colorScheme.primary,
        )
        is ModelStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val progress = status.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.settings_ai_status_downloading,
                        (progress * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.settings_ai_status_downloading_indeterminate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is ModelStatus.Failed -> Text(
            text = stringResource(R.string.settings_ai_status_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        is ModelStatus.NotDownloaded -> Text(
            text = stringResource(R.string.settings_ai_status_not_downloaded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusRow(
    icon: @Composable () -> Unit,
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon()
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun RecommendedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = stringResource(R.string.settings_ai_recommended),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReplaceModelDialog(
    target: AiModel,
    currentlyDownloaded: AiModel?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = buildString {
        if (currentlyDownloaded != null) {
            append(
                stringResource(
                    R.string.settings_ai_replace_message,
                    target.displayName,
                    currentlyDownloaded.displayName,
                ),
            )
        }
        if (target.approxSizeBytes >= LARGE_DOWNLOAD_BYTES) {
            if (isNotEmpty()) append("\n\n")
            append(
                stringResource(
                    R.string.settings_ai_large_download_warning,
                    formatSize(target.approxSizeBytes),
                ),
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_ai_replace_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_ai_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun backendLabel(backend: AiBackend): Int = when (backend) {
    AiBackend.AUTO -> R.string.settings_ai_backend_auto
    AiBackend.CLOUD -> R.string.settings_ai_backend_cloud
    AiBackend.LOCAL -> R.string.settings_ai_backend_local
}

private fun backendInfo(backend: AiBackend): Int = when (backend) {
    AiBackend.AUTO -> R.string.settings_ai_backend_auto_info
    AiBackend.CLOUD -> R.string.settings_ai_backend_cloud_info
    AiBackend.LOCAL -> R.string.settings_ai_backend_local_info
}

private fun tierLabel(tier: ModelTier): Int = when (tier) {
    ModelTier.HIGH -> R.string.settings_ai_tier_high
    ModelTier.MID -> R.string.settings_ai_tier_mid
    ModelTier.LOW -> R.string.settings_ai_tier_low
}

/** Human-readable size, e.g. "5.5 GB" / "280 MB". */
private fun formatSize(bytes: Long): String {
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        String.format(Locale.US, "%d MB", bytes / (1024 * 1024))
    }
}

/** Downloads at or above this size prompt a Wi-Fi recommendation. */
private const val LARGE_DOWNLOAD_BYTES = 1024L * 1024L * 1024L
