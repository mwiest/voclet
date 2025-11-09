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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.ui.Routes
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeScreenViewModel = hiltViewModel()) {
    val wordLists by viewModel.wordLists.collectAsState()
    HomeScreen(navController, wordLists)
}

@Composable
fun HomeScreen(navController: NavController, wordLists: List<WordList>) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WordListsPanel(
            modifier = Modifier.weight(1f),
            navController = navController,
            wordLists = wordLists,
            selectedIds = selectedIds,
            onSelectedIdsChange = { selectedIds = it }
        )
        PracticePanel(modifier = Modifier.weight(1f))
    }
}

@Composable
fun WordListsPanel(
    modifier: Modifier = Modifier,
    navController: NavController,
    wordLists: List<WordList>,
    selectedIds: Set<Long>,
    onSelectedIdsChange: (Set<Long>) -> Unit
) {
    val allIds = remember(wordLists) { wordLists.map { it.id }.toSet() }
    val isAllSelected = selectedIds.size == allIds.size && allIds.isNotEmpty()

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(id = R.drawable.voclet_logo), contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(id = R.string.settings))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.my_word_lists).uppercase(), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        WordLists(
            modifier = Modifier.weight(1f),
            navController = navController,
            wordLists = wordLists,
            selectedIds = selectedIds,
            onItemToggle = { listId, isSelected ->
                val newIds = if (isSelected) {
                    selectedIds + listId
                } else {
                    selectedIds - listId
                }
                onSelectedIdsChange(newIds)
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        if (isAllSelected) {
                            onSelectedIdsChange(emptySet())
                        } else {
                            onSelectedIdsChange(allIds)
                        }
                    }
                    .padding(end = 16.dp)
            ) {
                Checkbox(checked = isAllSelected, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.select_all))
            }
            Spacer(modifier = Modifier.weight(1f))
            FloatingActionButton(onClick = { navController.navigate(Routes.WORD_LIST_DETAIL.replace("{wordListId}", "-1")) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_word_list))
            }
        }
    }
}

@Composable
fun WordLists(
    modifier: Modifier = Modifier,
    navController: NavController,
    wordLists: List<WordList>,
    selectedIds: Set<Long>,
    onItemToggle: (Long, Boolean) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(wordLists) { wordList ->
            WordListItem(
                wordList = wordList,
                navController = navController,
                isChecked = wordList.id in selectedIds,
                onCheckedChange = { isSelected -> onItemToggle(wordList.id, isSelected) }
            )
        }
    }
}

@Composable
fun WordListItem(
    wordList: WordList,
    navController: NavController,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!isChecked) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Checkbox(checked = isChecked, onCheckedChange = null)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = wordList.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "150 pairs, 12/150 Hard", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
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
            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
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
                modifier = Modifier.clickable { selectedDifficulty = "starred" }
            ) {
                RadioButton(selected = selectedDifficulty == "starred", onClick = null)
                Text(text = stringResource(id = R.string.starred_pairs))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        PracticeModesGrid()
    }
}

@Composable
fun PracticeModesGrid() {
    val practiceModes = listOf(
        stringResource(id = R.string.match_pairs) to Icons.AutoMirrored.Filled.CompareArrows,
        stringResource(id = R.string.spelling_scramble) to Icons.Default.Shuffle,
        stringResource(id = R.string.flashcard_flip) to Icons.Default.Style,
        stringResource(id = R.string.mpelling_scramble) to Icons.Default.Shuffle, // Re-using for now
        stringResource(id = R.string.fill_in_blank) to Icons.Default.EditNote,
        stringResource(id = R.string.voice_challenge) to Icons.Default.Mic,
    )

    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(practiceModes) { (name, icon) ->
            PracticeModeItem(name = name, icon = icon)
        }
    }
}

@Composable
fun PracticeModeItem(name: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = name, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun HomeScreenPreview() {
    VocletTheme {
        HomeScreen(rememberNavController(), wordLists = listOf(WordList(id = 1, name = "Test List 1", language1 = "", language2 = ""), WordList(id = 2, name = "Test List 2", language1 = "", language2 = "")))
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun HomeScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        HomeScreen(rememberNavController(), wordLists = listOf(WordList(id = 1, name = "Test List 1", language1 = "", language2 = ""), WordList(id = 2, name = "Test List 2", language1 = "", language2 = "")))
    }
}
