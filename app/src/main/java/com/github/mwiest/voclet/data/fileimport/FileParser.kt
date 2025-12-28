package com.github.mwiest.voclet.data.fileimport

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Interface for parsing files (CSV, Excel) into a list of rows, where each row is a list of cell values
 */
interface FileParser {
    /**
     * Parse a file from the given URI
     * @param uri The URI of the file to parse
     * @param context Android context for content resolver
     * @return List of rows, where each row is a list of cell values (strings)
     */
    suspend fun parse(uri: Uri, context: Context): List<List<String>>
}

/**
 * CSV file parser using Apache Commons CSV with smart delimiter detection
 */
class CSVParser : FileParser {
    override suspend fun parse(uri: Uri, context: Context): List<List<String>> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Read first few lines to detect delimiter
                val sampleLines = mutableListOf<String>()
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    repeat(5) {
                        reader.readLine()?.let { sampleLines.add(it) }
                    }
                }

                // Detect the best delimiter
                val delimiter = detectDelimiter(sampleLines)
                Log.d("CSVParser", "Detected delimiter: '${delimiter}' (char code: ${delimiter.code})")

                // Re-open the stream to parse from the beginning
                context.contentResolver.openInputStream(uri)?.use { freshInputStream ->
                    BufferedReader(InputStreamReader(freshInputStream)).use { reader ->
                        val csvFormat = CSVFormat.DEFAULT.builder()
                            .setDelimiter(delimiter)
                            .setIgnoreEmptyLines(true)
                            .setTrim(true)
                            .build()

                        val csvParser = CSVParser(reader, csvFormat)
                        val records = csvParser.records

                        records.map { record ->
                            (0 until record.size()).map { index ->
                                record.get(index) ?: ""
                            }
                        }
                    }
                } ?: emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("CSVParser", "Error parsing CSV file", e)
            throw FileParseException("Failed to parse CSV file: ${e.message}", e)
        }
    }

    /**
     * Detects the most likely delimiter by testing common options
     * Returns the delimiter that produces the most consistent column count
     */
    private fun detectDelimiter(sampleLines: List<String>): Char {
        if (sampleLines.isEmpty()) return ','

        // Common delimiters to test, in order of likelihood
        val delimiters = listOf(';', ',', '\t', '|')

        data class DelimiterScore(
            val delimiter: Char,
            val avgColumns: Double,
            val consistency: Double // lower is better (standard deviation)
        )

        val scores = delimiters.map { delimiter ->
            val columnCounts = sampleLines.map { line ->
                line.split(delimiter).size
            }

            // Calculate average and standard deviation
            val avg = columnCounts.average()
            val variance = columnCounts.map { (it - avg) * (it - avg) }.average()
            val stdDev = kotlin.math.sqrt(variance)

            DelimiterScore(delimiter, avg, stdDev)
        }

        // Choose delimiter with:
        // 1. More than 1 column on average (it's actually splitting)
        // 2. Lowest standard deviation (most consistent)
        // 3. If tie, prefer higher column count
        val best = scores
            .filter { it.avgColumns > 1.0 }
            .minWithOrNull(compareBy<DelimiterScore> { it.consistency }.thenByDescending { it.avgColumns })

        return best?.delimiter ?: ',' // Default to comma if no good match
    }
}

/**
 * Excel (.xlsx) file parser - Currently not supported on Android
 * TODO: Implement when Android-compatible Excel library is available
 */
class ExcelParser : FileParser {
    override suspend fun parse(uri: Uri, context: Context): List<List<String>> {
        throw FileParseException("Excel format is not currently supported. Please use CSV format instead.")
    }
}

/**
 * Factory for creating file parsers based on file type
 */
object FileParserFactory {
    fun create(fileType: ImportFileType): FileParser {
        return when (fileType) {
            ImportFileType.CSV -> CSVParser()
            ImportFileType.XLSX -> ExcelParser()
        }
    }
}

/**
 * Enum representing supported import file types
 */
enum class ImportFileType {
    CSV,
    XLSX
}

/**
 * Enum representing the current step in the import wizard
 */
enum class ImportStep {
    FILE_SELECTION,   // Step 1: Pick file
    COLUMN_MAPPING,   // Step 2: Select columns
    PROCESSING        // Step 3: Import and close
}

/**
 * Exception thrown when file parsing fails
 */
class FileParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
