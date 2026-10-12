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

    /**
     * Строки взяты из живых файлов темы 4PDA «DNS CLUB» (showtopic=1117490):
     * adguard.txt из паков dnscomssone/xboxdnsru и rules.txt от AntiKeks.
     */
    @Test
    fun parseInput_adguardRewritesBecomeRewrites() {
        val result = HostsParser.parseInput(
            text = """
                ! Analytics
                # ChatGPT, OpenAI:
                ||chatgpt.com^${'$'}dnsrewrite=103.137.248.179
                ||only-fans.uk^${'$'}dnsrewrite=0.0.0.0
                ||files.oaiusercontent.com^${'$'}dnsrewrite=NOERROR;A;8.47.69.0
                @@||analytics.controld.com^ 
                ||cdn.*ads.^ 
                ||plesk.page^ 
            """.trimIndent(),
            defaultTarget = "9.9.9.9",
        )

        assertEquals(
            listOf(
                NextDnsRewrite("chatgpt.com", "103.137.248.179"),
                NextDnsRewrite("only-fans.uk", "0.0.0.0"),
                NextDnsRewrite("files.oaiusercontent.com", "8.47.69.0"),
            ),
            result.rewrites,
        )
        // Шаблон со `*` и правило без IP не переводятся в rewrite и должны быть видны
        // как issues — молча их терять нельзя.
        assertEquals(listOf(7, 8), result.issues.map { it.lineNumber })
    }

    /**
     * dnsmasq-формат из тех же паков (`aihosts.conf`): `server=/домен/ip`.
     */
    @Test
    fun parseInput_dnsmasqServerLinesBecomeRewrites() {
        val result = HostsParser.parseInput(
            text = """
                server=/chatgpt.com/83.220.169.155
                server=/openai.com/212.109.195.93
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                NextDnsRewrite("chatgpt.com", "83.220.169.155"),
                NextDnsRewrite("openai.com", "212.109.195.93"),
            ),
            result.rewrites,
        )
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun parseInput_mixesHostsAndRuleLines() {
        val result = HostsParser.parseInput(
            text = """
                87.228.47.204 api.openai.com
                ||chatgpt.com^${'$'}dnsrewrite=87.228.47.204
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                NextDnsRewrite("api.openai.com", "87.228.47.204"),
                NextDnsRewrite("chatgpt.com", "87.228.47.204"),
            ),
            result.rewrites,
        )
    }

    /**
     * Главное поле ввода тоже принимает любые диалекты темы: раньше строка
     * `103.137.248.179 chatgpt.com` или `||chatgpt.com^$dnsrewrite=…` уезжала в резолвер
     * целиком и становилась #unresolvedDomain.
     */
    @Test
    fun normalizeToDomainLines_convertsDialectsAndKeepsStructure() {
        val lines = HostsParser.normalizeToDomainLines(
            "# --- OpenAI ---\n" +
                "103.137.248.179 chatgpt.com\n" +
                "||ab.chatgpt.com^${'$'}dnsrewrite=103.137.248.179\n" +
                "server=/openai.com/83.220.169.155\n" +
                "1.2.3.4 one.example two.example\n" +
                "\n" +
                "chatgpt.com # комментарий\n" +
                "||cdn.*ads.^ "
        )

        assertEquals(
            listOf(
                "# --- OpenAI ---",
                "chatgpt.com",
                "ab.chatgpt.com",
                "openai.com",
                "one.example",
                "two.example",
                "",
                "chatgpt.com",
                "||cdn.*ads.^",
            ),
            lines,
        )
    }

    /**
     * Путь импорта доменов в группы (HostsGeneratorViewModel.addDomains):
     * блокирующие строки 127.0.0.1/0.0.0.0 — это тоже домены, терять их нельзя.
     */
    @Test
    fun parseInput_keepsLoopbackTargetsWhenSkipLoopbackIsOff() {
        val result = HostsParser.parseInput(
            text = "127.0.0.1 ads.example\n0.0.0.0 tracker.example\n::1 v6.example",
            skipLoopback = false,
        )

        assertEquals(
            listOf("ads.example", "tracker.example", "v6.example"),
            result.rewrites.map { it.domain },
        )
        assertEquals(0, result.skippedLoopback)
    }
}
