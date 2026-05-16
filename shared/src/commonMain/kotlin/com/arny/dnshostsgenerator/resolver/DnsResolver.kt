package com.arny.dnshostsgenerator.resolver

interface DnsResolver {
    suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int = 5_000,
    ): List<String>
}

expect fun createDnsResolver(): DnsResolver
