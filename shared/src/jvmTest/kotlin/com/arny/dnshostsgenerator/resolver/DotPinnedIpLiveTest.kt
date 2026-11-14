package com.arny.dnshostsgenerator.resolver

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DoT должен идти на IP из пресета, а имя — только для SNI и проверки сертификата.
 *
 * Проверено 14.11.2026: dns.bezmezhau.com резолвится в 143.20.64.55, который не принимает
 * TCP:853, а 91.108.243.78 отвечает и возвращает рабочий адрес. До правки резолвер
 * подключался по имени и любой такой пресет считался мёртвым.
 */
class DotPinnedIpLiveTest {

    @Test
    fun pinnedIpIsUsedForDot() = runTest {
        val resolver = createDnsResolver()
        // Провайдер иногда обрывает соединение под нагрузкой: 2-3 попытки, иначе тест
        // краснеет из-за сети, а не из-за кода.
        var ips = emptyList<String>()
        for (attempt in 1..3) {
            ips = resolver.resolveA(
                domain = "chatgpt.com",
                dnsServer = "91.108.243.78",
                timeoutMillis = 8_000,
                options = DnsQueryOptions(dotHost = "dns.bezmezhau.com", allowPlainFallback = false),
            )
            if (ips.isNotEmpty()) break
            println("попытка $attempt: пусто")
        }
        println("DoT с закреплённым IP вернул: $ips")
        assertTrue(ips.isNotEmpty(), "DoT по IP пресета должен вернуть адреса")
    }
}
