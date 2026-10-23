package com.arny.dnshostsgenerator.resolver

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Живая проверка валидатора адресов: он решает, попадёт ли IP из DNS-ответа в hosts.
 * Оба направления важны — и «рабочий адрес принимается», и «чужой/мёртвый отвергается».
 */
class LiveIpValidatorTest {

    private val validator = createIpValidator()

    @Test
    fun acceptsIpThatServesTheDomain() = runBlocking {
        val ip = InetAddress.getByName("example.com").hostAddress
        assertEquals(IpCheckResult.SERVES, validator.check("example.com", ip), "example.com должен подтвердиться на $ip")
    }

    @Test
    fun rejectsUnreachableIp() = runBlocking {
        // TEST-NET-1 (RFC 5737): адрес гарантированно не отвечает.
        assertEquals(IpCheckResult.REJECTED, validator.check("example.com", "192.0.2.1", timeoutMillis = 2_000))
    }

    @Test
    fun rejectsIpThatDoesNotServeTheRequestedDomain() = runBlocking {
        // 8.8.8.8:443 отдаёт сертификат dns.google — для chatgpt.com он не подходит.
        assertEquals(IpCheckResult.REJECTED, validator.check("chatgpt.com", "8.8.8.8", timeoutMillis = 5_000))
    }
}
