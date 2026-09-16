package com.github.mwiest.voclet.data.ai.ocr

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Clipper's round polygon offset, for the one shape PP-OCR asks it to expand:
 * a min-area rectangle.
 *
 * This exists because the obvious shortcut does not work. The detector grows
 * each box by `d` and immediately re-fits a rectangle to the result, which
 * looks exactly like "make the rectangle `d` bigger on each side" — and is
 * wrong by up to 1.4 px, because **Clipper works in integer coordinates**. Its
 * round joins put ~7 vertices on an arc around every corner, each snapped to a
 * whole pixel, and on a rotated box that scatter fits a slightly *larger*
 * rectangle than the arithmetic does. Substituting the arithmetic changed the
 * recognized text on two of the four bench pages, so the integer rounding is
 * not noise to be tidied away.
 *
 * Two rounding rules are in play and they differ: the incoming corners are
 * **truncated** (pyclipper casts doubles to Clipper's integer point type),
 * while the offset vertices are **rounded half away from zero** (Clipper's own
 * `Round`). Neither is Kotlin's default.
 *
 * Verified against pyclipper over all 293 candidate boxes the detector produces
 * for the four bench pages: identical fitted rectangles, to the last bit.
 */
internal object RoundOffset {

    /** Clipper's `def_arc_tolerance`, and pyclipper's default `ArcTolerance`. */
    private const val ARC_TOLERANCE = 0.25

    /**
     * Expands the polygon [corners] (x, y pairs) outwards by [delta], with
     * round joins, and returns the integer vertices of the result.
     *
     * Empty when the corners collapse to fewer than three distinct pixels,
     * which only a degenerate rectangle can manage; the caller drops those.
     */
    fun expand(corners: DoubleArray, delta: Double): List<Pt> {
        val source = sourcePolygon(corners)
        if (source.size < 3) return emptyList()

        val n = source.size
        val normalX = DoubleArray(n)
        val normalY = DoubleArray(n)
        for (i in 0 until n) {
            val next = source[(i + 1) % n]
            val dx = (next.x - source[i].x).toDouble()
            val dy = (next.y - source[i].y).toDouble()
            val f = 1.0 / hypot(dx, dy)
            normalX[i] = dy * f
            normalY[i] = -dx * f
        }

        // Clipper picks the arc's step count so no vertex strays further than
        // the arc tolerance from the true circle.
        val tolerance = if (ARC_TOLERANCE <= abs(delta) * 0.25) ARC_TOLERANCE else abs(delta) * 0.25
        val stepsPerTurn = Math.PI / acos(1 - tolerance / abs(delta))
        val stepSin = sin(2 * Math.PI / stepsPerTurn)
        val stepCos = cos(2 * Math.PI / stepsPerTurn)
        val stepsPerRadian = stepsPerTurn / (2 * Math.PI)

        val out = mutableListOf<Pt>()
        var k = n - 1
        for (j in 0 until n) {
            val sinA = (normalX[k] * normalY[j] - normalX[j] * normalY[k]).coerceIn(-1.0, 1.0)
            val angle = atan2(sinA, normalX[k] * normalX[j] + normalY[k] * normalY[j])
            val steps = maxOf(clipperRound(stepsPerRadian * abs(angle)), 1)

            var x = normalX[k]
            var y = normalY[k]
            repeat(steps) {
                out += Pt(
                    clipperRound(source[j].x + x * delta),
                    clipperRound(source[j].y + y * delta),
                )
                val previousX = x
                x = x * stepCos - stepSin * y
                y = previousX * stepSin + y * stepCos
            }
            out += Pt(
                clipperRound(source[j].x + normalX[j] * delta),
                clipperRound(source[j].y + normalY[j] * delta),
            )
            k = j
        }
        return out
    }

    /**
     * The corners as Clipper sees them: truncated to integers, consecutive
     * duplicates dropped, and wound positively.
     */
    private fun sourcePolygon(corners: DoubleArray): List<Pt> {
        val points = mutableListOf<Pt>()
        for (i in 0 until corners.size / 2) {
            val point = Pt(corners[2 * i].toInt(), corners[2 * i + 1].toInt())
            if (points.isEmpty() || points.last() != point) points += point
        }
        while (points.size > 1 && points.last() == points.first()) points.removeAt(points.size - 1)
        return if (points.size >= 3 && signedArea(points) < 0) points.asReversed() else points
    }

    private fun signedArea(points: List<Pt>): Double {
        var total = 0.0
        var j = points.size - 1
        for (i in points.indices) {
            total += (points[j].x.toDouble() + points[i].x) * (points[j].y.toDouble() - points[i].y)
            j = i
        }
        return -total * 0.5
    }

    /** Clipper's `Round`: half away from zero, where Kotlin's rounds to even. */
    private fun clipperRound(v: Double): Int = if (v < 0) (v - 0.5).toInt() else (v + 0.5).toInt()
}
