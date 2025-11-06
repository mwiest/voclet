package com.github.mwiest.voclet.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.ui.Routes
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun HomeScreen(navController: NavController) {
    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WordListsPanel(modifier = Modifier.weight(1f), navController = navController)
        PracticePanel(modifier = Modifier.weight(1f))
    }
}

@Composable
fun WordListsPanel(modifier: Modifier = Modifier, navController: NavController) {
    var selectAllChecked by remember { mutableStateOf(false) }
    var starredPairsChecked by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(id = R.drawable.voclet_logo), contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { /* TODO */ }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_settings), contentDescription = stringResource(id = R.string.settings))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.my_word_lists).uppercase(), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        WordLists(navController = navController)
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { selectAllChecked = !selectAllChecked }
                    .padding(end = 16.dp)
            ) {
                Checkbox(checked = selectAllChecked, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.select_all))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { starredPairsChecked = !starredPairsChecked }
            ) {
                Checkbox(checked = starredPairsChecked, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.starred_pairs))
            }
            Spacer(modifier = Modifier.weight(1f))
            FloatingActionButton(onClick = { navController.navigate(Routes.WORD_LIST_DETAIL.replace("{wordListId}", "-1")) }) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_add), contentDescription = stringResource(id = R.string.add_word_list))
            }
        }
    }
}

@Composable
fun WordLists(modifier: Modifier = Modifier, navController: NavController) {
    val wordLists = listOf(
        WordList(1, "Spanish Verbs", "English", "Spanish"),
        WordList(2, "Science Terms - Unit 1", "English", "French"),
        WordList(3, "French Food & Drink", "English", "German"),
        WordList(4, "French Food & Drink", "English", "German"),
        WordList(5, "Coding Glossay", "English", "German")
    )

    LazyColumn(modifier = modifier) {
        items(wordLists) { wordList ->
            WordListItem(wordList = wordList, navController = navController)
        }
    }
}

@Composable
fun WordListItem(wordList: WordList, navController: NavController) {
    var isChecked by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { isChecked = !isChecked },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Checkbox(checked = isChecked, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(painter = painterResource(id = R.drawable.voclet_logo), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = wordList.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "150 pairs, 12/150 Hard", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_more_vert), contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.edit)) },
                        onClick = { 
                            navController.navigate(Routes.WORD_LIST_DETAIL.replace("{wordListId}", wordList.id.toString()))
                            menuExpanded = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.delete)) },
                        onClick = { 
                            // TODO: Implement delete
                            menuExpanded = false 
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun PracticePanel(modifier: Modifier = Modifier) {
    var selectedDifficulty by remember { mutableStateOf("all") }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = stringResource(id = R.string.practicing_on_x_lists, 2), style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(id = R.string.x_total_words, 200), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(id = R.string.english))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_forward), contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(id = R.string.spanish))
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = false, onCheckedChange = {})
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.difficulty_focus).uppercase(), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { selectedDifficulty = "all" }
            ) {
                RadioButton(selected = selectedDifficulty == "all", onClick = null)
                Text(text = stringResource(id = R.string.all_words))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { selectedDifficulty = "hard" }
            ) {
                RadioButton(selected = selectedDifficulty == "hard", onClick = null)
                Text(text = stringResource(id = R.string.hard_words_only))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { selectedDifficulty = "words" }
            ) {
                RadioButton(selected = selectedDifficulty == "words", onClick = null)
                Text(text = stringResource(id = R.string.words_only))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        PracticeModesGrid()
    }
}

@Composable
fun PracticeModesGrid() {
    val practiceModes = listOf(
        stringResource(id = R.string.match_pairs) to R.drawable.ic_match_pairs,
        stringResource(id = R.string.spelling_scramble) to R.drawable.ic_spelling_scramble,
        stringResource(id = R.string.flashcard_flip) to R.drawable.ic_flashcard_flip,
        stringResource(id = R.string.mpelling_scramble) to R.drawable.ic_spelling_scramble, // TODO: Get correct icon
        stringResource(id = R.string.fill_in_blank) to R.drawable.ic_fill_in_blank,
        stringResource(id = R.string.voice_challenge) to R.drawable.ic_voice_challenge,
    )

    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(practiceModes) { (name, icon) ->
            PracticeModeItem(name = name, icon = icon)
        }
    }
}

@Composable
fun PracticeModeItem(name: String, icon: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = icon), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = name, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun HomeScreenPreview() {
    VocletTheme {
        HomeScreen(rememberNavController())
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun HomeScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        HomeScreen(rememberNavController())
    }
}
