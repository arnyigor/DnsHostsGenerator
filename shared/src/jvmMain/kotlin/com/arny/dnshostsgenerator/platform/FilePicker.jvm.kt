package com.arny.dnshostsgenerator.platform

import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

actual suspend fun pickArchive(): PickedArchive = withContext(Dispatchers.IO) {
    if (GraphicsEnvironment.isHeadless()) {
        return@withContext PickedArchive(message = "Выбор файла недоступен без графической среды")
    }
    var chosen: File? = null
    runCatching {
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser().apply {
                dialogTitle = "Выберите zip со списком доменов"
                fileFilter = FileNameExtensionFilter("Архив списка (*.zip)", "zip")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chosen = chooser.selectedFile
            }
        }
    }.onFailure { error ->
        AppLogger.e("File chooser failed", error)
        return@withContext PickedArchive(message = "Не удалось открыть выбор файла: ${error.message}")
    }

    val file = chosen ?: return@withContext PickedArchive(message = "Файл не выбран")
    runCatching { unzipTextEntries(file.readBytes()) }
        .map { entries ->
            if (entries.isEmpty()) {
                PickedArchive(message = "В архиве нет текстовых файлов (${file.name})")
            } else {
                PickedArchive(entries = entries)
            }
        }
        .getOrElse { error ->
            AppLogger.e("Archive read failed: ${file.name}", error)
            PickedArchive(message = "Не удалось прочитать архив: ${error.message}")
        }
}
