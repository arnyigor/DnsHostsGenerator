package com.arny.dnshostsgenerator.dnsvpn

import org.xbill.DNS.ARecord
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsVpnCoreTest {

    private val rules = DnsRules.parse(
        """
        # комментарий
        >chatgpt.com 45.13.237.213
        >ws.chatgpt.com 45.13.237.213
        chatgpt.com
        >broken.example not-an-ip
        """.trimIndent(),
    )

    @Test
    fun rules_parseMappingsAndBlocks() {
        assertEquals("45.13.237.213", rules.mappingFor("chatgpt.com"))
        assertEquals("45.13.237.213", rules.mappingFor("WS.ChatGPT.com"))
        assertNull(rules.mappingFor("broken.example"), "некорректный IP не должен попадать в правила")
        assertTrue(rules.isBlocked("chatgpt.com"))
        assertEquals(2, rules.mappings.size)
        assertEquals(1, rules.blocked.size)
    }

    @Test
    fun rules_singleIpDetectsUniformAndMixedAddresses() {
        assertEquals("45.13.237.213", rules.singleIp())
        val mixed = DnsRules.parse(listOf(">a.example 1.1.1.1", ">b.example 2.2.2.2").joinToString(separator = "\n"))
        assertNull(mixed.singleIp(), "разные адреса не дают одного значения")
    }

    @Test
    fun rules_recogniseHostsFileLinesAsNotBlocked() {
        val parsed = DnsRules.parse("0.0.0.0 ads.example")
        assertTrue(parsed.blocked.isEmpty(), "строка hosts-файла не должна превращаться в домен-блок")
    }

    @Test
    fun packets_roundTripQueryAndReply() {
        val payload = dnsQuery("chatgpt.com", Type.A)
        val query = buildQueryPacket(payload)

        val parsed = Ipv4Udp.parse(query, query.size)
        assertEquals("10.111.222.2", parsed!!.destinationIp.joinToString(".") { (it.toInt() and 0xFF).toString() })
        assertEquals(53, parsed.destinationPort)
        assertTrue(payload.contentEquals(parsed.payload))

        val reply = Ipv4Udp.buildReply(parsed, payload)
        val parsedReply = Ipv4Udp.parse(reply, reply.size)!!
        assertEquals(parsed.sourceIp.toList(), parsedReply.destinationIp.toList(), "адреса должны поменяться местами")
        assertEquals(parsed.sourcePort, parsedReply.destinationPort)
        assertEquals(parsed.destinationPort, parsedReply.sourcePort)
        assertTrue(payload.contentEquals(parsedReply.payload))
    }

    @Test
    fun packets_checksumsAreNonZero() {
        val payload = dnsQuery("chatgpt.com", Type.A)
        val reply = Ipv4Udp.buildReply(Ipv4Udp.parse(buildQueryPacket(payload), 28 + payload.size)!!, payload)
        val ipChecksum = ((reply[10].toInt() and 0xFF) shl 8) or (reply[11].toInt() and 0xFF)
        val udpChecksum = ((reply[26].toInt() and 0xFF) shl 8) or (reply[27].toInt() and 0xFF)
        assertTrue(ipChecksum != 0, "контрольная сумма IP должна быть заполнена")
        assertTrue(udpChecksum != 0, "контрольная сумма UDP должна быть заполнена")
    }

    @Test
    fun answerer_repliesMappedAddressForA() {
        val decision = DnsAnswerer.decide(dnsQuery("chatgpt.com", Type.A), rules)
        assertTrue(decision is DnsDecision.Reply)
        val response = Message((decision as DnsDecision.Reply).payload)
        assertEquals(1, response.getSection(Section.ANSWER).size)
        val answer = response.getSection(Section.ANSWER).first() as ARecord
        assertEquals("45.13.237.213", answer.address.hostAddress)
        assertTrue(response.header.getFlag(Flags.QR.toInt()))
    }

    @Test
    fun answerer_hidesIpv6ForMappedHost() {
        val decision = DnsAnswerer.decide(dnsQuery("chatgpt.com", Type.AAAA), rules)
        assertTrue(decision is DnsDecision.Reply)
        val response = Message((decision as DnsDecision.Reply).payload)
        assertEquals(0, response.getSection(Section.ANSWER).size, "AAAA не отдаём: иначе подмена обходится")
    }

    @Test
    fun answerer_returnsEmptyForBlockedHost() {
        val decision = DnsAnswerer.decide(dnsQuery("chatgpt.com", Type.A), DnsRules(blocked = setOf("chatgpt.com")))
        val response = Message((decision as DnsDecision.Reply).payload)
        assertEquals(0, response.getSection(Section.ANSWER).size)
    }

    @Test
    fun answerer_forwardsUnknownHost() {
        val decision = DnsAnswerer.decide(dnsQuery("example.org", Type.A), rules)
        assertEquals(DnsDecision.Forward, decision)
    }

    @Test
    fun forwardedResponse_carriesUpstreamAddresses() {
        val query = Message(dnsQuery("example.org", Type.A))
        val payload = DnsAnswerer.buildForwardedResponse(query, listOf("93.184.216.34"))!!
        val response = Message(payload)
        val answer = response.getSection(Section.ANSWER).first() as ARecord
        assertEquals("93.184.216.34", answer.address.hostAddress)
    }

    private fun dnsQuery(host: String, type: Int): ByteArray {
        val message = Message(0x1234)
        message.header.setFlag(Flags.RD.toInt())
        message.addRecord(Record.newRecord(Name.fromString("$host."), type, 1), Section.QUESTION)
        return message.toWire()
    }

    private fun buildQueryPacket(payload: ByteArray): ByteArray {
        val clientIp = byteArrayOf(10, 111, 222.toByte(), 3)
        val serverIp = byteArrayOf(10, 111, 222.toByte(), 2)
        return Ipv4Udp.buildReply(
            Ipv4Udp.Packet(
                sourceIp = serverIp,
                destinationIp = clientIp,
                sourcePort = 53,
                destinationPort = 41234,
                payload = payload,
            ),
            payload,
        )
    }
}
