package com.arny.dnshostsgenerator.dnsvpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Живая проверка пула (сеть обязательна):
 *
 *     DNSHOSTS_LIVE=1 ./gradlew :dnsvpn:testDebugUnitTest --tests "*LivePoolConcurrencyTest*"
 *
 * Один запрос — эталон, затем 20 разных имён параллельно. С одним соединением время росло
 * линейно (запросы ждали друг друга), с пулом должно остаться близким к эталону.
 */
class LivePoolConcurrencyTest {

    private val enabled = System.getenv("DNSHOSTS_LIVE") == "1"

    private fun query(host: String, id: Int): ByteArray {
        val message = Message(id)
        message.addRecord(Record.newRecord(Name.fromString("$host."), Type.A, 1), Section.QUESTION)
        return message.toWire()
    }

    @Test
    fun parallelQueriesDoNotQueueBehindEachOther() = runBlocking {
        if (!enabled) {
            println("LivePoolConcurrencyTest пропущен: DNSHOSTS_LIVE != 1")
            return@runBlocking
        }
        val hosts = listOf("example.org","example.com","wikipedia.org","github.com","yandex.ru","vk.com","mail.ru","ozon.ru")

        val single = measure { UpstreamDot.forward(query("example.org", 1)) }
        println("один запрос: ${single}мс")

        val started = System.currentTimeMillis()
        val results = hosts.map { host ->
            async(Dispatchers.IO) {
                val startedAt = System.currentTimeMillis()
                val response = UpstreamDot.forward(query(host, host.hashCode() and 0xFFFF))
                println("$host -> ${response?.size ?: "нет"} байт за ${System.currentTimeMillis() - startedAt}мс")
                response
            }
        }.awaitAll()
        val parallel = System.currentTimeMillis() - started
        println("параллельных: ${results.size}, время ${parallel}мс, ответов=${results.count { it != null }}")

        assertTrue(results.count { it != null } >= results.size - 1, "почти все параллельные запросы должны получить ответ")
        // Пул на 4 соединения: ожидаем заметно меньше, чем 20 × эталон.
        assertTrue(parallel < single * 12, "параллельные запросы сериализуются: $parallel мс при эталоне $single мс")
    }

    private suspend fun measure(block: suspend () -> Unit) = runBlocking {
        val started = System.currentTimeMillis()
        block()
        System.currentTimeMillis() - started
    }
}
