package com.github.mwiest.voclet.ui.wordlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.fileimport.ImportStep

@Composable
fun ImportDialog(
    importStep: ImportStep,
    previewData: List<List<String>>,
    columnHeaders: List<String>,
    sourceColumn: Int?,
    targetColumn: Int?,
    hasHeaderRow: Boolean,
    isProcessing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    onSourceColumnChange: (Int) -> Unit,
    onTargetColumnChange: (Int) -> Unit,
    onToggleHeaderRow: (Boolean) -> Unit,
    onImport: () -> Unit,
    onClearError: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = !isProcessing
        ),
        title = {
            BoxWithConstraints {
                val isWide = maxWidth > 500.dp

                if (isWide && importStep == ImportStep.COLUMN_MAPPING) {
                    // Wide layout: title + checkbox in same row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.column_mapping))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasHeaderRow,
                                onCheckedChange = onToggleHeaderRow
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.first_row_is_header),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // Narrow layout or other steps: just title
                    val titleText = when (importStep) {
                        ImportStep.FILE_SELECTION -> stringResource(id = R.string.select_file)
                        ImportStep.COLUMN_MAPPING -> stringResource(id = R.string.column_mapping)
                        ImportStep.PROCESSING -> stringResource(id = R.string.import_progress)
                    }
                    Text(titleText)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Error message (if any)
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Step-specific content
                when (importStep) {
                    ImportStep.FILE_SELECTION -> FileSelectionStep(onSelectFile)
                    ImportStep.COLUMN_MAPPING -> ColumnMappingStep(
                        previewData = previewData,
                        columnHeaders = columnHeaders,
                        sourceColumn = sourceColumn,
                        targetColumn = targetColumn,
                        hasHeaderRow = hasHeaderRow,
                        onSourceColumnChange = onSourceColumnChange,
                        onTargetColumnChange = onTargetColumnChange,
                        onToggleHeaderRow = onToggleHeaderRow
                    )

                    ImportStep.PROCESSING -> ProcessingStep(isProcessing)
                }
            }
        },
        confirmButton = {
            when (importStep) {
                ImportStep.FILE_SELECTION -> {
                    // No confirm button, just the "Select File" button in the content
                }

                ImportStep.COLUMN_MAPPING -> {
                    val pairCount = previewData.drop(if (hasHeaderRow) 1 else 0).size
                    Button(
                        onClick = onImport,
                        enabled = sourceColumn != null &&
                                targetColumn != null &&
                                sourceColumn != targetColumn &&
                                errorMessage == null
                    ) {
                        Text("${stringResource(id = R.string.import_button)} ($pairCount)")
                    }
                }

                ImportStep.PROCESSING -> {
                    // No button during processing
                }
            }
        },
        dismissButton = {
            if (importStep != ImportStep.PROCESSING) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        }
    )
}

@Composable
fun FileSelectionStep(onSelectFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.select_csv_or_excel),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(onClick = onSelectFile) {
            Text(stringResource(id = R.string.select_file))
        }
    }
}

@Composable
fun ColumnMappingStep(
    previewData: List<List<String>>,
    columnHeaders: List<String>,
    sourceColumn: Int?,
    targetColumn: Int?,
    hasHeaderRow: Boolean,
    onSourceColumnChange: (Int) -> Unit,
    onTargetColumnChange: (Int) -> Unit,
    onToggleHeaderRow: (Boolean) -> Unit
) {
    BoxWithConstraints {
        val isWide = maxWidth > 500.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row toggle (only show if not in wide mode with checkbox in title)
            if (!isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasHeaderRow,
                        onCheckedChange = onToggleHeaderRow
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.first_row_is_header),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Preview table (only 3 rows)
            PreviewTable(
                previewData = previewData,
                columnHeaders = columnHeaders,
                sourceColumn = sourceColumn,
                targetColumn = targetColumn,
                hasHeaderRow = hasHeaderRow
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Column selectors - responsive layout
            if (isWide) {
                // Side by side layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        ColumnDropdownSelector(
                            columnHeaders = columnHeaders,
                            selectedColumn = sourceColumn,
                            onColumnChange = onSourceColumnChange,
                            label = stringResource(id = R.string.source_column)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ColumnDropdownSelector(
                            columnHeaders = columnHeaders,
                            selectedColumn = targetColumn,
                            onColumnChange = onTargetColumnChange,
                            label = stringResource(id = R.string.target_column)
                        )
                    }
                }
            } else {
                // Stacked layout
                Column(modifier = Modifier.fillMaxWidth()) {
                    ColumnDropdownSelector(
                        columnHeaders = columnHeaders,
                        selectedColumn = sourceColumn,
                        onColumnChange = onSourceColumnChange,
                        label = stringResource(id = R.string.source_column)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ColumnDropdownSelector(
                        columnHeaders = columnHeaders,
                        selectedColumn = targetColumn,
                        onColumnChange = onTargetColumnChange,
                        label = stringResource(id = R.string.target_column)
                    )
                }
            }
        }
    }
}

@Composable
fun PreviewTable(
    previewData: List<List<String>>,
    columnHeaders: List<String>,
    sourceColumn: Int?,
    targetColumn: Int?,
    hasHeaderRow: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Header row
            Row(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                columnHeaders.forEachIndexed { index, header ->
                    val isSourceCol = index == sourceColumn
                    val isTargetCol = index == targetColumn

                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .padding(8.dp)
                            .then(
                                if (isSourceCol || isTargetCol) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Data rows - only show first 3
            var dataRowsShown = 0
            previewData.forEachIndexed { rowIndex, row ->
                // Skip first row if it's the header
                if (rowIndex == 0 && hasHeaderRow) {
                    return@forEachIndexed
                }

                // Only show first 3 data rows
                if (dataRowsShown >= 3) {
                    return@forEachIndexed
                }
                dataRowsShown++

                Row(
                    modifier = Modifier
                        .background(
                            if (dataRowsShown % 2 == 1)
                                MaterialTheme.colorScheme.surface
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        val isSourceCol = colIndex == sourceColumn
                        val isTargetCol = colIndex == targetColumn

                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .padding(8.dp)
                                .then(
                                    if (isSourceCol || isTargetCol) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                            MaterialTheme.shapes.extraSmall
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnDropdownSelector(
    columnHeaders: List<String>,
    selectedColumn: Int?,
    onColumnChange: (Int) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedColumn?.let { columnHeaders.getOrNull(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            columnHeaders.forEachIndexed { index, header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = {
                        onColumnChange(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ProcessingStep(isProcessing: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.importing_word_pairs),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}
