package com.github.mwiest.voclet.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mwiest.voclet.BuildConfig
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.ai.cloud.isCloudConfigured
import com.github.mwiest.voclet.data.ai.local.AiModelViewModel
import com.github.mwiest.voclet.data.ai.local.ModelStatus
import com.github.mwiest.voclet.data.database.ThemeMode
import com.github.mwiest.voclet.ui.Routes
import com.github.mwiest.voclet.ui.utils.LANGUAGES
import kotlinx.coroutines.launch

/** Index of the AI Assistant section in the settings LazyColumn (for scroll-to). */
private const val AI_SECTION_INDEX = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollToAi: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
    aiModelViewModel: AiModelViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    // Only for the on-device row's status marker; the models themselves are
    // managed on the detail screen.
    val aiModelState by aiModelViewModel.uiState.collectAsState()
    val deleteStatsState by viewModel.deleteStatsState.collectAsState()
    val practiceResultCount by viewModel.practiceResultCount.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteStatsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSystemTtsDialog by remember { mutableStateOf(false) }
    var showAiInfoDialog by remember { mutableStateOf(false) }

    // Section order in the LazyColumn below: Interface(0), TTS(1), AI Assistant(2),
    // Data(3), About(4). Scroll to the AI section when requested (first-use hint).
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToAi) {
        if (scrollToAi) listState.animateScrollToItem(AI_SECTION_INDEX)
    }

    // The state is reset right away and the snackbar is shown from a scope that
    // outlives this effect: `showSnackbar` suspends until the message is gone, so
    // resetting first would cancel it via the changed effect key.
    val deleteStatsSuccessMessage = stringResource(R.string.delete_all_stats_success)
    LaunchedEffect(deleteStatsState) {
        val (message, duration) = when (val state = deleteStatsState) {
            is DeleteStatsState.Success -> deleteStatsSuccessMessage to SnackbarDuration.Short
            is DeleteStatsState.Error -> state.message to SnackbarDuration.Long
            else -> return@LaunchedEffect
        }
        viewModel.resetDeleteStatsState()
        scope.launch {
            snackbarHostState.showSnackbar(message = message, duration = duration)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            // Rows run full width so their ripple does; the inset lives on the
            // section contents instead of on this column.
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Interface Section
            item {
                SettingsSection(title = stringResource(R.string.settings_interface)) {
                    SettingsRow(
                        icon = Icons.Outlined.Contrast,
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(themeModeLabel(settings.themeMode)),
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // TTS Section
            item {
                TtsSettingsSection(
                    enabledByDefault = settings.ttsEnabledByDefault,
                    onEnabledByDefaultChange = { viewModel.updateTtsEnabledByDefault(it) },
                    variantsSummary = variantsSummary(settings.ttsLanguageOverrides),
                    engineName = viewModel.ttsEngineName,
                    onVariantsClick = { navController.navigate(Routes.SETTINGS_TTS_VARIANTS) },
                    onSystemTtsClick = { showSystemTtsDialog = true }
                )
            }

            // AI Assistant Section: one row per backend, each opening its own
            // detail screen and showing whether it is set up.
            item {
                val cloudConfigured = isCloudConfigured(
                    provider = settings.aiCloudProvider,
                    baseUrl = settings.aiCloudBaseUrl,
                    apiKey = settings.aiCloudApiKey,
                    model = settings.aiCloudModel,
                )
                val downloadedModel = aiModelState.cards
                    .firstOrNull { it.status is ModelStatus.Ready }?.model

                AiSettingsSection(
                    cloudSummary = if (cloudConfigured) {
                        stringResource(
                            R.string.settings_ai_cloud_summary_ready,
                            stringResource(cloudProviderLabel(settings.aiCloudProvider)),
                            settings.aiCloudModel.ifBlank { settings.aiCloudProvider.defaultModel },
                        )
                    } else {
                        stringResource(R.string.settings_ai_cloud_summary_missing)
                    },
                    cloudConfigured = cloudConfigured,
                    localSummary = downloadedModel?.displayName
                        ?: stringResource(R.string.settings_ai_local_summary_missing),
                    localConfigured = downloadedModel != null,
                    onCloudClick = { navController.navigate(Routes.SETTINGS_CLOUD_AI) },
                    onLocalClick = { navController.navigate(Routes.SETTINGS_ON_DEVICE_AI) },
                    onInfoClick = { showAiInfoDialog = true },
                )
            }

            // Data Section
            item {
                DataSettingsSection(
                    practiceResultCount = practiceResultCount,
                    deleting = deleteStatsState is DeleteStatsState.Deleting,
                    onDeleteStatsClick = { showDeleteStatsDialog = true }
                )
            }

            // About Section
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(themeModeLabel(it)) },
            onSelect = { viewModel.updateThemeMode(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showSystemTtsDialog) {
        SystemTtsDialog(
            engineName = viewModel.ttsEngineName,
            onOpenSystemSettings = {
                showSystemTtsDialog = false
                context.startActivity(
                    Intent("com.android.settings.TTS_SETTINGS").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            onDismiss = { showSystemTtsDialog = false }
        )
    }

    if (showAiInfoDialog) {
        InfoDialog(
            title = stringResource(R.string.settings_ai),
            text = stringResource(R.string.settings_ai_info),
            onDismiss = { showAiInfoDialog = false }
        )
    }

    if (showDeleteStatsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteStatsDialog = false },
            title = { Text(stringResource(R.string.delete_all_stats_title)) },
            text = { Text(stringResource(R.string.delete_all_stats_confirmation)) },
            confirmButton = {
                // The only red on the way here: the step that actually deletes.
                TextButton(
                    onClick = {
                        viewModel.deleteAllStatistics()
                        showDeleteStatsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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

/** Label for a theme mode, shared by the settings row summary and its dialog. */
private fun themeModeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/**
 * What the language-variants row says about the overrides stored so far: the
 * single mapping when there is only one, a count once there are several.
 */
@Composable
private fun variantsSummary(overrides: Map<String, String>): String {
    val customized = LANGUAGES.mapNotNull { language ->
        language.variantOf(overrides)?.let { language to it }
    }
    return when (customized.size) {
        0 -> stringResource(R.string.settings_tts_variants_summary_none)
        1 -> stringResource(
            R.string.settings_tts_variants_summary_one,
            customized.first().first.nativeName,
            customized.first().second.displayName,
        )
        else -> stringResource(R.string.settings_tts_variants_summary_many, customized.size)
    }
}
