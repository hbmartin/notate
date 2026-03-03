package com.alexdremov.notate.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object UriUtils {
    fun getFileName(
        context: Context,
        uri: Uri,
    ): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return cursor.getString(index)
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
