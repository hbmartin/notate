package com.alexdremov.notate.data.io

import android.content.Context
import android.net.Uri
import com.alexdremov.notate.util.Logger
import com.alexdremov.notate.util.ZipUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Handles atomic packing and unpacking of Container files (ZIPs).
 * Ensures that writes are either fully successful or don't happen at all.
 */
class AtomicContainerStorage(
    private val context: Context,
) {
    /**
     * Unpacks a container stream to a target directory.
     * @throws IOException if unpacking fails.
     */
    fun unpack(
        inputStream: InputStream,
        targetDir: File,
    ) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        ZipUtils.unzip(inputStream, targetDir)
    }

    /**
     * Atomically packs the source directory into the target file.
     *
     * Strategy:
     * 1. Zip sourceDir to a temporary file.
     * 2. Verify temporary file integrity.
     * 3. Atomically replace target file with temporary file.
     *
     * @param sourceDir The directory containing unzipped project files.
     * @param targetPath Absolute path or content URI of the destination.
     */
    fun pack(
        sourceDir: File,
        targetPath: String,
    ) {
        val tempZip =
            if (targetPath.startsWith("content://")) {
                File(context.cacheDir, "save_${UUID.randomUUID()}.zip.tmp")
            } else {
                val target = File(targetPath)
                File(target.parentFile ?: context.cacheDir, ".${target.name}.${UUID.randomUUID()}.tmp")
            }
        try {
            // 1. Create ZIP (Incremental if possible)
            var usedIncremental = false
            if (!targetPath.startsWith("content://")) {
                val targetFile = File(targetPath)
                if (targetFile.exists() && targetFile.length() > 0) {
                    try {
                        ZipUtils.incrementalZip(sourceDir, targetFile, tempZip)
                        usedIncremental = true
                        Logger.d("AtomicContainerStorage", "Used incremental zip for local file")
                    } catch (e: Exception) {
                        Logger.e("AtomicContainerStorage", "Incremental zip failed, falling back to full zip", e)
                        // If incremental fails partially, we must ensure tempZip is clean/reset?
                        // incrementalZip uses FileOutputStream(tempZip), which truncates.
                        // But if it failed mid-way, it might be corrupt.
                        // ZipUtils usually throws. We should retry full zip.
                        if (tempZip.exists()) tempZip.delete()
                    }
                }
            }

            if (!usedIncremental) {
                if (tempZip.exists()) tempZip.delete() // Ensure clean start
                ZipUtils.zip(sourceDir, tempZip)
            }

            FileOutputStream(tempZip, true).use { it.fd.sync() }

            // 2. Verify
            verifyZip(tempZip)

            // 3. Commit
            commit(tempZip, targetPath)
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
            }
        }
    }

    private fun verifyZip(file: File) {
        if (!file.exists() || file.length() < 22) { // Empty zip is 22 bytes
            throw IOException("Generated ZIP is invalid: Too small or missing")
        }
        // Basic header check
        file.inputStream().use {
            val header = ByteArray(4)
            if (it.read(header) < 4) throw IOException("Generated ZIP is invalid: header missing")
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                throw IOException("Generated ZIP is invalid: Bad signature")
            }
        }

        // Read every entry so ZipFile validates entry data and CRCs, not just the
        // central directory.
        var hasEntries = false
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    hasEntries = true
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { input ->
                            while (input.read(buffer) >= 0) {
                                // Reading to EOF validates the entry checksum.
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Generated ZIP is corrupted: ${e.message}", e)
        }

        if (!hasEntries) {
            throw IOException("Generated ZIP is empty (no entries)")
        }
    }

    private fun commit(
        sourceFile: File,
        targetPath: String,
    ) {
        if (targetPath.startsWith("content://")) {
            commitToSaf(sourceFile, Uri.parse(targetPath))
        } else {
            commitToLocalFile(sourceFile, File(targetPath))
        }
    }

    private fun commitToLocalFile(
        source: File,
        target: File,
    ) {
        val backup = File(target.parent, "${target.name}.bak")
        recoverLocalBackup(target, backup)
        target.parentFile?.mkdirs()

        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            return
        } catch (_: AtomicMoveNotSupportedException) {
            Logger.w("AtomicContainerStorage", "Atomic replace unsupported for ${target.absolutePath}; using recoverable backup")
        } catch (error: IOException) {
            Logger.w("AtomicContainerStorage", "Atomic replace failed for ${target.absolutePath}: ${error.message}")
        }

        try {
            if (target.exists()) {
                Files.move(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: IOException) {
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
            }
            verifyZip(target)
            if (backup.exists() && !backup.delete()) {
                Logger.w("AtomicContainerStorage", "Unable to remove completed backup ${backup.absolutePath}")
            }
        } catch (error: Exception) {
            restoreLocalBackup(target, backup)
            throw IOException("Failed to commit file; restored the previous version.", error)
        }
    }

    private fun commitToSaf(
        source: File,
        targetUri: Uri,
    ) {
        // SAF cannot guarantee rename-over-target. Keep a durable, app-private recovery
        // copy until the provider confirms and fsyncs the replacement.
        val backup = safBackupFile(targetUri)
        try {
            recoverInterruptedCommit(targetUri.toString())
            createSafBackup(targetUri, backup)
            context.contentResolver.openFileDescriptor(targetUri, "wt")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                    output.fd.sync()
                }
            } ?: throw IOException("Failed to open SAF stream")
            verifySafTarget(targetUri)
            if (backup.exists() && !backup.delete()) {
                Logger.w("AtomicContainerStorage", "Unable to remove completed SAF backup ${backup.absolutePath}")
            }
        } catch (e: Exception) {
            runCatching { restoreSafBackup(targetUri, backup) }
                .onFailure { Logger.e("AtomicContainerStorage", "Failed to restore SAF backup for $targetUri", it) }
            throw IOException("Failed to commit to SAF URI", e)
        }
    }

    /** Repairs an interrupted local or SAF commit before callers inspect or open it. */
    fun recoverInterruptedCommit(targetPath: String) {
        if (targetPath.startsWith("content://")) {
            val uri = Uri.parse(targetPath)
            val backup = safBackupFile(uri)
            if (!backup.exists()) return
            if (runCatching { verifySafTarget(uri) }.isSuccess) {
                backup.delete()
            } else {
                restoreSafBackup(uri, backup)
            }
        } else {
            val target = File(targetPath)
            recoverLocalBackup(target, File(target.parent, "${target.name}.bak"))
        }
    }

    private fun recoverLocalBackup(
        target: File,
        backup: File,
    ) {
        if (!backup.exists()) return
        if (target.exists() && runCatching { verifyZip(target) }.isSuccess) {
            backup.delete()
            return
        }
        restoreLocalBackup(target, backup)
    }

    private fun restoreLocalBackup(
        target: File,
        backup: File,
    ) {
        if (!backup.exists()) return
        if (target.exists() && !target.delete()) {
            throw IOException("Unable to remove interrupted target ${target.absolutePath}")
        }
        try {
            Files.move(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: IOException) {
            backup.inputStream().use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (!backup.delete()) Logger.w("AtomicContainerStorage", "Unable to delete restored backup ${backup.absolutePath}")
        }
        verifyZip(target)
    }

    private fun safBackupFile(uri: Uri): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "container-recovery/$name.bak").also { it.parentFile?.mkdirs() }
    }

    private fun createSafBackup(
        uri: Uri,
        backup: File,
    ) {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            backup.delete()
            return
        }
        val temporary = File(backup.parentFile, "${backup.name}.tmp")
        try {
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            }
            if (temporary.length() == 0L) {
                temporary.delete()
                backup.delete()
                return
            }
            try {
                Files.move(
                    temporary.toPath(),
                    backup.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun restoreSafBackup(
        uri: Uri,
        backup: File,
    ) {
        if (!backup.exists()) return
        context.contentResolver.openFileDescriptor(uri, "wt")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                backup.inputStream().use { input -> input.copyTo(output) }
                output.fd.sync()
            }
        } ?: throw IOException("Unable to reopen SAF target for recovery")
        verifySafTarget(uri)
        if (!backup.delete()) Logger.w("AtomicContainerStorage", "Unable to delete restored SAF backup ${backup.absolutePath}")
    }

    private fun verifySafTarget(uri: Uri) {
        val check = File(context.cacheDir, "verify_${UUID.randomUUID()}.zip.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(check).use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to read SAF target for verification")
            verifyZip(check)
        } finally {
            check.delete()
        }
    }
}
