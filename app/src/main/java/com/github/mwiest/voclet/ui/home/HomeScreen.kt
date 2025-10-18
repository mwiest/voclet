package com.github.mwiest.voclet.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun HomeScreen() {
    Row(Modifier.fillMaxSize()) {
        // TODO: Replace with actual list of word lists
        Text(modifier = Modifier.weight(1f), text = "Word Lists")

        // TODO: Replace with practice mode options
        Text(modifier = Modifier.weight(1f), text = "Select a list to start practicing")
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    VocletTheme {
        HomeScreen()
    }
}
