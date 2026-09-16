package com.github.mwiest.voclet.data.ai.ocr

/** One line of text as the recognizer read it. */
data class Recognition(val text: String, val confidence: Float)

/**
 * Turns the recognizer's output into a string.
 *
 * The model does not say where one character ends and the next begins. For each
 * of ~T horizontal slices of the crop it emits a probability over every class,
 * and a single letter typically wins several slices in a row. CTC
 * (Connectionist Temporal Classification) is the convention that makes that
 * decodable: take the winner per slice, **collapse runs of the same class**,
 * then drop the blank class. The blank exists so that a genuine double letter
 * survives the collapse — `ss` is emitted as `s`, blank, `s`, and without the
 * blank between them the two would merge into one.
 *
 * Note that the run-collapse looks at the *raw* winning class of the previous
 * slice, blanks included. Skipping blanks first and then comparing against the
 * last kept letter would silently delete every double letter on the page.
 *
 * A port of RapidOCR's `CTCLabelDecode`.
 */
class CtcDecoder(
    /**
     * Output classes in the model's own order. Index 0 is the CTC blank; see
     * [alphabetFrom].
     */
    private val alphabet: List<String>,
) {

    /**
     * @param probabilities the recognizer's output for one crop, row-major,
     *   [timesteps] rows of [alphabet].size probabilities.
     */
    fun decode(probabilities: FloatArray, timesteps: Int): Recognition {
        val classes = alphabet.size
        require(probabilities.size == timesteps * classes) {
            "expected ${timesteps * classes} probabilities, got ${probabilities.size}"
        }

        val text = StringBuilder()
        var total = 0.0
        var kept = 0
        var previous = -1

        for (step in 0 until timesteps) {
            val row = step * classes
            var best = 0
            var bestProbability = probabilities[row]
            for (index in 1 until classes) {
                // Strictly greater, so ties go to the lowest class index the
                // way numpy's argmax does.
                if (probabilities[row + index] > bestProbability) {
                    bestProbability = probabilities[row + index]
                    best = index
                }
            }

            if (best != BLANK && best != previous) {
                text.append(alphabet[best])
                total += bestProbability
                kept++
            }
            previous = best
        }

        // Divided by kept + 1, not kept. Upstream appends a 1e-50 sentinel to
        // the list it averages so an empty one cannot produce NaN, and then
        // averages over it too. That is not rounding error: a confident
        // single-character line scores ~0.5 rather than ~1.0, which is right at
        // the threshold such a line is later filtered on, so the off-by-one has
        // to come along.
        return Recognition(text.toString(), (total / (kept + 1)).toFloat())
    }

    companion object {
        /** The CTC blank, which the export puts first. */
        const val BLANK = 0

        /** Where the character list lives in the APK. */
        const val DICTIONARY_ASSET = "ocr/latin_dict.txt"

        /**
         * The model's class list, built from the lines of [DICTIONARY_ASSET]:
         * the blank, then the dictionary, then a space.
         *
         * The ONNX export does not carry the character list, so it ships beside
         * the weights and the order here is the only thing tying the two
         * together. One entry out of place shifts every character the model
         * ever returns, and nothing errors — which is why the recognizer checks
         * this list against the model's output width before reading anything.
         */
        fun alphabetFrom(dictionaryLines: List<String>): List<String> =
            buildList(dictionaryLines.size + 2) {
                add("")
                addAll(dictionaryLines)
                add(" ")
            }
    }
}
