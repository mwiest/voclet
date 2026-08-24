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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mwiest.voclet.R

/** "Cloud AI" settings screen: provider preset, the user's own API key and the model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAiSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ai_cloud)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            CloudAiProviderSection(
                provider = settings.aiCloudProvider,
                baseUrl = settings.aiCloudBaseUrl,
                apiKey = settings.aiCloudApiKey,
                model = settings.aiCloudModel,
                onProviderChange = { viewModel.updateCloudProvider(it) },
                onBaseUrlChange = { viewModel.updateCloudBaseUrl(it) },
                onApiKeyChange = { viewModel.updateCloudApiKey(it) },
                onModelChange = { viewModel.updateCloudModel(it) },
            )
        }
    }
}
