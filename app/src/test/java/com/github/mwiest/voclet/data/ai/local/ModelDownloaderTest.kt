package com.github.mwiest.voclet.data.ai.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Bundles here are local to the test rather than taken from the catalog: the
 * downloader is not supposed to know what it is fetching, and a test that reads
 * the real catalog fails whenever the catalog changes for unrelated reasons.
 */
class ModelDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestBundle(
        override val id: String,
        override val files: List<BundleFile>,
    ) : DownloadBundle {
        override val displayName = id
    }

    /** Lopsided on purpose: two big files and a tiny one, like the OCR set. */
    private val bundle = TestBundle(
        "three-files",
        listOf(
            BundleFile("https://example.invalid/big.bin", "big.bin", 900),
            BundleFile("https://example.invalid/small.param", "small.param", 50),
            BundleFile("https://example.invalid/other.bin", "other.bin", 50),
        ),
    )

    private val single = TestBundle(
        "one-file",
        listOf(BundleFile("https://example.invalid/only.gguf", "only.gguf", 1000)),
    )

    /** Serves each file its declared size, so progress can be checked. */
    private class FakeDownloader(
        private val sizes: Map<String, Long> = emptyMap(),
        private val failOn: String? = null,
    ) : FileDownloader {
        val urls = mutableListOf<String>()

        override suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
            if (failOn != null && url.contains(failOn)) throw IOException("boom")
            urls.add(url)
            val size = (sizes[url.substringAfterLast('/')] ?: 1024L).toInt()
            dest.parentFile?.mkdirs()
            dest.writeBytes(ByteArray(size))
            onProgress(size.toLong(), size.toLong())
        }
    }

    private fun sizesOf(bundle: DownloadBundle) =
        bundle.files.associate { it.fileName to it.sizeBytes }

    @Test
    fun `successful download writes every final file and ends at full progress`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        val progress = mutableListOf<Float>()
        ModelDownloader.download(bundle, dir, FakeDownloader(sizesOf(bundle))) { progress.add(it) }

        assertTrue(ModelDownloader.isReady(bundle, dir))
        bundle.files.forEach { assertTrue("${it.fileName} missing", File(dir, it.fileName).exists()) }
        assertEquals(1f, progress.last(), 0.0001f)
    }

    @Test
    fun `progress is weighted by size, not by file count`() = runBlocking {
        // Three files, but the first is 90% of the bytes. Counting files
        // equally would report a third done when nine tenths of the download
        // had arrived - or worse, leap to two thirds on two tiny files.
        val dir = tempFolder.newFolder("models")
        val progress = mutableListOf<Float>()
        ModelDownloader.download(bundle, dir, FakeDownloader(sizesOf(bundle))) { progress.add(it) }

        assertEquals("after the big file", 0.9f, progress.first(), 0.0001f)
        assertEquals(listOf(0.9f, 0.95f, 1f, 1f), progress.map { it })
    }

    @Test
    fun `download leaves no part files behind`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        ModelDownloader.download(bundle, dir, FakeDownloader(sizesOf(bundle))) {}
        val leftovers = dir.listFiles()!!.filter { it.name.endsWith(ModelDownloader.PART_SUFFIX) }
        assertTrue("expected no .part files, found $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `a failure part way through finalises nothing`() {
        // The whole point of the .part dance: a bundle whose first file arrived
        // and whose second did not must not read as ready, or the app will load
        // half a model and fail somewhere far less obvious.
        val dir = tempFolder.newFolder("models")
        assertThrows(IOException::class.java) {
            runBlocking {
                ModelDownloader.download(bundle, dir, FakeDownloader(sizesOf(bundle), failOn = "other")) {}
            }
        }
        assertFalse(ModelDownloader.isReady(bundle, dir))
        bundle.files.forEach { assertFalse(File(dir, it.fileName).exists()) }
    }

    @Test
    fun `cleanupPartials removes only part files`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        File(dir, "big.bin" + ModelDownloader.PART_SUFFIX).writeBytes(ByteArray(8))
        File(dir, "keep.me").writeBytes(ByteArray(8))
        ModelDownloader.cleanupPartials(bundle, dir)
        assertFalse(File(dir, "big.bin" + ModelDownloader.PART_SUFFIX).exists())
        assertTrue(File(dir, "keep.me").exists())
    }

    @Test
    fun `deleteFiles removes every file of the bundle`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        ModelDownloader.download(bundle, dir, FakeDownloader(sizesOf(bundle))) {}
        assertTrue(ModelDownloader.isReady(bundle, dir))

        ModelDownloader.deleteFiles(bundle, dir)
        assertFalse(ModelDownloader.isReady(bundle, dir))
        bundle.files.forEach { assertFalse(File(dir, it.fileName).exists()) }
    }

    @Test
    fun `a one-file bundle downloads that file and nothing else`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        val downloader = FakeDownloader(sizesOf(single))
        val progress = mutableListOf<Float>()
        ModelDownloader.download(single, dir, downloader) { progress.add(it) }

        assertEquals(listOf(single.files.first().url), downloader.urls)
        assertEquals(listOf("only.gguf"), dir.list()!!.toList())
        assertEquals(1f, progress.last(), 0.0001f)
    }

    @Test
    fun `deleting one bundle does not disturb another beside it`() = runBlocking {
        // Every bundle shares one directory, and a user can hold several.
        val dir = tempFolder.newFolder("models")
        ModelDownloader.download(single, dir, FakeDownloader(sizesOf(single))) {}
        ModelDownloader.download(bundle, dir, FakeDownloader(sizesOf(bundle))) {}

        ModelDownloader.deleteFiles(single, dir)

        assertFalse(ModelDownloader.isReady(single, dir))
        assertTrue(ModelDownloader.isReady(bundle, dir))
    }
}
