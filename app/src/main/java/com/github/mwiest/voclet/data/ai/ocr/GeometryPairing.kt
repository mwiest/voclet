package com.github.mwiest.voclet.data.ai.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns the text boxes of a classical OCR engine into word pairs, by geometry.
 *
 * A port of `tools/llm-bench/geompair.py`, which scores 129 of 136 pairs over
 * four pages with zero swapped columns. A lower number here is a porting bug,
 * not a model limit.
 *
 * This exists because of what the vision bench measured: given a page read
 * correctly, a *deterministic* second pass turned it into pairs at 85/86 while
 * a 1.2B text model managed 28/86. If the pairing does not want a language
 * model, the recognition does not need a vision-language model either - and a
 * classical recognizer hands over something better than a markdown table: the
 * coordinates. Column membership becomes an x-interval instead of an inference.
 *
 * Two properties of a real vocabulary page drive the algorithm:
 *
 * - A page can hold **more than two columns**. The photographed glossary is
 *   two independent two-column tables side by side, so a global left/right
 *   split would pair the German of one table with the German of the other.
 *   Columns are the x ranges hardly any *row* has ink in, however many there
 *   are, paired up two at a time.
 * - **Rows are not aligned to pixels.** A photographed line drifts, and a
 *   wrapped cell ("Where do you come / from?") is two lines that belong to one
 *   row, so lines are clustered by their vertical centre rather than by y.
 *
 * The order of operations is load-bearing: drop non-text boxes, cluster rows,
 * find gutters, keep only populated columns, then pair columns two at a time.
 */
object GeometryPairing {

    /** An x range holding one column of the table. */
    private data class Column(val start: Double, val end: Double)

    /**
     * @param wholeCells the recognizer returned whole cells rather than single
     *   words, which PP-OCR does. It changes what a gutter is allowed to look
     *   like; see [findBoundaries].
     */
    fun pairUp(boxes: List<TextBox>, wholeCells: Boolean = false): List<Pair<String, String>> {
        val words = dropRules(boxes)
        if (words.isEmpty()) return emptyList()

        val columns = populated(findGutters(words, wholeCells), clusterRows(words))
        if (columns.size < 2) return emptyList()

        // Titles are set larger than the table they head - on the photographed
        // glossary the headings measure 44-53 px against a 22-26 px body. The
        // margin is 1.5x rather than 1.3x because a row of tall glyphs is not a
        // title: "dreissig / thirty" (eszett plus two descenders) measures 1.35x
        // and was being thrown away.
        val bodyHeight = words.map { it.height }.median()

        val pairs = mutableListOf<Pair<String, String>>()
        for (row in clusterRows(words)) {
            if (row.map { it.height }.median() > bodyHeight * 1.5) continue

            val cells = mutableMapOf<Int, MutableList<TextBox>>()
            for (word in row) {
                val index = columnOf(columns, word) ?: continue
                cells.getOrPut(index) { mutableListOf() }.add(word)
            }
            val text = cells.mapValues { (_, boxesInCell) ->
                boxesInCell.sortedBy { it.x }.joinToString(" ") { it.text }
            }
            // Columns are paired left to right: 0 with 1, 2 with 3. A row that
            // only reaches one column of a pair is a heading, not a vocabulary row.
            for (first in 0 until columns.size - 1 step 2) {
                val one = text[first].orEmpty().trim()
                val two = text[first + 1].orEmpty().trim()
                if (one.isNotEmpty() && two.isNotEmpty()) pairs.add(one to two)
            }
        }

        // A row repeated across the page is a table header ("Deutsch | Englisch"),
        // the same signal the markdown splitter uses.
        val counts = pairs.groupingBy { it }.eachCount()
        return pairs.filter { counts.getValue(it) == 1 }
    }

