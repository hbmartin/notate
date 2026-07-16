package com.alexdremov.notate

import android.content.Context
import android.content.Intent
import com.alexdremov.notate.navigation.CanvasDestination
import com.alexdremov.notate.navigation.NotateNavigator
import com.alexdremov.notate.navigation.NotePickerDestination
import com.alexdremov.notate.navigation.writeCanvasDestination
import com.alexdremov.notate.navigation.writeNotePickerDestination

class AppNotateNavigator : NotateNavigator {
    override fun canvasIntent(
        context: Context,
        destination: CanvasDestination,
    ): Intent = Intent(context, CanvasActivity::class.java).writeCanvasDestination(destination)

    override fun notePickerIntent(
        context: Context,
        destination: NotePickerDestination,
    ): Intent = Intent(context, NotePickerActivity::class.java).writeNotePickerDestination(destination)
}
