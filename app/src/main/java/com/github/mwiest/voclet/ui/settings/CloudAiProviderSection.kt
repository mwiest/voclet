package com.github.mwiest.voclet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.ai.CloudProvider

/**
 * Body of the "Cloud AI" settings screen: which provider to talk to, the
 * user's own API key, and the model (plus the endpoint, for a custom one).
 *
 * Voclet ships no key, so cloud AI does nothing until one is pasted here. A
 * blank model (or base URL) means "use the preset's default", which the field
 * shows as its placeholder.
 *
 * Each text field renders the persisted value until the user types in it, then
 * switches to a local draft: every keystroke is written to the database, and
 * rendering the value as it comes back through the settings Flow would fight
 * the cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAiProviderSection(
    provider: CloudProvider,
    baseUrl: String,
    apiKey: String,
    model: String,
    onProviderChange: (CloudProvider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    // Null until the user types in that field, so it renders the persisted
    // value. Seeding the draft on first composition instead would capture the
    // settings Flow's blank initial value - opening this screen creates a fresh
    // ViewModel, and the stored row only arrives on the next emission, after
    // remember{} has already latched the blank.
    var keyEdit by remember { mutableStateOf<String?>(null) }
    var baseUrlEdit by remember { mutableStateOf<String?>(null) }
    var modelEdit by remember { mutableStateOf<String?>(null) }

    val keyDraft = keyEdit ?: apiKey
    val baseUrlDraft = baseUrlEdit ?: baseUrl
    val modelDraft = modelEdit ?: model

    Column {
        Text(
            text = stringResource(R.string.settings_ai_cloud_info),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = it },
        ) {
            OutlinedTextField(
                value = stringResource(cloudProviderLabel(provider)),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_ai_cloud_provider)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )

            ExposedDropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false },
            ) {
                CloudProvider.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(cloudProviderLabel(option))) },
                        onClick = {
                            onProviderChange(option)
                            // Switching preset drops the overrides (the repository
                            // clears them in the same write); blank means "use this
                            // preset's default", which the fields show as placeholder.
                            baseUrlEdit = null
                            modelEdit = null
                            providerExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(providerHelp(provider)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = keyDraft,
            onValueChange = {
                keyEdit = it
                onApiKeyChange(it)
            },
            label = { Text(stringResource(R.string.settings_ai_cloud_api_key)) },
            placeholder = { Text(stringResource(R.string.settings_ai_cloud_api_key_placeholder)) },
            singleLine = true,
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
            ),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = stringResource(
                            if (keyVisible) {
                                R.string.settings_ai_cloud_api_key_hide
                            } else {
                                R.string.settings_ai_cloud_api_key_show
                            },
                        ),
                    )
                }
            },
            isError = keyDraft.isBlank(),
            supportingText = {
                Text(
                    text = stringResource(
                        if (keyDraft.isBlank()) {
                            R.string.settings_ai_cloud_missing_key
                        } else {
                            R.string.settings_ai_cloud_api_key_info
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(
                if (provider == CloudProvider.CUSTOM) {
                    R.string.settings_ai_cloud_advanced
                } else {
                    R.string.settings_ai_cloud_model
                },
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                if (provider == CloudProvider.CUSTOM) {
                    R.string.settings_ai_cloud_custom_fields_hint
                } else {
                    R.string.settings_ai_cloud_field_default_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Only CUSTOM needs an endpoint: for a named preset the URL is part
            // of what the preset *is*, and anyone who needs a different one is
            // by definition on a custom endpoint.
            if (provider == CloudProvider.CUSTOM) {
                OutlinedTextField(
                    value = baseUrlDraft,
                    onValueChange = {
                        baseUrlEdit = it
                        onBaseUrlChange(it)
                    },
                    label = { Text(stringResource(R.string.settings_ai_cloud_base_url)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_cloud_base_url_placeholder)) },
                    singleLine = true,
                    isError = baseUrlDraft.isBlank(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = modelDraft,
                onValueChange = {
                    modelEdit = it
                    onModelChange(it)
                },
                label = { Text(stringResource(R.string.settings_ai_cloud_model)) },
                placeholder = {
                    Text(
                        provider.defaultModel.ifEmpty {
                            stringResource(R.string.settings_ai_cloud_model_placeholder)
                        },
                    )
                },
                singleLine = true,
                isError = provider == CloudProvider.CUSTOM && modelDraft.isBlank(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                ),
                supportingText = {
                    Text(
                        text = stringResource(R.string.settings_ai_cloud_model_vision_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Display name of a preset, shared with the settings overview row. */
internal fun cloudProviderLabel(provider: CloudProvider): Int = when (provider) {
    CloudProvider.GEMINI -> R.string.settings_ai_cloud_provider_gemini
    CloudProvider.GROQ -> R.string.settings_ai_cloud_provider_groq
    CloudProvider.OPENROUTER -> R.string.settings_ai_cloud_provider_openrouter
    CloudProvider.MISTRAL -> R.string.settings_ai_cloud_provider_mistral
    CloudProvider.CUSTOM -> R.string.settings_ai_cloud_provider_custom
}

private fun providerHelp(provider: CloudProvider): Int = when (provider) {
    CloudProvider.GEMINI -> R.string.settings_ai_cloud_provider_help_gemini
    CloudProvider.GROQ -> R.string.settings_ai_cloud_provider_help_groq
    CloudProvider.OPENROUTER -> R.string.settings_ai_cloud_provider_help_openrouter
    CloudProvider.MISTRAL -> R.string.settings_ai_cloud_provider_help_mistral
    CloudProvider.CUSTOM -> R.string.settings_ai_cloud_provider_help_custom
}