    /**
     * Which column a box belongs to, **by its left edge, not its centre**: a
     * cell wide enough to overflow the gutter ("Ich arbeite bei BMW.")
     * otherwise loses its last word to the next column, which corrupts two rows
     * at once instead of one.
     */
    private fun columnOf(columns: List<Column>, word: TextBox): Int? =
        columns.indexOfFirst { word.x >= it.start - 2 && word.x <= it.end + 2 }
            .takeIf { it >= 0 }

    /**
     * Discards boxes that cannot be text, chiefly the printed ruling lines.
     *
     * Tesseract returns a table's rules as words - `103x1` reading "Een",
     * `214x2` reading "FRE" - and because they are wide they cross the gutters
     * and hide them, which cost a whole page (0/14 on the cleanest photo of the
     * set). Text on one page has a consistent x-height, so a box a fraction of
     * the median height is a rule, a speck or a pencil mark, whatever glyphs
     * the recognizer thought it saw.
     */
    private fun dropRules(words: List<TextBox>): List<TextBox> {
        if (words.isEmpty()) return words
        val body = words.map { it.height }.median()
        return words.filter { it.height >= body * 0.4 }
    }

    /**
     * Groups boxes into rows of text.
     *
     * A box joins the row it is level with, judged against that row's *average*
     * centre line and a tolerance of half a line height. The first version
     * compared against the row's full extent instead, which chains: every box
     * that joins stretches the extent, the stretched extent admits the next
     * one, and on a dense page one row eventually swallows the rest - 71 rows
     * of glossary collapsed into 12, and the page scored zero. The average
     * cannot run away like that, and it still tolerates the drift of a
     * photographed line.
     */
    private fun clusterRows(words: List<TextBox>): List<List<TextBox>> {
        if (words.isEmpty()) return emptyList()
        val tolerance = words.map { it.height }.median() * 0.6

        val rows = mutableListOf<MutableList<TextBox>>()
        val centres = mutableListOf<Double>()
        for (word in words.sortedWith(compareBy({ it.centerY }, { it.x }))) {
            if (rows.isNotEmpty() && abs(word.centerY - centres.last()) <= tolerance) {
                rows.last().add(word)
                centres[centres.lastIndex] = rows.last().sumOf { it.centerY } / rows.last().size
            } else {
                rows.add(mutableListOf(word))
                centres.add(word.centerY)
            }
        }
        return rows.map { row -> row.sortedBy { it.x } }
    }

    /** Column x-ranges: the page edges plus every agreed boundary. */
    private fun findGutters(words: List<TextBox>, wholeCells: Boolean): List<Column> {
        if (words.isEmpty()) return emptyList()
        val left = words.minOf { it.x }
        val right = words.maxOf { it.right }
        val edges = buildList {
            add(left - 1.0)
            addAll(findBoundaries(clusterRows(words), left, right, wholeCells))
            add(right + 1.0)
        }
        return edges.zipWithNext { a, b -> Column(a, b) }
    }

