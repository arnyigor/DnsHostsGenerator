package com.arny.dnshostsgenerator.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

actual suspend fun saveTextFile(
    fileName: String,
    content: String,
): SaveFileResult = withContext(Dispatchers.IO) {
    runCatching {
        val path = Path.of(fileName).toAbsolutePath()
        Files.writeString(path, content, StandardCharsets.UTF_8)
        SaveFileResult(success = true, message = "Сохранено: $path")
    }.getOrElse { error ->
        SaveFileResult(success = false, message = "Ошибка сохранения: ${error.message}")
    }
}
