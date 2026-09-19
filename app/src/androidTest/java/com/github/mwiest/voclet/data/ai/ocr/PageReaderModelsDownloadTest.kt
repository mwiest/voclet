package com.github.mwiest.voclet.data.ai.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.mwiest.voclet.data.ai.local.HttpFileDownloader
import com.github.mwiest.voclet.data.ai.local.ModelDownloader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks that the hosted page-reader models are actually reachable, and that
 * the sizes the catalog declares are the sizes GitHub serves.
 *
 * Worth a network test because none of it can be checked on the JVM: the
 * catalog's URLs and byte counts were typed in by hand from a release that
 * lives outside this repo, and every way of getting them wrong is silent. A
 * wrong size only skews the progress bar; a wrong URL fails the download at the
 * moment the user asks for it, which is the worst place to find out.
 *
 * Skips when the device is offline rather than failing, so it is safe in a
 * suite that runs anywhere.
 *
 * ```
 * ./gradlew.bat :app:installDebug :app:installDebugAndroidTest
 * adb shell am instrument -w \
 *   -e class com.github.mwiest.voclet.data.ai.ocr.PageReaderModelsDownloadTest \
 *   com.github.mwiest.voclet.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PageReaderModelsDownloadTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scratch = File(context.cacheDir, "page-reader-download-test")

    @After
    fun cleanUp() {
        scratch.deleteRecursively()
    }

    @Test
    fun everyFileIsServedAtTheDeclaredSize() {
        PageReaderModels.files.forEach { file ->
            val connection = try {
                (URL(file.url).openConnection() as HttpURLConnection).apply {
                    // The app's downloader is a GET; HEAD is enough to confirm
                    // the URL and the length without pulling 12.7 MB per run.
                    requestMethod = "HEAD"
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    connect()
                }
            } catch (offline: Exception) {
                assumeNoException("no network to reach ${file.url}", offline)
                return
            }
            try {
                assertEquals("${file.fileName} is not served", 200, connection.responseCode)
                assertEquals(
                    "${file.fileName} is ${connection.contentLengthLong} bytes, " +
                        "the catalog says ${file.sizeBytes}",
                    file.sizeBytes,
                    connection.contentLengthLong,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    @Test
    fun theDownloaderFollowsGitHubsRedirectAndReportsProgress() {
        // The two parameter files, 43 KB, exercised through the real
        // downloader: GitHub answers with a 302 to a signed URL, and
        // HttpURLConnection only follows that because both hops are HTTPS.
        val small = object : com.github.mwiest.voclet.data.ai.local.DownloadBundle {
            override val id = "page-reader-params-only"
            override val displayName = "PP-OCRv5 (params)"
            override val files = listOf(
                PageReaderModels.detectorParam,
                PageReaderModels.recognizerParam,
            )
        }

        val progress = mutableListOf<Float>()
        try {
            runBlocking {
                ModelDownloader.download(small, scratch, HttpFileDownloader()) { progress.add(it) }
            }
        } catch (offline: Exception) {
            assumeNoException("no network", offline)
            return
        }

        assertTrue(ModelDownloader.isReady(small, scratch))
        small.files.forEach { file ->
            assertEquals(
                "${file.fileName} arrived at the wrong size",
                file.sizeBytes,
                File(scratch, file.fileName).length(),
            )
        }
        // A reported length is what the progress bar needs; without it the
        // downloader never calls back and the bar sits indeterminate.
        assertTrue("no progress was reported", progress.isNotEmpty())
        assertEquals(1f, progress.last(), 0.0001f)
    }
}
