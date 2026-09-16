package com.github.mwiest.voclet.data.ai.ocr

import kotlin.math.hypot
import kotlin.math.min

/**
 * The smallest-area rectangle enclosing a set of points, at any rotation —
 * OpenCV's `minAreaRect`, which the detector's post-processing needs twice per
 * candidate box and which is the one step whose precision actually reaches the
 * output (it fixes the box coordinates; see [DbPostProcess] for what does not).
 *
 * Held as a centre, two side lengths and the unit vector along [width] rather
 * than as an angle. The angle would only ever exist to be turned back into
 * corners — and PP-OCR re-sorts those corners itself — so a degree convention
 * would be a round trip with nothing at the far end.
 */
internal data class MinRect(
    val cx: Double,
    val cy: Double,
    val width: Double,
    val height: Double,
    /** Unit vector along the [width] side. */
    val ux: Double,
    val uy: Double,
) {
    val shortSide: Double get() = min(width, height)

    /** The four corners, in no particular order — every caller re-sorts them. */
    fun corners(): DoubleArray {
        val hw = width / 2.0
        val hh = height / 2.0
        val vx = -uy
        val vy = ux
        return doubleArrayOf(
            cx - hw * ux - hh * vx, cy - hw * uy - hh * vy,
            cx + hw * ux - hh * vx, cy + hw * uy - hh * vy,
            cx + hw * ux + hh * vx, cy + hw * uy + hh * vy,
            cx - hw * ux + hh * vx, cy - hw * uy + hh * vy,
        )
    }

}

/**
 * [MinRect] of a point set, by the standard result that a minimal enclosing
 * rectangle has a side collinear with an edge of the convex hull: measure the
 * extent along every hull edge and keep the smallest area.
 *
 * O(h²) in the hull size rather than the rotating-calipers O(h). Hulls here are
 * the outline of one text line, so h is tens of points and the simpler loop is
 * not worth trading for the subtler one.
 *
 * Returns null only for an empty input.
 */
internal fun minAreaRect(points: List<Pt>): MinRect? {
    val hull = convexHull(points)
    if (hull.isEmpty()) return null
    if (hull.size == 1) {
        return MinRect(hull[0].x.toDouble(), hull[0].y.toDouble(), 0.0, 0.0, 1.0, 0.0)
    }

    var best: MinRect? = null
    var bestArea = Double.MAX_VALUE
    for (i in hull.indices) {
        val a = hull[i]
        val b = hull[(i + 1) % hull.size]
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        if (len == 0.0) continue
        val ux = (b.x - a.x) / len
        val uy = (b.y - a.y) / len

        var minU = Double.MAX_VALUE
        var maxU = -Double.MAX_VALUE
        var minV = Double.MAX_VALUE
        var maxV = -Double.MAX_VALUE
        for (p in hull) {
            val u = p.x * ux + p.y * uy
            val v = -p.x * uy + p.y * ux
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }

        val w = maxU - minU
        val h = maxV - minV
        val area = w * h
        if (area < bestArea) {
            bestArea = area
            val cu = (minU + maxU) / 2.0
            val cv = (minV + maxV) / 2.0
            best = MinRect(cu * ux - cv * uy, cu * uy + cv * ux, w, h, ux, uy)
        }
    }
    return best
}

/** Convex hull, counter-clockwise, by Andrew's monotone chain. */
internal fun convexHull(points: List<Pt>): List<Pt> {
    if (points.size < 3) return points.distinct()
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size < 3) return sorted

    fun half(source: List<Pt>): MutableList<Pt> {
        val chain = mutableListOf<Pt>()
        for (p in source) {
            while (chain.size >= 2 && cross(chain[chain.size - 2], chain[chain.size - 1], p) <= 0L) {
                chain.removeAt(chain.size - 1)
            }
            chain.add(p)
        }
        return chain
    }

    val lower = half(sorted)
    val upper = half(sorted.asReversed())
    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
}

private fun cross(o: Pt, a: Pt, b: Pt): Long =
    (a.x - o.x).toLong() * (b.y - o.y) - (a.y - o.y).toLong() * (b.x - o.x)
