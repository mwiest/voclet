package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.R

/**
 * Horizontal inset of the section headers. The rows below them are laid out
 * full-width so their ripple spans the screen, and reach the same inset through
 * `ListItem`'s own content padding.
 */
private val SectionTitleInset = 16.dp

/**
 * A settings section: a coloured header with its rows stacked underneath,
 * directly on the screen background (no card).
 *
 * [onInfoClick], where a section needs explaining, puts an info button next to
 * the header — so the explanation is one tap away instead of a paragraph the
 * reader has to get past on every visit.
 */
@Composable
fun SettingsSection(
    title: String,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(start = SectionTitleInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            onInfoClick?.let {
                IconButton(onClick = it, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.more_information),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        content()
    }
}

/**
 * One tappable row of a [SettingsSection]: leading icon, title and — where the
 * setting has a current value worth showing — a summary line carrying it.
 *
 * [trailingIcon] marks where the row leads: a chevron for another screen, an
 * open-in-new for something outside the app. Rows that open a dialog in place
 * carry neither.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    summaryLeading: @Composable (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    SettingsRow(
        leading = { RowIcon(icon) },
        title = title,
        summary = summary,
        summaryLeading = summaryLeading,
        trailingIcon = trailingIcon,
        onClick = onClick,
    )
}

/**
 * [SettingsRow] for rows whose leading slot is not an icon — a flag emoji, say.
 *
 * [summaryLeading] goes in front of the summary text, for a small marker that
 * qualifies the value rather than the setting.
 */
@Composable
fun SettingsRow(
    leading: @Composable () -> Unit,
    title: String,
    summary: String? = null,
    summaryLeading: @Composable (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    summaryLeading?.invoke()
                    Text(it)
                }
            }
        },
        leadingContent = leading,
        trailingContent = trailingIcon?.let { { RowIcon(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/** A [SettingsRow] for an on/off setting. Tapping anywhere on the row toggles it. */
@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { RowIcon(icon) },
        trailingContent = {
            // The row owns the click, so the switch itself must not also be clickable.
            Switch(checked = checked, onCheckedChange = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
