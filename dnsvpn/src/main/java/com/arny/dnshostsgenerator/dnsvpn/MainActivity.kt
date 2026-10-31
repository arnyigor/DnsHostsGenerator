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
import com.arny.dnshostsgenerator.domain.resolvePerHost
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
            // Поле тянется на весь экран и раньше выталкивало кнопки за пределы видимой части.
            // Теперь оно занимает остаток места и прокручивается внутри себя.
            minLines = 3
            isSingleLine = false
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = true
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
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
        // Порядок важен: состояние и кнопки сверху, редактор правил — ниже и в остатке экрана.
        layout.addView(status)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(autoButton)
        layout.addView(updateStatus)
        layout.addView(TextView(this).apply { text = "Правила (>домен IP, домен = блок)" })
        layout.addView(rulesInput)
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
        ensureBatteryExemption()
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

    /**
     * Просит исключить приложение из оптимизации батареи: без этого OxygenOS убивает службу
     * (в логах «Stopping service due to app idle» и «[BgDetect] kill … level 3»).
     */
    private fun ensureBatteryExemption() {
        val power = getSystemService(android.os.PowerManager::class.java)
        if (power?.isIgnoringBatteryOptimizations(packageName) == true) return
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { updateStatus.text = "Разрешите работу в фоне: Настройки → Батарея" }
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
            // Адрес подбирается для каждого домена отдельно: один прокси часто не обслуживает
            // часть доменов (вход через auth.openai.com), и «общий» адрес не находится.
            val result = withContext(Dispatchers.IO) { checker.resolvePerHost(profile, preset) }
            android.util.Log.i(
                "DnsVpn",
                "AUTO ${preset.title}: подобрано ${result.entries.size}/${profile.hosts.size} " +
                    "за ${result.elapsedMs} мс, не вышло: ${result.failed}",
            )
            if (!result.isUsable) {
                lastFailure = "${preset.title}: не подобрано ни одного адреса"
                continue
            }
            val newRules = buildPersonalDnsFilterRules(result.entries, profile.title)
            val currentIp = DnsRules.parse(rulesInput.text.toString()).singleIp()
            val newIps = result.entries.values.toSet().singleOrNull()
            if (newRules == rulesInput.text.toString()) {
                val message = "$reason: правила без изменений (адресов ${result.entries.size})"
                prefs.edit().putString(KEY_LAST_UPDATE, message).apply()
                updateStatus.text = message
                return null
            }
            rulesInput.setText(newRules)
            prefs.edit().putString(KEY_RULES, newRules).apply()
            val stamp = android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis())
            val message = "$reason $stamp: адресов ${result.entries.size}, неудачных ${result.failed.size}"
            prefs.edit().putString(KEY_LAST_UPDATE, message).apply()
            updateStatus.text = message
            android.util.Log.i("DnsVpn", "AUTO: правила обновлены ($message), было=$currentIp стало=$newIps")
            notifyAddressChanged(currentIp, newIps ?: "несколько адресов")
            if (DnsVpnService.running) restartServiceWith(newRules)
            return newIps
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
            # Домены ChatGPT и вход (проверено с телефона 31.10.2026)
            >chatgpt.com 37.221.93.128
            >ws.chatgpt.com 37.221.93.128
            >ab.chatgpt.com 37.221.93.128
            >codex-cloud-backend.chatgpt.com 37.221.93.128
            >android.chat.openai.com 37.221.93.128
            >aw.api.openai.com 37.221.93.128
            >cdn.platform.openai.com 37.221.93.128
            >auth.openai.com 37.221.93.128
            >auth0.openai.com 37.221.93.128
            >chat.openai.com 37.221.93.128
            >api.openai.com 37.221.93.128
            >cdn.oaistatic.com 37.221.93.128
            >files.oaiusercontent.com 37.221.93.128
        """.trimIndent()
    }
}
