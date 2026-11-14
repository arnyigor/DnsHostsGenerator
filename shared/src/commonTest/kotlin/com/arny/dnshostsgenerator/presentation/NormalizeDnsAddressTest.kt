package com.arny.dnshostsgenerator.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Приведение введённого адреса к хосту: пользователь вставляет его как придётся. */
class NormalizeDnsAddressTest {

    @Test
    fun stripsSchemeAndPath() {
        assertEquals("dns.example.com", normalizeDnsAddress("https://dns.example.com/dns-query"))
        assertEquals("dns.example.com", normalizeDnsAddress("http://dns.example.com"))
    }

    @Test
    fun stripsPortForHostAndIpv4() {
        assertEquals("dns.example.com", normalizeDnsAddress("dns.example.com:853"))
        assertEquals("1.2.3.4", normalizeDnsAddress(" 1.2.3.4:53 "))
    }

    @Test
    fun keepsIpv6Intact() {
        // У IPv6 несколько двоеточий: резать по первому нельзя.
        assertEquals("2001:db8::1", normalizeDnsAddress("2001:db8::1"))
    }

    @Test
    fun keepsPlainAddress() {
        assertEquals("1.1.1.1", normalizeDnsAddress("1.1.1.1"))
        assertEquals("", normalizeDnsAddress("   "))
    }
}
