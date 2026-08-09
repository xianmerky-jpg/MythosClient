package com.mythos.client.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mythos.client.BuildConfig
import com.mythos.client.core.MythosController
import com.mythos.client.core.QrTools
import com.mythos.client.model.*
import com.mythos.client.vpn.MythosVpnService
import com.mythos.client.vpn.VpnStateBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private enum class Screen { Home, Profiles, EditProfile, Settings, Import, Export, ModeView, Routing, Dns, Logs, About }
private enum class HomeSheet { None, Options, Modes }

private fun ProtocolMode.icon(): ImageVector = when (this) {
    ProtocolMode.VLESS -> Icons.Outlined.VpnKey
    ProtocolMode.VMESS -> Icons.Outlined.Hub
    ProtocolMode.TROJAN -> Icons.Outlined.Security
    ProtocolMode.SHADOWSOCKS -> Icons.Outlined.Public
}

@Composable
fun MythosApp() {
    MythosTheme {
        val context = LocalContext.current
        val controller = remember { MythosController(context) }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }

        var profiles by remember { mutableStateOf(controller.store.loadProfiles()) }
        var settings by remember { mutableStateOf(controller.store.loadSettings()) }
        var vpn by remember { mutableStateOf(VpnStateBus.snapshot) }
        var screen by remember { mutableStateOf(Screen.Home) }
        var editingProfileId by remember { mutableStateOf<String?>(null) }
        var sheet by remember { mutableStateOf(HomeSheet.None) }
        var splash by remember { mutableStateOf(true) }
        var backStack by remember { mutableStateOf<List<Screen>>(emptyList()) }

        fun navigate(target: Screen) {
            sheet = HomeSheet.None
            if (target == screen) return
            backStack = backStack + screen
            screen = target
        }

        fun selectRoot(target: Screen) {
            sheet = HomeSheet.None
            backStack = emptyList()
            screen = target
        }

        fun goBack() {
            sheet = HomeSheet.None
            val previous = backStack.lastOrNull()
            if (previous == null) {
                screen = Screen.Home
            } else {
                backStack = backStack.dropLast(1)
                screen = previous
            }
        }

        fun reload() {
            profiles = controller.store.loadProfiles()
            settings = controller.store.loadSettings()
        }

        fun saveSettings(next: AppSettings) {
            settings = next
            controller.saveSettings(next)
        }

        val selectedProfile = profiles.firstOrNull { it.id == settings.selectedProfileId }
            ?: profiles.firstOrNull { it.protocol == settings.selectedMode }
            ?: profiles.firstOrNull()

        DisposableEffect(Unit) {
            val remove = VpnStateBus.addListener { vpn = it }
            onDispose(remove)
        }

        LaunchedEffect(Unit) {
            delay(650)
            splash = false
            val current = controller.store.loadSettings()
            val first = controller.store.loadProfiles().firstOrNull { it.id == current.selectedProfileId }
            if (current.connectOnLaunch && first != null && VpnService.prepare(context) == null && VpnStateBus.snapshot.status == VpnStatus.DISCONNECTED) {
                MythosVpnService.connect(context)
            }
        }

        val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) MythosVpnService.connect(context)
            else scope.launch { snackbar.showSnackbar("VPN permission was not granted") }
        }

        fun toggleConnection() {
            when (vpn.status) {
                VpnStatus.CONNECTED, VpnStatus.CONNECTING -> MythosVpnService.disconnect(context)
                else -> {
                    val p = selectedProfile
                    if (p == null) {
                        scope.launch { snackbar.showSnackbar("Import a profile before connecting") }
                        navigate(Screen.Import)
                        return
                    }
                    if (settings.selectedProfileId != p.id) {
                        val next = settings.copy(selectedProfileId = p.id, selectedMode = p.protocol)
                        saveSettings(next)
                    }
                    val intent = VpnService.prepare(context)
                    if (intent == null) MythosVpnService.connect(context) else vpnPermissionLauncher.launch(intent)
                }
            }
        }

        BackHandler(enabled = !splash && (sheet != HomeSheet.None || screen != Screen.Home)) {
            if (sheet != HomeSheet.None) sheet = HomeSheet.None else goBack()
        }

        if (splash) {
            Box(Modifier.fillMaxSize().background(MythosColors.Background)) { SplashScreen() }
        } else {
            val showNavigation = screen == Screen.Home || screen == Screen.Profiles || screen == Screen.Settings
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MythosColors.Background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (showNavigation) {
                        MythosNavigationBar(current = screen, onSelect = ::selectRoot)
                    }
                }
            ) { scaffoldPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = scaffoldPadding.calculateBottomPadding())
                ) {
                    Crossfade(screen, label = "screen") { target ->
                        when (target) {
                            Screen.Home -> HomeScreen(
                                selectedProfile = selectedProfile,
                                settings = settings,
                                vpn = vpn,
                                sheet = sheet,
                                onOpenOptions = { sheet = HomeSheet.Options },
                                onOpenModes = { sheet = HomeSheet.Modes },
                                onCloseSheet = { sheet = HomeSheet.None },
                                onSelectMode = { mode ->
                                    val firstMatch = profiles.firstOrNull { it.protocol == mode }
                                    saveSettings(settings.copy(selectedMode = mode, selectedProfileId = firstMatch?.id ?: settings.selectedProfileId))
                                },
                                onViewToggle = {
                                    saveSettings(settings.copy(modeViewEnabled = it))
                                    if (it) navigate(Screen.ModeView)
                                },
                                onModeView = { navigate(Screen.ModeView) },
                                onImport = { navigate(Screen.Import) },
                                onExport = { navigate(Screen.Export) },
                                onProfiles = { selectRoot(Screen.Profiles) },
                                onSettings = { selectRoot(Screen.Settings) },
                                onRouting = { navigate(Screen.Routing) },
                                onDns = { navigate(Screen.Dns) },
                                onToggleIpv6 = { saveSettings(settings.copy(ipv6 = !settings.ipv6)) },
                                onLogs = { navigate(Screen.Logs) },
                                onStart = ::toggleConnection
                            )

                            Screen.Profiles -> ProfilesScreen(
                                profiles = profiles,
                                selectedId = selectedProfile?.id,
                                vpnRunning = vpn.status == VpnStatus.CONNECTED || vpn.status == VpnStatus.CONNECTING,
                                onSelect = {
                                    controller.selectProfile(it)
                                    reload()
                                    selectRoot(Screen.Home)
                                },
                                onDelete = {
                                    controller.deleteProfile(it.id)
                                    reload()
                                },
                                onLatency = { profile ->
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { controller.testLatency(profile) } }
                                            .onSuccess { reload(); snackbar.showSnackbar("${profile.name}: $it ms") }
                                            .onFailure { snackbar.showSnackbar(it.message ?: "Latency test failed") }
                                    }
                                },
                                onEdit = { profile ->
                                    editingProfileId = profile.id
                                    navigate(Screen.EditProfile)
                                },
                                onImport = { navigate(Screen.Import) },
                                onBack = { selectRoot(Screen.Home) }
                            )

                            Screen.EditProfile -> EditProfileScreen(
                                controller = controller,
                                profile = profiles.firstOrNull { it.id == editingProfileId },
                                vpnRunning = vpn.status == VpnStatus.CONNECTED || vpn.status == VpnStatus.CONNECTING,
                                onSaved = { message ->
                                    reload()
                                    scope.launch { snackbar.showSnackbar(message) }
                                    goBack()
                                },
                                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                onBack = ::goBack
                            )

                            Screen.Settings -> SettingsScreen(
                                settings = settings,
                                onSettings = ::saveSettings,
                                onBack = { selectRoot(Screen.Home) },
                                onRouting = { navigate(Screen.Routing) },
                                onDns = { navigate(Screen.Dns) },
                                onLogs = { navigate(Screen.Logs) },
                                onAbout = { navigate(Screen.About) }
                            )

                            Screen.Import -> ImportScreen(
                                controller = controller,
                                onImported = { message -> reload(); scope.launch { snackbar.showSnackbar(message) }; selectRoot(Screen.Home) },
                                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                onBack = ::goBack
                            )

                            Screen.Export -> ExportScreen(
                                controller = controller,
                                profile = selectedProfile,
                                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                onBack = ::goBack
                            )

                            Screen.ModeView -> ModeViewScreen(
                                controller = controller,
                                mode = settings.selectedMode,
                                enabled = settings.modeViewEnabled,
                                profiles = profiles.filter { it.protocol == settings.selectedMode },
                                onEnabled = { saveSettings(settings.copy(modeViewEnabled = it)) },
                                onSaved = { message -> reload(); scope.launch { snackbar.showSnackbar(message) } },
                                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                onBack = ::goBack
                            )

                            Screen.Routing -> RoutingScreen(settings.routingMode, { saveSettings(settings.copy(routingMode = it)) }, ::goBack)
                            Screen.Dns -> DnsScreen(settings, ::saveSettings, ::goBack)
                            Screen.Logs -> LogsScreen(controller, ::goBack)
                            Screen.About -> AboutScreen(controller, ::goBack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MythosNavigationBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(
        containerColor = MythosColors.Panel,
        contentColor = MythosColors.Text,
        tonalElevation = 0.dp
    ) {
        listOf(
            Triple(Screen.Home, Icons.Outlined.Home, "Home"),
            Triple(Screen.Profiles, Icons.Outlined.Storage, "Profiles"),
            Triple(Screen.Settings, Icons.Outlined.Settings, "Settings")
        ).forEach { (screen, icon, label) ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(24.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.Border), modifier = Modifier.size(92.dp)) {
                Box(contentAlignment = Alignment.Center) { MythosMark(52.dp, MythosColors.Text) }
            }
            Spacer(Modifier.height(20.dp))
            Text("Mythos", color = MythosColors.Text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(5.dp))
            Text("XRAY NETWORK CLIENT", color = MythosColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
        }
    }
}

@Composable
private fun HomeScreen(
    selectedProfile: ProxyProfile?,
    settings: AppSettings,
    vpn: VpnSnapshot,
    sheet: HomeSheet,
    onOpenOptions: () -> Unit,
    onOpenModes: () -> Unit,
    onCloseSheet: () -> Unit,
    onSelectMode: (ProtocolMode) -> Unit,
    onViewToggle: (Boolean) -> Unit,
    onModeView: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onProfiles: () -> Unit,
    onSettings: () -> Unit,
    onRouting: () -> Unit,
    onDns: () -> Unit,
    onToggleIpv6: () -> Unit,
    onLogs: () -> Unit,
    onStart: () -> Unit
) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ProfessionalHeader(onSettings = onSettings, onLogs = onLogs) }
            item { ConnectionOverview(vpn = vpn, selectedProfile = selectedProfile, onToggle = onStart) }
            if (vpn.status == VpnStatus.ERROR && vpn.error.isNotBlank()) {
                item { ConnectionErrorCard(vpn.error, onLogs) }
            }
            item { ActiveProfileCard(profile = selectedProfile, onClick = onProfiles) }
            item { SectionLabel("Quick actions") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.SwapVert,
                        title = "Import / export",
                        supporting = "Configurations",
                        onClick = onOpenOptions
                    )
                    QuickActionTile(
                        modifier = Modifier.weight(1f),
                        icon = settings.selectedMode.icon(),
                        title = settings.selectedMode.label,
                        supporting = "Protocol mode",
                        onClick = onOpenModes
                    )
                }
            }
            item {
                SectionLabel("Network policy")
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PolicyTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Outlined.AltRoute,
                        label = "Routing",
                        value = settings.routingMode.label,
                        onClick = onRouting
                    )
                    PolicyTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Dns,
                        label = "DNS",
                        value = if (settings.dnsMode == DnsMode.CUSTOM) settings.customDns.ifBlank { "Custom" } else settings.dnsMode.label,
                        onClick = onDns
                    )
                }
            }
            item {
                PolicyWideRow(
                    icon = Icons.Outlined.Language,
                    title = "IPv6 routing",
                    subtitle = if (settings.ipv6) "IPv6 traffic is included in the VPN tunnel" else "IPv6 routing is disabled",
                    value = if (settings.ipv6) "On" else "Off",
                    onClick = onToggleIpv6
                )
            }
        }

        when (sheet) {
            HomeSheet.None -> Unit
            HomeSheet.Options -> OptionsSheet(onCloseSheet, onImport, onExport)
            HomeSheet.Modes -> ModesSheet(settings.selectedMode, settings.modeViewEnabled, onCloseSheet, onSelectMode, onViewToggle, onModeView)
        }
    }
}

