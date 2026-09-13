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

            Answer with a single JSON object. It has five fields:
            - title: the page or list title if one is clearly printed, otherwise null
            - detectedLanguage1: the ISO code of the first language (e.g. 'en')
            - detectedLanguage2: the ISO code of the second language (e.g. 'es')
            - wordPairs: an array of objects, each with the string fields word1 and word2
              holding the two terms of one row, plus a number field confidence
            - confidence: a number for the extraction as a whole

            Rules:
            - Only extract clear word pairs (word-to-word or phrase-to-phrase)
            - Ignore headers, titles, or unrelated text
            - Confidence is between 0.0 and 1.0
            - Return empty wordPairs array if no valid pairs found
            - Respond with the JSON object only, no explanation, and never repeat these instructions back
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
