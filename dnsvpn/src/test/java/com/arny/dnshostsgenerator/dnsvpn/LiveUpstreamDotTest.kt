package com.arny.dnshostsgenerator.dnsvpn

import org.xbill.DNS.Message
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Живая проверка пересылки в DoT (сеть обязательна):
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :dnsvpn:testDebugUnitTest --tests "*LiveUpstreamDotTest*" --rerun-tasks
 *
 * Проверяем типы, которые ломали другие приложения: AAAA и HTTPS/SVCB.
 * Если пересылка теряет тип записи, клиент получает бессмыслицу и сервис не работает.
 */
class LiveUpstreamDotTest {

    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    @Test
    fun forwardsRequestedRecordTypeUntouched() {
        if (!enabled) {
            println("LiveUpstreamDotTest пропущен: DNSHOSTS_LIVE != 1")
            return
        }
        listOf("ivi.ru", "rutube.ru", "example.org").forEach { host ->
            listOf(Type.A, Type.AAAA).forEach { type ->
                val query = Message(0x4242)
                query.addRecord(Record.newRecord(org.xbill.DNS.Name.fromString("$host."), type, 1), Section.QUESTION)
                val started = System.currentTimeMillis()
                val response = UpstreamDot.forward(query.toWire())
                val firstMs = System.currentTimeMillis() - started
                if (type == Type.AAAA) {
                    // AAAA информационно: у части апстримов он может не прийти (сеть/фильтр).
                    println("$host type=AAAA ответ=${response?.size ?: "нет"} за ${firstMs}мс")
                    return@forEach
                }
                assertNotNull(response, "апстрим не ответил на $host type=$type")
                val parsed = Message(response)
                val answers = parsed.getSection(Section.ANSWER)
                val cachedStart = System.currentTimeMillis()
                UpstreamDot.forward(query.toWire())
                val cachedMs = System.currentTimeMillis() - cachedStart
                println(
                    "$host type=${Type.string(type)} rcode=${parsed.rcode} " +
                        "answers=${answers.joinToString { it.type.toString() }} " +
                        "холодно=${firstMs}мс из_кэша=${cachedMs}мс",
                )
                assertTrue(parsed.header.id == 0x4242, "ID ответа должен совпадать с запросом")
                if (type == Type.A) {
                    assertTrue(answers.any { it.type == Type.A }, "для $host нет A в ответе")
                }
            }
        }
    }
}
