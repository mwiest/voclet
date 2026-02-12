package com.github.mwiest.voclet.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mwiest.voclet.BuildConfig
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.ThemeMode

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

    // Handle delete stats state changes
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
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = settings.themeMode == ThemeMode.SYSTEM
                                ) {
                                    Icon(
                                        Icons.Outlined.SettingsBrightness,
                                        contentDescription = null
                                    )
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_theme_system))
                        }

                        SegmentedButton(
                            selected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = settings.themeMode == ThemeMode.LIGHT
                                ) {
                                    Icon(
                                        Icons.Filled.LightMode,
                                        contentDescription = null
                                    )
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_theme_light))
                        }

                        SegmentedButton(
                            selected = settings.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DARK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = settings.themeMode == ThemeMode.DARK
                                ) {
                                    Icon(
                                        Icons.Filled.DarkMode,
                                        contentDescription = null
                                    )
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_theme_dark))
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
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp)
                            )
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
                                text = stringResource(
                                    R.string.app_version,
                                    BuildConfig.VERSION_NAME
                                ),
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
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(context.getString(R.string.privacy_policy_url))
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete All Statistics Confirmation Dialog
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
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStatsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
