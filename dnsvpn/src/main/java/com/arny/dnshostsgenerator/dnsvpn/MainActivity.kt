package com.arny.dnshostsgenerator.dnsvpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Экран правил: состояние службы, редактор правил и автоподбор адреса.
 *
 * Автоподбор и применение правил принадлежат службе, а не жизненному циклу экрана.
 */
class MainActivity : Activity() {

    private lateinit var rulesInput: EditText
    private lateinit var status: TextView
    private lateinit var updateStatus: TextView
    private lateinit var startButton: Button
    private lateinit var autoButton: Button

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            renderState()
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }


    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
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
            setOnClickListener { if (DnsVpnService.running) stopVpn() else startVpn() }
        }
        autoButton = Button(this).apply {
            text = "Автоподбор адреса: выключен"
            setOnClickListener { toggleAutoUpdate() }
        }
        // Порядок важен: состояние и кнопки сверху, редактор правил — ниже и в остатке экрана.
        layout.addView(status)
        layout.addView(startButton)
        layout.addView(autoButton)
        layout.addView(updateStatus)
        rulesInput.visibility = android.view.View.GONE
        val applyButton = Button(this).apply {
            text = "Применить правила"
            visibility = android.view.View.GONE
            setOnClickListener {
                val rules = rulesInput.text.toString()
                prefs.edit().putString(KEY_RULES, rules).apply()
                if (DnsVpnService.running) startService(Intent(this@MainActivity, DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_RULES
                    putExtra(DnsVpnService.EXTRA_RULES, rules)
                })
                rulesInput.visibility = android.view.View.GONE
                visibility = android.view.View.GONE
            }
        }
        layout.addView(Button(this).apply {
            text = "Правила · ${DnsRules.parse(rulesInput.text.toString()).mappings.size} доменов ▾"
            setOnClickListener {
                val expanded = rulesInput.visibility != android.view.View.VISIBLE
                rulesInput.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
                applyButton.visibility = rulesInput.visibility
                text = if (expanded) "Скрыть правила ▴" else "Показать правила ▾"
                if (expanded) rulesInput.setText(prefs.getString(KEY_RULES, DEFAULT_RULES))
            }
        })
        layout.addView(applyButton)
        layout.addView(rulesInput)
        setContentView(layout)

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
        if (auto) startAutoUpdate()
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
        startButton.text = if (running) "Выключить DNS-VPN" else "Включить DNS-VPN"
        startButton.isEnabled = true
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
        if (DnsVpnService.running) startService(Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_AUTO
        })
    }

    private fun stopAutoUpdate() = startAutoUpdate()

    // --- служба ---

    private fun startVpn() {
        if (rulesInput.visibility != android.view.View.VISIBLE) {
            rulesInput.setText(prefs.getString(KEY_RULES, DEFAULT_RULES))
        }
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

    private fun stopVpn() {
        startService(Intent(this, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_STOP })
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshTask)
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

    private companion object {
        const val REQUEST_VPN = 1001
        const val REFRESH_INTERVAL_MS = 1_000L

        const val PREFS = "dnsvpn"
        const val KEY_RULES = "rules"
        const val KEY_AUTO = "auto"
        const val KEY_LAST_UPDATE = "lastUpdate"

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
