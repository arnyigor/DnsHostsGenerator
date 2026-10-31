package com.arny.dnshostsgenerator.dnsvpn

import org.xbill.DNS.Message
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Пересылка произвольного DNS-запроса в DoT-апстрим «как есть».
 *
 * Важно пересылать исходные байты, а не собирать ответ из A-записей: запросы бывают любых
 * типов (AAAA, HTTPS, MX, TXT, PTR). Раньше ответ собирался только из A — и приложения,
 * которым нужен AAAA или другой тип, получали бессмыслицу и переставали работать.
 */
object UpstreamDot {

    /** Логгер подставляется вызывающим кодом: так объект остаётся тестируемым без Android. */
    var logger: (String) -> Unit = {}

    private const val TAG = "DnsVpn"
    private const val DOT_PORT = 853
    private const val TIMEOUT_MS = 4_000
    private const val CACHE_TTL_MS = 60_000L
    private const val CACHE_LIMIT = 512

    private val hosts = listOf("cloudflare-dns.com", "1.1.1.1", "dns.google")
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(val response: ByteArray, val expiresAt: Long)

    /** Возвращает ответ апстрима или null, если переслать не удалось. */
    fun forward(payload: ByteArray): ByteArray? {
        if (payload.size < 3) return null
        val key = cacheKey(payload) ?: return queryUpstream(payload)
        cache[key]?.let { entry ->
            if (entry.expiresAt > System.currentTimeMillis()) return withId(entry.response, payload)
            cache.remove(key)
        }
        val response = queryUpstream(payload) ?: return null
        if (response.size > 3 && (response[3].toInt() and 0x0F) == 0) {
            cache[key] = CacheEntry(response.copyOf(), System.currentTimeMillis() + CACHE_TTL_MS)
            if (cache.size > CACHE_LIMIT) cache.clear()
        }
        return response
    }

    /**
     * Ключ кэша без ID запроса: одинаковые вопросы от разных приложений дают один ответ.
     * Имя и тип берём из разобранного вопроса; для непарсящихся запросов кэш не используем.
     */
    private fun cacheKey(payload: ByteArray): String? = runCatching {
        val message = Message(payload)
        val question = message.question ?: return null
        "${question.name.toString(true).lowercase()}|${question.type}|${question.dClass}"
    }.getOrNull()

    private fun withId(response: ByteArray, query: ByteArray): ByteArray =
        response.copyOf().also {
            it[0] = query[0]
            it[1] = query[1]
        }

    private fun queryUpstream(payload: ByteArray): ByteArray? {
        hosts.forEach { host ->
            try {
                connect(host)?.use { socket ->
                    socket.soTimeout = TIMEOUT_MS
                    val out = socket.outputStream
                    out.write((payload.size shr 8) and 0xFF)
                    out.write(payload.size and 0xFF)
                    out.write(payload)
                    out.flush()
                    val input = socket.inputStream
                    val high = input.read()
                    val low = input.read()
                    if (high < 0 || low < 0) return@use
                    val length = (high shl 8) or low
                    if (length <= 0) return@use
                    val response = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val count = input.read(response, read, length - read)
                        if (count <= 0) return@use
                        read += count
                    }
                    // SERVFAIL/REFUSED от одного резолвера — повод спросить следующий:
                    // Cloudflare отдаёт SERVFAIL на часть российских зон.
                    val rcode = response.getOrNull(3)?.toInt()?.and(0x0F)
                    if (rcode == 2 || rcode == 5) {
                        logger("DoT upstream $host ответил rcode=$rcode, пробую следующий")
                        return@use
                    }
                    return response
                }
            } catch (error: Exception) {
                logger("DoT upstream $host недоступен: ${error.message}")
            }
        }
        return null
    }

    private fun connect(host: String): SSLSocket? {
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse { emptyList() }
        addresses.forEach { address ->
            val raw = Socket()
            try {
                raw.connect(InetSocketAddress(address, DOT_PORT), TIMEOUT_MS)
                val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, host, DOT_PORT, true) as SSLSocket
                socket.sslParameters = SSLParameters().apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(host))
                }
                socket.startHandshake()
                return socket
            } catch (error: Exception) {
                runCatching { raw.close() }
            }
        }
        return null
    }
}
