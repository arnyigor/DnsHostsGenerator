package com.arny.dnshostsgenerator.platform

/** Результат выбора архива: либо текстовые файлы, либо причина, которую стоит показать. */
data class PickedArchive(
    val entries: List<ZipTextEntry> = emptyList(),
    val message: String? = null,
) {
    val hasEntries: Boolean get() = entries.isNotEmpty()
}

/**
 * Открывает системный выбор файла и читает текстовые файлы из zip.
 * Формат zip — потому что именно так списки распространяют в теме (паки aihosts.conf/txt,
 * adguard.txt, nextdns.txt). RAR не поддержан: java.util.zip его не читает.
 */
expect suspend fun pickArchive(): PickedArchive
