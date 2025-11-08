package com.github.mwiest.voclet.ui.wordlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    value = uiState.wordList?.name ?: "",
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
                items(uiState.wordPairs) { pair ->
                    WordPairRow(
                        pair = pair,
                        onPairChange = { updatedPair -> viewModel.updateWordPair(updatedPair) },
                        onDelete = { viewModel.deleteWordPair(pair) }
                    )
                }
                item {
                    NewWordPairRow(onAdd = { newPair -> viewModel.addWordPair(newPair) })
                }
            }

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
fun WordPairRow(pair: WordPair, onPairChange: (WordPair) -> Unit, onDelete: () -> Unit) {
    var word1 by remember { mutableStateOf(pair.word1) }
    var word2 by remember { mutableStateOf(pair.word2) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = word1,
            onValueChange = { word1 = it; onPairChange(pair.copy(word1 = it)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = word2,
            onValueChange = { word2 = it; onPairChange(pair.copy(word2 = it)) },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete))
        }
    }
}

@Composable
fun NewWordPairRow(onAdd: (WordPair) -> Unit) {
    var word1 by remember { mutableStateOf("") }
    var word2 by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = word1,
            onValueChange = { word1 = it },
            modifier = Modifier.weight(1f),
            label = { Text("New word") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = word2,
            onValueChange = { word2 = it },
            modifier = Modifier.weight(1f),
            label = { Text("Translation") }
        )
        IconButton(onClick = {
            if (word1.isNotBlank() && word2.isNotBlank()) {
                onAdd(WordPair(word1 = word1, word2 = word2, wordListId = 0)) // listId will be set in VM
                word1 = ""
                word2 = ""
            }
        }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_word_pair))
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
