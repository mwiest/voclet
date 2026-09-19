package com.github.mwiest.voclet.data.ai.local

import com.github.mwiest.voclet.data.ai.ocr.PageReaderModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCatalogTest {

    @Test
    fun `ids are unique across every kind of bundle`() {
        // The id names a WorkManager job. A language model and the page reader
        // colliding would mean one download cancelling the other.
        val ids = DownloadCatalog.ALL.map { it.id }
        assertEquals("duplicate bundle ids: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `file names are unique across every kind of bundle`() {
        // Every bundle unpacks into the same models directory, so a collision
        // between a language model and the page reader is exactly as damaging
        // as one within a catalog - and far less likely to be noticed.
        val names = DownloadCatalog.ALL.flatMap { it.files }.map { it.fileName }
        assertEquals("two bundles share a file name: $names", names.size, names.toSet().size)
    }

    @Test
    fun `the worker can resolve every bundle it may be handed`() {
        // ModelDownloadWorker fails the job outright on an id it cannot resolve,
        // so anything downloadable has to be reachable from the catalog.
        DownloadCatalog.ALL.forEach { bundle ->
            assertNotNull("${bundle.id} is not resolvable", DownloadCatalog.byId(bundle.id))
        }
        assertNull(DownloadCatalog.byId("does-not-exist"))
    }

    @Test
    fun `the page reader is in the catalog and is not a language model`() {
        assertNotNull(DownloadCatalog.byId(PageReaderModels.id))
        assertNull(
            "the page reader must not be an AiModel - it has no tier or RAM gate",
            AiModel.byId(PageReaderModels.id),
        )
    }

    @Test
    fun `the page reader's files are pinned to the hosted release`() {
        // The sizes weight the progress bar and are the exact bytes of the
        // published assets; the urls must end in the name the file is saved
        // under, or the download lands somewhere PageReader will not look.
        assertEquals(4, PageReaderModels.files.size)
        PageReaderModels.files.forEach { file ->
            assertTrue(
                "${file.fileName} is not served from the pinned release",
                file.url.startsWith("https://github.com/mwiest/voclet/releases/download/"),
            )
            assertTrue("${file.url} does not end in ${file.fileName}", file.url.endsWith(file.fileName))
            assertTrue("${file.fileName} has no size", file.sizeBytes > 0)
        }
        assertEquals(12_679_240L, PageReaderModels.totalSizeBytes)
    }

    @Test
    fun `the page reader downloads its weights before its parameter files`() {
        // Progress is weighted by size, and the two .param files are 43 KB of a
        // 12.7 MB download. Fetching them first would move the bar by a third
        // of a percent and then appear to stall, so the bytes come first.
        val names = PageReaderModels.files.map { it.fileName }
        assertEquals(
            "the .param files should be fetched last",
            names.filterNot { it.endsWith(".param") } + names.filter { it.endsWith(".param") },
            names,
        )
    }
}
