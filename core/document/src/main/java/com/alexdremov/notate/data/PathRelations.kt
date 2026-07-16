package com.alexdremov.notate.data

import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Boundary-aware path containment for local paths and Storage Access Framework trees. */
object PathRelations {
    fun contains(
        root: String,
        candidate: String,
    ): Boolean {
        val rootIsContent = root.startsWith("content://")
        if (rootIsContent != candidate.startsWith("content://")) return false
        return if (rootIsContent) containsDocument(root, candidate) else containsLocal(root, candidate)
    }

    private fun containsLocal(
        root: String,
        candidate: String,
    ): Boolean {
        val normalizedRoot = runCatching { File(root).canonicalPath }.getOrElse { File(root).absolutePath }.trimEnd(File.separatorChar)
        val normalizedCandidate = runCatching { File(candidate).canonicalPath }.getOrElse { File(candidate).absolutePath }
        return normalizedCandidate == normalizedRoot || normalizedCandidate.startsWith(normalizedRoot + File.separator)
    }

    private fun containsDocument(
        root: String,
        candidate: String,
    ): Boolean =
        runCatching {
            val rootUri = Uri.parse(root)
            val candidateUri = Uri.parse(candidate)
            if (rootUri.authority != candidateUri.authority) return@runCatching false
            // A document URI built from a tree is also reported as a tree URI.
            // Prefer its concrete document ID so folder operations do not match
            // every item under the granted tree.
            val rootId =
                runCatching { DocumentsContract.getDocumentId(rootUri) }
                    .getOrElse { DocumentsContract.getTreeDocumentId(rootUri) }
            val candidateId =
                runCatching { DocumentsContract.getDocumentId(candidateUri) }
                    .getOrElse { DocumentsContract.getTreeDocumentId(candidateUri) }
            candidateId == rootId || candidateId.startsWith(rootId.trimEnd('/') + "/")
        }.getOrDefault(false)
}