    /**
     * Column boundaries: the x ranges hardly any row has ink in.
     *
     * Every threshold is measured in units the page supplies - the median word
     * gap and the median word height - rather than as a fraction of the page
     * width. Fractions of the page were the first version and they are the
     * wrong scale twice over: the same page photographed at 3000 px and at
     * 1600 px got different answers, and a page of large print behaves like a
     * page of small print with wider gutters. A column gap is a multiple of a
     * *word space*, which is a property of the typesetting.
     */
    private fun findBoundaries(
        rows: List<List<TextBox>>,
        left: Int,
        right: Int,
        wholeCells: Boolean,
    ): List<Double> {
        val gaps = rows.filter { it.size >= 2 }
            .flatMap { row -> row.zipWithNext { a, b -> b.x - a.right } }
            .filter { it > 0 }
        val heights = rows.flatten().map { it.height }
        if (gaps.isEmpty() || heights.isEmpty()) return emptyList()
        val wordSpace = gaps.median()
        val wordHeight = heights.median()

        // A gutter is several word spaces wide; a word space is not. Both
        // bounds matter: a page whose columns nearly touch needs the height
        // floor, and a loosely tracked one needs the multiple of the space.
        //
        // With whole cells every gap inside a row is already a column gap, the
        // median of them is not a word space at all, and three times it would
        // swallow the page.
        val minGap = if (wholeCells) wordHeight * 0.6 else max(wordSpace * 3, wordHeight * 0.6)
        // A boundary is only ever localised to about a character, so that is the bin.
        val binWidth = max(4.0, wordHeight * 0.6)

        // A gutter is an x that hardly any *row* has a box crossing. Counting
        // rows rather than boxes is what makes it work on a real page: the
        // section bars and the title cross every gutter, but they are two rows
        // out of forty, so a small tolerance ignores them while a plain ink
        // projection - which has no notion of rows - sees one unbroken column
        // and finds nothing.
        //
        // Two vote-and-threshold formulations were tried first and each broke a
        // different page: the midpoint of a wide gap smears across 130 px when
        // entries differ in length ("dehors" against "faire les devoirs (m pl)"),
        // and the start of the word after the gap lands 45-85 px right of the
        // true column start on a dense page. Neither is a property of the
        // layout; "no row has ink here" is.
        //
        // How many rows may have ink in a gutter and it still count as one: a
        // long entry overflows toward the next column, and with whole cells the
        // box *is* the whole entry, so it reaches the gutter far more often than
        // a single word would. On the glossary the narrowest point of one real
        // gutter was crossed by 5 rows in 40, which a tenth rejected and cost
        // the whole page.
        val tolerance = max(1, (rows.size * (if (wholeCells) 0.25 else 0.1)).toInt())
        val steps = ((right - left) / binWidth).toInt() + 1
        val crossings = IntArray(steps)
        for (row in rows) {
            val hit = mutableSetOf<Int>()
            for (word in row) {
                val first = max(0, ((word.x - left) / binWidth).toInt())
                val last = min(steps - 1, ((word.right - left) / binWidth).toInt())
                for (step in first..last) hit.add(step)
            }
            for (step in hit) crossings[step]++
        }

        val boundaries = mutableListOf<Double>()
        var runStart: Int? = null
        for (step in 0 until steps) {
            val clear = crossings[step] <= tolerance
            if (clear && runStart == null) {
                runStart = step
            } else if (!clear && runStart != null) {
                gutterCentre(runStart, step, left, binWidth, minGap)?.let(boundaries::add)
                runStart = null
            }
        }
        // A run reaching the right edge is the margin, not a gutter, so it is
        // left unclosed here and dropped along with the one at the left edge.
        return boundaries.filter { it > left + minGap }
    }

    /** The middle of one clear run, if it is wide enough to be a gutter. */
    private fun gutterCentre(
        runStart: Int,
        runEnd: Int,
        left: Int,
        binWidth: Double,
        minGap: Double,
    ): Double? {
        val width = (runEnd - runStart) * binWidth
        return if (width < minGap) null else left + (runStart + runEnd) / 2.0 * binWidth
    }

    /**
     * Keeps the columns that behave like columns of a vocabulary table.
     *
     * A column of such a table has an entry in most rows. The margins do not:
     * they hold the reader's pencil ticks, a dozen marks down the side. Those
     * were invisible to the recognizer until binarization brought them out, at
     * which point they became two extra columns and the pairing - which takes
     * columns two at a time - matched the margin with the French and the German
     * with the other margin. A page of 36 pairs scored zero.
     */
    private fun populated(columns: List<Column>, rows: List<List<TextBox>>): List<Column> {
        if (rows.isEmpty()) return columns
        val kept = columns.filter { column ->
            val covered = rows.count { row ->
                row.any { it.x >= column.start - 2 && it.x <= column.end + 2 }
            }
            covered >= rows.size * 0.4
        }
        return if (kept.size >= 2) kept else columns
    }

    /** `statistics.median`: the mean of the two middle values when even. */
    private fun List<Int>.median(): Double {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble()
        else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
