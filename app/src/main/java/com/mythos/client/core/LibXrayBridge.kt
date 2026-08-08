package com.mythos.client.core

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Thin reflection bridge around the official XTLS/libXray Android AAR.
 * Reflection keeps Mythos resilient to gomobile's Java package casing while still calling
 * the official generated LibXray API at runtime.
 */
class LibXrayBridge {
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

    private fun invoke(method: String, payload: JSONObject = JSONObject()): Any? {
        val request = JSONObject()
            .put("apiVersion", 2)
            .put("method", method)
            .put("payload", payload)
            .toString()
        val raw = callStatic(findMethod("invoke", 1), request)?.toString()
            ?: throw IllegalStateException("libXray returned no response")
        val envelope = JSONObject(raw)
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
        // Defensive handling if a future wrapper encodes the object as text.
        return runCatching { JSONObject(data?.toString().orEmpty()).optString("links") }.getOrDefault("")
    }

    fun testXray(xrayJson: String) {
        invoke("testXray", JSONObject().put("xrayJson", xrayJson))
    }

    fun runXray(xrayJson: String) {
        invoke("runXray", JSONObject().put("xrayJson", xrayJson))
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
        val items = JSONArray()
        configs.forEach { (json, tag) ->
            items.put(JSONObject().put("xrayJson", json).apply {
                if (tag.isNotBlank()) put("outboundTag", tag)
            })
        }
        val data = invoke(
            "pingBatch",
            JSONObject()
                .put("configs", items)
                .put("timeout", timeoutSeconds)
                .put("url", "https://cp.cloudflare.com/")
        ) as? JSONObject ?: return emptyList()
        val results = data.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                add(PingResult(item.optBoolean("success"), item.optLong("delay"), item.optString("error")))
            }
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

        // Listener protection is available in current libXray and harmless for TUN-only mode.
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
