package com.github.mwiest.voclet.data.ai.ocr

/** An integer point, in pixels of whichever image is under discussion. */
data class Pt(val x: Int, val y: Int)

/**
 * One detected text line, in source-image pixels, corners clockwise from the
 * top left.
 *
 * A quadrilateral rather than a rectangle because a photographed line is rarely
 * square to the page; the recognizer perspective-crops each quad before reading
 * it, which is what lets a slightly skewed page be read without deskewing it.
 */
data class Quad(
    val topLeft: Pt,
    val topRight: Pt,
    val bottomRight: Pt,
    val bottomLeft: Pt,
) {
    val corners: List<Pt> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)
}
