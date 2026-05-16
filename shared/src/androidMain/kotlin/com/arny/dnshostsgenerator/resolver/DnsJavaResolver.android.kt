package com.arny.dnshostsgenerator.resolver

import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.ARecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type

actual fun createDnsResolver(): DnsResolver = DnsJavaResolver()

private class DnsJavaResolver : DnsResolver {
    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        AppLogger.d("DNS query started: type=A, domain=$domain, dnsServer=$dnsServer, timeoutMs=$timeoutMillis")
        val resolver = SimpleResolver(dnsServer).apply {
            @Suppress("DEPRECATION")
            setTimeout(timeoutMillis / 1_000, timeoutMillis % 1_000)
        }
        val lookup = Lookup(domain, Type.A).apply {
            setResolver(resolver)
        }
        val records = lookup.run()
        if (records == null) {
            AppLogger.d("DNS query returned no records: domain=$domain, dnsServer=$dnsServer, result=${lookup.result}")
            return@withContext emptyList()
        }
        val ips = records
            .filterIsInstance<ARecord>()
            .mapNotNull { it.address?.hostAddress }
            .distinct()
        AppLogger.d("DNS query finished: domain=$domain, dnsServer=$dnsServer, ips=${ips.joinToString()}")
        ips
    }
}
