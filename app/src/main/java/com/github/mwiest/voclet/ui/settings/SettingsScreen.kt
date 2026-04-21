package com.github.mwiest.voclet.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mwiest.voclet.BuildConfig
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.ThemeMode
import com.github.mwiest.voclet.ui.utils.Language
import com.github.mwiest.voclet.ui.utils.LANGUAGES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val deleteStatsState by viewModel.deleteStatsState.collectAsState()
    val context = LocalContext.current

    var showDeleteStatsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteStatsState) {
        when (deleteStatsState) {
            is DeleteStatsState.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.delete_all_stats_success),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.resetDeleteStatsState()
            }
            is DeleteStatsState.Error -> {
                Toast.makeText(
                    context,
                    (deleteStatsState as DeleteStatsState.Error).message,
                    Toast.LENGTH_LONG
                ).show()
                viewModel.resetDeleteStatsState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme Section
            item {
                Column {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = settings.themeMode == ThemeMode.SYSTEM) {
                                    Icon(Icons.Outlined.SettingsBrightness, contentDescription = null)
                                }
                            }
                        ) { Text(stringResource(R.string.settings_theme_system)) }
                        SegmentedButton(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = settings.themeMode == ThemeMode.LIGHT) {
                                    Icon(Icons.Filled.LightMode, contentDescription = null)
                                }
                            }
                        ) { Text(stringResource(R.string.settings_theme_light)) }
                        SegmentedButton(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DARK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = settings.themeMode == ThemeMode.DARK) {
                                    Icon(Icons.Filled.DarkMode, contentDescription = null)
                                }
                            }
                        ) { Text(stringResource(R.string.settings_theme_dark)) }
                    }
                }
            }

            // TTS Section
            item {
                val openMojiFont = remember { FontFamily(Font(R.font.openmoji, FontWeight.Normal)) }

                var showAddOverride by remember { mutableStateOf(false) }
                var addLanguageExpanded by remember { mutableStateOf(false) }
                var addVariantExpanded by remember { mutableStateOf(false) }
                var addingLanguage by remember { mutableStateOf<Language?>(null) }

                // Width reserved for the leading "icon" column (flag emoji / + icon) so
                // the action text aligns with language names in the override rows above it.
                val leadingWidth = 28.dp

                Column {
                    Text(
                        text = stringResource(R.string.settings_tts),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Info row: text + links on left, button on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_tts_info),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val epeakUrl = stringResource(R.string.settings_tts_espeak_url)
                                    Text(
                                        text = "• ${stringResource(R.string.settings_tts_espeak)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(epeakUrl))
                                            )
                                        }
                                    )
                                    val rhvoiceUrl = stringResource(R.string.settings_tts_rhvoice_url)
                                    Text(
                                        text = "• ${stringResource(R.string.settings_tts_rhvoice)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(rhvoiceUrl))
                                            )
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent("com.android.settings.TTS_SETTINGS").apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                        )
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_tts_open_system_settings))
                                }
                            }

                            viewModel.ttsEngineName?.let { engineName ->
                                Text(
                                    text = stringResource(R.string.settings_tts_active_engine, engineName),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // TTS default toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_tts_enabled_by_default),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = settings.ttsEnabledByDefault,
                                    onCheckedChange = { viewModel.updateTtsEnabledByDefault(it) }
                                )
                            }

                            // Language variant overrides
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_tts_language_overrides),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.settings_tts_language_overrides_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // Existing overrides
                                settings.ttsLanguageOverrides.forEach { (baseCode, variantCode) ->
                                    val language = LANGUAGES.find { it.code == baseCode }
                                    val variantLabel =
                                        language?.commonVariants?.find { it.code == variantCode }
                                            ?.displayName ?: variantCode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.width(leadingWidth),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = language?.flagEmoji ?: baseCode,
                                                fontFamily = if (language != null) openMojiFont else null,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = language?.nativeName ?: baseCode,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = " → $variantLabel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.updateTtsLanguageOverride(baseCode, null)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Add override form
                                if (showAddOverride) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExposedDropdownMenuBox(
                                            expanded = addLanguageExpanded,
                                            onExpandedChange = {
                                                addLanguageExpanded = !addLanguageExpanded
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            OutlinedTextField(
                                                value = addingLanguage?.nativeName ?: "",
                                                onValueChange = {},
                                                readOnly = true,
                                                singleLine = true,
                                                placeholder = {
                                                    Text(stringResource(R.string.settings_tts_select_language))
                                                },
                                                prefix = {
                                                    addingLanguage?.let {
                                                        Text(
                                                            text = it.flagEmoji,
                                                            fontFamily = openMojiFont,
                                                            modifier = Modifier.padding(end = 4.dp)
                                                        )
                                                    }
                                                },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                        expanded = addLanguageExpanded
                                                    )
                                                },
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .menuAnchor(
                                                        androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable,
                                                        true
                                                    )
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = addLanguageExpanded,
                                                onDismissRequest = { addLanguageExpanded = false }
                                            ) {
                                                LANGUAGES.filter { lang ->
                                                    lang.commonVariants.isNotEmpty() &&
                                                        lang.code !in settings.ttsLanguageOverrides
                                                }.forEach { lang ->
                                                    val itemText = buildAnnotatedString {
                                                        withStyle(SpanStyle(fontFamily = openMojiFont)) {
                                                            append(lang.flagEmoji)
                                                        }
                                                        append("  ${lang.nativeName}")
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(itemText) },
                                                        onClick = {
                                                            addingLanguage = lang
                                                            addLanguageExpanded = false
                                                            addVariantExpanded = true
                                                        },
                                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                    )
                                                }
                                            }
                                        }

                                        ExposedDropdownMenuBox(
                                            expanded = addVariantExpanded,
                                            onExpandedChange = {
                                                if (addingLanguage != null)
                                                    addVariantExpanded = !addVariantExpanded
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            OutlinedTextField(
                                                value = "",
                                                onValueChange = {},
                                                readOnly = true,
                                                singleLine = true,
                                                placeholder = {
                                                    Text(stringResource(R.string.settings_tts_select_variant))
                                                },
                                                enabled = addingLanguage != null,
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                                        expanded = addVariantExpanded
                                                    )
                                                },
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .menuAnchor(
                                                        androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable,
                                                        addingLanguage != null
                                                    )
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = addVariantExpanded,
                                                onDismissRequest = { addVariantExpanded = false }
                                            ) {
                                                addingLanguage?.let { lang ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(
                                                                    R.string.settings_tts_variant_default,
                                                                    lang.code
                                                                )
                                                            )
                                                        },
                                                        onClick = {
                                                            viewModel.updateTtsLanguageOverride(
                                                                lang.code, null
                                                            )
                                                            addVariantExpanded = false
                                                            showAddOverride = false
                                                            addingLanguage = null
                                                        },
                                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                    )
                                                    lang.commonVariants.forEach { variant ->
                                                        DropdownMenuItem(
                                                            text = { Text(variant.displayName) },
                                                            onClick = {
                                                                viewModel.updateTtsLanguageOverride(
                                                                    lang.code, variant.code
                                                                )
                                                                addVariantExpanded = false
                                                                showAddOverride = false
                                                                addingLanguage = null
                                                            },
                                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                showAddOverride = false
                                                addingLanguage = null
                                                addLanguageExpanded = false
                                                addVariantExpanded = false
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.cancel)
                                            )
                                        }
                                    }
                                }

                                // "Add override" row — leading icon and text align with the
                                // flag column and language-name column of the rows above.
                                val availableToAdd = LANGUAGES.filter { lang ->
                                    lang.commonVariants.isNotEmpty() &&
                                        lang.code !in settings.ttsLanguageOverrides
                                }
                                if (!showAddOverride && availableToAdd.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showAddOverride = true }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.width(leadingWidth),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.settings_tts_add_override),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Data Section
            item {
                Column {
                    Text(
                        text = stringResource(R.string.settings_data),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteStatsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = deleteStatsState !is DeleteStatsState.Deleting
                    ) {
                        if (deleteStatsState is DeleteStatsState.Deleting) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        } else {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(stringResource(R.string.delete_all_stats))
                    }
                }
            }

            // About Section
            item {
                Column {
                    Text(
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.app_description),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.open_source),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.donations_welcome),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.attributions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.privacy_policy),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(context.getString(R.string.privacy_policy_url))
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteStatsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteStatsDialog = false },
            title = { Text(stringResource(R.string.delete_all_stats_title)) },
            text = { Text(stringResource(R.string.delete_all_stats_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllStatistics()
                        showDeleteStatsDialog = false
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStatsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
