package com.arny.dnshostsgenerator.platform

import android.content.Context
import android.os.Environment
import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private var appContext: Context? = null

fun setAndroidAppContext(context: Context) {
    appContext = context.applicationContext
    AppLogger.d("Android app context set: package=${context.packageName}")
}

actual suspend fun saveTextFile(
    fileName: String,
    content: String,
): SaveFileResult = withContext(Dispatchers.IO) {
    val context = appContext
        ?: return@withContext SaveFileResult(
            success = false,
            message = "Android context is not initialized",
        ).also {
            AppLogger.e("Save file failed: Android context is not initialized")
        }

    AppLogger.d("Save file requested: fileName=$fileName, contentLength=${content.length}")
    runCatching {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        AppLogger.d("Save file finished: path=${file.absolutePath}, bytes=${file.length()}")
        SaveFileResult(success = true, message = "Сохранено: ${file.absolutePath}")
    }.getOrElse { error ->
        AppLogger.e("Save file failed: fileName=$fileName", error)
        SaveFileResult(success = false, message = "Ошибка сохранения: ${error.message}")
    }
}
