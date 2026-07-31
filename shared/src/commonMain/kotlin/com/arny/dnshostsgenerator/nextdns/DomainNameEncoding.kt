package com.arny.dnshostsgenerator.nextdns

internal data class ParsedIpAddress(
    val normalized: String,
    val isLoopback: Boolean,
)

internal expect fun domainToAscii(domain: String): String

internal expect fun parseIpAddress(raw: String): ParsedIpAddress?
