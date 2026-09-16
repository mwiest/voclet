package com.github.mwiest.voclet.data.ai.ocr

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Turns PP-OCRv5's detector output — a per-pixel probability map the size of
 * the resized page — into text quads. The "DB" of Differentiable Binarization,
 * whose post-processing is the whole of the step; the inference around it is a
 * single ONNX call.
 *
 * A port of RapidOCR's `DBPostProcess`, in the configuration the bench measured
 * at 129/136 pairs. Two of its steps are *not* ported literally, each because
 * substituting it was measured to leave the recognized text of all four bench
 * pages unchanged:
 *
 * - **No border following.** Upstream finds candidates with OpenCV's
 *   `findContours`; 8-connected components give the same rectangles within a
 *   pixel, without a Suzuki-Abe tracer and without `RETR_LIST`'s hole contours.
 * - **No angle classifier.** PP-OCR's 0-vs-180 classifier never flipped a line
 *   on any bench page, so the pipeline is two models rather than three.
 *
 * The polygon expansion, by contrast, *is* ported literally — see [RoundOffset]
 * for why the one-line arithmetic that appears to replace it does not.
 *
 * The thresholds are RapidOCR's own and are load-bearing: [UNCLIP_RATIO] in
 * particular is **1.6**, not the 2.0 quoted in PP-OCR's papers and in an early
 * draft of this task.
 */
object DbPostProcess {

    /** A map pixel counts as ink above this. */
    private const val THRESHOLD = 0.3f

    /** Mean probability inside a candidate box, below which it is discarded. */
    private const val BOX_THRESHOLD = 0.5

    /** How far a box grows, as a multiple of its area over its perimeter. */
    private const val UNCLIP_RATIO = 1.6

    /** Shortest side a candidate may have, in map pixels, before growing. */
    private const val MIN_SIZE = 3.0

    private const val MAX_CANDIDATES = 1000

    /**
     * @param probabilities the detector's output map, row-major,
     *   [mapWidth] x [mapHeight], each value 0..1.
     * @param sourceWidth width of the image the boxes are wanted in — the page
     *   as it was handed to the detector, before the detector's own resize.
     */
    fun detect(
        probabilities: FloatArray,
        mapWidth: Int,
        mapHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
    ): List<Quad> {
        require(probabilities.size == mapWidth * mapHeight) {
            "probability map is ${probabilities.size}, expected ${mapWidth * mapHeight}"
        }

        val mask = inkMask(probabilities, mapWidth, mapHeight)
        val quads = mutableListOf<Quad>()
        for (component in components(mask, mapWidth, mapHeight).take(MAX_CANDIDATES)) {
            val rect = minAreaRect(component) ?: continue
            if (rect.shortSide < MIN_SIZE) continue
            if (meanInside(probabilities, mapWidth, rect.corners()) < BOX_THRESHOLD) continue

            val area = rect.width * rect.height
            val perimeter = 2.0 * (rect.width + rect.height)
            val expanded = RoundOffset.expand(rect.corners(), area * UNCLIP_RATIO / perimeter)
            val grown = minAreaRect(expanded) ?: continue
            if (grown.shortSide < MIN_SIZE + 2) continue

            quads += toQuad(grown.corners(), mapWidth, mapHeight, sourceWidth, sourceHeight)
                ?: continue
        }
        return quads
    }

