package com.arny.dnshostsgenerator.dnsvpn

import android.content.Context
import android.util.Log
import com.arny.dnshostsgenerator.data.BuiltInDnsPresets
import com.arny.dnshostsgenerator.domain.BuiltInAppProfiles
import com.arny.dnshostsgenerator.domain.ProfileChecker
import com.arny.dnshostsgenerator.domain.resolvePerHost
import com.arny.dnshostsgenerator.domain.verifyRules
import com.arny.dnshostsgenerator.resolver.createDnsResolver
import com.arny.dnshostsgenerator.resolver.createHttpProber
import com.arny.dnshostsgenerator.resolver.createIpValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Owned by the VPN service: closing the screen must not cancel periodic checks. */
internal class RuleUpdater(context: Context, private val scope: CoroutineScope, private val applyRules: (String) -> Unit) {
    private val prefs = context.getSharedPreferences("dnsvpn", Context.MODE_PRIVATE)
    private val checker = ProfileChecker(createDnsResolver(), createIpValidator(), createHttpProber())
    private var job: Job? = null

    fun configure() {
        if (!prefs.getBoolean("auto", false)) {
            job?.cancel()
            job = null
            status("Автоподбор выключен")
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            delay(3_000)
            while (isActive) {
                try {
                    val completed = withTimeoutOrNull(90_000) { refresh(); true }
                    if (completed == null) status("Время проверки истекло; прежние правила сохранены")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("DnsVpn", "AUTO failed", error)
                    status("Ошибка проверки; прежние правила сохранены")
                }
                delay(15 * 60 * 1_000L)
            }
        }
    }

    private suspend fun refresh() {
        val original = prefs.getString("rules", "").orEmpty()
        val current = DnsRules.parse(original)
        status("Проверяю текущие адреса…")
        // Check every mapping, not just the first three (which omitted authentication).
        // Reserve most of the refresh deadline for discovery: a slow old mapping must
        // not consume the entire cycle and indefinitely starve repairs.
        val verified = withTimeoutOrNull(15_000) {
            current.mappings.isNotEmpty() && checker.verifyRules(current.mappings, current.mappings.size)
        } ?: false
        if (verified) {
            status("${stamp()}: текущие адреса прошли проверку; без изменений")
            return
        }
        val profile = BuiltInAppProfiles.chatGptAndroid
        val presets = BuiltInDnsPresets.defaults.filter { it.enabledByDefault }.take(4)
        for (preset in presets) {
            status("Проверка: ${preset.title} · ${profile.hosts.size} доменов")
            val result = checker.resolvePerHost(profile, preset, onProgress = { done, total ->
                status("${preset.title}: проверено $done/$total")
            })
            if (result.entries.isEmpty()) continue
            // A manual edit made during the network check takes precedence.
            if (prefs.getString("rules", "").orEmpty() != original) {
                status("Правила изменены вручную; результат проверки не применён")
                return
            }
            val merged = current.withMappings(result.entries)
            if (merged != current) applyRules(merged.toRulesText())
            status("${stamp()}: проверено ${result.entries.size}/${profile.hosts.size}; " +
                "не прошли ${result.failed.size} (прежние адреса сохранены)")
            return
        }
        status("${stamp()}: адреса не найдены; прежние правила сохранены")
    }

    private fun stamp(): CharSequence = android.text.format.DateFormat.format("HH:mm", System.currentTimeMillis())

    private fun status(message: String) {
        prefs.edit().putString("lastUpdate", message).apply()
        Log.i("DnsVpn", "AUTO: $message")
    }
}
