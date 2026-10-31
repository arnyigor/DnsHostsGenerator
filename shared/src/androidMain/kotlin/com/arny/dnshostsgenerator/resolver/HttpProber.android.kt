package com.arny.dnshostsgenerator.resolver

import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private const val HTTPS_PORT = 443

/**
 * HTTP-проверка адреса поверх TLS с проверкой сертификата и имени.
 * Читаем только начало ответа: тело нужно лишь для поиска маркеров отказа.
 */
private class TlsHttpProber : HttpProber {

    override suspend fun probe(
        host: String,
        ip: String,
        path: String,
        timeoutMillis: Int,
    ): HttpProbeResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val request = buildString {
            append("GET ").append(path).append(" HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("User-Agent: DnsHostsGenerator/probe\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }
        var raw: Socket? = null
        try {
            raw = Socket()
            raw.connect(InetSocketAddress(ip, HTTPS_PORT), timeoutMillis)
            raw.soTimeout = timeoutMillis
            val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, HTTPS_PORT, true) as SSLSocket
            socket.sslParameters = SSLParameters().apply {
                endpointIdentificationAlgorithm = "HTTPS"
                serverNames = listOf(SNIHostName(host))
            }
            socket.startHandshake()
            socket.outputStream.apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8), 8 * 1024)
            val statusLine = reader.readLine()
            val statusCode = statusLine?.split(' ')?.getOrNull(1)?.toIntOrNull()
            val builder = StringBuilder()
            var line = reader.readLine()
            while (line != null && builder.length < BODY_LIMIT) {
                builder.append(line).append('\n')
                line = reader.readLine()
            }
            runCatching { socket.close() }
            val elapsed = System.currentTimeMillis() - startedAt
            val verdict = classify(statusCode, builder.toString())
            AppLogger.d("HTTP probe: host=$host, ip=$ip, status=$statusCode, verdict=$verdict")
            HttpProbeResult(
                verdict = verdict,
                statusCode = statusCode,
                elapsedMs = elapsed,
                detail = statusLine,
            )
        } catch (error: Exception) {
            runCatching { raw?.close() }
            AppLogger.d("HTTP probe failed: host=$host, ip=$ip, reason=${error.message}")
            HttpProbeResult(
                verdict = HttpVerdict.FAILED,
                elapsedMs = System.currentTimeMillis() - startedAt,
                detail = error.message,
            )
        }
    }

    private fun classify(statusCode: Int?, body: String): HttpVerdict = when {
        statusCode == null -> HttpVerdict.FAILED
        body.contains("unsupported_country_region_territory") -> HttpVerdict.REGION_BLOCKED
        body.contains("Request is not allowed") -> HttpVerdict.CLOUDFLARE_BLOCKED
        body.contains("cf-chl-") || body.contains("Just a moment") ||
            body.contains("google.com/sorry") -> HttpVerdict.CHALLENGE
        else -> HttpVerdict.OK
    }

    private companion object {
        /** Хватает на JSON-ошибку и начало HTML-страницы. */
        const val BODY_LIMIT = 8_192
    }
}

actual fun createHttpProber(): HttpProber = TlsHttpProber()
