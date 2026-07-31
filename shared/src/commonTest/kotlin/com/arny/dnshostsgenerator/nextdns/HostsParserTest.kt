package com.arny.dnshostsgenerator.nextdns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HostsParserTest {

    @Test
    fun parseHostsText_normalizesDomainsSkipsLoopbackAndCollectsIssues() {
        val result = HostsParser.parseHostsText(
            text = """
                # comment
                1.2.3.4 www.Example.com example.org
                1.2.3.4 example.org # duplicate pair
                5.6.7.8 example.org # conflict, first target must win
                127.0.0.2 localhost loopback.test
                ::1 ipv6-loopback.test
                bad-ip invalid.example
                9.9.9.9 bad_domain.com
            """.trimIndent(),
            stripWww = true,
            skipLoopback = true,
        )

        assertEquals(
            listOf(
                NextDnsRewrite("example.com", "1.2.3.4"),
                NextDnsRewrite("example.org", "1.2.3.4"),
            ),
            result.rewrites,
        )
        assertEquals(1, result.exactDuplicates)
        assertEquals(3, result.skippedLoopback)
        assertEquals(1, result.conflicts.size)
        assertEquals("example.org", result.conflicts.single().domain)
        assertEquals("1.2.3.4", result.conflicts.single().firstTarget)
        assertEquals("5.6.7.8", result.conflicts.single().rejectedTarget)
        assertEquals(2, result.issues.size)
        assertTrue(result.issues.any { it.reason == "некорректный IP" })
        assertTrue(result.issues.any { it.reason == "недопустимые символы в домене" })
    }

    @Test
    fun parseDomainList_supportsIdnaUrlsCommentsAndDuplicates() {
        val result = HostsParser.parseDomainList(
            text = """
                https://www.Example.com/path # comment
                пример.рф
                example.com
                bad_domain
            """.trimIndent(),
            defaultTarget = "10.0.0.1",
            stripWww = true,
        )

        assertEquals(
            listOf(
                NextDnsRewrite("example.com", "10.0.0.1"),
                NextDnsRewrite("xn--e1afmkfd.xn--p1ai", "10.0.0.1"),
            ),
            result.rewrites,
        )
        assertEquals(1, result.exactDuplicates)
        assertEquals(1, result.issues.size)
        assertEquals(4, result.issues.single().lineNumber)
    }

    @Test
    fun parseDomainList_rejectsInvalidTargetIp() {
        assertFailsWith<IllegalArgumentException> {
            HostsParser.parseDomainList(
                text = "example.com",
                defaultTarget = "not-an-ip",
            )
        }
    }

    @Test
    fun parseInput_autoDetectsHostsFormat() {
        val result = HostsParser.parseInput(
            text = "1.1.1.1 www.example.com",
            defaultTarget = "9.9.9.9",
            stripWww = true,
        )

        assertEquals(listOf(NextDnsRewrite("example.com", "1.1.1.1")), result.rewrites)
    }
}
