package com.arny.dnshostsgenerator.nextdns

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal actual fun domainToAscii(domain: String): String =
    IDN.toASCII(domain)

internal actual fun parseIpAddress(raw: String): ParsedIpAddress? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    if (!value.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' }) return null
    if ('.' !in value && ':' !in value) return null

    return runCatching {
        val address = InetAddress.getByName(value)
        when (address) {
            is Inet4Address -> {
                val normalized = address.hostAddress ?: return null
                ParsedIpAddress(normalized = normalized, isLoopback = address.isLoopbackAddress)
            }
            is Inet6Address -> {
                val normalized = address.hostAddress
                    ?.substringBefore('%')
                    ?: return null
                ParsedIpAddress(normalized = normalized, isLoopback = address.isLoopbackAddress)
            }
            else -> null
        }
    }.getOrNull()
}
