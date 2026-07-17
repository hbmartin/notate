package com.alexdremov.notate.data

import android.content.Context
import android.content.Intent

/**
 * Process-local contract used by device surfaces such as widgets. The broadcast is package
 * restricted and carries no notebook contents.
 */
object NotebookChangeNotifier {
    const val ACTION_NOTEBOOK_CHANGED = "com.alexdremov.notate.action.NOTEBOOK_CHANGED"

    fun notify(
        context: Context,
        path: String? = null,
    ) {
        context.sendBroadcast(
            Intent(ACTION_NOTEBOOK_CHANGED)
                .setPackage(context.packageName)
                .putExtra("path", path),
        )
    }
}
