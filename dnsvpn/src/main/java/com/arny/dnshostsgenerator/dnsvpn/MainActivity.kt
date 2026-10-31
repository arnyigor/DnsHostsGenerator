package com.arny.dnshostsgenerator.dnsvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Тестовый экран минимального DNS-VPN. Отдельное приложение: основной
 * DnsHostsGenerator этот модуль не использует, поэтому его функциональность
 * остаётся как есть, даже если эксперимент не удастся.
 */
class MainActivity : Activity() {

    private lateinit var rulesInput: EditText
    private lateinit var status: TextView

    private val EXTRA_AUTORUN = "run"
    private val EXTRA_SELFTEST = "selftest"
    private val EXTRA_SELFTEST_HOSTS = "hosts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        status = TextView(this).apply { text = "Не запущено" }
        rulesInput = EditText(this).apply {
            setText(DEFAULT_RULES)
            minLines = 6
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val start = Button(this).apply {
            text = "Включить DNS-VPN"
            setOnClickListener { startVpn() }
        }
        val stop = Button(this).apply {
            text = "Выключить"
            setOnClickListener { stopVpn() }
        }
        layout.addView(TextView(this).apply { text = "Правила (>домен IP, домен = блок)" })
        layout.addView(rulesInput)
        layout.addView(start)
        layout.addView(stop)
        layout.addView(status)
        setContentView(layout)

        // Автозапуск для проверки с adb: am start ... --ez run true
        runSelfTestIfRequested(intent)

        if (intent?.getBooleanExtra(EXTRA_AUTORUN, false) == true) {
            rulesInput.setText(intent.getStringExtra(DnsVpnService.EXTRA_RULES) ?: DEFAULT_RULES)
            android.os.Handler(mainLooper).postDelayed({ startVpn() }, 500)
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runSelfTestIfRequested(intent)
    }

    /**
     * Самопроверка из adb: разрешаем домены штатным резолвером (трафик идёт через наш VPN)
     * и пишем в лог, что вернулось. Так видно, работает ли подмена, без участия пользователя.
     */
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

    private fun startVpn() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            startActivityForResult(prepare, REQUEST_VPN)
            return
        }
        launchService()
    }

    @Deprecated("Нужно для VpnService.prepare на старых API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) launchService() else status.text = "Разрешение VPN не выдано"
        }
    }

    private fun launchService() {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
            putExtra(DnsVpnService.EXTRA_RULES, rulesInput.text.toString())
        }
        startForegroundService(intent)
        status.text = "Запущено: ${DnsRules.parse(rulesInput.text.toString()).mappings.size} правил"
    }

    private fun stopVpn() {
        startService(Intent(this, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_STOP })
        status.text = "Остановлено"
    }

    private companion object {
        const val REQUEST_VPN = 1001
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
