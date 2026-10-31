package com.arny.dnshostsgenerator.dnsvpn

import org.xbill.DNS.Message
import org.xbill.DNS.Section
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Пересылка DNS-запроса в DoT-апстрим «как есть».
 *
 * Важно пересылать исходные байты, а не собирать ответ из A-записей: запросы бывают любых
 * типов (AAAA, HTTPS, MX, TXT, PTR). Раньше ответ собирался только из A — и приложения,
 * которым нужен AAAA или другой тип, получали бессмыслицу и переставали работать.
 *
 * Тормоза были из-за нового TLS-рукопожатия на каждый запрос (десятки запросов на старте
 * приложения). Теперь соединение переиспользуется, а ответы кэшируются по TTL из ответа.
 */
object UpstreamDot {

    /** Логгер подставляется вызывающим кодом: так объект остаётся тестируемым без Android. */
    var logger: (String) -> Unit = {}

    private const val DOT_PORT = 853
    private const val TIMEOUT_MS = 4_000
    private const val MIN_CACHE_MS = 30_000L
    private const val MAX_CACHE_MS = 600_000L
    private const val CACHE_LIMIT = 2_048

    /**
     * Адрес + имя для проверки сертификата. Подключаемся по IP: это убирает резолв имени
     * апстрима, который сам идёт через нас (и тормозил на старте). Cloudflare идёт последним:
     * с этой сети он периодически отвечает медленно или SERVFAIL на российские зоны.
     */
    private val upstreams = listOf(
        "9.9.9.9" to "dns.quad9.net",
        "8.8.8.8" to "dns.google",
        "1.1.1.1" to "cloudflare-dns.com",
    )

    /** Пустой ответ тоже кэшируем: иначе приложение спрашивает его заново и ждёт снова. */
    private const val EMPTY_RESPONSE_CACHE_MS = 60_000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(val response: ByteArray, val expiresAt: Long)

    /** Постоянное соединение: TLS-хендшейк стоит сотни миллисекунд, его нельзя делать на каждый запрос. */
    private val connectionLock = Any()
    private var connection: Pair<String, SSLSocket>? = null

    /** Возвращает ответ апстрима или null, если переслать не удалось. */
    fun forward(payload: ByteArray): ByteArray? {
        if (payload.size < 3) return null
        val key = cacheKey(payload)
        if (key != null) {
            cache[key]?.let { entry ->
                if (entry.expiresAt > System.currentTimeMillis()) return withId(entry.response, payload)
                cache.remove(key)
            }
        }
        val response = queryUpstream(payload) ?: return null
        if (key != null && cacheTtlMillis(response) > 0) {
            cache[key] = CacheEntry(response.copyOf(), System.currentTimeMillis() + cacheTtlMillis(response))
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

    /** TTL берём из ответа: у популярных доменов он большой, у CDN — маленький. */
    private fun cacheTtlMillis(response: ByteArray): Long = runCatching {
        val message = Message(response)
        if (message.rcode != 0) return 0
        val ttl = message.getSection(Section.ANSWER).minOfOrNull { it.ttl }
            ?: return EMPTY_RESPONSE_CACHE_MS
        (ttl * 1000L).coerceIn(MIN_CACHE_MS, MAX_CACHE_MS)
    }.getOrDefault(0)

    private fun withId(response: ByteArray, query: ByteArray): ByteArray =
        response.copyOf().also {
            it[0] = query[0]
            it[1] = query[1]
        }

    private fun queryUpstream(payload: ByteArray): ByteArray? {
        // Два прохода: если соединение с первым апстримом сорвалось, второй проход
        // начнётся с нового TLS-соединения вместо повторной попытки в тот же сокет.
        repeat(2) { pass ->
            upstreams.forEach { (address, name) ->
            val response = runCatching { exchange(address, name, payload) }.getOrNull()
            if (response == null) return@forEach
            val rcode = response.getOrNull(3)?.toInt()?.and(0x0F)
            if (rcode == 2 || rcode == 5) {
                // SERVFAIL/REFUSED от одного резолвера — повод спросить следующий:
                // Cloudflare отдаёт SERVFAIL на часть российских зон (ivi.ru).
                logger("DoT upstream $name ответил rcode=$rcode, пробую следующий")
                return@forEach
            }
            return response
            }
            if (pass == 0) closeConnection()
        }
        return null
    }

    private fun exchange(address: String, name: String, payload: ByteArray): ByteArray? =
        synchronized(connectionLock) {
        repeat(2) { attempt ->
            val socket = openConnection(address, name) ?: return null
            try {
                val output = DataOutputStream(socket.outputStream)
                output.writeShort(payload.size)
                output.write(payload)
                output.flush()
                val input = DataInputStream(socket.inputStream)
                val length = input.readUnsignedShort()
                if (length <= 0) return@repeat
                val response = ByteArray(length)
                input.readFully(response)
                return response
            } catch (error: Exception) {
                // Соединение могло закрыться со стороны сервера — один раз переподключаемся.
                logger("DoT $name: ${error.message}, переподключаюсь (#${attempt + 1})")
                closeConnection()
            }
        }
        null
    }

    private fun openConnection(address: String, name: String): SSLSocket? {
        connection?.let { (currentAddress, socket) ->
            if (currentAddress == address && !socket.isClosed) return socket
        }
        closeConnection()
        val socket = connect(address, name) ?: return null
        connection = address to socket
        return socket
    }

    private fun closeConnection() {
        connection?.second?.let { socket -> runCatching { socket.close() } }
        connection = null
    }

    /** Подключение по адресу с проверкой сертификата на [name]. */
    private fun connect(address: String, name: String): SSLSocket? {
        val raw = Socket()
        return try {
            raw.connect(InetSocketAddress(InetAddress.getByName(address), DOT_PORT), TIMEOUT_MS)
            val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, name, DOT_PORT, true) as SSLSocket
            socket.sslParameters = SSLParameters().apply {
                endpointIdentificationAlgorithm = "HTTPS"
                serverNames = listOf(SNIHostName(name))
            }
            socket.startHandshake()
            socket
        } catch (error: Exception) {
            logger("DoT $name ($address) недоступен: ${error.message}")
            runCatching { raw.close() }
            null
        }
    }
}
