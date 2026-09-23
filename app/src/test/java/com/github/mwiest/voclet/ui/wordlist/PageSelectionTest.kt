package com.github.mwiest.voclet.ui.wordlist

import com.github.mwiest.voclet.data.ai.ocr.ImageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PageSelectionTest {

    private val start = PageQuad.inset()

    @Test
    fun `the selection starts five percent inside the photo`() {
        assertEquals(PagePoint(0.05f, 0.05f), start.topLeft)
        assertEquals(PagePoint(0.95f, 0.95f), start.bottomRight)
    }

    @Test
    fun `a corner can be pulled inwards`() {
        val moved = start.moved(PageCorner.TOP_LEFT, PagePoint(0.3f, 0.2f))
        assertEquals(PagePoint(0.3f, 0.2f), moved?.topLeft)
        assertEquals(start.topRight, moved?.topRight)
    }

    @Test
    fun `a corner cannot leave the photo`() {
        val moved = start.moved(PageCorner.BOTTOM_RIGHT, PagePoint(1.4f, 1.3f))
        assertEquals(PagePoint(1f, 1f), moved?.bottomRight)
    }

    @Test
    fun `a corner cannot be dragged across the opposite side`() {
        assertNull(start.moved(PageCorner.TOP_LEFT, PagePoint(0.99f, 0.99f)))
        assertNull(start.moved(PageCorner.TOP_RIGHT, PagePoint(0.02f, 0.5f)))
    }

    @Test
    fun `a corner that would make the quad concave is refused`() {
        // Past the diagonal from top right to bottom left, but still inside the box.
        assertNull(start.moved(PageCorner.TOP_LEFT, PagePoint(0.6f, 0.6f)))
        assertNotNull(start.moved(PageCorner.TOP_LEFT, PagePoint(0.4f, 0.4f)))
    }

    @Test
    fun `the quad cannot shrink to a sliver`() {
        val narrow = PageQuad(
            PagePoint(0.4f, 0.1f), PagePoint(0.5f, 0.1f),
            PagePoint(0.5f, 0.9f), PagePoint(0.4f, 0.9f),
        )
        assertNull(narrow.moved(PageCorner.TOP_RIGHT, PagePoint(0.42f, 0.1f)))
    }

    @Test
    fun `the whole photo flattens to its own size`() {
        val whole = PageQuad.inset(0f)
        assertEquals(ImageSize(4000, 3000), whole.outputSize(4000, 3000))
    }

    @Test
    fun `a skewed quad takes the longer of each pair of edges`() {
        // A page shot at an angle: the top edge is foreshortened.
        val skewed = PageQuad(
            PagePoint(0.2f, 0f), PagePoint(0.8f, 0f),
            PagePoint(1f, 1f), PagePoint(0f, 1f),
        )
        val size = skewed.outputSize(1000, 1000)
        assertEquals(1000, size.width)
        assertEquals(1020, size.height) // hypot(200, 1000)
    }

    @Test
    fun `a wide photo is letterboxed top and bottom`() {
        val fit = FitRect.of(imageWidth = 2000, imageHeight = 1000, boxWidth = 400f, boxHeight = 400f)
        assertEquals(FitRect(0f, 100f, 400f, 200f), fit)
    }

    @Test
    fun `a tall photo is letterboxed left and right`() {
        val fit = FitRect.of(imageWidth = 1000, imageHeight = 2000, boxWidth = 400f, boxHeight = 400f)
        assertEquals(FitRect(100f, 0f, 200f, 400f), fit)
    }

    @Test
    fun `box and image coordinates round-trip`() {
        val fit = FitRect(100f, 0f, 200f, 400f)
        val point = PagePoint(0.25f, 0.5f)
        val (x, y) = fit.toBox(point)
        assertEquals(150f, x)
        assertEquals(200f, y)
        assertEquals(point, fit.toImage(x, y))
    }

    @Test
    fun `a touch picks the nearest corner within reach`() {
        val fit = FitRect(0f, 0f, 1000f, 1000f)
        assertEquals(PageCorner.TOP_LEFT, fit.cornerNear(start, 60f, 40f, radius = 48f))
        assertEquals(PageCorner.BOTTOM_RIGHT, fit.cornerNear(start, 940f, 960f, radius = 48f))
        assertNull(fit.cornerNear(start, 500f, 500f, radius = 48f))
    }
}