@Composable
private fun ProfessionalHeader(onSettings: () -> Unit, onLogs: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                MythosMark(27.dp, MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Mythos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("Private network client", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HeaderIcon(Icons.AutoMirrored.Outlined.ReceiptLong, "Runtime logs", onLogs)
        Spacer(Modifier.width(4.dp))
        HeaderIcon(Icons.Outlined.Tune, "Settings", onSettings)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ConnectionOverview(vpn: VpnSnapshot, selectedProfile: ProxyProfile?, onToggle: () -> Unit) {
    var now by remember(vpn.status, vpn.connectedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vpn.status, vpn.connectedAt) {
        while (vpn.status == VpnStatus.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val uptime = if (vpn.status == VpnStatus.CONNECTED && vpn.connectedAt > 0L) {
        val total = ((now - vpn.connectedAt).coerceAtLeast(0L) / 1000L)
        "%02d:%02d:%02d".format(total / 3600L, (total % 3600L) / 60L, total % 60L)
    } else "00:00:00"
    val title = when (vpn.status) {
        VpnStatus.CONNECTED -> "Secure tunnel active"
        VpnStatus.CONNECTING -> "Establishing secure tunnel"
        VpnStatus.ERROR -> "Connection requires attention"
        VpnStatus.DISCONNECTED -> "Ready to connect"
    }
    val subtitle = when (vpn.status) {
        VpnStatus.CONNECTED -> "Traffic is being handled by the selected Mythos profile."
        VpnStatus.CONNECTING -> "Starting Android VPN and Xray services."
        VpnStatus.ERROR -> "Open logs for the exact Xray or Android VPN error."
        VpnStatus.DISCONNECTED -> "Select a profile, review policy, then start the tunnel."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (vpn.status == VpnStatus.CONNECTED) MaterialTheme.colorScheme.primary.copy(alpha = .55f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(vpn.status)
                Spacer(Modifier.weight(1f))
                Text(uptime, color = MythosColors.TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(18.dp))
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = if (vpn.status == VpnStatus.CONNECTED) MaterialTheme.colorScheme.primaryContainer else MythosColors.Interactive
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (vpn.status == VpnStatus.CONNECTED) ConnectedMythosMark(58.dp)
                        else MythosMark(48.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (vpn.status == VpnStatus.CONNECTED) {
                Spacer(Modifier.height(18.dp))
                SessionTrafficBand(vpn)
            }
            if (vpn.status == VpnStatus.CONNECTING) {
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
            Spacer(Modifier.height(20.dp))
            when (vpn.status) {
                VpnStatus.CONNECTED, VpnStatus.CONNECTING -> FilledTonalButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Icon(if (vpn.status == VpnStatus.CONNECTED) Icons.Outlined.Stop else Icons.Outlined.Close, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (vpn.status == VpnStatus.CONNECTED) "Disconnect" else "Cancel connection")
                }
                else -> Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Outlined.PowerSettingsNew, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (selectedProfile == null) "Choose a profile" else "Connect")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun QuickActionTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 104.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MythosColors.Panel,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionTrafficBand(vpn: VpnSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MythosColors.Soft,
        border = BorderStroke(1.dp, MythosColors.BorderSoft)
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "LIVE THROUGHPUT",
                        color = MythosColors.TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = .8.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "1-second samples • current session",
                        color = MythosColors.TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Text(
                    "48s",
                    color = MythosColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(12.dp))
            LiveTrafficChart(
                download = vpn.downloadHistory,
                upload = vpn.uploadHistory,
                modifier = Modifier.fillMaxWidth().height(92.dp)
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                TrafficMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.South,
                    label = "DOWNLOAD",
                    current = "${formatBytes(vpn.bytesInPerSecond)}/s",
                    peak = "Peak ${formatBytes(vpn.peakBytesInPerSecond)}/s",
                    total = "Total ${formatBytes(vpn.bytesIn)}"
                )
                Spacer(Modifier.width(12.dp))
                TrafficMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.North,
                    label = "UPLOAD",
                    current = "${formatBytes(vpn.bytesOutPerSecond)}/s",
                    peak = "Peak ${formatBytes(vpn.peakBytesOutPerSecond)}/s",
                    total = "Total ${formatBytes(vpn.bytesOut)}"
                )
            }
        }
    }
}

@Composable
private fun LiveTrafficChart(download: List<Long>, upload: List<Long>, modifier: Modifier = Modifier) {
    val maxValue = maxOf(
        1L,
        download.maxOrNull() ?: 0L,
        upload.maxOrNull() ?: 0L
    ).toFloat()

    Canvas(modifier = modifier) {
        val gridColor = MythosColors.BorderSoft
        val downloadColor = MythosColors.AccentMuted
        val uploadColor = MythosColors.TextMuted

        // Crisp, low-contrast guide lines: no translucent card wash or bright accent colors.
        repeat(3) { index ->
            val y = size.height * (index + 1) / 4f
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun buildPath(values: List<Long>): Path {
            val path = Path()
            if (values.isEmpty()) return path
            val denominator = (values.size - 1).coerceAtLeast(1).toFloat()
            values.forEachIndexed { index, value ->
                val x = size.width * index / denominator
                val normalized = (value.toFloat() / maxValue).coerceIn(0f, 1f)
                val y = size.height - (normalized * size.height * .88f) - size.height * .04f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        if (download.isNotEmpty()) {
            drawPath(
                path = buildPath(download),
                color = downloadColor,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        if (upload.isNotEmpty()) {
            drawPath(
                path = buildPath(upload),
                color = uploadColor,
                style = Stroke(
                    width = 1.7.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                )
            )
        }
    }
}

@Composable
private fun TrafficMetric(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    current: String,
    peak: String,
    total: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = MythosColors.Interactive,
        border = BorderStroke(1.dp, MythosColors.BorderSoft)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    color = MythosColors.TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .7.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                current,
                color = MythosColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Text(peak, color = MythosColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(total, color = MythosColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    return when {
        safe >= 1024.0 * 1024.0 * 1024.0 -> String.format(java.util.Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024.0 * 1024.0 -> String.format(java.util.Locale.US, "%.2f MB", safe / (1024.0 * 1024.0))
        safe >= 1024.0 -> String.format(java.util.Locale.US, "%.1f KB", safe / 1024.0)
        else -> "${safe.toLong()} B"
    }
}

@Composable
private fun StatusBadge(status: VpnStatus) {
    val text = when (status) {
        VpnStatus.CONNECTED -> "CONNECTED"
        VpnStatus.CONNECTING -> "CONNECTING"
        VpnStatus.ERROR -> "ERROR"
        VpnStatus.DISCONNECTED -> "DISCONNECTED"
    }
    val contentColor = when (status) {
        VpnStatus.CONNECTED -> MythosColors.Success
        VpnStatus.CONNECTING -> MythosColors.Warning
        VpnStatus.ERROR -> MaterialTheme.colorScheme.error
        VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = contentColor.copy(alpha = .12f),
        border = BorderStroke(1.dp, contentColor.copy(alpha = .36f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(contentColor))
            Spacer(Modifier.width(7.dp))
            Text(text, color = contentColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActiveProfileCard(profile: ProxyProfile?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MythosColors.Panel,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(profile?.protocol?.icon() ?: Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("ACTIVE PROFILE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(3.dp))
                    Text(profile?.name ?: "No profile selected", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                profile?.latencyMs?.let { Text("$it ms", color = MythosColors.TextSecondary, fontSize = 12.sp) }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = MythosColors.TextMuted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(15.dp))
            HorizontalDivider(color = MythosColors.BorderSoft)
            Spacer(Modifier.height(14.dp))
            if (profile == null) {
                Text("Import a configuration or create a manual profile to begin.", color = MythosColors.TextSecondary, fontSize = 13.sp)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailPill(profile.protocol.label, Modifier.weight(1f))
                    DetailPill(profile.transport.ifBlank { "Default transport" }, Modifier.weight(1f))
                    DetailPill(profile.security.ifBlank { "No security label" }, Modifier.weight(1f))
                }
                if (profile.server.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("${profile.server}:${profile.port}", color = MythosColors.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DetailPill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MythosColors.Interactive) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

@Composable
private fun PolicyTile(modifier: Modifier, icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 104.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MythosColors.Panel, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PolicyWideRow(icon: ImageVector, title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MythosColors.Panel, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = MythosColors.TextMuted, fontSize = 11.sp, maxLines = 2)
            }
            Text(value, color = MythosColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConnectionErrorCard(message: String, onLogs: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onLogs), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .28f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .55f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Connection error", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(message, color = MythosColors.TextSecondary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("Open runtime logs", color = MythosColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetBase(heightFraction: Float, title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = MythosColors.Panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = .64f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
                    Text("Mythos control panel", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun OptionsSheet(onClose: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    SheetBase(.62f, "Configuration", onClose) {
        Text("Move profiles into or out of Mythos.", color = MythosColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OptionRow(Icons.Outlined.FileDownload, "Import configuration", "Links, QR images, local files, raw JSON and HTTPS subscriptions", onImport)
        Spacer(Modifier.height(10.dp))
        OptionRow(Icons.Outlined.FileUpload, "Export configuration", "Share the active profile as a link, QR code or Xray JSON", onExport)
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MythosColors.Elevated, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = MythosColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MythosColors.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ModesSheet(selected: ProtocolMode, viewEnabled: Boolean, onClose: () -> Unit, onSelect: (ProtocolMode) -> Unit, onToggle: (Boolean) -> Unit, onView: () -> Unit) {
    SheetBase(.88f, "Connection modes", onClose) {
        Text("Choose the protocol used by the active profile. Manual setup exposes protocol and transport fields for advanced configurations.", color = MythosColors.TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
            items(ProtocolMode.entries) { item -> ModeRow(item, item == selected, viewEnabled, { onSelect(item) }, onToggle, onView) }
        }
    }
}

@Composable
private fun ModeRow(mode: ProtocolMode, selected: Boolean, viewEnabled: Boolean, onSelect: () -> Unit, onToggle: (Boolean) -> Unit, onView: () -> Unit) {
    if (selected) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MythosColors.Elevated, border = BorderStroke(1.dp, MythosColors.Border)) {
            Column(Modifier.padding(17.dp)) {
                Row(Modifier.clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MythosColors.Interactive) {
                        Box(contentAlignment = Alignment.Center) { Icon(mode.icon(), null, tint = MythosColors.Text, modifier = Modifier.size(23.dp)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, color = MythosColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(mode.description, color = MythosColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Icon(Icons.Outlined.CheckCircle, null, tint = MythosColors.Text, modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MythosColors.BorderSoft)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(onClick = onView)) {
                        Text("Manual setup", color = MythosColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("View and configure every ${mode.label} field", color = MythosColors.TextMuted, fontSize = 10.sp)
                    }
                    Switch(checked = viewEnabled, onCheckedChange = onToggle, colors = monochromeSwitchColors())
                }
            }
        }
    } else {
        Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect), shape = RoundedCornerShape(20.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = MythosColors.Soft) {
                    Box(contentAlignment = Alignment.Center) { Icon(mode.icon(), null, tint = MythosColors.TextSecondary, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, color = MythosColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(mode.description, color = MythosColors.TextSecondary, fontSize = 11.sp, maxLines = 2)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MythosColors.TextMuted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun ProfilesScreen(
    profiles: List<ProxyProfile>, selectedId: String?, vpnRunning: Boolean,
    onSelect: (ProxyProfile) -> Unit, onDelete: (ProxyProfile) -> Unit, onLatency: (ProxyProfile) -> Unit,
    onEdit: (ProxyProfile) -> Unit, onImport: () -> Unit, onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var protocolFilter by remember { mutableStateOf<ProtocolMode?>(null) }
    val visible = profiles.filter { p ->
        (protocolFilter == null || p.protocol == protocolFilter) &&
            (query.isBlank() || listOf(p.name, p.server, p.protocol.label, p.transport, p.security).any { it.contains(query, ignoreCase = true) })
    }

    ScreenScaffold("Profiles", null, Icons.Outlined.Storage) {
        Text("Saved proxy configurations", color = MythosColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotBlank()) Icon(Icons.Outlined.Close, null, modifier = Modifier.clickable { query = "" }) },
            placeholder = { Text("Search name, endpoint or protocol") },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MythosColors.Border,
                unfocusedBorderColor = MythosColors.BorderSoft,
                focusedContainerColor = MythosColors.Panel,
                unfocusedContainerColor = MythosColors.Panel,
                focusedTextColor = MythosColors.Text,
                unfocusedTextColor = MythosColors.Text
            )
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ProtocolFilterChip("All", protocolFilter == null) { protocolFilter = null } }
            items(ProtocolMode.entries) { mode -> ProtocolFilterChip(mode.label, protocolFilter == mode) { protocolFilter = mode } }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${visible.size} profile${if (visible.size == 1) "" else "s"}", color = MythosColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Surface(modifier = Modifier.clickable(onClick = onImport), shape = RoundedCornerShape(14.dp), color = MythosColors.Interactive, border = BorderStroke(1.dp, MythosColors.Border)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, null, tint = MythosColors.Text, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import", color = MythosColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            InfoCard("No matching profiles", if (profiles.isEmpty()) "Import a VLESS, VMess, Trojan or Shadowsocks configuration." else "Change the search or protocol filter.")
        }
        visible.forEach { p ->
            ProfileCard(
                profile = p,
                selected = p.id == selectedId,
                vpnRunning = vpnRunning,
                onSelect = { onSelect(p) },
                onLatency = { onLatency(p) },
                onEdit = { onEdit(p) },
                onDelete = { onDelete(p) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProtocolFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MythosColors.Panel,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f)
        )
    )
}

@Composable
private fun ProfileCard(profile: ProxyProfile, selected: Boolean, vpnRunning: Boolean, onSelect: () -> Unit, onLatency: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(profile.id) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This removes the saved profile from this device. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f) else MythosColors.Panel,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .52f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(43.dp), shape = RoundedCornerShape(14.dp), color = MythosColors.Interactive) {
                    Box(contentAlignment = Alignment.Center) { Icon(profile.protocol.icon(), null, tint = MythosColors.TextSecondary, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f).clickable(onClick = onSelect)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, color = MythosColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text("ACTIVE", color = MythosColors.Text, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(listOf(profile.protocol.label, profile.transport, profile.security).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { profile.detail }, color = MythosColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile.server.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text("${profile.server}:${profile.port}", color = MythosColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MythosColors.TextMuted, modifier = Modifier.size(18.dp).clickable(onClick = onSelect))
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MythosColors.BorderSoft)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, null, tint = MythosColors.TextMuted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(profile.latencyMs?.let { "$it ms" } ?: "Latency not tested", color = MythosColors.TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onLatency, enabled = !vpnRunning, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Test")
                }
                TextButton(onClick = onEdit, enabled = !vpnRunning, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete profile", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EditProfileScreen(
    controller: MythosController,
    profile: ProxyProfile?,
    vpnRunning: Boolean,
    onSaved: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    if (profile == null) {
        ScreenScaffold("Edit profile", onBack, Icons.Outlined.Edit) {
            InfoCard("Profile unavailable", "The selected profile no longer exists.")
        }
        return
    }

    val parsed = remember(profile.id, profile.xrayJson) { runCatching { controller.manualDraftForProfile(profile) } }
    val initialDraft = parsed.getOrNull()
    if (initialDraft == null) {
        ScreenScaffold("Edit profile", onBack, Icons.Outlined.Edit) {
            InfoCard(
                "Field editor unavailable",
                parsed.exceptionOrNull()?.message ?: "This profile contains a structure that cannot be mapped safely into Mythos fields. The saved profile has not been changed."
            )
            Spacer(Modifier.height(12.dp))
            InfoCard("Safety first", "Mythos refuses lossy field edits instead of silently deleting advanced Xray options from an imported profile.")
        }
        return
    }

    var draft by remember(profile.id, profile.xrayJson) { mutableStateOf(initialDraft) }
    var busy by remember { mutableStateOf(false) }

    ScreenScaffold("Edit ${profile.protocol.label} profile", onBack, Icons.Outlined.Edit) {
        InfoCard(
            "Field-based editor",
            "Edit this profile using the same controls as Manual setup. Existing transport, TLS/REALITY, Mux, sockopt and advanced fields are loaded into their matching sections."
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            profile.name,
            listOf(profile.server.takeIf { it.isNotBlank() }, profile.port.takeIf { it > 0 }?.toString(), profile.transport.takeIf { it.isNotBlank() }, profile.security.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString(" · ").ifBlank { profile.detail }
        )
        Spacer(Modifier.height(22.dp))

        ManualProfileFields(draft) { draft = it }

        Spacer(Modifier.height(22.dp))
        if (vpnRunning) {
            InfoCard("Disconnect before editing", "Profile changes are locked while the VPN is active so the running tunnel cannot drift from the saved configuration.")
        } else {
            SecondaryAction("Validate changes", busy) {
                busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { controller.validateManualProfile(draft) } }
                        .onSuccess { onMessage("Configuration is valid") }
                        .onFailure { onMessage(it.message ?: "Configuration validation failed") }
                    busy = false
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryAction("Validate & save changes", busy) {
                busy = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { controller.updateManualProfile(profile, draft) } }
                        .onSuccess { updated -> onSaved("${updated.name} updated") }
                        .onFailure { onMessage(it.message ?: "Could not update profile") }
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        InfoCard("Safe editing", "Every save is validated by the bundled Xray core. Edited subscription profiles are detached from their subscription source so a later refresh cannot silently overwrite your changes.")
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, onSettings: (AppSettings) -> Unit, onBack: () -> Unit, onRouting: () -> Unit, onDns: () -> Unit, onLogs: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold("Settings", null, Icons.Outlined.Settings) {
        SettingsRow(Icons.AutoMirrored.Outlined.AltRoute, "Routing", settings.routingMode.label, onRouting)
        SettingsRow(Icons.Outlined.Dns, "DNS", if (settings.dnsMode == DnsMode.CUSTOM) settings.customDns.ifBlank { "Custom" } else settings.dnsMode.label, onDns)
        ToggleSettingsRow(Icons.Outlined.Language, "IPv6", "Route IPv6 traffic through the VPN", settings.ipv6) { onSettings(settings.copy(ipv6 = it)) }
        ToggleSettingsRow(Icons.Outlined.Power, "Connect on launch", "Reconnect automatically when VPN permission already exists", settings.connectOnLaunch) { onSettings(settings.copy(connectOnLaunch = it)) }
        SettingsRow(Icons.AutoMirrored.Outlined.Article, "Logs", "App and Xray runtime logs", onLogs)
        SettingsRow(Icons.Outlined.VpnKey, "Android VPN settings", "Open system VPN settings") {
            runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
        }
        SettingsRow(Icons.Outlined.Info, "About", "Core and app version", onAbout)
    }
}

@Composable
private fun ImportScreen(controller: MythosController, onImported: (String) -> Unit, onMessage: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf("Clipboard") }
    var text by remember { mutableStateOf("") }
    var subscriptionName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun importPayload(payload: String, source: String) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { controller.importText(payload, sourceType = source) } }
                .onSuccess { onImported("Imported ${it.size} profile(s)") }
                .onFailure { onMessage(it.message ?: "Import failed") }
            busy = false
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not read file") }
                .onSuccess { importPayload(it, "file") }.onFailure { onMessage(it.message ?: "Could not read file") }
        }
    }
    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("Could not read image")
                        QrTools.decode(bitmap)
                    }
                }.onSuccess { importPayload(it, "qr") }.onFailure { onMessage(it.message ?: "No readable QR code found") }
            }
        }
    }

    ScreenScaffold("Import", onBack, Icons.Outlined.FolderOpen) {
        Text("Choose source", color = MythosColors.TextSecondary, fontSize = 14.sp); Spacer(Modifier.height(14.dp))
        val methods = listOf(
            Triple(Icons.Outlined.ContentPaste, "Clipboard", "Read a VLESS / VMess / Trojan / SS link"),
            Triple(Icons.Outlined.QrCodeScanner, "QR code", "Read a QR code from an image or screenshot"),
            Triple(Icons.Outlined.Description, "File / JSON", "Read a local text or JSON configuration"),
            Triple(Icons.Outlined.Link, "Subscription URL", "Fetch an HTTPS subscription"),
            Triple(Icons.Outlined.Code, "Raw JSON / Link", "Paste an Xray JSON config or share link")
        )
        methods.forEach { (icon, title, subtitle) ->
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { chosen = title }, shape = RoundedCornerShape(20.dp), color = if (chosen == title) MythosColors.Elevated else MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 17.sp); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 12.sp) }
                    if (chosen == title) Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (chosen) {
            "Clipboard" -> PrimaryAction("Read clipboard & import", busy) {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val value = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (value.isBlank()) onMessage("Clipboard is empty") else importPayload(value, "clipboard")
            }
            "QR code" -> PrimaryAction("Choose QR image", busy) { qrLauncher.launch("image/*") }
            "File / JSON" -> PrimaryAction("Choose file", busy) { fileLauncher.launch("*/*") }
            "Subscription URL" -> {
                OutlinedTextField(subscriptionName, { subscriptionName = it }, label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { text = it }, label = { Text("https://…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(12.dp))
                PrimaryAction("Fetch & import", busy) {
                    val url = text.trim()
                    if (!url.startsWith("https://")) { onMessage("Subscription URL must use HTTPS"); return@PrimaryAction }
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { controller.importSubscription(subscriptionName, url) } }
                            .onSuccess { onImported("Subscription imported ${it.size} profile(s)") }
                            .onFailure { onMessage(it.message ?: "Subscription import failed") }
                        busy = false
                    }
                }
            }
            else -> {
                OutlinedTextField(text, { text = it }, label = { Text("Configuration") }, modifier = Modifier.fillMaxWidth(), minLines = 7, maxLines = 14)
                Spacer(Modifier.height(12.dp))
                PrimaryAction("Validate & import", busy) { if (text.isBlank()) onMessage("Paste a configuration first") else importPayload(text, "manual") }
            }
        }
    }
}

@Composable
private fun ExportScreen(controller: MythosController, profile: ProxyProfile?, onMessage: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qrText by remember { mutableStateOf<String?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingJson
        if (uri != null && payload != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) } ?: error("Could not open destination") }
                .onSuccess { onMessage("JSON exported") }.onFailure { onMessage(it.message ?: "Export failed") }
        }
        pendingJson = null
    }

    fun withShareLink(action: (String) -> Unit) {
        val p = profile ?: return
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { controller.exportShareLink(p) } }
                .onSuccess { link -> if (link.isBlank()) onMessage("This profile cannot be represented as a share link") else action(link) }
                .onFailure { onMessage(it.message ?: "Export failed") }
            busy = false
        }
    }

    ScreenScaffold("Export", onBack, Icons.Outlined.IosShare) {
        if (profile == null) {
            InfoCard("No profile selected", "Import and select a profile first.")
            return@ScreenScaffold
        }
        InfoCard(profile.name, "${profile.protocol.label} · ${profile.detail}")
        Spacer(Modifier.height(18.dp))
        SettingsRow(Icons.Outlined.Link, "Share link", "Share through Android") {
            withShareLink { link ->
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }, "Share Mythos profile"))
            }
        }
        SettingsRow(Icons.Outlined.QrCode, "QR code", "Generate a scannable share QR") { withShareLink { qrText = it } }
        SettingsRow(Icons.Outlined.Description, "JSON file", "Save the selected Xray outbound as JSON") {
            pendingJson = controller.exportJson(profile)
            saveLauncher.launch("${profile.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
        }
        SettingsRow(Icons.Outlined.ContentCopy, "Copy", "Copy the share link to clipboard") {
            withShareLink { link ->
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Mythos profile", link))
                onMessage("Share link copied")
            }
        }
        if (busy) { Spacer(Modifier.height(20.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MythosColors.Text) }
        qrText?.let { value ->
            Spacer(Modifier.height(22.dp))
            val bitmap = remember(value) { QrTools.encode(value, 720) }
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF3F3EF), modifier = Modifier.fillMaxWidth()) {
                Image(bitmap.asImageBitmap(), "Profile QR", modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(18.dp))
            }
        }
    }
}

@Composable
private fun ModeViewScreen(
    controller: MythosController,
    mode: ProtocolMode,
    enabled: Boolean,
    profiles: List<ProxyProfile>,
    onEnabled: (Boolean) -> Unit,
    onSaved: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var draft by remember(mode) {
        mutableStateOf(
            ManualProfileDraft(
                protocol = mode,
                name = "${mode.label} Manual",
                security = SecurityMode.TLS
            )
        )
    }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }

    ScreenScaffold("${mode.label} manual setup", onBack, mode.icon()) {
        InfoCard(mode.label, mode.description)
        Spacer(Modifier.height(16.dp))
        ToggleSettingsRow(
            Icons.Outlined.Tune,
            "Manual configuration",
            "Enable access to every ${mode.label} protocol, transport and security field",
            enabled,
            onEnabled
        )
        Spacer(Modifier.height(12.dp))
        InfoCard("Stored profiles", "${profiles.size} ${mode.label} profile(s) currently stored")

        if (!enabled) {
            Spacer(Modifier.height(16.dp))
            InfoCard("Manual configuration is disabled", "Enable the switch above to build a ${mode.label} profile manually. Imported profiles continue to work normally.")
            return@ScreenScaffold
        }

        Spacer(Modifier.height(24.dp))
        ManualProfileFields(draft) { draft = it }

        Spacer(Modifier.height(22.dp))
        SecondaryAction("Validate configuration", busy) {
            busy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { controller.validateManualProfile(draft) } }
                    .onSuccess { json -> preview = json; onMessage("Configuration is valid") }
                    .onFailure { onMessage(it.message ?: "Configuration validation failed") }
                busy = false
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryAction("Validate & save profile", busy) {
            busy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { controller.saveManualProfile(draft) } }
                    .onSuccess { profile -> preview = ""; onSaved("${profile.name} saved and selected") }
                    .onFailure { onMessage(it.message ?: "Could not save manual profile") }
                busy = false
            }
        }
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            ManualSectionTitle("Validated JSON preview")
            Surface(shape = RoundedCornerShape(20.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft)) {
                Text(preview, color = MythosColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
            }
        }
    }
}


@Composable
private fun ManualProfileFields(draft: ManualProfileDraft, onDraft: (ManualProfileDraft) -> Unit) {
    ManualSectionTitle("Profile")
    ManualTextField(draft.name, { onDraft(draft.copy(name = it)) }, "Profile name", "My ${draft.protocol.label} server")
    ManualTextField(draft.address, { onDraft(draft.copy(address = it)) }, "Server address", "example.com or 1.2.3.4")
    ManualTextField(draft.port, { onDraft(draft.copy(port = it)) }, "Port", "443")

    Spacer(Modifier.height(20.dp))
    ManualSectionTitle("${draft.protocol.label} settings")
    when (draft.protocol) {
        ProtocolMode.VLESS -> {
            ManualTextField(draft.credential, { onDraft(draft.copy(credential = it)) }, "UUID / ID", "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
            ManualTextField(draft.vlessEncryption, { onDraft(draft.copy(vlessEncryption = it)) }, "Encryption", "none or server-provided VLESS Encryption")
            ManualChoiceField("Flow", draft.flow.ifBlank { "None" }, listOf("None", "xtls-rprx-vision", "xtls-rprx-vision-udp443")) { selected ->
                onDraft(draft.copy(flow = if (selected == "None") "" else selected))
            }
            ManualTextField(draft.level, { onDraft(draft.copy(level = it)) }, "Level", "0")
        }
        ProtocolMode.VMESS -> {
            ManualTextField(draft.credential, { onDraft(draft.copy(credential = it)) }, "VMess ID", "UUID or custom ID")
            ManualChoiceField("VMess security", draft.vmessSecurity, listOf("auto", "aes-128-gcm", "chacha20-poly1305")) { onDraft(draft.copy(vmessSecurity = it)) }
            ManualTextField(draft.vmessExperiments, { onDraft(draft.copy(vmessExperiments = it)) }, "Experiments", "Optional, e.g. AuthenticatedLength")
            ManualTextField(draft.level, { onDraft(draft.copy(level = it)) }, "Level", "0")
        }
        ProtocolMode.TROJAN -> {
            ManualTextField(draft.credential, { onDraft(draft.copy(credential = it)) }, "Password", "Trojan password")
            ManualTextField(draft.email, { onDraft(draft.copy(email = it)) }, "Email", "Optional")
            ManualTextField(draft.level, { onDraft(draft.copy(level = it)) }, "Level", "0")
            InfoCard("Security note", "Public Trojan servers should normally use TLS. Mythos still validates the final combination against Xray before saving.")
        }
        ProtocolMode.SHADOWSOCKS -> {
            ManualTextField(draft.credential, { onDraft(draft.copy(credential = it)) }, "Password / PSK", "Server password or Shadowsocks 2022 key")
            ManualChoiceField(
                "Encryption method",
                draft.shadowsocksMethod,
                listOf(
                    "2022-blake3-aes-128-gcm",
                    "2022-blake3-aes-256-gcm",
                    "2022-blake3-chacha20-poly1305",
                    "aes-256-gcm",
                    "aes-128-gcm",
                    "chacha20-poly1305",
                    "xchacha20-poly1305"
                )
            ) { onDraft(draft.copy(shadowsocksMethod = it)) }
            ManualTextField(draft.email, { onDraft(draft.copy(email = it)) }, "Email", "Optional")
            ManualTextField(draft.level, { onDraft(draft.copy(level = it)) }, "Level", "0")
        }
    }

    Spacer(Modifier.height(20.dp))
    ManualSectionTitle("Transport")
    ManualChoiceField("Transport", draft.transport.label, TransportMode.entries.map { it.label }) { selected ->
        val transport = TransportMode.entries.first { it.label == selected }
        val nextSecurity = if (draft.security == SecurityMode.REALITY && transport !in setOf(TransportMode.RAW, TransportMode.XHTTP, TransportMode.GRPC)) SecurityMode.TLS else draft.security
        onDraft(draft.copy(transport = transport, security = nextSecurity, muxEnabled = if (transport == TransportMode.GRPC) false else draft.muxEnabled))
    }
    val allowedSecurity = buildList {
        add(SecurityMode.NONE)
        add(SecurityMode.TLS)
        if (draft.transport in setOf(TransportMode.RAW, TransportMode.XHTTP, TransportMode.GRPC)) add(SecurityMode.REALITY)
    }
    ManualChoiceField("Transport security", draft.security.label, allowedSecurity.map { it.label }) { selected ->
        onDraft(draft.copy(security = allowedSecurity.first { it.label == selected }))
    }

    when (draft.transport) {
        TransportMode.RAW -> {
            ManualChoiceField("RAW header", draft.rawHeaderType, listOf("none", "http")) { onDraft(draft.copy(rawHeaderType = it)) }
            if (draft.rawHeaderType == "http") {
                ManualJsonField(draft.rawHeaderJson, { onDraft(draft.copy(rawHeaderJson = it)) }, "RAW HTTP header JSON", "{\"request\":{...},\"response\":{...}}")
            }
        }
        TransportMode.XHTTP -> {
            ManualTextField(draft.host, { onDraft(draft.copy(host = it)) }, "Host", "Optional host / CDN host")
            ManualTextField(draft.path, { onDraft(draft.copy(path = it)) }, "Path", "/xhttp")
            ManualChoiceField("XHTTP mode", draft.xhttpMode, listOf("auto", "stream-up", "stream-one", "packet-up")) { onDraft(draft.copy(xhttpMode = it)) }
            ManualJsonField(draft.xhttpHeadersJson, { onDraft(draft.copy(xhttpHeadersJson = it)) }, "Headers JSON", "{\"User-Agent\":\"...\"}")
            ManualJsonField(draft.xhttpExtraJson, { onDraft(draft.copy(xhttpExtraJson = it)) }, "XHTTP extra JSON", "Padding, xmux, placements, downloadSettings, etc.")
        }
        TransportMode.WEBSOCKET -> {
            ManualTextField(draft.host, { onDraft(draft.copy(host = it)) }, "Host", "Optional Host header")
            ManualTextField(draft.path, { onDraft(draft.copy(path = it)) }, "Path", "/")
            ManualJsonField(draft.headersJson, { onDraft(draft.copy(headersJson = it)) }, "Headers JSON", "{\"User-Agent\":\"...\"}")
            ManualTextField(draft.wsHeartbeatPeriod, { onDraft(draft.copy(wsHeartbeatPeriod = it)) }, "Heartbeat period (seconds)", "0 = disabled")
        }
        TransportMode.HTTP_UPGRADE -> {
            ManualTextField(draft.host, { onDraft(draft.copy(host = it)) }, "Host", "Optional Host header")
            ManualTextField(draft.path, { onDraft(draft.copy(path = it)) }, "Path", "/")
            ManualJsonField(draft.headersJson, { onDraft(draft.copy(headersJson = it)) }, "Headers JSON", "{\"User-Agent\":\"...\"}")
        }
        TransportMode.GRPC -> {
            ManualTextField(draft.grpcAuthority, { onDraft(draft.copy(grpcAuthority = it)) }, "Authority", "Optional")
            ManualTextField(draft.grpcServiceName, { onDraft(draft.copy(grpcServiceName = it)) }, "Service name", "e.g. grpc")
            ManualTextField(draft.grpcUserAgent, { onDraft(draft.copy(grpcUserAgent = it)) }, "User-Agent", "Optional")
            ManualSwitchField("Multi mode", "Experimental gRPC multi-mode", draft.grpcMultiMode) { onDraft(draft.copy(grpcMultiMode = it)) }
            ManualTextField(draft.grpcIdleTimeout, { onDraft(draft.copy(grpcIdleTimeout = it)) }, "Idle timeout", "0 = disabled")
            ManualTextField(draft.grpcHealthCheckTimeout, { onDraft(draft.copy(grpcHealthCheckTimeout = it)) }, "Health-check timeout", "20")
            ManualSwitchField("Permit without stream", "Allow health checks without active streams", draft.grpcPermitWithoutStream) { onDraft(draft.copy(grpcPermitWithoutStream = it)) }
            ManualTextField(draft.grpcInitialWindowSize, { onDraft(draft.copy(grpcInitialWindowSize = it)) }, "Initial window size", "0 = default")
            InfoCard("Mux", "Mythos disables outbound Mux with gRPC because gRPC already multiplexes over HTTP/2.")
        }
        TransportMode.MKCP -> {
            ManualTextField(draft.kcpMtu, { onDraft(draft.copy(kcpMtu = it)) }, "MTU", "Core minimum 21; typical 1350")
            ManualTextField(draft.kcpTti, { onDraft(draft.copy(kcpTti = it)) }, "TTI (ms)", "10–1000")
            ManualTextField(draft.kcpUplinkCapacity, { onDraft(draft.copy(kcpUplinkCapacity = it)) }, "Uplink capacity", "5")
            ManualTextField(draft.kcpDownlinkCapacity, { onDraft(draft.copy(kcpDownlinkCapacity = it)) }, "Downlink capacity", "20")
            ManualTextField(draft.kcpCwndMultiplier, { onDraft(draft.copy(kcpCwndMultiplier = it)) }, "CWND multiplier", "2 or server-compatible value")
            ManualTextField(draft.kcpMaxSendingWindow, { onDraft(draft.copy(kcpMaxSendingWindow = it)) }, "Max sending window", "0 = core default")
            InfoCard("Current mKCP", "Legacy header/seed fields are intentionally not offered because this bundled Xray generation rejects them.")
        }
        TransportMode.HYSTERIA -> {
            ManualTextField(draft.hysteriaAuth, { onDraft(draft.copy(hysteriaAuth = it)) }, "Hysteria transport auth", "Optional transport password")
            ManualTextField(draft.hysteriaUdpIdleTimeout, { onDraft(draft.copy(hysteriaUdpIdleTimeout = it)) }, "UDP idle timeout", "60")
            ManualJsonField(draft.hysteriaMasqueradeJson, { onDraft(draft.copy(hysteriaMasqueradeJson = it)) }, "Masquerade JSON", "Optional Hysteria HTTP/3 masquerade object")
        }
    }
    ManualJsonField(draft.transportExtraJson, { onDraft(draft.copy(transportExtraJson = it)) }, "Transport advanced JSON", "Merged into the selected transport settings")

    Spacer(Modifier.height(20.dp))
    ManualSectionTitle("${draft.security.label} security")
    when (draft.security) {
        SecurityMode.NONE -> InfoCard("No outer security", "Use only when the server configuration expects no TLS/REALITY. VLESS and Trojan on public networks normally require an outer security layer.")
        SecurityMode.TLS -> {
            ManualTextField(draft.tlsServerName, { onDraft(draft.copy(tlsServerName = it)) }, "Server Name / SNI", "example.com")
            ManualTextField(draft.tlsFingerprint, { onDraft(draft.copy(tlsFingerprint = it)) }, "Fingerprint", "chrome")
            ManualTextField(draft.tlsAlpn, { onDraft(draft.copy(tlsAlpn = it)) }, "ALPN", "h2,http/1.1")
            ManualTextField(draft.tlsMinVersion, { onDraft(draft.copy(tlsMinVersion = it)) }, "Minimum TLS version", "1.2 or blank")
            ManualTextField(draft.tlsMaxVersion, { onDraft(draft.copy(tlsMaxVersion = it)) }, "Maximum TLS version", "1.3 or blank")
            InfoCard("Certificate verification", "The bundled Xray core removed allowInsecure. Use a correct SNI/certificate, certificate pinning, or verify-by-name instead.")
            ManualTextField(draft.tlsVerifyPeerCertByName, { onDraft(draft.copy(tlsVerifyPeerCertByName = it)) }, "Verify peer certificate by name", "Optional")
            ManualTextField(draft.tlsPinnedPeerCertSha256, { onDraft(draft.copy(tlsPinnedPeerCertSha256 = it)) }, "Pinned peer cert SHA-256", "Optional")
            ManualTextField(draft.tlsCurvePreferences, { onDraft(draft.copy(tlsCurvePreferences = it)) }, "Curve preferences", "Comma-separated, optional")
            ManualJsonField(draft.tlsExtraJson, { onDraft(draft.copy(tlsExtraJson = it)) }, "TLS advanced JSON", "ECH, session-resumption and future TLS fields")
        }
        SecurityMode.REALITY -> {
            ManualTextField(draft.realityServerName, { onDraft(draft.copy(realityServerName = it)) }, "Server Name / SNI", "Required")
            ManualTextField(draft.realityFingerprint, { onDraft(draft.copy(realityFingerprint = it)) }, "Fingerprint", "chrome")
            ManualTextField(draft.realityPassword, { onDraft(draft.copy(realityPassword = it)) }, "Password / public key", "REALITY client credential")
            ManualTextField(draft.realityShortId, { onDraft(draft.copy(realityShortId = it)) }, "Short ID", "Optional")
            ManualTextField(draft.realitySpiderX, { onDraft(draft.copy(realitySpiderX = it)) }, "SpiderX", "Optional")
            ManualTextField(draft.realityMldsa65Verify, { onDraft(draft.copy(realityMldsa65Verify = it)) }, "ML-DSA-65 verify", "Optional")
            ManualJsonField(draft.realityExtraJson, { onDraft(draft.copy(realityExtraJson = it)) }, "REALITY advanced JSON", "Additional current/future client fields")
        }
    }

    Spacer(Modifier.height(20.dp))
    ManualSectionTitle("Mux & outbound")
    ManualChoiceField(
        "Target strategy",
        draft.targetStrategy,
        listOf("AsIs", "UseIP", "UseIPv6v4", "UseIPv6", "UseIPv4v6", "UseIPv4", "ForceIP", "ForceIPv6v4", "ForceIPv6", "ForceIPv4v6", "ForceIPv4")
    ) { onDraft(draft.copy(targetStrategy = it)) }
    if (draft.transport != TransportMode.GRPC) {
        ManualSwitchField("Mux", "Multiplex multiple requests through fewer connections", draft.muxEnabled) { onDraft(draft.copy(muxEnabled = it)) }
        if (draft.muxEnabled) {
            ManualTextField(draft.muxConcurrency, { onDraft(draft.copy(muxConcurrency = it)) }, "Mux concurrency", "1–128, -1 disables TCP mux")
            ManualTextField(draft.xudpConcurrency, { onDraft(draft.copy(xudpConcurrency = it)) }, "XUDP concurrency", "1–1024, -1 disables XUDP mux")
            ManualChoiceField("UDP/443 through Mux", draft.xudpProxyUdp443, listOf("reject", "allow", "skip")) { onDraft(draft.copy(xudpProxyUdp443 = it)) }
        }
    }
    ManualJsonField(draft.sockoptJson, { onDraft(draft.copy(sockoptJson = it)) }, "Sockopt JSON", "Interface, domainStrategy, TCP keepalive, dialerProxy, etc.")

    Spacer(Modifier.height(20.dp))
    ManualSectionTitle("Advanced")
    InfoCard("Safe escape hatches", "These JSON objects are merged into generated Xray sections. This keeps Mythos compatible with advanced/new Xray fields without removing core-side validation.")
    Spacer(Modifier.height(10.dp))
    ManualJsonField(draft.protocolExtraJson, { onDraft(draft.copy(protocolExtraJson = it)) }, "Protocol advanced JSON", "Merged into protocol settings")
    ManualJsonField(draft.streamExtraJson, { onDraft(draft.copy(streamExtraJson = it)) }, "Stream advanced JSON", "Merged into streamSettings")
    ManualJsonField(draft.outboundExtraJson, { onDraft(draft.copy(outboundExtraJson = it)) }, "Outbound advanced JSON", "Merged into the outbound object")
}

@Composable
private fun ManualSectionTitle(text: String) {
    Text(text, color = MythosColors.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ManualTextField(value: String, onValue: (String) -> Unit, label: String, placeholder: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MythosColors.Border,
            unfocusedBorderColor = MythosColors.BorderSoft,
            focusedContainerColor = MythosColors.Panel,
            unfocusedContainerColor = MythosColors.Panel,
            focusedTextColor = MythosColors.Text,
            unfocusedTextColor = MythosColors.Text
        )
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ManualJsonField(value: String, onValue: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MythosColors.Border,
            unfocusedBorderColor = MythosColors.BorderSoft,
            focusedContainerColor = MythosColors.Panel,
            unfocusedContainerColor = MythosColors.Panel,
            focusedTextColor = MythosColors.Text,
            unfocusedTextColor = MythosColors.Text
        ),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ManualChoiceField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember(label, value, options) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(58.dp).clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MythosColors.Panel,
            border = BorderStroke(1.dp, MythosColors.Border)
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = MythosColors.TextMuted, fontSize = 11.sp)
                    Text(value, color = MythosColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = MythosColors.TextSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    trailingIcon = { if (option == value) Icon(Icons.Outlined.Check, null) },
                    onClick = { expanded = false; onSelect(option) }
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ManualSwitchField(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 15.sp)
                Text(subtitle, color = MythosColors.TextSecondary, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onChecked, colors = monochromeSwitchColors())
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun RoutingScreen(selected: RoutingMode, onSelect: (RoutingMode) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Routing", onBack, Icons.AutoMirrored.Outlined.AltRoute) {
        Text("Traffic policy", color = MythosColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        RoutingMode.entries.forEach { mode ->
            SelectablePolicyCard(
                icon = when (mode) {
                    RoutingMode.GLOBAL -> Icons.Outlined.Public
                    RoutingMode.RULE_BASED -> Icons.AutoMirrored.Outlined.Rule
                    RoutingMode.DIRECT -> Icons.AutoMirrored.Outlined.ArrowForward
                    RoutingMode.BLOCK -> Icons.Outlined.Block
                },
                title = mode.label,
                subtitle = mode.description,
                selected = mode == selected,
                onClick = { onSelect(mode) }
            )
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(10.dp))
        InfoCard("Applied on the next connection", "Routing changes are compiled into the next Xray configuration when the VPN starts.")
    }
}

@Composable
private fun DnsScreen(settings: AppSettings, onSettings: (AppSettings) -> Unit, onBack: () -> Unit) {
    var custom by remember(settings.customDns) { mutableStateOf(settings.customDns) }
    ScreenScaffold("DNS", onBack, Icons.Outlined.Dns) {
        Text("Resolver policy", color = MythosColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        DnsMode.entries.forEach { mode ->
            SelectablePolicyCard(
                icon = when (mode) {
                    DnsMode.SYSTEM -> Icons.Outlined.SettingsEthernet
                    DnsMode.CLOUDFLARE -> Icons.Outlined.Cloud
                    DnsMode.GOOGLE -> Icons.Outlined.Language
                    DnsMode.CUSTOM -> Icons.Outlined.Edit
                },
                title = mode.label,
                subtitle = mode.defaultServer ?: if (mode == DnsMode.SYSTEM) "Use the DNS resolver supplied by the active network" else "Enter an IPv4 or IPv6 DNS address",
                selected = settings.dnsMode == mode,
                onClick = { onSettings(settings.copy(dnsMode = mode)) }
            )
            Spacer(Modifier.height(9.dp))
        }
        if (settings.dnsMode == DnsMode.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                custom,
                { custom = it },
                label = { Text("DNS IP address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MythosColors.Border,
                    unfocusedBorderColor = MythosColors.BorderSoft,
                    focusedContainerColor = MythosColors.Panel,
                    unfocusedContainerColor = MythosColors.Panel
                )
            )
            Spacer(Modifier.height(12.dp))
            PrimaryAction("Save custom DNS", false) {
                if (custom.isNotBlank()) onSettings(settings.copy(customDns = custom.trim()))
            }
        }
        Spacer(Modifier.height(18.dp))
        InfoCard("DNS behavior", "Android routes application DNS traffic through the VPN. Xray's own resolver socket is protected from the tunnel to prevent routing loops.")
    }
}

@Composable
private fun SelectablePolicyCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MythosColors.Elevated else MythosColors.Panel,
        border = BorderStroke(1.dp, if (selected) MythosColors.Border else MythosColors.BorderSoft)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = MythosColors.Interactive) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(21.dp)) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = MythosColors.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = MythosColors.Text, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun LogsScreen(controller: MythosController, onBack: () -> Unit) {
    var logs by remember { mutableStateOf(controller.combinedLogs()) }
    ScreenScaffold("Logs", onBack, Icons.AutoMirrored.Outlined.Article) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Refresh", color = MythosColors.Text, modifier = Modifier.clickable { logs = controller.combinedLogs() }.padding(10.dp))
            Text("Clear", color = MythosColors.TextSecondary, modifier = Modifier.clickable { controller.clearLogs(); logs = controller.combinedLogs() }.padding(10.dp))
        }
        Surface(shape = RoundedCornerShape(22.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft)) {
            Text(logs, color = MythosColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(18.dp))
        }
    }
}

@Composable
private fun AboutScreen(controller: MythosController, onBack: () -> Unit) {
    var core by remember { mutableStateOf("Checking…") }
    LaunchedEffect(Unit) { core = runCatching { withContext(Dispatchers.Default) { controller.coreVersion() } }.getOrElse { "Unavailable: ${it.message}" } }
    ScreenScaffold("About", onBack, Icons.Outlined.Info) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MythosMark(72.dp, MythosColors.Text); Spacer(Modifier.height(14.dp))
            Text("mythos", color = MythosColors.Text, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text("${BuildConfig.VERSION_NAME}", color = MythosColors.TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(30.dp))
        InfoCard("Xray-core", core)
        Spacer(Modifier.height(12.dp))
        InfoCard("Engine", "Official XTLS/libXray wrapper. Profiles and settings remain on-device in this build.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(title: String, onBack: (() -> Unit)?, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MythosColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text("MYTHOS · XRAY CLIENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier.padding(end = 8.dp).size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(21.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MythosColors.Background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        ) {
            item { Column { content() } }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MythosColors.Panel, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = MythosColors.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MythosColors.TextMuted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ToggleSettingsRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onChecked(!checked) }, shape = MaterialTheme.shapes.large, color = MythosColors.Panel, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = MythosColors.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Switch(checked = checked, onCheckedChange = null, colors = monochromeSwitchColors())
        }
    }
}

@Composable
private fun PrimaryAction(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SecondaryAction(text: String, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MythosColors.Elevated, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun monochromeSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MythosColors.Soft,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun ConnectedMythosMark(size: androidx.compose.ui.unit.Dp) {
    val pulse = remember { Animatable(0.18f) }
    var light by remember { mutableStateOf(Color(0xFFFF453A)) }

    LaunchedEffect(Unit) {
        val palette = listOf(
            Color(0xFFFF453A), // red
            Color(0xFFFFD60A), // yellow
            Color(0xFFBF5AF2)  // violet
        )
        while (true) {
            light = palette[Random.nextInt(palette.size)]
            pulse.animateTo(1f, animationSpec = tween(Random.nextInt(80, 230)))
            delay(Random.nextLong(35L, 150L))
            pulse.animateTo(0.12f, animationSpec = tween(Random.nextInt(110, 360)))
            delay(Random.nextLong(120L, 680L))
            if (Random.nextInt(100) < 32) {
                pulse.animateTo(0.78f, animationSpec = tween(Random.nextInt(45, 120)))
                pulse.animateTo(0.10f, animationSpec = tween(Random.nextInt(70, 180)))
            }
        }
    }

    Canvas(Modifier.size(size)) {
        val a = pulse.value
        drawCircle(light.copy(alpha = 0.035f + a * 0.08f), radius = this.size.minDimension * 0.49f)
        drawCircle(light.copy(alpha = 0.06f + a * 0.14f), radius = this.size.minDimension * 0.39f, style = Stroke(width = this.size.minDimension * 0.035f))
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(w * .12f, h * .78f); lineTo(w * .25f, h * .18f); lineTo(w * .50f, h * .46f); lineTo(w * .75f, h * .18f); lineTo(w * .88f, h * .78f)
            lineTo(w * .70f, h * .78f); lineTo(w * .64f, h * .48f); lineTo(w * .50f, h * .66f); lineTo(w * .36f, h * .48f); lineTo(w * .30f, h * .78f); close()
        }
        drawPath(p, light.copy(alpha = 0.42f + a * 0.58f))
    }
}

@Composable
private fun MythosMark(size: androidx.compose.ui.unit.Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(w * .12f, h * .78f); lineTo(w * .25f, h * .18f); lineTo(w * .50f, h * .46f); lineTo(w * .75f, h * .18f); lineTo(w * .88f, h * .78f)
            lineTo(w * .70f, h * .78f); lineTo(w * .64f, h * .48f); lineTo(w * .50f, h * .66f); lineTo(w * .36f, h * .48f); lineTo(w * .30f, h * .78f); close()
        }
        drawPath(p, color)
    }
}