    /**
     * Thresholded and dilated in one pass. OpenCV dilates with a 2x2 kernel
     * anchored at (1,1), which is exactly "also take the pixel above, to the
     * left, and diagonally above-left of me".
     */
    private fun inkMask(prob: FloatArray, w: Int, h: Int): BooleanArray {
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            val above = row - w
            for (x in 0 until w) {
                mask[row + x] = prob[row + x] > THRESHOLD ||
                    (x > 0 && prob[row + x - 1] > THRESHOLD) ||
                    (y > 0 && prob[above + x] > THRESHOLD) ||
                    (x > 0 && y > 0 && prob[above + x - 1] > THRESHOLD)
            }
        }
        return mask
    }

    /**
     * 8-connected components of [mask], each returned as the endpoints of its
     * horizontal runs.
     *
     * Runs rather than pixels: only a run's two ends can be a hull vertex, and
     * [minAreaRect] wants nothing but the hull. On a dense page that is tens of
     * thousands of points instead of millions.
     */
    private fun components(mask: BooleanArray, w: Int, h: Int): List<List<Pt>> {
        var total = 0
        forEachRun(mask, w, h) { _, _, _ -> total++ }
        if (total == 0) return emptyList()

        val runRow = IntArray(total)
        val runStart = IntArray(total)
        val runEnd = IntArray(total)
        val rowFirst = IntArray(h + 1)
        var n = 0
        var filled = 0
        forEachRun(mask, w, h) { y, start, end ->
            while (filled <= y) rowFirst[filled++] = n
            runRow[n] = y
            runStart[n] = start
            runEnd[n] = end
            n++
        }
        while (filled <= h) rowFirst[filled++] = n

        val parent = IntArray(total) { it }
        for (y in 1 until h) {
            var i = rowFirst[y]
            var j = rowFirst[y - 1]
            val iEnd = rowFirst[y + 1]
            val jEnd = rowFirst[y]
            while (i < iEnd && j < jEnd) {
                // Touching or meeting only at a corner both count: 8-connectivity.
                if (runStart[i] <= runEnd[j] + 1 && runStart[j] <= runEnd[i] + 1) {
                    union(parent, i, j)
                }
                if (runEnd[i] < runEnd[j]) i++ else j++
            }
        }

        val byRoot = LinkedHashMap<Int, MutableList<Pt>>()
        for (k in 0 until total) {
            val points = byRoot.getOrPut(find(parent, k)) { mutableListOf() }
            points += Pt(runStart[k], runRow[k])
            points += Pt(runEnd[k], runRow[k])
        }
        return byRoot.values.toList()
    }

    private inline fun forEachRun(
        mask: BooleanArray,
        w: Int,
        h: Int,
        onRun: (y: Int, start: Int, end: Int) -> Unit,
    ) {
        for (y in 0 until h) {
            val row = y * w
            var x = 0
            while (x < w) {
                if (!mask[row + x]) {
                    x++
                    continue
                }
                val start = x
                while (x < w && mask[row + x]) x++
                onRun(y, start, x - 1)
            }
        }
    }

    private fun find(parent: IntArray, of: Int): Int {
        var i = of
        while (parent[i] != i) {
            parent[i] = parent[parent[i]]
            i = parent[i]
        }
        return i
    }

    private fun union(parent: IntArray, a: Int, b: Int) {
        val ra = find(parent, a)
        val rb = find(parent, b)
        if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
    }

    /**
     * Mean probability over the pixels a quad covers, by scanline fill.
     *
     * Deliberately only *approximately* OpenCV's `fillPoly`: the result is
     * compared against [BOX_THRESHOLD] and used for nothing else, and across
     * the four bench pages the closest any of 293 candidates came to that
     * cutoff was 0.09. A pixel either way cannot change an outcome.
     */
    private fun meanInside(prob: FloatArray, w: Int, corners: DoubleArray): Double {
        val h = prob.size / w
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (i in 0 until 4) {
            minX = minOf(minX, corners[2 * i])
            maxX = maxOf(maxX, corners[2 * i])
            minY = minOf(minY, corners[2 * i + 1])
            maxY = maxOf(maxY, corners[2 * i + 1])
        }
        val x0 = floor(minX).toInt().coerceIn(0, w - 1)
        val x1 = ceil(maxX).toInt().coerceIn(0, w - 1)
        val y0 = floor(minY).toInt().coerceIn(0, h - 1)
        val y1 = ceil(maxY).toInt().coerceIn(0, h - 1)

        // Truncation towards zero, matching numpy's astype(int32) upstream.
        val px = IntArray(4) { (corners[2 * it] - x0).toInt() }
        val py = IntArray(4) { (corners[2 * it + 1] - y0).toInt() }

        val crossings = DoubleArray(4)
        var sum = 0.0
        var count = 0
        for (ly in 0..(y1 - y0)) {
            var found = 0
            for (edge in 0 until 4) {
                val next = (edge + 1) and 3
                val ya = py[edge]
                val yb = py[next]
                if ((ya <= ly && yb > ly) || (yb <= ly && ya > ly)) {
                    crossings[found++] =
                        px[edge] + (ly - ya).toDouble() * (px[next] - px[edge]) / (yb - ya)
                }
            }
            if (found < 2) continue
            crossings.sort(0, found)
            var k = 0
            while (k + 1 < found) {
                val from = ceil(crossings[k]).toInt().coerceAtLeast(0)
                val to = floor(crossings[k + 1]).toInt().coerceAtMost(x1 - x0)
                for (lx in from..to) {
                    sum += prob[(y0 + ly) * w + x0 + lx]
                    count++
                }
                k += 2
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    /**
     * Scales map coordinates back to the source image, orders the corners
     * clockwise from the top left, and drops anything degenerate.
     *
     * [Math.rint], not [kotlin.math.round]: numpy rounds halves to even and
     * Kotlin rounds them away from zero, which is a whole pixel of drift on
     * every coordinate that lands on one.
     */
    private fun toQuad(
        corners: DoubleArray,
        mapWidth: Int,
        mapHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Quad? {
        val scaled = (0 until 4).map { i ->
            Pt(
                Math.rint(corners[2 * i] / mapWidth * sourceWidth).toInt()
                    .coerceIn(0, sourceWidth),
                Math.rint(corners[2 * i + 1] / mapHeight * sourceHeight).toInt()
                    .coerceIn(0, sourceHeight),
            )
        }

        fun clip(p: Pt) = Pt(p.x.coerceIn(0, sourceWidth - 1), p.y.coerceIn(0, sourceHeight - 1))
        val byX = scaled.sortedBy { it.x }
        val left = byX.take(2).sortedBy { it.y }
        val right = byX.drop(2).sortedBy { it.y }
        val topLeft = clip(left[0])
        val bottomLeft = clip(left[1])
        val topRight = clip(right[0])
        val bottomRight = clip(right[1])

        val width = hypot((topLeft.x - topRight.x).toDouble(), (topLeft.y - topRight.y).toDouble())
        val height = hypot((topLeft.x - bottomLeft.x).toDouble(), (topLeft.y - bottomLeft.y).toDouble())
        if (width.toInt() <= 3 || height.toInt() <= 3) return null

        return Quad(topLeft, topRight, bottomRight, bottomLeft)
    }
}
