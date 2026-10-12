package com.arny.dnshostsgenerator.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

actual val canUpdateSystemHosts: Boolean = true

actual suspend fun updateSystemHosts(content: String): SaveFileResult = withContext(Dispatchers.IO) {
    runCatching {
        val hostsPath = systemHostsPath()
        val parent = hostsPath.parent ?: error("Не удалось определить папку hosts-файла")
        val backupPath = Files.createTempFile(parent, "hosts.dnshostsgenerator.", ".bak")
        val tempPath = Files.createTempFile(parent, "hosts.dnshostsgenerator.", ".tmp")
        val normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n').let { text ->
            if (text.endsWith('\n')) text else "$text\n"
        }

        try {
            if (Files.exists(hostsPath)) {
                Files.copy(hostsPath, backupPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            }
            Files.writeString(tempPath, normalizedContent, StandardCharsets.UTF_8)
            Files.move(tempPath, hostsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            SaveFileResult(success = true, message = "Системный hosts обновлён: $hostsPath. Резервная копия: $backupPath")
        } finally {
            Files.deleteIfExists(tempPath)
        }
    }.getOrElse { error ->
        SaveFileResult(
            success = false,
            message = "Не удалось обновить системный hosts: ${error.message}. Запустите приложение от имени администратора/root.",
        )
    }
}

private fun systemHostsPath(): Path {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    return if ("win" in osName) {
        val systemRoot = System.getenv("SystemRoot")?.takeIf { it.isNotBlank() } ?: "C:\\Windows"
        Path.of(systemRoot, "System32", "drivers", "etc", "hosts")
    } else {
        Path.of("/etc/hosts")
    }
}
