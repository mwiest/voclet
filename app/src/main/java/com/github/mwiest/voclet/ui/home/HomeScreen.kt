package com.github.mwiest.voclet.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordList
import com.github.mwiest.voclet.data.database.WordListInfo
import com.github.mwiest.voclet.ui.Routes
import com.github.mwiest.voclet.ui.theme.VocletTheme

@Composable
fun HomeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    viewModel: HomeScreenViewModel = hiltViewModel()
) {
    val wordListsWithInfo by viewModel.wordListsWithInfo.collectAsState()
    HomeScreen(navController, windowSizeClass, wordListsWithInfo)
}

@Composable
fun HomeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    wordListsWithInfo: List<WordListInfo> = emptyList()
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    Surface(color = MaterialTheme.colorScheme.background) {
        if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) {
            Row(
                Modifier
                    .fillMaxSize()
                    .safeGesturesPadding()
            ) {
                WordListsPanel(
                    modifier = Modifier.weight(1f),
                    navController = navController,
                    expandHeight = true,
                    wordListsWithInfo = wordListsWithInfo,
                    selectedIds = selectedIds,
                    onSelectedIdsChange = { selectedIds = it }
                )
                PracticePanel(
                    modifier = Modifier.weight(1f),
                    selectedListCount = selectedIds.size,
                    selectedWordCount = wordListsWithInfo
                        .filter { it.wordList.id in selectedIds }
                        .sumOf { it.pairCount }
                )
            }
        } else {
            Column {
                WordListsPanel(
                    modifier = Modifier.weight(weight = 1.0f, fill = false),
                    navController = navController,
                    expandHeight = false,
                    wordListsWithInfo = wordListsWithInfo,
                    selectedIds = selectedIds,
                    onSelectedIdsChange = { selectedIds = it }
                )
                PracticePanel(
                    modifier = Modifier.weight(1f),
                    selectedListCount = selectedIds.size,
                    selectedWordCount = wordListsWithInfo
                        .filter { it.wordList.id in selectedIds }
                        .sumOf { it.pairCount }
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    }
}

@Composable
fun TitleRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = R.drawable.voclet_logo),
            contentDescription = null,
            modifier = Modifier.size(45.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { /* TODO */ }) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(id = R.string.settings)
            )
        }
    }
}

@Composable
fun WordListsPanel(
    modifier: Modifier = Modifier,
    navController: NavController,
    expandHeight: Boolean,
    wordListsWithInfo: List<WordListInfo> = emptyList(),
    selectedIds: Set<Long>,
    onSelectedIdsChange: (Set<Long>) -> Unit
) {
    val allIds = remember(wordListsWithInfo) { wordListsWithInfo.map { it.wordList.id }.toSet() }
    val isAllSelected = selectedIds.size == allIds.size && allIds.isNotEmpty()

    Column(modifier = modifier.padding(16.dp)) {
        TitleRow()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.my_word_lists),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        WordLists(
            modifier = Modifier.weight(weight = 1f, fill = expandHeight),
            navController = navController,
            wordListsWithInfo = wordListsWithInfo,
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
        Spacer(modifier = Modifier.height(if (expandHeight) 0.dp else 16.dp))
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
                    .padding(end = 16.dp, start = 8.dp)
            ) {
                Checkbox(checked = isAllSelected, onCheckedChange = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = stringResource(id = R.string.select_all))
            }
            Spacer(modifier = Modifier.weight(1f))
            FloatingActionButton(onClick = {
                navController.navigate(
                    Routes.WORD_LIST_DETAIL.replace(
                        "{wordListId}",
                        "-1"
                    )
                )
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_word_list)
                )
            }
        }
    }
}

@Composable
fun WordLists(
    modifier: Modifier = Modifier,
    navController: NavController,
    wordListsWithInfo: List<WordListInfo> = emptyList(),
    selectedIds: Set<Long>,
    onItemToggle: (Long, Boolean) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(wordListsWithInfo) { info ->
            WordListItem(
                wordList = info.wordList,
                pairCount = info.pairCount,
                navController = navController,
                isChecked = info.wordList.id in selectedIds,
                onCheckedChange = { isSelected -> onItemToggle(info.wordList.id, isSelected) }
            )
        }
    }
}

@Composable
fun WordListItem(
    wordList: WordList,
    pairCount: Int = 0,
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
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = wordList.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        id = R.string.x_words,
                        pairCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.edit)) },
                        onClick = {
                            navController.navigate(
                                Routes.WORD_LIST_DETAIL.replace(
                                    "{wordListId}",
                                    wordList.id.toString()
                                )
                            )
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
fun PracticePanel(
    modifier: Modifier = Modifier,
    selectedListCount: Int = 0,
    selectedWordCount: Int = 0
) {
    var selectedDifficulty by remember { mutableStateOf("all") }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.practicing_on_x_lists, selectedListCount),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(id = R.string.x_total_words, selectedWordCount),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val difficultyOptions = remember {
                listOf(
                    "all" to R.string.all_words,
                    "hard" to R.string.hard_words_only,
                    "starred" to R.string.starred_pairs
                )
            }
            val selectedIndex = difficultyOptions.indexOfFirst { it.first == selectedDifficulty }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                difficultyOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = difficultyOptions.size
                        ),
                        onClick = { selectedDifficulty = option.first },
                        selected = index == selectedIndex
                    ) {
                        Text(
                            stringResource(option.second)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        PracticeModesGrid()
    }
}

@Composable
fun PracticeModesGrid() {
    val practiceModes = listOf(
        stringResource(id = R.string.match_pairs) to Icons.AutoMirrored.Filled.CompareArrows,
        stringResource(id = R.string.spelling_scramble) to Icons.Default.Shuffle,
        stringResource(id = R.string.flashcard_flip) to Icons.Default.Style,
        stringResource(id = R.string.fill_in_blank) to Icons.Default.EditNote,
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun HomeScreenPreview() {
    VocletTheme {
        HomeScreen(
            rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            wordListsWithInfo = listOf(
                WordListInfo(
                    wordList = WordList(
                        id = 1,
                        name = "Test List 1",
                        language1 = "",
                        language2 = ""
                    ),
                    pairCount = 10
                ),
                WordListInfo(
                    wordList = WordList(
                        id = 2,
                        name = "Test List 2",
                        language1 = "",
                        language2 = ""
                    ),
                    pairCount = 15
                )
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun HomeScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        HomeScreen(
            rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            wordListsWithInfo = listOf(
                WordListInfo(
                    wordList = WordList(
                        id = 1,
                        name = "Test List 1",
                        language1 = "",
                        language2 = ""
                    ),
                    pairCount = 10
                ),
                WordListInfo(
                    wordList = WordList(
                        id = 2,
                        name = "Test List 2",
                        language1 = "",
                        language2 = ""
                    ),
                    pairCount = 15
                )
            )
        )
    }
}
