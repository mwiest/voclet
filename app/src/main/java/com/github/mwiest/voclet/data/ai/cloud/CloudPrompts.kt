package com.github.mwiest.voclet.data.ai.cloud

/**
 * Prompts for the cloud backend.
 *
 * Both ask for strict JSON, which [CloudResponseParser] then reads. They are
 * kept separate from transport so the wording can be tuned without touching
 * the HTTP layer.
 */
object CloudPrompts {

    fun imageExtraction(language1: String?, language2: String?): String {
        val languageHint = if (language1 != null && language2 != null) {
            "Expected languages: $language1 and $language2."
        } else {
            "Detect the languages automatically."
        }

        return """
            You are a vocabulary learning assistant. Analyze this image containing a vocabulary list.

            Extract all word pairs from the image. $languageHint

            Provide your response in JSON format:
            {
              "title": "Page or list title if clearly found, null otherwise",
              "detectedLanguage1": "language ISO code (e.g., 'en')",
              "detectedLanguage2": "language ISO code (e.g., 'es')",
              "wordPairs": [
                {"word1": "hello", "word2": "hola", "confidence": 0.95},
                {"word1": "goodbye", "word2": "adiós", "confidence": 0.90}
              ],
              "confidence": 0.92
            }

            Rules:
            - Only extract clear word pairs (word-to-word or phrase-to-phrase)
            - Ignore headers, titles, or unrelated text
            - Confidence should be between 0.0 and 1.0
            - Return empty wordPairs array if no valid pairs found
            - Respond with the JSON object only, no explanation
        """.trimIndent()
    }

    fun translation(word: String, fromLanguage: String, toLanguage: String): String = """
        Translate the word "$word" from $fromLanguage to $toLanguage.

        Provide your response in JSON format:
        {
          "primaryTranslation": "main translation",
          "alternatives": ["alternative1", "alternative2"],
          "contextualNotes": "optional usage notes in $fromLanguage to help distinguish usage in $toLanguage"
        }

        Respond with the JSON object only, no explanation.
    """.trimIndent()
}
