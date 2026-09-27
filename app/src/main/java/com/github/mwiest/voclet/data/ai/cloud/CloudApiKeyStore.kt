package com.github.mwiest.voclet.data.ai.cloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The cloud API key, kept in `noBackupFilesDir` rather than Room: Android backs
 * up the whole database, and the key must never leave the device that way.
 */
class CloudApiKeyStore(private val file: File) {

    private val _key = MutableStateFlow(read(file))
    val key: StateFlow<String> = _key.asStateFlow()

    @Synchronized
    fun set(value: String) {
        write(file, value)
        _key.value = value
    }

    companion object {
        const val FILE_NAME = "cloud_api_key"

        fun read(file: File): String = if (file.exists()) file.readText() else ""

        fun write(file: File, value: String) {
            if (value.isEmpty()) {
                file.delete()
                return
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(value)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
