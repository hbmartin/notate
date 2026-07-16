package com.alexdremov.notate.ocr

import android.content.Context
import java.io.File

internal class OcrAssetManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    data class PreparedAssets(
        val detector: File,
        val recognizer: File,
        val dictionary: File,
    )

    @Synchronized
    fun prepare(): PreparedAssets {
        val modelDir = OcrModelPackManager.get(appContext).installedDirectory() ?: throw OcrModelsNotInstalledException()
        val models = OcrModelPackCatalog.current.files.associateBy(OcrModelFile::name)
        val detector = verified(modelDir, checkNotNull(models["det_db.nb"]))
        val recognizer = verified(modelDir, checkNotNull(models["rec_crnn.nb"]))
        val dictionary = verified(modelDir, checkNotNull(models["ppocr_keys_v1.txt"]))
        return PreparedAssets(detector, recognizer, dictionary)
    }

    private fun verified(
        modelDir: File,
        model: OcrModelFile,
    ): File {
        val file = File(modelDir, model.name)
        if (!file.isFile || file.length() != model.sizeBytes || OcrModelPackInstaller.sha256(file) != model.sha256) {
            throw OcrModelsNotInstalledException("Text recognition file failed verification: ${model.name}")
        }
        return file
    }
}
