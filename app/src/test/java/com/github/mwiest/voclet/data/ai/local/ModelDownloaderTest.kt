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

class ModelDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val model = AiModel.forTier(ModelTier.LOW)

    private class FakeDownloader(
        private val failOn: String? = null,
        private val content: ByteArray = ByteArray(1024),
    ) : FileDownloader {
        override suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
            if (failOn != null && url.contains(failOn)) throw IOException("boom")
            dest.parentFile?.mkdirs()
            dest.writeBytes(content)
            onProgress(content.size.toLong(), content.size.toLong())
        }
    }

    @Test
    fun `successful download writes both final files and ends at full progress`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        val progress = mutableListOf<Float>()
        ModelDownloader.download(model, dir, FakeDownloader()) { progress.add(it) }

        assertTrue(ModelDownloader.isReady(model, dir))
        assertTrue(File(dir, model.ggufFileName).exists())
        assertTrue(File(dir, model.mmprojFileName).exists())
        assertEquals(1f, progress.last(), 0.0001f)
    }

    @Test
    fun `download leaves no part files behind`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        ModelDownloader.download(model, dir, FakeDownloader()) {}
        val leftovers = dir.listFiles()!!.filter { it.name.endsWith(ModelDownloader.PART_SUFFIX) }
        assertTrue("expected no .part files, found $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `failed download throws and writes no final file`() {
        val dir = tempFolder.newFolder("models")
        assertThrows(IOException::class.java) {
            runBlocking { ModelDownloader.download(model, dir, FakeDownloader(failOn = "gguf")) {} }
        }
        assertFalse(ModelDownloader.isReady(model, dir))
        assertFalse(File(dir, model.ggufFileName).exists())
    }

    @Test
    fun `cleanupPartials removes only part files`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        File(dir, model.ggufFileName + ModelDownloader.PART_SUFFIX).writeBytes(ByteArray(8))
        ModelDownloader.cleanupPartials(model, dir)
        assertFalse(File(dir, model.ggufFileName + ModelDownloader.PART_SUFFIX).exists())
    }

    @Test
    fun `deleteFiles removes both final files`() = runBlocking {
        val dir = tempFolder.newFolder("models")
        ModelDownloader.download(model, dir, FakeDownloader()) {}
        assertTrue(ModelDownloader.isReady(model, dir))

        ModelDownloader.deleteFiles(model, dir)
        assertFalse(ModelDownloader.isReady(model, dir))
        assertFalse(File(dir, model.ggufFileName).exists())
        assertFalse(File(dir, model.mmprojFileName).exists())
    }
}
