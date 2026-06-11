package com.github.mwiest.voclet.data.ai.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ModelRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val model = AiModel.forTier(ModelTier.LOW)

    /** Writes fixed bytes to the destination, reporting a single progress tick. */
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

    private fun repo(downloader: FileDownloader) =
        ModelRepository(tempFolder.newFolder("models"), downloader)

    @Test
    fun `fresh repository reports NotDownloaded for all models`() {
        val repo = repo(FakeDownloader())
        AiModel.ALL.forEach { m ->
            assertEquals(ModelStatus.NotDownloaded, repo.statuses.value[m.id])
        }
        assertNull(repo.activeModel())
    }

    @Test
    fun `successful download writes both files and marks Ready`() = runBlocking {
        val repo = repo(FakeDownloader())
        repo.startDownload(model)
        waitForTerminalStatus(repo, model.id)

        assertEquals(ModelStatus.Ready, repo.statuses.value[model.id])
        assertTrue(repo.isReady(model))
        assertTrue(repo.ggufFile(model).exists())
        assertTrue(repo.mmprojFile(model).exists())
        assertEquals(model, repo.activeModel())
    }

    @Test
    fun `download leaves no part files behind`() = runBlocking {
        val repo = repo(FakeDownloader())
        repo.startDownload(model)
        waitForTerminalStatus(repo, model.id)

        val leftovers = repo.ggufFile(model).parentFile!!.listFiles()!!.filter { it.name.endsWith(".part") }
        assertTrue("expected no .part files, found $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `failed download reports Failed and writes nothing`() = runBlocking {
        val repo = repo(FakeDownloader(failOn = "gguf"))
        repo.startDownload(model)
        waitForTerminalStatus(repo, model.id)

        assertTrue(repo.statuses.value[model.id] is ModelStatus.Failed)
        assertFalse(repo.isReady(model))
        assertFalse(repo.ggufFile(model).exists())
    }

    @Test
    fun `delete removes files and resets status`() = runBlocking {
        val repo = repo(FakeDownloader())
        repo.startDownload(model)
        waitForTerminalStatus(repo, model.id)
        assertTrue(repo.isReady(model))

        repo.delete(model)
        assertEquals(ModelStatus.NotDownloaded, repo.statuses.value[model.id])
        assertFalse(repo.isReady(model))
        assertFalse(repo.ggufFile(model).exists())
        assertFalse(repo.mmprojFile(model).exists())
    }

    /** Spin until the model reaches a terminal (Ready/Failed/NotDownloaded) status. */
    private fun waitForTerminalStatus(repo: ModelRepository, id: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            when (repo.statuses.value[id]) {
                is ModelStatus.Ready, is ModelStatus.Failed, ModelStatus.NotDownloaded -> return
                else -> Thread.sleep(10)
            }
        }
        throw AssertionError("Download did not reach a terminal status in time")
    }
}
