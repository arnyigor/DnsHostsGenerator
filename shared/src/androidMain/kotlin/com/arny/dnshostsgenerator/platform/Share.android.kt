package com.arny.dnshostsgenerator.platform

import android.content.Intent
import com.arny.dnshostsgenerator.logging.AppLogger

actual fun shareText(subject: String, text: String): Boolean = runCatching {
    val context = androidAppContext()
        ?: return@runCatching false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrElse { error ->
    AppLogger.e("Share failed", error)
    false
}
