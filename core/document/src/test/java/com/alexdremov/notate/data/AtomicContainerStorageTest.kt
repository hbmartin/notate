package com.alexdremov.notate.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alexdremov.notate.data.io.AtomicContainerStorage
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AtomicContainerStorageTest {
    private lateinit var directory: File
    private lateinit var storage: AtomicContainerStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.cacheDir, "atomic-container-test").apply { deleteRecursively(); mkdirs() }
        storage = AtomicContainerStorage(context)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun interruptedCommitRestoresValidBackupOverCorruptTarget() {
        val target = File(directory, "note.notate").apply { writeText("partial replacement") }
        val backup = File(directory, "note.notate.bak")
        writeZip(backup, "previous")

        storage.recoverInterruptedCommit(target.absolutePath)

        assertThat(readEntry(target)).isEqualTo("previous")
        assertThat(backup.exists()).isFalse()
    }

    @Test
    fun completedCommitKeepsValidTargetAndRemovesStaleBackup() {
        val target = File(directory, "note.notate")
        val backup = File(directory, "note.notate.bak")
        writeZip(target, "replacement")
        writeZip(backup, "previous")

        storage.recoverInterruptedCommit(target.absolutePath)

        assertThat(readEntry(target)).isEqualTo("replacement")
        assertThat(backup.exists()).isFalse()
    }

    private fun writeZip(
        file: File,
        value: String,
    ) {
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("manifest.bin"))
            output.write(value.toByteArray())
            output.closeEntry()
        }
    }

    private fun readEntry(file: File): String =
        ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("manifest.bin")).bufferedReader().readText() }
}
