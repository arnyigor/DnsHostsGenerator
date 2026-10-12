package com.arny.dnshostsgenerator.platform

import android.content.ContentResolver
import android.net.Uri
import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Мост между shared-кодом и системным выбором файла на Android: launcher регистрируется
 * в MainActivity (Compose), сюда приходят открыватель диалога и разрешение ContentResolver.
 */
object AndroidArchivePicker {
    /** Показать системный диалог выбора файла (регистрируется в MainActivity). */
    @Volatile
    var openFileDialog: (() -> Unit)? = null

    /** Доставка выбранного uri из MainActivity в ожидающий вызов [pickArchive]. */
    @Volatile
    var deliverPickedUri: ((Uri?) -> Unit)? = null

    @Volatile
    var contentResolver: ContentResolver? = null
}

actual suspend fun pickArchive(): PickedArchive {
    val open = AndroidArchivePicker.openFileDialog
    if (open == null) {
        return PickedArchive(message = "Выбор файла недоступен: диалог не зарегистрирован")
    }
    val uri = suspendCancellableCoroutine { continuation ->
        AndroidArchivePicker.deliverPickedUri = { picked ->
            AndroidArchivePicker.deliverPickedUri = null
            if (continuation.isActive) continuation.resume(picked)
        }
        open()
    }
    if (uri == null) return PickedArchive(message = "Файл не выбран")

    val resolver = AndroidArchivePicker.contentResolver
        ?: return PickedArchive(message = "Android context is not initialized")

    return withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        }.map { bytes ->
            if (bytes == null) {
                PickedArchive(message = "Не удалось открыть файл")
            } else {
                val entries = unzipTextEntries(bytes)
                if (entries.isEmpty()) {
                    PickedArchive(message = "В архиве нет текстовых файлов")
                } else {
                    PickedArchive(entries = entries)
                }
            }
        }.getOrElse { error ->
            AppLogger.e("Archive read failed: $uri", error)
            PickedArchive(message = "Не удалось прочитать архив: ${error.message}")
        }
    }
}
