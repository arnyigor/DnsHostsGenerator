package com.arny.dnshostsgenerator.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareTextTest {

    @Test
    fun desktopSharePutsRulesIntoClipboard() {
        val rules = ">chatgpt.com 1.2.3.4\nchatgpt.com"

        // В headless-окружении (CI) системного буфера может не быть — тогда проверка пропускается.
        // В headless-окружении буфер может быть недоступен или занят другим процессом —
        // тогда проверка копирования не имеет смысла.
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
        val canWrite = runCatching {
            clipboard?.setContents(StringSelection("probe"), null)
            true
        }.getOrDefault(false)
        if (clipboard == null || !canWrite) {
            println("ShareTextTest пропущен: системный буфер обмена недоступен")
            return
        }
        val sent = shareText("Правила DNS-VPN", rules)

        assertTrue(sent, "shareText должен вернуть true")
        assertEquals(rules, clipboard.getData(DataFlavor.stringFlavor))
    }
}
