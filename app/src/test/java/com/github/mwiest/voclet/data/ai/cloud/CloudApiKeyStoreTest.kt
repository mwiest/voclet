package com.github.mwiest.voclet.data.ai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CloudApiKeyStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun keyFile() = File(tempFolder.root, CloudApiKeyStore.FILE_NAME)

    @Test
    fun startsEmptyWithoutAFile() {
        assertEquals("", CloudApiKeyStore(keyFile()).key.value)
    }

    @Test
    fun aSetKeySurvivesARestart() {
        CloudApiKeyStore(keyFile()).set("sk-secret")

        assertEquals("sk-secret", CloudApiKeyStore(keyFile()).key.value)
    }

    @Test
    fun setUpdatesTheFlow() {
        val store = CloudApiKeyStore(keyFile())

        store.set("sk-secret")

        assertEquals("sk-secret", store.key.value)
    }

    @Test
    fun clearingDeletesTheFile() {
        val store = CloudApiKeyStore(keyFile())
        store.set("sk-secret")

        store.set("")

        assertEquals("", store.key.value)
        assertFalse(keyFile().exists())
    }

    @Test
    fun overwritingReplacesTheKey() {
        val store = CloudApiKeyStore(keyFile())
        store.set("sk-old")

        store.set("sk-new")

        assertEquals("sk-new", CloudApiKeyStore(keyFile()).key.value)
    }
}
