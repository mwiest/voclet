package com.github.mwiest.voclet.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun HomeScreen() {
    Row(Modifier.fillMaxSize()) {
        WordLists(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Select a list to start practicing")
        }
    }
}

@Composable
fun WordLists(modifier: Modifier = Modifier) {
    // Dummy data for now
    val wordLists = listOf(
        WordList(1, "Spanish Verbs", "English", "Spanish"),
        WordList(2, "French Nouns", "English", "French"),
        WordList(3, "German Adjectives", "English", "German")
    )

    LazyColumn(modifier = modifier.padding(16.dp)) {
        items(wordLists) { wordList ->
            Text(text = wordList.name, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun HomeScreenPreview() {
    VocletTheme {
        HomeScreen()
    }
}
