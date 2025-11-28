package com.github.mwiest.voclet.ui.wordlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.ui.theme.VocletTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListDetailScreen(
    navController: NavController,
    viewModel: WordListDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    WordListDetailScreen(
        navController,
        uiState,
        viewModel::updateWordListName,
        viewModel::updateWordPair,
        viewModel::deleteWordPair,
        viewModel::saveChanges,
        viewModel::deleteWordList,
        viewModel::resetToOriginal
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListDetailScreen(
    navController: NavController,
    uiState: WordListDetailUiState,
    updateWordListName: (String) -> Unit = {},
    updateWordPair: (WordPair) -> Unit = {},
    deleteWordPair: (WordPair) -> Unit = {},
    saveChanges: () -> Unit = {},
    deleteWordList: () -> Unit = {},
    resetToOriginal: () -> Unit = {},
) {
    val focusRequesters = remember { mutableMapOf<Long, Pair<FocusRequester, FocusRequester>>() }
    var isTitleFocused by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    fun handleNavigation() {
        if (uiState.hasUnsavedChanges) {
            showUnsavedChangesDialog = true
        } else {
            navController.navigateUp()
        }
    }

    fun handleDiscard() {
        resetToOriginal()
        navController.navigateUp()
    }

    BackHandler(enabled = true) {
        handleNavigation()
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(id = R.string.unsaved_changes)) },
            text = { Text(stringResource(id = R.string.unsaved_changes_message)) },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text(stringResource(id = R.string.stay))
                }
            },
            confirmButton = {
                TextButton(onClick = { handleDiscard() }) {
                    Text(stringResource(id = R.string.discard))
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false
            )
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(id = R.string.delete_word_list)) },
            text = { Text(stringResource(id = R.string.delete_word_list_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteWordList()
                    showDeleteDialog = false
                    navController.navigateUp()
                }) {
                    Text(stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { handleNavigation() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            saveChanges()
                            navController.navigateUp()
                        },
                        enabled = uiState.hasUnsavedChanges && !uiState.isSaving
                    ) {
                        Text(stringResource(id = R.string.save))
                    }

                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(id = R.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            enabled = !uiState.isNewList,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(24.dp)
                                )
                            },
                            text = {
                                Text(
                                    stringResource(id = R.string.delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline
                BasicTextField(
                    value = uiState.listName,
                    onValueChange = { updateWordListName(it) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isTitleFocused = it.isFocused }
                        .drawBehind {
                            val strokeWidth = if (isTitleFocused) 2.dp.toPx() else 1.dp.toPx()
                            val color = if (isTitleFocused) primaryColor else outlineColor
                            drawLine(
                                color = color,
                                start = Offset(0f, size.height),
                                end = Offset(size.width - 48.dp.toPx(), size.height),
                                strokeWidth = strokeWidth
                            )
                        },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                itemsIndexed(uiState.wordPairs) { index, pair ->
                    val requesters =
                        focusRequesters.getOrPut(pair.id) { FocusRequester() to FocusRequester() }
                    val isLastAndEmpty =
                        index == uiState.wordPairs.size - 1 && pair.word1.isEmpty() && pair.word2.isEmpty()
                    WordPairRow(
                        pair = pair,
                        onPairChange = { updatedPair -> updateWordPair(updatedPair) },
                        onDelete = { deleteWordPair(pair) },
                        focusRequesters = requesters,
                        onTab = {
                            if (index < uiState.wordPairs.size - 1) {
                                focusRequesters[uiState.wordPairs[index + 1].id]?.first?.requestFocus()
                            }
                        },
                        showDeleteButton = !isLastAndEmpty
                    )
                }
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
    onTab: () -> Unit,
    showDeleteButton: Boolean
) {
    var tabPressed by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = pair.word1,
            onValueChange = { onPairChange(pair.copy(word1 = it)) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequesters.first)
                .onPreviewKeyEvent {
                    if ((it.key == Key.Tab || it.key == Key.Enter) && !tabPressed) {
                        tabPressed = true
                        focusRequesters.second.requestFocus()
                        true
                    } else false
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = pair.word2,
            onValueChange = { onPairChange(pair.copy(word2 = it)) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequesters.second)
                .onPreviewKeyEvent {
                    if ((it.key == Key.Tab || it.key == Key.Enter) && !tabPressed) {
                        tabPressed = true
                        onTab()
                        true
                    } else if ((it.key == Key.Tab || it.key == Key.Enter) && tabPressed) {
                        // Reset the flag when key is released (next key event)
                        tabPressed = false
                        true
                    } else false
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        if (showDeleteButton) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.delete)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp)) // Reserve space for the delete button
        }
    }
}


@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun WordListDetailScreenPreview() {
    VocletTheme {
        WordListDetailScreen(
            rememberNavController(), uiState = WordListDetailUiState(
                listName = "Test List", wordPairs = listOf(
                    WordPair(id = 1, wordListId = 1, word1 = "You", word2 = "Usted"),
                    WordPair(id = 2, wordListId = 1, word1 = "Town hall", word2 = "Ayutamiento"),
                ), isNewList = false
            ),
            resetToOriginal = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun WordListDetailScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        WordListDetailScreen(
            rememberNavController(), uiState = WordListDetailUiState(
                listName = "Test List", wordPairs = listOf(
                    WordPair(id = 1, wordListId = 1, word1 = "You", word2 = "Usted"),
                    WordPair(id = 2, wordListId = 1, word1 = "Town hall", word2 = "Ayutamiento"),
                ), isNewList = false
            ),
            resetToOriginal = {}
        )
    }
}
