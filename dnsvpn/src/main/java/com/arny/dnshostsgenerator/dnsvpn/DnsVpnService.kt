package com.arny.dnshostsgenerator.dnsvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val updater by lazy { RuleUpdater(this, scope, ::applyRules) }
    @Volatile private var activeRules = DnsRules()
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val workers = Executors.newFixedThreadPool(WORKERS)
    private val answered = AtomicInteger()
    private val forwarded = AtomicInteger()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "COMMAND action=${intent?.action ?: "sticky-restore"} startId=$startId running=$running")
        when (intent?.action) {
            ACTION_AUTO -> {
                if (tun == null) { stopSelf(); return START_NOT_STICKY }
                updater.configure()
            }
            ACTION_RULES -> {
                if (tun == null) { stopSelf(); return START_NOT_STICKY }
                applyRules(intent.getStringExtra(EXTRA_RULES).orEmpty())
            }
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> startVpn(intent?.getStringExtra(EXTRA_RULES)
                ?: getSharedPreferences("dnsvpn", MODE_PRIVATE).getString("rules", "").orEmpty())
        }
        return START_STICKY
    }

    private fun startVpn(rulesText: String) {
        if (tun != null) {
            Log.i(TAG, "VPN уже запущен")
            return
        }
        val rules = DnsRules.parse(rulesText)
        activeRules = rules
        getSharedPreferences("dnsvpn", MODE_PRIVATE).edit().putString("rules", rulesText).apply()
        Log.i(TAG, "Старт: подмен ${rules.mappings.size}, блокировок ${rules.blocked.size}")
        startForeground(NOTIFICATION_ID, buildNotification(rules))
        UpstreamDot.logger = { message -> Log.w(TAG, message) }
        // Прогрев соединения: первый DNS-запрос приложения не должен ждать TLS-рукопожатия.
        UpstreamDot.warmUp()

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
        // Only IPv4 UDP DNS is implemented. Do not advertise a nonfunctional IPv6 DNS route.
        // Allow native IPv6 application traffic to bypass this IPv4-only DNS VPN.
        builder.allowFamily(android.system.OsConstants.AF_INET6)
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
        reader = Thread({ readLoop(descriptor) }, "dnsvpn-reader").also { it.start() }
        updater.configure()
    }

    private fun applyRules(text: String) {
        val parsed = DnsRules.parse(text)
        activeRules = parsed
        ruleCount = parsed.mappings.size
        getSharedPreferences("dnsvpn", MODE_PRIVATE).edit().putString("rules", text).apply()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(parsed))
        if (manager.getNotificationChannel("dnsvpn_updates") == null) {
            manager.createNotificationChannel(NotificationChannel(
                "dnsvpn_updates", "Смена адреса", NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        manager.notify(43, Notification.Builder(this, "dnsvpn_updates")
            .setContentTitle("Правила DNS обновлены")
            .setContentText("Доменов: $ruleCount · VPN не перезапускался")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setAutoCancel(true)
            .build())
        Log.i(TAG, "RULES applied without TUN restart: mappings=$ruleCount blocks=${parsed.blocked.size}")
    }

    private fun readLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET)
        try {
            while (tun === descriptor) {
                val length = input.read(buffer)
                if (length < 0) break
                if (length == 0) continue
                val packet = Ipv4Udp.parse(buffer, length) ?: continue
                if (packet.destinationPort != DNS_PORT) continue
                workers.execute {
                    val payload = handleQuery(packet.payload, activeRules)
                    if (payload != null && payload.isNotEmpty() && tun === descriptor) {
                        synchronized(output) {
                            runCatching { output.write(Ipv4Udp.buildReply(packet, payload)) }
                                .onFailure { Log.w(TAG, "TUN write failed: ${it.message}") }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "TUN reader exited: ${error.message}; active=${tun === descriptor}")
        } finally {
            if (tun === descriptor) android.os.Handler(mainLooper).post {
                if (tun === descriptor) {
                    Log.e(TAG, "Unexpected TUN reader exit; stopping unhealthy VPN")
                    stopVpn()
                }
            }
        }
    }

    private fun handleQuery(payload: ByteArray, rules: DnsRules): ByteArray? {
        when (val decision = DnsAnswerer.decide(payload, rules)) {
            is DnsDecision.Reply -> {
                answered.incrementAndGet()
                runCatching {
                    val question = org.xbill.DNS.Message(payload).question
                    val host = question?.name?.toString(true)?.trimEnd('.')
                    val mapped = host?.let { rules.mappingFor(it) }
                    Log.i(TAG, "ANSWER ${host ?: "?"} type=${question?.type} -> ${mapped ?: "blocked"}")
                }
                logCounters()
                return decision.payload
            }
            DnsDecision.Forward -> Unit
        }
        // Всё остальное пересылаем в DoT как есть: так сохраняются любые типы записей
        // (AAAA, HTTPS, MX, TXT) и приложения не ломаются.
        forwarded.incrementAndGet()
        logCounters()
        val response = runCatching { UpstreamDot.forward(payload) }.getOrNull()
        runCatching {
            val question = org.xbill.DNS.Message(payload).question
            Log.i(
                TAG,
                "FORWARD ${question?.name} type=${question?.type} " +
                    if (response == null) "-> апстрим не ответил" else "-> ${response.size} байт",
            )
        }
        if (response == null || response.isEmpty()) {
            val query = runCatching { org.xbill.DNS.Message(payload) }.getOrNull()
            Log.w(TAG, "Апстрим не ответил: ${query?.question?.name}")
            return query?.let { DnsAnswerer.buildEmptyResponse(it) }
        }
        return response
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
        Log.i(TAG, "STOP cleanup tun=${tun != null} running=$running")
        running = false
        val descriptor = tun
        tun = null
        reader?.interrupt()
        reader = null
        runCatching { descriptor?.close() }
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
        Log.i(TAG, "DESTROY service")
        scope.cancel()
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
            // adb (uid 2000) — через него работает TaskBridge и отладка по Wi-Fi:
            // если его трафик уходит в наш VPN, локальная связь с ПК рвётся.
            "com.android.shell",
        )

        private const val TAG = "DnsVpn"
        private const val CHANNEL_ID = "dnsvpn"
        private const val NOTIFICATION_ID = 42

        const val ACTION_RULES = "com.arny.dnshostsgenerator.dnsvpn.RULES"
        const val ACTION_AUTO = "com.arny.dnshostsgenerator.dnsvpn.AUTO"
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
