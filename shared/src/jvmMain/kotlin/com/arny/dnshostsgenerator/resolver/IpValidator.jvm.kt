package com.arny.dnshostsgenerator.resolver

import com.arny.dnshostsgenerator.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket

/** Порт, на котором проверяются hosts-записи: ИИ-домены работают по HTTPS. */
private const val HTTPS_PORT = 443

actual fun createIpValidator(): IpValidator = TlsIpValidator()

/**
 * TLS-проверка: подключаемся к [ip] с SNI нужного домена и проверяем сертификат.
 * Несовпадение сертификата — сразу [IpCheckResult.REJECTED], таймаут — [IpCheckResult.UNKNOWN]
 * (медленный прокси может ответить позже, его перепроверяют с большим таймаутом).
 */
private class TlsIpValidator : IpValidator {

    override suspend fun check(domain: String, ip: String, timeoutMillis: Int): IpCheckResult =
        inspect(domain, ip, timeoutMillis).result

    override suspend fun inspect(
        domain: String,
        ip: String,
        timeoutMillis: Int,
    ): IpCheckOutcome =
        withContext(Dispatchers.IO) {
            val sni = domain.trim().removeSuffix(".")
            if (sni.isEmpty() || !ip.matches(IPV4) && !ip.contains(':')) {
                return@withContext IpCheckOutcome(IpCheckResult.REJECTED)
            }
            // TCP — на IP, TLS-обёртка — с именем домена: только так JSSE проверяет
            // сертификат по SNI. Простой createSocket()+SNI проверку hostname пропускает
            // (проверено: 8.8.8.8 «подтверждался» для chatgpt.com при чужом сертификате).
            val raw = Socket()
            // Таймаут TCP отделён от таймаута TLS: мёртвый адрес не принимает соединение и
            // отсекается сразу, а медленный прокси (хендшейк ~10 с) уходит в UNKNOWN и
            // перепроверяется с большим таймаутом.
            try {
                raw.connect(InetSocketAddress(ip, HTTPS_PORT), timeoutMillis)
            } catch (error: SocketTimeoutException) {
                AppLogger.d("IP rejected (connect timeout): domain=$domain, ip=$ip")
                runCatching { raw.close() }
                return@withContext IpCheckOutcome(IpCheckResult.REJECTED)
            } catch (error: IOException) {
                AppLogger.d("IP rejected: domain=$domain, ip=$ip, reason=${error.message ?: "io"}")
                runCatching { raw.close() }
                return@withContext IpCheckOutcome(IpCheckResult.REJECTED)
            }
            try {
                raw.soTimeout = timeoutMillis
                val socket = socketFactory().createSocket(raw, sni, HTTPS_PORT, true) as SSLSocket
                socket.sslParameters = SSLParameters().apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(sni))
                }
                socket.startHandshake()
                AppLogger.d("IP confirmed: domain=$domain, ip=$ip")
                IpCheckOutcome(IpCheckResult.SERVES, certificateFacts(socket))
            } catch (error: SSLHandshakeException) {
                val reason = error.message ?: error::class.simpleName ?: "handshake"
                if (error.cause is CertificateException || reason.contains("certificate", ignoreCase = true)) {
                    AppLogger.d("IP rejected: domain=$domain, ip=$ip, reason=$reason")
                    IpCheckOutcome(IpCheckResult.REJECTED)
                } else {
                    AppLogger.d("IP unknown: domain=$domain, ip=$ip, reason=$reason")
                    IpCheckOutcome(IpCheckResult.UNKNOWN)
                }
            } catch (error: SocketTimeoutException) {
                AppLogger.d("IP unknown (timeout): domain=$domain, ip=$ip")
                IpCheckOutcome(IpCheckResult.UNKNOWN)
            } catch (error: ConnectException) {
                AppLogger.d("IP rejected: domain=$domain, ip=$ip, reason=connect refused")
                IpCheckOutcome(IpCheckResult.REJECTED)
            } catch (error: NoRouteToHostException) {
                AppLogger.d("IP rejected: domain=$domain, ip=$ip, reason=no route")
                IpCheckOutcome(IpCheckResult.REJECTED)
            } catch (error: SSLException) {
                AppLogger.d("IP unknown: domain=$domain, ip=$ip, reason=${error.message ?: "ssl"}")
                IpCheckOutcome(IpCheckResult.UNKNOWN)
            } catch (error: IOException) {
                AppLogger.d("IP unknown: domain=$domain, ip=$ip, reason=${error.message ?: "io"}")
                IpCheckOutcome(IpCheckResult.UNKNOWN)
            } finally {
                runCatching { raw.close() }
            }
        }

    /**
     * Данные сертификата для UI. Ошибки чтения не должны ломать уже успешную проверку,
     * поэтому любая проблема просто оставляет сертификат пустым.
     */
    private fun certificateFacts(socket: SSLSocket): CertificateFacts? = runCatching {
        val certificate = socket.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: return@runCatching null
        CertificateFacts(
            issuer = certificate.issuerX500Principal.name,
            notAfterEpochMillis = certificate.notAfter.time,
        )
    }.getOrNull()

    private fun socketFactory() = SSLContext.getInstance("TLS").apply {
        // Стандартные trust store: сертификат обязан быть валидным именно на этот домен.
        init(null, null, null)
    }.socketFactory

    private companion object {
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    }
}
