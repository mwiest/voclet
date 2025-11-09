package com.github.mwiest.voclet.ui.wordlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.ui.theme.VocletTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListDetailScreen(navController: NavController, viewModel: WordListDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequesters = remember { mutableMapOf<Long, Pair<FocusRequester, FocusRequester>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Word List") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = uiState.listName,
                    onValueChange = { viewModel.updateWordListName(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(id = R.string.edit)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                itemsIndexed(uiState.wordPairs) { index, pair ->
                    val requesters = focusRequesters.getOrPut(pair.id) { FocusRequester() to FocusRequester() }
                    WordPairRow(
                        pair = pair,
                        onPairChange = { updatedPair -> viewModel.updateWordPair(updatedPair) },
                        onDelete = { viewModel.deleteWordPair(pair) },
                        focusRequesters = requesters,
                        onTab = {
                            if (index < uiState.wordPairs.size - 1) {
                                focusRequesters[uiState.wordPairs[index + 1].id]?.first?.requestFocus()
                            } else {
                                viewModel.addWordPair()
                            }
                        }
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.addWordPair()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Word")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.saveChanges()
                    navController.navigateUp()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun WordPairRow(
    pair: WordPair,
    onPairChange: (WordPair) -> Unit,
    onDelete: () -> Unit,
    focusRequesters: Pair<FocusRequester, FocusRequester>,
    onTab: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = pair.word1,
            onValueChange = { onPairChange(pair.copy(word1 = it)) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequesters.first)
                .onPreviewKeyEvent {
                    if (it.key == Key.Tab) {
                        focusRequesters.second.requestFocus()
                        true
                    } else false
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = pair.word2,
            onValueChange = { onPairChange(pair.copy(word2 = it)) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequesters.second)
                .onPreviewKeyEvent {
                    if (it.key == Key.Tab) {
                        onTab()
                        true
                    } else false
                }
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WordListDetailScreenPreview() {
    VocletTheme {
        WordListDetailScreen(rememberNavController())
    }
}
