package com.github.mwiest.voclet.data.export

import kotlinx.serialization.Serializable

/**
 * Root structure of exported .voclet.json file
 * Contains metadata and one or more word lists
 */
@Serializable
data class VocletExport(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val lists: List<ExportWordList>
)

/**
 * Exported word list - excludes internal DB IDs and practice statistics
 * Includes only essential data for backup/sharing
 */
@Serializable
data class ExportWordList(
    val name: String,
    val language1: String?,
    val language2: String?,
    val pairs: List<ExportWordPair>
)

/**
 * Exported word pair - includes starred flag but excludes correctInARow
 * This allows users to preserve their starred selections while not exporting
 * practice progress (which is device-specific)
 */
@Serializable
data class ExportWordPair(
    val word1: String,
    val word2: String,
    val starred: Boolean = false
)

/**
 * Exception thrown when export operations fail
 */
class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when import operations fail
 */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
