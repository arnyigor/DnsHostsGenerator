package com.arny.dnshostsgenerator.dnsvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.arny.dnshostsgenerator.resolver.DnsQueryOptions
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Минимальный DNS-VPN: забирает только DNS (A/AAAA), остальной трафик не трогает.
 *
 * Схема: TUN получает ровно один маршрут — адрес нашего DNS (10.111.222.2). Приложения
 * шлют запросы туда, мы читаем пакеты с TUN, для доменов из правил отвечаем сами,
 * остальное пересылаем в DoT-резолвер. Трафик приложений при этом идёт напрямую.
 */
class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val workers = Executors.newFixedThreadPool(WORKERS)
    private val upstream = createDnsResolver()
    private val answered = AtomicInteger()
    private val forwarded = AtomicInteger()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn(intent?.getStringExtra(EXTRA_RULES).orEmpty())
        }
        return START_STICKY
    }

    private fun startVpn(rulesText: String) {
        if (tun != null) {
            Log.i(TAG, "VPN уже запущен")
            return
        }
        val rules = DnsRules.parse(rulesText)
        Log.i(TAG, "Старт: подмен ${rules.mappings.size}, блокировок ${rules.blocked.size}")
        startForeground(NOTIFICATION_ID, buildNotification(rules))

        val builder = Builder()
            .setSession("DNS-подмены")
            .setMtu(1500)
            .addAddress(VPN_IPV4, 32)
            .addDnsServer(DNS_IPV4)
            .addRoute(DNS_IPV4, 32)
        // TaskBridge и сам генератор не должны зависеть от нашей службы: иначе пропадает
        // локальная связь, как только VPN перехватывает DNS.
        EXCLUDED_PACKAGES.forEach { pkg ->
            runCatching { builder.addDisallowedApplication(pkg) }
                .onFailure { Log.w(TAG, "Не удалось исключить $pkg: ${it.message}") }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching {
                builder.addAddress(VPN_IPV6, 128)
                builder.addDnsServer(DNS_IPV6)
                builder.addRoute(DNS_IPV6, 128)
            }
        }
        val descriptor = builder.establish()
        if (descriptor == null) {
            Log.e(TAG, "Не удалось поднять TUN (нет разрешения VPN?)")
            stopSelf()
            return
        }
        tun = descriptor
        running = true
        ruleCount = rules.mappings.size
        answeredQueries = 0
        forwardedQueries = 0
        reader = Thread({ readLoop(descriptor, rules) }, "dnsvpn-reader").also { it.start() }
        // Самопроверка без участия пользователя: процесс службы уже в нашем VPN,
        // поэтому штатный резолвер должен вернуть подменённые адреса.
        Thread({ selfTest(rules.mappings.keys.toList()) }, "dnsvpn-selftest").start()
    }

    private fun readLoop(descriptor: ParcelFileDescriptor, rules: DnsRules) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET)
        while (tun != null) {
            val length = try {
                input.read(buffer)
            } catch (error: Exception) {
                Log.w(TAG, "Чтение TUN прервано: ${error.message}")
                break
            }
            if (length <= 0) continue
            val packet = Ipv4Udp.parse(buffer, length) ?: continue
            if (packet.destinationPort != DNS_PORT) continue
            workers.execute {
                val payload = handleQuery(packet.payload, rules)
                if (payload != null && payload.isNotEmpty()) {
                    runCatching { output.write(Ipv4Udp.buildReply(packet, payload)) }
                        .onFailure { Log.w(TAG, "Запись в TUN не удалась: ${it.message}") }
                }
            }
        }
        runCatching { input.close() }
        runCatching { output.close() }
    }

    private fun handleQuery(payload: ByteArray, rules: DnsRules): ByteArray? {
        when (val decision = DnsAnswerer.decide(payload, rules)) {
            is DnsDecision.Reply -> {
                answered.incrementAndGet()
                logCounters()
                return decision.payload
            }
            DnsDecision.Forward -> Unit
        }
        val query = runCatching { org.xbill.DNS.Message(payload) }.getOrNull() ?: return null
        val question = query.question ?: return null
        val host = question.name.toString(true).trimEnd('.')
        val ips = runCatching {
            runBlocking {
                upstream.resolveA(
                    domain = host,
                    dnsServer = UPSTREAM_IPV4,
                    timeoutMillis = UPSTREAM_TIMEOUT_MS,
                    options = DnsQueryOptions(dotHost = UPSTREAM_DOT_HOST, allowPlainFallback = false),
                )
            }
        }.getOrElse { emptyList() }
        forwarded.incrementAndGet()
        logCounters()
        // Отвечаем всегда: если апстрим не дал адресов, отдаём пустой ответ, иначе
        // приложение-клиент будет ждать и решит, что сети нет.
        return DnsAnswerer.buildForwardedResponse(query, ips)
            ?: DnsAnswerer.buildEmptyResponse(query)
    }

    private fun selfTest(hosts: List<String>) {
        // Небольшая пауза: первый пакет, пришедший сразу после establish(), TUN может отбросить.
        Thread.sleep(2_500)
        hosts.take(SELFTEST_HOSTS).forEach { host ->
            val result = runCatching {
                java.net.InetAddress.getAllByName(host).joinToString()
            }.getOrElse { "ошибка: ${it.message}" }
            Log.i(TAG, "SELFTEST $host -> $result")
        }
    }

    private fun logCounters() {
        val answeredCount = answered.get()
        val forwardedCount = forwarded.get()
        answeredQueries = answeredCount
        forwardedQueries = forwardedCount
        if ((answeredCount + forwardedCount) % 20 == 0) {
            Log.i(TAG, "Ответов локально: $answeredCount, переслано: $forwardedCount")
        }
    }

    private fun buildNotification(rules: DnsRules): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "DNS-подмены", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val intent = Intent(this, MainActivity::class.java)
        val contentIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS-подмены включены")
            .setContentText("Правил: ${rules.mappings.size}")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun stopVpn() {
        running = false
        reader?.interrupt()
        reader = null
        runCatching { tun?.close() }
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Остановлено: локально ${answered.get()}, переслано ${forwarded.get()}")
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN отозван системой")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        workers.shutdownNow()
        super.onDestroy()
    }

    companion object {
        /** Состояние для интерфейса: служба живёт в том же процессе, поэтому флаг достаточен. */
        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        var ruleCount: Int = 0
            private set

        /** Сколько запросов обслужено нашими правилами. */
        @Volatile
        var answeredQueries: Int = 0
            private set

        /** Сколько запросов ушло во внешний резолвер. */
        @Volatile
        var forwardedQueries: Int = 0
            private set

        /** Пакеты, которые никогда не должны попадать в наш VPN. */
        private val EXCLUDED_PACKAGES = listOf(
            "ru.arny.taskbridge",
            "com.arny.dnshostsgenerator",
            // Себя тоже исключаем: иначе запросы к DoT-серверу из самой службы уходят
            // в собственный VPN и разрешение имён зацикливается (проверено 31.10.2026).
            "com.arny.dnshostsgenerator.dnsvpn",
        )

        private const val TAG = "DnsVpn"
        private const val CHANNEL_ID = "dnsvpn"
        private const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.arny.dnshostsgenerator.dnsvpn.START"
        const val ACTION_STOP = "com.arny.dnshostsgenerator.dnsvpn.STOP"
        const val EXTRA_RULES = "rules"

        private const val DNS_PORT = 53
        private const val VPN_IPV4 = "10.111.222.1"
        private const val DNS_IPV4 = "10.111.222.2"
        private const val VPN_IPV6 = "fd00:111:222::1"
        private const val DNS_IPV6 = "fd00:111:222::2"
        private const val UPSTREAM_IPV4 = "1.1.1.1"
        private const val UPSTREAM_DOT_HOST = "cloudflare-dns.com"
        private const val UPSTREAM_TIMEOUT_MS = 4_000
        private const val WORKERS = 4
        private const val MAX_PACKET = 4096
        private const val SELFTEST_HOSTS = 4
    }
}
