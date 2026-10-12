package com.arny.dnshostsgenerator.platform

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual fun zipTextEntries(entries: List<ZipTextEntry>): ByteArray {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        entries.forEach { entry ->
            zip.putNextEntry(ZipEntry(entry.name))
            zip.write(entry.content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return buffer.toByteArray()
}

actual fun unzipTextEntries(
    bytes: ByteArray,
    maxEntries: Int,
    maxTotalBytes: Int,
): List<ZipTextEntry> {
    val result = mutableListOf<ZipTextEntry>()
    var totalBytes = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null && result.size < maxEntries) {
            if (!entry.isDirectory && isTextEntryName(entry.name)) {
                val data = zip.readBytes()
                if (totalBytes + data.size <= maxTotalBytes) {
                    totalBytes += data.size
                    result += ZipTextEntry(entry.name, data.toString(Charsets.UTF_8))
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}
