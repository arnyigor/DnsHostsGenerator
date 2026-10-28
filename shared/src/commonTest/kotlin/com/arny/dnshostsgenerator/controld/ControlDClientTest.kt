package com.arny.dnshostsgenerator.controld

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Контракт Control D API, зафиксированный по docs.controld.com (28.10.2026):
 * профили — `body[].PK/name`, ошибки — `error.code/message`, правило — `do=2` (SPOOF),
 * `via` = IP, домены — повторами `hostnames[]`.
 */
class ControlDClientTest {

    @Test
    fun parseProfiles_readsPkAndName() {
        val body = """{"success":true,"body":[
            {"PK":"abc123","name":"Main"},
            {"PK":"def456","profileName":"Second"}
        ]}"""

        val profiles = parseProfiles(body)

        assertEquals(2, profiles.size)
        assertEquals(ControlDProfile(pk = "abc123", name = "Main"), profiles[0])
        assertEquals("def456", profiles[1].pk)
        assertEquals("Second", profiles[1].name)
    }

    @Test
    fun parseProfiles_toleratesGarbage() {
        assertTrue(parseProfiles("<html>error</html>").isEmpty())
        assertTrue(parseProfiles("""{"success":false}""").isEmpty())
    }

    @Test
    fun parseApiError_extractsMessageAndCode() {
        val body = """{"success":false,"error":{"date":"Tue","message":"Invalid session","code":40001}}"""

        assertEquals("Invalid session (код 40001)", parseApiError(body))
        assertEquals(null, parseApiError("""{"success":true}"""))
    }

    @Test
    fun buildRulesForm_createsSpoofRuleWithHostnamesArray() {
        val form = buildRulesForm("103.137.248.179", listOf("chatgpt.com", "api.openai.com"))

        val pairs = (0 until form.size).map { form.name(it) to form.value(it) }
        assertEquals("2", pairs.toMap()["do"], "do=2 — это SPOOF")
        assertEquals("1", pairs.toMap()["status"])
        assertEquals("103.137.248.179", pairs.toMap()["via"])
        assertEquals(
            listOf("chatgpt.com", "api.openai.com"),
            pairs.filter { it.first == "hostnames[]" }.map { it.second },
        )
    }

    @Test
    fun parseHostEntries_skipsServiceLinesAndComments() {
        val text = """
            #forwarded example.com
            103.137.248.179 chatgpt.com
            0.0.0.0 blocked.example
            # комментарий
            just-a-domain.example
            83.220.169.155   api.openai.com   
        """.trimIndent()

        val entries = parseHostEntries(text)

        assertEquals(
            listOf(
                HostEntry("chatgpt.com", "103.137.248.179"),
                HostEntry("api.openai.com", "83.220.169.155"),
            ),
            entries,
        )
    }
}
