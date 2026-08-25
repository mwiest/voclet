package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.ui.utils.LANGUAGES
import com.github.mwiest.voclet.ui.utils.Language
import com.github.mwiest.voclet.ui.utils.LanguageVariant

/**
 * Picks a regional accent per language for TTS playback.
 *
 * Every language that has variants gets a row, whether or not it has been
 * customized — so there is nothing to add and nothing to remove: a language
 * left at "Default" simply has no override stored for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageVariantsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val openMojiFont = remember { FontFamily(Font(R.font.openmoji, FontWeight.Normal)) }
    var editing by remember { mutableStateOf<Language?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tts_language_overrides)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_tts_language_overrides_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LANGUAGES.filter { it.commonVariants.isNotEmpty() }.forEach { language ->
                val variant = language.variantOf(settings.ttsLanguageOverrides)
                SettingsRow(
                    leading = {
                        Text(
                            text = language.flagEmoji,
                            fontFamily = openMojiFont,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    title = language.nativeName,
                    summary = variant?.displayName
                        ?: stringResource(R.string.settings_tts_variant_standard),
                    onClick = { editing = language },
                )
            }
        }
    }

    editing?.let { language ->
        // Null is the "Default" option: no override stored for this language.
        SingleChoiceDialog(
            title = language.nativeName,
            options = listOf(null) + language.commonVariants,
            selected = language.variantOf(settings.ttsLanguageOverrides),
            label = { it?.displayName ?: stringResource(R.string.settings_tts_variant_standard) },
            onSelect = { viewModel.updateTtsLanguageOverride(language.code, it?.code) },
            onDismiss = { editing = null },
        )
    }
}

/** The variant currently chosen for this language, or null when left at the default. */
internal fun Language.variantOf(overrides: Map<String, String>): LanguageVariant? =
    overrides[code]?.let { code -> commonVariants.find { it.code == code } }
