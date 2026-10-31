package com.arny.dnshostsgenerator.dnsvpn

import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

/**
 * Что делать с DNS-запросом: ответить самим или отдать наверх.
 *
 * Ответ формируется на месте только для доменов из правил. Всё остальное уходит
 * в обычный резолвер — так поведение остальных приложений не меняется.
 */
sealed interface DnsDecision {
    data class Reply(val payload: ByteArray) : DnsDecision
    data object Forward : DnsDecision
}

object DnsAnswerer {

    private const val TTL = 60L

    fun decide(queryPayload: ByteArray, rules: DnsRules): DnsDecision {
        val query = runCatching { Message(queryPayload) }.getOrNull() ?: return DnsDecision.Forward
        val question = query.question ?: return DnsDecision.Forward
        val type = question.type
        val host = question.name.toString(true).trimEnd('.').lowercase()

        // Подмена важнее блокировки: в правилах эти строки идут парой
        // (`>host ip` + `host`), и вторая нужна только чтобы скрыть остальные типы записей.
        val mapped = rules.mappingFor(host)
        if (mapped != null) {
            val answer = when (type) {
                Type.A -> listOfNotNull(ipv4Record(question.name, mapped))
                // AAAA/HTTPS/SVCB не отдаём: клиент должен идти по IPv4, иначе подмена обходится.
                else -> emptyList()
            }
            return DnsDecision.Reply(buildResponse(query, answer))
        }
        if (rules.isBlocked(host)) {
            return DnsDecision.Reply(buildResponse(query, emptyList()))
        }
        return DnsDecision.Forward
    }

    private fun ipv4Record(name: Name, ip: String): Record? = runCatching {
        ARecord(name, DClass.IN, TTL, InetAddress.getByName(ip))
    }.getOrNull()

    /**
     * Ответ на запрос, который мы переслали наверх: тот же вердикт, но с адресами апстрима.
     * Записи остаются типа запроса — клиенту не важно, кто именно сформировал ответ.
     */
    fun buildForwardedResponse(query: Message, ips: List<String>): ByteArray? {
        val question = query.question ?: return null
        val records = ips.mapNotNull { ip ->
            runCatching { ARecord(question.name, DClass.IN, TTL, InetAddress.getByName(ip)) }.getOrNull()
        }
        if (records.isEmpty()) return null
        return buildResponse(query, records)
    }

    /** Пустой ответ: клиент сразу получает «адресов нет» и не висит в ожидании. */
    fun buildEmptyResponse(query: Message): ByteArray = buildResponse(query, emptyList())

    private fun buildResponse(query: Message, answers: List<Record>): ByteArray {
        val response = Message(query.header.id)
        response.header.setFlag(Flags.QR.toInt())
        response.header.setFlag(Flags.RA.toInt())
        if (query.header.getFlag(Flags.RD.toInt())) response.header.setFlag(Flags.RD.toInt())
        response.header.setRcode(Rcode.NOERROR)
        query.question?.let { response.addRecord(it, Section.QUESTION) }
        answers.forEach { response.addRecord(it, Section.ANSWER) }
        return response.toWire()
    }
}
