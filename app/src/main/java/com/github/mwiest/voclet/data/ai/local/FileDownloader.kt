package com.github.mwiest.voclet.data.ai.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single file with progress reporting. Abstracted behind an
 * interface so [ModelRepository] can be unit-tested with a fake.
 */
interface FileDownloader {
    /**
     * Downloads [url] into [dest], invoking [onProgress] with
     * (bytesDownloaded, totalBytes); totalBytes is -1 when the server does not
     * report a content length. Honours coroutine cancellation. Throws on any
     * network / IO failure.
     */
    suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit)
}

/** [FileDownloader] backed by [HttpURLConnection] — no extra dependencies. */
class HttpFileDownloader : FileDownloader {

    override suspend fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code for $url")
            }
            val total = connection.contentLengthLong
            dest.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive() // cancellation check
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
