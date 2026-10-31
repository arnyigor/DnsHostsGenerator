package com.arny.dnshostsgenerator.dnsvpn

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.domain.BuiltInAppProfiles
import com.arny.dnshostsgenerator.domain.ProfileChecker
import com.arny.dnshostsgenerator.domain.buildPersonalDnsFilterRules
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Экран правил: состояние службы, редактор правил и автоподбор адреса.
 *
 * Автоподбор использует [ProfileChecker] из основного проекта: он проверяет все домены
 * профиля и возвращает один адрес, годный для всех. Если адрес отличается от текущего,
 * правила обновляются, служба перезапускается, а пользователь получает уведомление.
 */
class MainActivity : Activity() {

    private lateinit var rulesInput: EditText
    private lateinit var status: TextView
    private lateinit var updateStatus: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var autoButton: Button

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            renderState()
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private var autoUpdateJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val checker by lazy {
        ProfileChecker(createDnsResolver(), createIpValidator(), createHttpProber())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        status = TextView(this).apply {
            textSize = 18f
            setPadding(0, 24, 0, 24)
        }
        updateStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }
        rulesInput = EditText(this).apply {
            setText(prefs.getString(KEY_RULES, null) ?: DEFAULT_RULES)
            minLines = 6
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        startButton = Button(this).apply {
            text = "Включить DNS-VPN"
            setOnClickListener { startVpn() }
        }
        stopButton = Button(this).apply {
            text = "Выключить"
            setOnClickListener { stopVpn() }
        }
        autoButton = Button(this).apply {
            text = "Автоподбор адреса: выключен"
            setOnClickListener { toggleAutoUpdate() }
        }
        layout.addView(TextView(this).apply { text = "Правила (>домен IP, домен = блок)" })
        layout.addView(rulesInput)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(autoButton)
        layout.addView(status)
        layout.addView(updateStatus)
        setContentView(layout)

        createNotificationChannel()
        renderState()
        runSelfTestIfRequested(intent)
        if (intent?.getBooleanExtra(EXTRA_AUTO, false) == true) {
            prefs.edit().putBoolean(KEY_AUTO, true).apply()
            renderAutoButton(true)
            startAutoUpdate()
        }
        if (intent?.getBooleanExtra(EXTRA_AUTORUN, false) == true) {
            android.os.Handler(mainLooper).postDelayed({ startVpn() }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(refreshTask)
        refreshHandler.post(refreshTask)
        val auto = prefs.getBoolean(KEY_AUTO, false)
        renderAutoButton(auto)
        if (auto && autoUpdateJob == null) startAutoUpdate()
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshTask)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runSelfTestIfRequested(intent)
    }

    // --- состояние ---

    private fun renderState() {
        val running = DnsVpnService.running
        status.text = if (running) {
            "ВКЛЮЧЕНО · правил ${DnsVpnService.ruleCount}" +
                " · ответов ${DnsVpnService.answeredQueries}" +
                " · переслано ${DnsVpnService.forwardedQueries}"
        } else {
            "ВЫКЛЮЧЕНО"
        }
        status.setTextColor(if (running) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
        startButton.isEnabled = !running
        stopButton.isEnabled = running
        updateStatus.text = prefs.getString(KEY_LAST_UPDATE, "Автоподбор ещё не запускался")
            .orEmpty()
    }

    private fun renderAutoButton(enabled: Boolean) {
        autoButton.text = if (enabled) "Автоподбор адреса: включён" else "Автоподбор адреса: выключен"
    }

    // --- автоподбор ---

    private fun toggleAutoUpdate() {
        val enabled = !prefs.getBoolean(KEY_AUTO, false)
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        renderAutoButton(enabled)
        if (enabled) startAutoUpdate() else stopAutoUpdate()
    }

    private fun startAutoUpdate() {
        if (autoUpdateJob != null) return
        autoUpdateJob = scope.launch {
            while (isActive) {
                // Первый запуск — с паузой: сразу после старта процесса сеть приложения
                // ещё не готова, и DNS отвечает UnknownHostException.
                delay(FIRST_REFRESH_DELAY_MS)
                refreshAddressWithRetry(reason = "автоподбор")
                delay(AUTO_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
    }

    /**
     * Подбирает адрес и, если он изменился, переписывает правила и перезапускает службу.
     * Возвращает новый адрес или null, если ничего не поменялось.
     */
    /**
     * Автоподбор с повтором: первый проход после холодного старта может не увидеть сеть.
     */
    private suspend fun refreshAddressWithRetry(reason: String): String? {
        repeat(REFRESH_ATTEMPTS) { attempt ->
            val ip = refreshAddress(reason = if (attempt == 0) reason else "$reason (повтор)")
            if (ip != null) return ip
            if (attempt < REFRESH_ATTEMPTS - 1) delay(RETRY_PAUSE_MS)
        }
        return null
    }

    private suspend fun refreshAddress(reason: String): String? {
        val profile = BuiltInAppProfiles.chatGptAndroid
        // Пробуем подряд несколько пресетов: конкретный DNS может отвалиться,
        // а нам нужен хоть один адрес, годный для всех доменов профиля.
        val presets = BuiltInDnsPresets.defaults
            .filter { it.enabledByDefault }
            .ifEmpty { BuiltInDnsPresets.defaults }
            .take(MAX_PRESETS_PER_REFRESH)
        var lastFailure: String? = null
        for (preset in presets) {
            updateStatus.text = "$reason: проверяю ${profile.hosts.size} доменов через ${preset.title}…"
            val result = withContext(Dispatchers.IO) { checker.check(profile, preset) }
            val newIp = result.ip
            android.util.Log.i(
                "DnsVpn",
                "AUTO ${preset.title}: ip=$newIp за ${result.elapsedMs} мс, кандидаты=${result.candidatesTried}",
            )
            if (newIp == null) {
                lastFailure = "${preset.title}: ${result.detail.orEmpty().take(80)}"
                continue
            }
            val currentIp = DnsRules.parse(rulesInput.text.toString()).singleIp()
            val stamp = android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis())
            if (currentIp == newIp) {
                val message = "$reason $stamp: адрес $newIp без изменений (${preset.title})"
                prefs.edit().putString(KEY_LAST_UPDATE, message).apply()
                updateStatus.text = message
                return null
            }
            val newRules = buildRulesText(profile.hosts, newIp)
            rulesInput.setText(newRules)
            prefs.edit().putString(KEY_RULES, newRules).apply()
            val message = "$reason $stamp: ${currentIp ?: "—"} → $newIp (${preset.title})"
            prefs.edit().putString(KEY_LAST_UPDATE, message).apply()
            updateStatus.text = message
            android.util.Log.i("DnsVpn", "AUTO: адрес изменён ${currentIp ?: "—"} → $newIp")
            notifyAddressChanged(currentIp, newIp)
            if (DnsVpnService.running) restartServiceWith(newRules)
            return newIp
        }
        val message = "$reason: рабочий адрес не найден (${lastFailure ?: "нет пресетов"})"
        prefs.edit().putString(KEY_LAST_UPDATE, message).apply()
        updateStatus.text = message
        android.util.Log.i("DnsVpn", "AUTO: $message")
        return null
    }

    private fun buildRulesText(hosts: List<String>, ip: String): String = buildString {
        hosts.forEach { appendLine(">$it $ip") }
        appendLine()
        hosts.forEach { appendLine(it) }
    }

    private fun notifyAddressChanged(oldIp: String?, newIp: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_UPDATES) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_UPDATES, "Смена адреса", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
        val notification = Notification.Builder(this, CHANNEL_UPDATES)
            .setContentTitle("Адрес подмены обновлён")
            .setContentText("${oldIp ?: "—"} → $newIp")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    // --- служба ---

    private fun startVpn() {
        prefs.edit().putString(KEY_RULES, rulesInput.text.toString()).apply()
        // Адрес прокси живёт часами, поэтому по умолчанию автоподбор включается вместе с VPN.
        // Если пользователь выключил его руками (ключ уже есть) — уважаем выбор.
        if (!prefs.contains(KEY_AUTO)) {
            prefs.edit().putBoolean(KEY_AUTO, true).commit()
            renderAutoButton(true)
            startAutoUpdate()
            updateStatus.text = "Автоподбор включён: адрес будет проверяться раз в 15 минут"
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            startActivityForResult(prepare, REQUEST_VPN)
            return
        }
        launchService(rulesInput.text.toString())
    }

    @Deprecated("Нужно для VpnService.prepare на старых API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) launchService(rulesInput.text.toString())
            else updateStatus.text = "Разрешение VPN не выдано"
        }
    }

    private fun launchService(rules: String) {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
            putExtra(DnsVpnService.EXTRA_RULES, rules)
        }
        startForegroundService(intent)
    }

    private fun restartServiceWith(rules: String) {
        startService(Intent(this, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_STOP })
        android.os.Handler(mainLooper).postDelayed({ launchService(rules) }, 700)
    }

    private fun stopVpn() {
        startService(Intent(this, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_STOP })
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshTask)
        stopAutoUpdate()
        scope.cancel()
        super.onDestroy()
    }

    // --- самопроверка для adb ---

    private fun runSelfTestIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SELFTEST, false) != true) return
        val hosts = intent.getStringExtra(EXTRA_SELFTEST_HOSTS)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf("chatgpt.com")
        Thread {
            hosts.forEach { host ->
                val result = runCatching { java.net.InetAddress.getAllByName(host).joinToString() }
                    .getOrElse { "ошибка: ${it.message}" }
                android.util.Log.i("DnsVpn", "SELFTEST $host -> $result")
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_UPDATES) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_UPDATES, "Смена адреса", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
    }

    private companion object {
        const val REQUEST_VPN = 1001
        const val REFRESH_INTERVAL_MS = 1_000L

        /** Период автоподбора: адреса прокси живут часами, чаще проверять смысла нет. */
        const val AUTO_UPDATE_INTERVAL_MS = 15 * 60 * 1000L
        const val FIRST_REFRESH_DELAY_MS = 3_000L
        const val RETRY_PAUSE_MS = 2_000L
        const val REFRESH_ATTEMPTS = 2

        /** Сколько DNS-серверов перебрать за один автоподбор. */
        const val MAX_PRESETS_PER_REFRESH = 4

        const val PREFS = "dnsvpn"
        const val KEY_RULES = "rules"
        const val KEY_AUTO = "auto"
        const val KEY_LAST_UPDATE = "lastUpdate"
        const val CHANNEL_UPDATES = "dnsvpn_updates"
        const val UPDATE_NOTIFICATION_ID = 43

        const val EXTRA_AUTORUN = "run"
        const val EXTRA_SELFTEST = "selftest"
        const val EXTRA_SELFTEST_HOSTS = "hosts"
        const val EXTRA_AUTO = "auto"

        val DEFAULT_RULES = """
            # Домены приложения ChatGPT
            >chatgpt.com 45.13.237.213
            >ws.chatgpt.com 45.13.237.213
            >ab.chatgpt.com 45.13.237.213
            >codex-cloud-backend.chatgpt.com 45.13.237.213
            >android.chat.openai.com 45.13.237.213
            >aw.api.openai.com 45.13.237.213
            >cdn.platform.openai.com 45.13.237.213
        """.trimIndent()
    }
}
