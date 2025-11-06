package com.github.mwiest.voclet.ui.wordlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun WordListDetailScreen(viewModel: WordListDetailViewModel = hiltViewModel()) {
    val wordList by viewModel.wordList.collectAsState()
    val wordPairs by viewModel.wordPairs.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = wordList?.name ?: "",
                onValueChange = { viewModel.updateWordListName(it) },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_edit),
                contentDescription = stringResource(id = R.string.edit)
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
            items(wordPairs) { pair ->
                WordPairRow(
                    pair = pair,
                    onPairChange = { updatedPair -> viewModel.updateWordPair(updatedPair) },
                    onDelete = { viewModel.deleteWordPair(pair) }
                )
            }
        }

        Row {
            Spacer(modifier = Modifier.weight(1f))
            FloatingActionButton(
                onClick = { viewModel.addWordPair() },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_add), contentDescription = stringResource(id = R.string.add_word_pair))
            }
            Button(onClick = { viewModel.saveChanges() }) {
                Text("Save")
            }
        }
    }
}

@Composable
fun WordPairRow(pair: WordPair, onPairChange: (WordPair) -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = pair.word1,
            onValueChange = { onPairChange(pair.copy(word1 = it)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = pair.word2,
            onValueChange = { onPairChange(pair.copy(word2 = it)) },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), contentDescription = stringResource(id = R.string.delete))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WordListDetailScreenPreview() {
    VocletTheme {
        // This preview will not work correctly with Hilt. A better preview will be created later.
        // WordListDetailScreen()
    }
}
