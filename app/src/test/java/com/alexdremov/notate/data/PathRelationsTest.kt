package com.alexdremov.notate.data

import android.provider.DocumentsContract
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PathRelationsTest {
    @Test
    fun localContainmentRequiresAPathBoundary() {
        val root = File("/tmp/Notes").absolutePath

        assertThat(PathRelations.contains(root, "$root/page.notate")).isTrue()
        assertThat(PathRelations.contains(root, "/tmp/NotesArchive/page.notate")).isFalse()
    }

    @Test
    fun documentContainmentRequiresAPathBoundary() {
        val root = DocumentsContract.buildTreeDocumentUri("test.provider", "primary:Notes")
        val child = DocumentsContract.buildDocumentUriUsingTree(root, "primary:Notes/page.notate")
        val sibling = DocumentsContract.buildDocumentUriUsingTree(root, "primary:NotesArchive/page.notate")

        assertThat(PathRelations.contains(root.toString(), child.toString())).isTrue()
        assertThat(PathRelations.contains(root.toString(), sibling.toString())).isFalse()
    }

    @Test
    fun nestedDocumentRootDoesNotExpandToItsWholeGrantedTree() {
        val tree = DocumentsContract.buildTreeDocumentUri("test.provider", "primary:Notes")
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Notes/Folder")
        val child = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Notes/Folder/page.notate")
        val otherFolder = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Notes/Other/page.notate")

        assertThat(PathRelations.contains(folder.toString(), child.toString())).isTrue()
        assertThat(PathRelations.contains(folder.toString(), otherFolder.toString())).isFalse()
    }
}
