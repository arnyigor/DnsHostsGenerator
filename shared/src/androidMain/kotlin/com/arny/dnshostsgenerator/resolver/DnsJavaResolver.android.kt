package com.arny.dnshostsgenerator.resolver

import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Lookup
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

actual fun createDnsResolver(): DnsResolver = DnsJavaResolver()

/** Стандартный порт DNS-over-TLS (RFC 7858). */
private const val DOT_PORT = 853

private class DnsJavaResolver : DnsResolver {

    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        AppLogger.d("DNS query started (UDP): type=A, domain=$domain, dnsServer=$dnsServer, timeoutMs=$timeoutMillis")
        val resolver = SimpleResolver(dnsServer).apply {
            @Suppress("DEPRECATION")
            setTimeout(timeoutMillis / 1_000, timeoutMillis % 1_000)
        }
        val lookup = Lookup(domain, Type.A).apply {
            setResolver(resolver)
        }
        val records = lookup.run()
        if (records == null) {
            AppLogger.d("DNS query returned no records (UDP): domain=$domain, dnsServer=$dnsServer, result=${lookup.result}")
            return@withContext emptyList()
        }
        val ips = records
            .filterIsInstance<ARecord>()
            .mapNotNull { it.address?.hostAddress }
            .distinct()
        AppLogger.d("DNS query finished (UDP): domain=$domain, dnsServer=$dnsServer, ips=${ips.joinToString()}")
        ips
    }

    override suspend fun resolveA(
        domain: String,
        dnsServer: String,
        timeoutMillis: Int,
        options: DnsQueryOptions,
    ): List<String> {
        // Сначала пробуем DNS-over-TLS: он обходит прозрачный перехват UDP/TCP:53
        // (VPN-антиблокировщики, DNS-прокси операторов и т.п.), из-за которого
        // primary и check-резолверы возвращают одинаковые IP и всё уходит в #forwarded.
        val dotHost = options.dotHost ?: dnsServer
        val dotResult = runCatching {
            resolveADot(domain, dotHost, timeoutMillis, options.allowInvalidTls)
        }.onFailure { error ->
            AppLogger.e(
                "DoT query failed, falling back to plain UDP: domain=$domain, dotHost=$dotHost, " +
                    "dnsServer=$dnsServer, error=${error.message ?: error::class.simpleName}",
                error,
            )
        }.getOrNull().orEmpty()

        if (dotResult.isNotEmpty()) {
            return dotResult
        }

        // Fallback на legacy plain DNS (прежнее поведение).
        return resolveA(domain, dnsServer, timeoutMillis)
    }

    private suspend fun resolveADot(
        domain: String,
        dotHost: String,
        timeoutMillis: Int,
        allowInvalidTls: Boolean,
    ): List<String> = withContext(Dispatchers.IO) {
        AppLogger.d(
            "DNS query started (DoT): type=A, domain=$domain, dotHost=$dotHost, " +
                "timeoutMs=$timeoutMillis, allowInvalidTls=$allowInvalidTls",
        )
        val socket = socketFactory(allowInvalidTls).createSocket(dotHost, DOT_PORT) as SSLSocket
        try {
            socket.soTimeout = timeoutMillis
            if (!allowInvalidTls) {
                socket.sslParameters = SSLParameters().apply {
                    // Проверка hostname + цепочки сертификатов. Проверено: все строгие
                    // провайдеры пресетов проходят hostname-верификацию.
                    endpointIdentificationAlgorithm = "HTTPS"
                }
            }

            val query = Message()
            // Случайный ID (а не от времени): параллельные запросы в одну миллисекунду
            // иначе получают одинаковый ID, а сам ID становится предсказуемым.
            val queryId = SECURE_RANDOM.nextInt(0x10000)
            query.header.id = queryId
            query.header.setFlag(Flags.RD.toInt())
            query.addRecord(
                Record.newRecord(Name.fromString("$domain."), Type.A, DClass.IN),
                Section.QUESTION,
            )

            val wire = query.toWire()
            val out = DataOutputStream(socket.getOutputStream())
            out.writeShort(wire.size)
            out.write(wire)
            out.flush()

            val input = DataInputStream(socket.getInputStream())
            val length = input.readUnsignedShort()
            val buffer = ByteArray(length)
            input.readFully(buffer)
            val response = Message(buffer)
            check(response.header.id == queryId) {
                "DoT response id mismatch: expected=$queryId, actual=${response.header.id}"
            }

            val ips = response.getSection(Section.ANSWER)
                .filterIsInstance<ARecord>()
                .mapNotNull { it.address?.hostAddress }
                .distinct()
            AppLogger.d(
                "DNS query finished (DoT): domain=$domain, dotHost=$dotHost, " +
                    "rcode=${response.header.rcode}, ips=${ips.joinToString()}",
            )
            ips
        } finally {
            socket.close()
        }
    }

    private fun socketFactory(allowInvalidTls: Boolean): SSLSocketFactory {
        if (allowInvalidTls) return RELAXED_SOCKET_FACTORY
        // В Kotlin SSLSocketFactory.getDefault() типизируется как SocketFactory
        // (унаследованный статик из javax.net.SocketFactory) — нужен явный downcast.
        @Suppress("CAST_NEVER_SUCCEEDS")
        return SSLSocketFactory.getDefault() as SSLSocketFactory
    }

    private companion object {
        val SECURE_RANDOM = SecureRandom()

        /** Trust-all фабрика для провайдеров с проблемным сертификатом (mafioznik, free.shecan). */
        val RELAXED_SOCKET_FACTORY: SSLSocketFactory by lazy {
            val trustAll = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                },
            )
            SSLContext.getInstance("TLS").apply {
                init(null, trustAll, SecureRandom())
            }.socketFactory
        }
    }
}
