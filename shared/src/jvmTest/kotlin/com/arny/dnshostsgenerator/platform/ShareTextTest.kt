package com.arny.dnshostsgenerator.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareTextTest {

    @Test
    fun desktopSharePutsRulesIntoClipboard() {
        val rules = ">chatgpt.com 1.2.3.4\nchatgpt.com"

        val sent = shareText("Правила DNS-VPN", rules)

        assertTrue(sent, "shareText должен вернуть true")
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        assertEquals(rules, clipboard.getData(DataFlavor.stringFlavor))
    }
}
