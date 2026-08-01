package com.arny.dnshostsgenerator.resolver

/**
 * Настройки транспорта для одного DNS-запроса.
 *
 * @param dotHost         hostname для DNS-over-TLS (порт 853, RFC 7858). Если null,
 *                        резолвер попытается использовать сам [dnsServer] как DoT-host.
 *                        DoT обходит прозрачный перехват UDP/TCP:53 (VPN-антиблокировщики,
 *                        DNS-прокси операторов), при котором primary и check-резолверы
 *                        возвращают одинаковые IP и все домены ошибочно уходят в #forwarded.
 * @param allowInvalidTls разрешить TLS-соединение с некорректным сертификатом
 *                        (нужно для провайдеров с просроченными сертификатами:
 *                        dns.mafioznik.xyz, free.shecan.ir).
 */
data class DnsQueryOptions(
    val dotHost: String? = null,
    val allowInvalidTls: Boolean = false,
)

interface DnsResolver {
    suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int = 5_000,
    ): List<String>

    /**
     * Запрос A-записей с расширенными настройками (DNS-over-TLS + fallback на plain UDP).
     * Дефолтная реализация делегирует в [resolveA] и используется фейками в тестах.
     */
    suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
        options: DnsQueryOptions,
    ): List<String> = resolveA(domain, dnsServer, timeoutMillis)
}

expect fun createDnsResolver(): DnsResolver
