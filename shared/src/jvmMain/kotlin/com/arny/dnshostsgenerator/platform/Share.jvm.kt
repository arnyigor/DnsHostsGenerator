package com.arny.dnshostsgenerator.platform

import com.arny.dnshostsgenerator.logging.AppLogger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** На desktop «поделиться» — это копирование в системный буфер обмена. */
actual fun shareText(subject: String, text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    true
}.getOrElse { error ->
    AppLogger.e("Share (clipboard) failed", error)
    false
}
