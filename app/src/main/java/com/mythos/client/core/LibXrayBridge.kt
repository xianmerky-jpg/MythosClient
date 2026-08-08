package com.mythos.client.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Thin reflection bridge around the official XTLS/libXray Android AAR.
 *
 * libXray changed its structured Invoke contract after v26.7.28. Mythos currently pins
 * the released v26.7.28 Android artifact, whose Invoke API is v1, while newer builds use v2.
 * This bridge detects the supported API at runtime and adapts the config payload shape.
 */
class LibXrayBridge(private val tempDir: File? = null) {
    private val libClass: Class<*> by lazy {
        val candidates = listOf(
            "libXray.LibXray",
            "go.libXray.LibXray",
            "libxray.LibXray",
            "go.libxray.LibXray"
        )
        candidates.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }
            ?: throw IllegalStateException("Official libXray Android library is not packaged in this APK")
    }

    data class PingResult(val success: Boolean, val delay: Long, val error: String)

    private fun findMethod(name: String, parameterCount: Int): Method =
        libClass.methods.firstOrNull { it.name.equals(name, true) && it.parameterCount == parameterCount }
            ?: throw IllegalStateException("libXray method $name/$parameterCount is unavailable")

    private fun callStatic(method: Method, vararg args: Any?): Any? = try {
        method.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw (e.targetException ?: e)
    }

    private fun rawEnvelope(apiVersion: Int, method: String, payload: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject()
            .put("apiVersion", apiVersion)
            .put("method", method)
            .put("payload", payload)
            .toString()
        val raw = callStatic(findMethod("invoke", 1), request)?.toString()
            ?: throw IllegalStateException("libXray returned no response")
        return JSONObject(raw)
    }

    /** Prefer the newest contract, then fall back to the v26.7.28 contract. */
    private val apiVersion: Int by lazy {
        for (candidate in intArrayOf(2, 1)) {
            val response = runCatching { rawEnvelope(candidate, "xrayVersion") }.getOrNull()
            if (response?.optBoolean("success") == true) return@lazy candidate
        }
        throw IllegalStateException("Unable to negotiate a supported libXray Invoke API version")
    }

    fun negotiatedApiVersion(): Int = apiVersion

    private fun invoke(method: String, payload: JSONObject = JSONObject()): Any? {
        val envelope = rawEnvelope(apiVersion, method, payload)
        if (!envelope.optBoolean("success")) {
            throw IllegalArgumentException(envelope.optString("error", "libXray request failed"))
        }
        return envelope.opt("data").takeUnless { it == JSONObject.NULL }
    }

    fun convertShareLinksToXrayJson(text: String): String {
        val data = invoke("convertShareLinksToXrayJson", JSONObject().put("text", text))
        return data?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("No supported proxy configuration found")
    }

    fun convertXrayJsonToShareLinks(xrayJson: String): String {
        val data = invoke("convertXrayJsonToShareLinks", JSONObject().put("xrayJson", xrayJson))
        if (data is JSONObject) return data.optString("links")
        return runCatching { JSONObject(data?.toString().orEmpty()).optString("links") }.getOrDefault("")
    }

    fun testXray(xrayJson: String) {
        if (apiVersion >= 2) {
            invoke("testXray", JSONObject().put("xrayJson", xrayJson))
        } else {
            withTempConfig("test", xrayJson) { config ->
                invoke("testXray", JSONObject().put("configPath", config.absolutePath))
            }
        }
    }

    fun runXray(xrayJson: String) {
        if (apiVersion >= 2) {
            invoke("runXray", JSONObject().put("xrayJson", xrayJson))
        } else {
            // v26.7.28 accepts in-memory JSON through this compatibility method.
            invoke("runXrayFromJson", JSONObject().put("configJSON", xrayJson))
        }
    }

    fun stopXray() {
        invoke("stopXray")
    }

    fun isRunning(): Boolean {
        val data = invoke("getXrayState")
        return (data as? JSONObject)?.optBoolean("running")
            ?: runCatching { JSONObject(data?.toString().orEmpty()).optBoolean("running") }.getOrDefault(false)
    }

    fun version(): String {
        val data = invoke("xrayVersion")
        return (data as? JSONObject)?.optString("version")
            ?: runCatching { JSONObject(data?.toString().orEmpty()).optString("version") }.getOrDefault("unknown")
    }

    fun pingBatch(configs: List<Pair<String, String>>, timeoutSeconds: Int = 6): List<PingResult> {
        require(configs.size <= 5) { "libXray supports at most five profiles per latency batch" }
        return if (apiVersion >= 2) {
            val items = JSONArray()
            configs.forEach { (json, tag) ->
                items.put(JSONObject().put("xrayJson", json).apply {
                    if (tag.isNotBlank()) put("outboundTag", tag)
                })
            }
            parsePingResults(invoke(
                "pingBatch",
                JSONObject()
                    .put("configs", items)
                    .put("timeout", timeoutSeconds)
                    .put("url", "https://cp.cloudflare.com/")
            ))
        } else {
            withTempConfigs("ping", configs.map { it.first }) { files ->
                val items = JSONArray()
                configs.forEachIndexed { index, (_, tag) ->
                    items.put(JSONObject().put("configPath", files[index].absolutePath).apply {
                        if (tag.isNotBlank()) put("outboundTag", tag)
                    })
                }
                parsePingResults(invoke(
                    "pingBatch",
                    JSONObject()
                        .put("configs", items)
                        .put("timeout", timeoutSeconds)
                        .put("url", "https://cp.cloudflare.com/")
                ))
            }
        }
    }

    private fun parsePingResults(data: Any?): List<PingResult> {
        val obj = data as? JSONObject
            ?: runCatching { JSONObject(data?.toString().orEmpty()) }.getOrNull()
            ?: return emptyList()
        val results = obj.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                add(PingResult(item.optBoolean("success"), item.optLong("delay"), item.optString("error")))
            }
        }
    }

    private fun <T> withTempConfig(prefix: String, json: String, block: (File) -> T): T =
        withTempConfigs(prefix, listOf(json)) { block(it.first()) }

    private fun <T> withTempConfigs(prefix: String, jsons: List<String>, block: (List<File>) -> T): T {
        val dir = tempDir ?: throw IllegalStateException("Temporary config directory is unavailable")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Could not create libXray temp directory")
        val files = mutableListOf<File>()
        try {
            jsons.forEach { json ->
                val file = File.createTempFile("mythos-$prefix-", ".json", dir)
                file.writeText(json, Charsets.UTF_8)
                files += file
            }
            return block(files)
        } finally {
            files.forEach { runCatching { it.delete() } }
        }
    }

    private fun proxyFor(interfaceType: Class<*>, protect: (Int) -> Boolean): Any =
        Proxy.newProxyInstance(interfaceType.classLoader, arrayOf(interfaceType)) { proxy, method, args ->
            when (method.name) {
                "ProtectFd", "protectFd" -> protect((args?.getOrNull(0) as? Number)?.toInt() ?: -1)
                "toString" -> "MythosDialerController"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        }

    fun registerSocketProtector(protect: (Int) -> Boolean) {
        val dialer = findMethod("registerDialerController", 1)
        val controller = proxyFor(dialer.parameterTypes[0], protect)
        callStatic(dialer, controller)

        runCatching {
            val listener = findMethod("registerListenerController", 1)
            callStatic(listener, proxyFor(listener.parameterTypes[0], protect))
        }
    }

    fun setDns(serverEndpoint: String, protect: (Int) -> Boolean) {
        val method = findMethod("setDNS", 2)
        val controller = proxyFor(method.parameterTypes[0], protect)
        callStatic(method, controller, serverEndpoint)
    }

    fun resetDns() {
        runCatching { callStatic(findMethod("resetDNS", 0)) }
    }
}
