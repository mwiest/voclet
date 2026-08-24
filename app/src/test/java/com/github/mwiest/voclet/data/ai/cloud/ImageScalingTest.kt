package com.github.mwiest.voclet.data.ai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageScalingTest {

    @Test
    fun `an image already within the cap is sent untouched`() {
        assertNull(ImageScaling.targetSize(1600, 1200, maxLongEdge = 1600))
        assertNull(ImageScaling.targetSize(800, 600, maxLongEdge = 1600))
    }

    @Test
    fun `a landscape capture is capped on its width`() {
        val size = ImageScaling.targetSize(4032, 3024, maxLongEdge = 1600)!!
        assertEquals(1600, size.width)
        assertEquals(1200, size.height)
    }

    @Test
    fun `a portrait capture is capped on its height`() {
        val size = ImageScaling.targetSize(3024, 4032, maxLongEdge = 1600)!!
        assertEquals(1200, size.width)
        assertEquals(1600, size.height)
    }

    @Test
    fun `the aspect ratio survives scaling`() {
        val size = ImageScaling.targetSize(4000, 2250, maxLongEdge = 1600)!!
        val before = 4000.0 / 2250.0
        val after = size.width.toDouble() / size.height
        assertTrue("ratio drifted: $before vs $after", abs(before - after) < 0.01)
    }

    @Test
    fun `an extreme panorama keeps a usable short edge`() {
        val size = ImageScaling.targetSize(20000, 30, maxLongEdge = 1600)!!
        assertEquals(1600, size.width)
        assertTrue("short edge collapsed to ${size.height}", size.height >= 1)
    }

    @Test
    fun `degenerate dimensions are left alone`() {
        assertNull(ImageScaling.targetSize(0, 1200, maxLongEdge = 1600))
        assertNull(ImageScaling.targetSize(1600, -1, maxLongEdge = 1600))
    }
}
