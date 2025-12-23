package com.github.mwiest.voclet.data.ai.models

/**
 * Result of extracting word pairs from an image.
 */
data class WordPairExtractionResult(
    val detectedLanguage1: String,  // e.g., "en"
    val detectedLanguage2: String,  // e.g., "es"
    val wordPairs: List<ExtractedWordPair>,
    val confidence: Float = 1.0f    // Overall confidence score (0.0-1.0)
)

/**
 * A single extracted word pair with confidence score.
 */
data class ExtractedWordPair(
    val word1: String,
    val word2: String,
    val confidence: Float = 1.0f   // Confidence for this specific pair (0.0-1.0)
)

/**
 * Translation suggestion with alternatives.
 */
data class TranslationSuggestion(
    val primaryTranslation: String,
    val alternatives: List<String> = emptyList(),  // Up to 3-4 alternatives
    val contextualNotes: String? = null            // Optional grammar/usage notes
)
