package com.arny.dnshostsgenerator.platform

/** Текстовый файл внутри архива: имя записи и её содержимое. */
data class ZipTextEntry(
    val name: String,
    val content: String,
)

private val TEXT_EXTENSIONS = setOf("txt", "conf", "list", "md", "csv", "json", "hosts")

/**
 * Подходит ли запись архива для импорта списка доменов. Файлы без расширения тоже берём:
 * в паках с 4PDA hosts-списки иногда лежат именно так.
 */
fun isTextEntryName(name: String): Boolean {
    val file = name.substringAfterLast('/')
    if (file.isBlank() || file.startsWith(".")) return false
    val extension = file.substringAfterLast('.', "")
    return extension.isEmpty() || extension.lowercase() in TEXT_EXTENSIONS
}

/** Собирает текстовые файлы в zip-архив (формат паков сообщества: 2–4 файла). */
expect fun zipTextEntries(entries: List<ZipTextEntry>): ByteArray

/**
 * Читает текстовые файлы из zip. Каталоги, бинарные записи и записи вне лимитов
 * пропускаются — наружу отдаются только те, что осмысленно скормить парсеру.
 */
expect fun unzipTextEntries(
    bytes: ByteArray,
    maxEntries: Int = 20,
    maxTotalBytes: Int = 8 * 1024 * 1024,
): List<ZipTextEntry>
