package com.github.mwiest.voclet.data.ai.ocr

/**
 * One box of text from a recognizer, with its axis-aligned bounds in pixels.
 *
 * PP-OCR detects text *lines*, so on a two-column table one box is a whole
 * cell rather than a word. [GeometryPairing] needs to be told which it is
 * getting; the thresholds differ.
 */
data class TextBox(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int get() = x + width
    val centerY: Double get() = y + height / 2.0
}
