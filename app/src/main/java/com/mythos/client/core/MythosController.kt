package com.mythos.client.core

import android.content.Context
import com.mythos.client.data.MythosStore
import com.mythos.client.model.AppSettings
import com.mythos.client.model.ManualProfileDraft
import com.mythos.client.model.ProxyProfile
import com.mythos.client.model.Subscription
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MythosController(context: Context) {
    private val appContext = context.applicationContext
    val store = MythosStore(appContext)
    val bridge = LibXrayBridge(java.io.File(appContext.cacheDir, "libxray"))
    private val importer = ProfileImporter(bridge)

    fun importText(text: String, sourceType: String = "manual", sourceId: String? = null): List<ProxyProfile> {
        val imported = importer.importText(text, sourceType, sourceId)
        val merged = mergeProfiles(store.loadProfiles(), imported, sourceId)
        store.saveProfiles(merged)
        if (store.loadSettings().selectedProfileId == null && merged.isNotEmpty()) {
            store.saveSettings(store.loadSettings().copy(
                selectedProfileId = merged.first().id,
                selectedMode = merged.first().protocol
            ))
        }
        log("Imported ${imported.size} profile(s) from $sourceType")
        return imported
    }

    fun importSubscription(name: String, url: String): List<ProxyProfile> {
        val existing = store.loadSubscriptions().firstOrNull { it.url == url }
        val subscription = existing ?: Subscription(name = name.ifBlank { "Subscription" }, url = url)
        val payload = NetworkTools.fetchSubscription(url)
        val imported = importer.importText(payload, sourceType = "subscription", sourceId = subscription.id)
        val merged = mergeProfiles(store.loadProfiles(), imported, subscription.id)
        store.saveProfiles(merged)
        val updated = subscription.copy(
            name = name.ifBlank { subscription.name },
            updatedAt = System.currentTimeMillis(),
            profileCount = imported.size
        )
        val subscriptions = store.loadSubscriptions().filterNot { it.id == updated.id } + updated
        store.saveSubscriptions(subscriptions)
        if (store.loadSettings().selectedProfileId == null && merged.isNotEmpty()) {
            store.saveSettings(store.loadSettings().copy(
                selectedProfileId = merged.first().id,
                selectedMode = merged.first().protocol
            ))
        }
        log("Updated subscription ${updated.name}: ${imported.size} profile(s)")
        return imported
    }

    fun deleteProfile(id: String) {
        val remaining = store.loadProfiles().filterNot { it.id == id }
        store.saveProfiles(remaining)
        val settings = store.loadSettings()
        if (settings.selectedProfileId == id) {
            val replacement = remaining.firstOrNull()
            store.saveSettings(settings.copy(
                selectedProfileId = replacement?.id,
                selectedMode = replacement?.protocol ?: settings.selectedMode
            ))
        }
        log("Deleted profile $id")
    }

    fun selectProfile(profile: ProxyProfile) {
        val s = store.loadSettings()
        store.saveSettings(s.copy(selectedProfileId = profile.id, selectedMode = profile.protocol))
        log("Selected ${profile.name}")
    }

    fun saveSettings(settings: AppSettings) = store.saveSettings(settings)

    fun saveManualProfile(draft: ManualProfileDraft): ProxyProfile {
        val built = ManualProfileBuilder.build(draft)
        // Validate against the actual bundled Xray core before anything is persisted.
        bridge.testXray(built.json)
        val imported = importer.importXrayJson(
            built.json,
            sourceType = "manual-builder",
            sourceId = null,
            validate = false
        ).firstOrNull() ?: throw IllegalStateException("Manual configuration produced no supported profile")
        val profile = imported.copy(name = draft.name.trim().ifBlank { "${draft.protocol.label} Manual" })
        val current = store.loadProfiles().filterNot { it.id == profile.id } + profile
        store.saveProfiles(current)
        store.saveSettings(store.loadSettings().copy(selectedProfileId = profile.id, selectedMode = profile.protocol))
        log("Created manual ${profile.protocol.label} profile ${profile.name}")
        return profile
    }

    fun validateManualProfile(draft: ManualProfileDraft): String {
        val built = ManualProfileBuilder.build(draft)
        bridge.testXray(built.json)
        return built.json
    }

    fun exportShareLink(profile: ProxyProfile): String = importer.shareLinks(profile).trim()

    fun exportJson(profile: ProxyProfile): String = importer.singleProfileJson(profile)

    fun testLatency(profile: ProxyProfile): Long {
        // Keep the full stored outbound set so dialerProxy/proxySettings dependencies remain available.
        val result = bridge.pingBatch(listOf(profile.xrayJson to profile.outboundTag), 7).firstOrNull()
            ?: throw IllegalStateException("No latency result returned")
        if (!result.success) throw IllegalStateException(result.error.ifBlank { "Latency test failed" })
        val updated = profile.copy(latencyMs = result.delay)
        store.saveProfiles(store.loadProfiles().map { if (it.id == profile.id) updated else it })
        log("Latency ${profile.name}: ${result.delay} ms")
        return result.delay
    }

    fun coreVersion(): String = bridge.version()

    fun clearLogs() {
        store.clearAppLogs()
        listOf("access.log", "error.log").forEach { name ->
            runCatching { java.io.File(appContext.filesDir, "xray/$name").writeText("") }
        }
    }

    fun combinedLogs(): String {
        val app = store.appLogs().trim()
        val error = runCatching { java.io.File(appContext.filesDir, "xray/error.log").readText() }.getOrDefault("").trim()
        val access = runCatching { java.io.File(appContext.filesDir, "xray/access.log").readText() }.getOrDefault("").trim()
        return buildString {
            if (app.isNotBlank()) appendLine("APP\n$app")
            if (error.isNotBlank()) appendLine("\nXRAY ERROR\n$error")
            if (access.isNotBlank()) appendLine("\nXRAY ACCESS\n$access")
            if (isBlank()) append("No logs yet.")
        }.takeLast(200_000)
    }

    fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        store.appendAppLog("$stamp  $message")
    }

    private fun mergeProfiles(current: List<ProxyProfile>, imported: List<ProxyProfile>, sourceId: String?): List<ProxyProfile> {
        val base = if (sourceId != null) current.filterNot { it.sourceId == sourceId } else current
        val fingerprints = base.map { fingerprint(it) }.toMutableSet()
        val result = base.toMutableList()
        imported.forEach { p ->
            val fp = fingerprint(p)
            if (fp !in fingerprints) {
                result += p
                fingerprints += fp
            }
        }
        return result
    }

    private fun fingerprint(p: ProxyProfile): String = listOf(
        p.protocol.wireName, p.server, p.port.toString(), p.transport, p.security, p.flow, p.xrayJson.hashCode().toString()
    ).joinToString("|")
}
