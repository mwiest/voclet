package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.R

/**
 * The "Data" settings section.
 *
 * The row itself is styled like any other — the weight of a destructive action
 * belongs in its confirmation, not on a screen the user opens for other
 * reasons. It reports how much there is to delete, and steps aside entirely
 * when there is nothing.
 */
@Composable
fun DataSettingsSection(
    practiceResultCount: Int,
    deleting: Boolean,
    onDeleteStatsClick: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_data)) {
        SettingsRow(
            leading = {
                if (deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    RowIcon(Icons.Outlined.DeleteOutline)
                }
            },
            title = stringResource(R.string.delete_all_stats),
            summary = if (practiceResultCount == 0) {
                stringResource(R.string.delete_all_stats_summary_empty)
            } else {
                pluralStringResource(
                    R.plurals.delete_all_stats_summary,
                    practiceResultCount,
                    practiceResultCount,
                )
            },
            enabled = practiceResultCount > 0 && !deleting,
            onClick = onDeleteStatsClick,
        )
    }
}
