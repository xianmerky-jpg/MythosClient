package com.mythos.client.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.mythos.client.MainActivity
import com.mythos.client.R
import com.mythos.client.core.LibXrayBridge
import com.mythos.client.core.NetworkTools
import com.mythos.client.core.XrayConfigBuilder
import com.mythos.client.data.MythosStore
import com.mythos.client.model.VpnSnapshot
import com.mythos.client.model.VpnStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MythosVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val stopping = AtomicBoolean(false)
    private var tun: ParcelFileDescriptor? = null
    private var bridge: LibXrayBridge? = null
    private lateinit var store: MythosStore

    override fun onCreate() {
        super.onCreate()
        store = MythosStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> worker.execute { stopTunnel("Disconnected") }
            ACTION_CONNECT -> {
                startAsForeground("Connecting…")
                if (VpnStateBus.snapshot.status == VpnStatus.CONNECTING || VpnStateBus.snapshot.status == VpnStatus.CONNECTED) {
                    return Service.START_STICKY
                }
                worker.execute { startTunnel() }
            }
        }
        return Service.START_STICKY
    }

    private fun startTunnel() {
        stopping.set(false)
        val settings = store.loadSettings()
        val profile = store.loadProfiles().firstOrNull { it.id == settings.selectedProfileId }
        if (profile == null) {
            fail("Select or import a profile first")
            return
        }

        VpnStateBus.update(VpnSnapshot(VpnStatus.CONNECTING, profile.id, profile.name))
        log("Starting ${profile.name}")

        try {
            clearXrayLogs()
            val dns = NetworkTools.dnsServer(this, settings)
            log("DNS selected: $dns")

            // Xray consumes the Android VpnService TUN descriptor from native/Go code.
            // Use blocking mode so reads do not immediately return EAGAIN on the descriptor.
            val builder = Builder()
                .setSession("Mythos • ${profile.name}")
                .setMtu(1500)
                .setBlocking(true)
                .addAddress("172.30.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dns)

            if (settings.ipv6) {
                builder.addAddress("fd12:3456:789a::2", 64)
                builder.addRoute("::", 0)
            }

            tun = builder.establish() ?: throw IllegalStateException("Android did not create the VPN interface")
            val tunFd = tun!!.fd
            log("VPN interface established (fd=$tunFd)")

            val localBridge = LibXrayBridge(File(cacheDir, "libxray"))
            bridge = localBridge
            log("libXray ready • Xray ${runCatching { localBridge.version() }.getOrDefault("unknown")} • API ${localBridge.negotiatedApiVersion()}")

            localBridge.registerSocketProtector { fd -> fd >= 0 && protect(fd) }
            log("Socket protection registered")

            localBridge.setDns(dnsEndpoint(dns)) { fd -> fd >= 0 && protect(fd) }
            log("VPN-aware DNS resolver configured")

            val config = XrayConfigBuilder.build(
                profile = profile,
                tunFd = tunFd,
                routingMode = settings.routingMode,
                logDir = File(filesDir, "xray")
            )

            // Validate before starting so malformed/incompatible profile details surface as a
            // useful error instead of a connect/disconnect flicker.
            localBridge.testXray(config)
            log("Xray configuration validated")

            localBridge.runXray(config)
            log("Xray start request accepted")

            // The managed core can take a short moment to publish its running state.
            var running = false
            for (attempt in 0 until 25) {
                if (runCatching { localBridge.isRunning() }.getOrDefault(false)) {
                    running = true
                    break
                }
                Thread.sleep(100)
            }
            if (!running) {
                val tail = xrayErrorTail()
                throw IllegalStateException(
                    if (tail.isBlank()) "Xray-core did not enter running state"
                    else "Xray-core stopped during startup: $tail"
                )
            }

            val snapshot = VpnSnapshot(
                status = VpnStatus.CONNECTED,
                profileId = profile.id,
                profileName = profile.name,
                connectedAt = System.currentTimeMillis()
            )
            VpnStateBus.update(snapshot)
            startAsForeground("Connected • ${profile.name}")
            log("Connected ${profile.name}")
        } catch (t: Throwable) {
            val base = t.message?.trim().orEmpty().ifBlank { t.javaClass.simpleName }
            val tail = xrayErrorTail()
            val detail = if (tail.isNotBlank() && !base.contains(tail)) "$base • Xray: $tail" else base
            fail(detail)
        }
    }

    private fun fail(message: String) {
        log("Connection error: $message")
        cleanupCore()
        // Keep ERROR as the final state. Previous builds called stopSelf(), then onDestroy()
        // overwrote this with DISCONNECTED, which caused the visible status flicker and hid the
        // actual Xray failure from the UI.
        VpnStateBus.update(VpnSnapshot(VpnStatus.ERROR, error = message))
        removeForeground()
        stopSelf()
    }

    private fun stopTunnel(reason: String) {
        if (!stopping.compareAndSet(false, true)) return
        cleanupCore()
        VpnStateBus.update(VpnSnapshot(VpnStatus.DISCONNECTED))
        log(reason)
        removeForeground()
        stopSelf()
        stopping.set(false)
    }

    private fun cleanupCore() {
        runCatching { bridge?.stopXray() }
        runCatching { bridge?.resetDns() }
        runCatching { tun?.close() }
        tun = null
        bridge = null
    }

    override fun onRevoke() {
        worker.execute { stopTunnel("VPN permission revoked") }
        super.onRevoke()
    }

    override fun onDestroy() {
        val stateBeforeDestroy = VpnStateBus.snapshot.status
        cleanupCore()
        // Preserve a startup/connection error until the user retries. Only an unexpected service
        // destruction while actively connecting/connected should fall back to DISCONNECTED.
        if (stateBeforeDestroy == VpnStatus.CONNECTING || stateBeforeDestroy == VpnStatus.CONNECTED) {
            VpnStateBus.update(VpnSnapshot(VpnStatus.DISCONNECTED))
        }
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun clearXrayLogs() {
        val dir = File(filesDir, "xray")
        dir.mkdirs()
        listOf("access.log", "error.log").forEach { name ->
            runCatching { File(dir, name).writeText("") }
        }
    }

    private fun xrayErrorTail(maxLines: Int = 4): String {
        val file = File(filesDir, "xray/error.log")
        if (!file.exists()) return ""
        return runCatching {
            file.readLines()
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(maxLines)
                .joinToString(" | ")
                .take(900)
        }.getOrDefault("")
    }

    private fun dnsEndpoint(address: String): String =
        if (':' in address && !address.contains('.')) "[$address]:53" else "$address:53"

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MythosVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Mythos")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(R.drawable.ic_launcher, "Disconnect", disconnectIntent).build())
            .build()
    }

    @Suppress("DEPRECATION")
    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN connection", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the active Mythos VPN connection"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        store.appendAppLog("$stamp  $message")
    }

    companion object {
        const val ACTION_CONNECT = "com.mythos.client.CONNECT"
        const val ACTION_DISCONNECT = "com.mythos.client.DISCONNECT"
        private const val CHANNEL_ID = "mythos_vpn"
        private const val NOTIFICATION_ID = 4102

        fun connect(context: Context) {
            val intent = Intent(context, MythosVpnService::class.java).setAction(ACTION_CONNECT)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun disconnect(context: Context) {
            context.startService(Intent(context, MythosVpnService::class.java).setAction(ACTION_DISCONNECT))
        }
    }
}
