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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

private enum class Screen { Home, Profiles, Settings, Import, Export, ModeView, Routing, Dns, Logs, About }
private enum class HomeSheet { None, Options, Modes }

private fun ProtocolMode.icon(): ImageVector = when (this) {
    ProtocolMode.VLESS -> Icons.Outlined.NearMe
    ProtocolMode.VMESS -> Icons.Outlined.Widgets
    ProtocolMode.TROJAN -> Icons.Outlined.Shield
    ProtocolMode.SHADOWSOCKS -> Icons.Outlined.Send
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
        var sheet by remember { mutableStateOf(HomeSheet.None) }
        var splash by remember { mutableStateOf(true) }

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
                        screen = Screen.Import
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

        Box(Modifier.fillMaxSize().background(MythosColors.Background)) {
            if (splash) {
                SplashScreen()
            } else {
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
                            onViewToggle = { saveSettings(settings.copy(modeViewEnabled = it)) },
                            onModeView = { sheet = HomeSheet.None; screen = Screen.ModeView },
                            onImport = { sheet = HomeSheet.None; screen = Screen.Import },
                            onExport = { sheet = HomeSheet.None; screen = Screen.Export },
                            onProfiles = { sheet = HomeSheet.None; screen = Screen.Profiles },
                            onSettings = { sheet = HomeSheet.None; screen = Screen.Settings },
                            onLogs = { sheet = HomeSheet.None; screen = Screen.Logs },
                            onStart = ::toggleConnection
                        )

                        Screen.Profiles -> ProfilesScreen(
                            profiles = if (settings.modeViewEnabled) profiles.filter { it.protocol == settings.selectedMode } else profiles,
                            selectedId = selectedProfile?.id,
                            filterLabel = if (settings.modeViewEnabled) settings.selectedMode.label else "All protocols",
                            vpnRunning = vpn.status == VpnStatus.CONNECTED || vpn.status == VpnStatus.CONNECTING,
                            onSelect = {
                                controller.selectProfile(it)
                                reload()
                                screen = Screen.Home
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
                            onImport = { screen = Screen.Import },
                            onBack = { screen = Screen.Home }
                        )

                        Screen.Settings -> SettingsScreen(
                            settings = settings,
                            onSettings = ::saveSettings,
                            onBack = { screen = Screen.Home },
                            onRouting = { screen = Screen.Routing },
                            onDns = { screen = Screen.Dns },
                            onLogs = { screen = Screen.Logs },
                            onAbout = { screen = Screen.About }
                        )

                        Screen.Import -> ImportScreen(
                            controller = controller,
                            onImported = { message -> reload(); scope.launch { snackbar.showSnackbar(message) }; screen = Screen.Home },
                            onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                            onBack = { screen = Screen.Home }
                        )

                        Screen.Export -> ExportScreen(
                            controller = controller,
                            profile = selectedProfile,
                            onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                            onBack = { screen = Screen.Home }
                        )

                        Screen.ModeView -> ModeViewScreen(
                            mode = settings.selectedMode,
                            enabled = settings.modeViewEnabled,
                            profiles = profiles.filter { it.protocol == settings.selectedMode },
                            onEnabled = { saveSettings(settings.copy(modeViewEnabled = it)) },
                            onBack = { screen = Screen.Home }
                        )

                        Screen.Routing -> RoutingScreen(settings.routingMode, { saveSettings(settings.copy(routingMode = it)) }) { screen = Screen.Settings }
                        Screen.Dns -> DnsScreen(settings, ::saveSettings) { screen = Screen.Settings }
                        Screen.Logs -> LogsScreen(controller) { screen = Screen.Settings }
                        Screen.About -> AboutScreen(controller) { screen = Screen.Settings }
                    }
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MythosMark(76.dp, MythosColors.Text.copy(alpha = .72f))
            Spacer(Modifier.height(18.dp))
            Text("mythos", color = MythosColors.Text.copy(alpha = .68f), fontSize = 42.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
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
    onLogs: () -> Unit,
    onStart: () -> Unit
) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) {
        TopBar(vpn, selectedProfile, onSettings, onProfiles, onLogs)

        Column(Modifier.align(Alignment.Center).offset(y = (-20).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MythosMark(74.dp, MythosColors.Text.copy(alpha = .12f))
            Spacer(Modifier.height(12.dp))
            Text("mythos", color = MythosColors.Text.copy(alpha = .31f), fontSize = 49.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            Spacer(Modifier.height(16.dp))
            ConnectionLabel(vpn)
            if (vpn.status == VpnStatus.ERROR && vpn.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(vpn.error, color = MythosColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 28.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        HomeComposer(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedProfile = selectedProfile,
            mode = settings.selectedMode,
            vpn = vpn,
            onOpenOptions = onOpenOptions,
            onOpenModes = onOpenModes,
            onStart = onStart
        )

        when (sheet) {
            HomeSheet.None -> Unit
            HomeSheet.Options -> OptionsSheet(onCloseSheet, onImport, onExport)
            HomeSheet.Modes -> ModesSheet(settings.selectedMode, settings.modeViewEnabled, onCloseSheet, onSelectMode, onViewToggle, onModeView)
        }
    }
}

@Composable
private fun TopBar(vpn: VpnSnapshot, profile: ProxyProfile?, onSettings: () -> Unit, onProfiles: () -> Unit, onLogs: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(Icons.Outlined.AccountCircle, onSettings)
        Spacer(Modifier.width(14.dp))
        Surface(
            modifier = Modifier.weight(1f).height(58.dp).clickable(onClick = onProfiles),
            shape = RoundedCornerShape(30.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.Border)
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = MythosColors.Text, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    when (vpn.status) {
                        VpnStatus.CONNECTED -> vpn.profileName.ifBlank { profile?.name ?: "Connected" }
                        VpnStatus.CONNECTING -> "Connecting…"
                        VpnStatus.ERROR -> "Connection failed"
                        else -> profile?.name ?: "Select a profile"
                    },
                    color = MythosColors.TextSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Box(Modifier.height(28.dp).width(1.dp).background(MythosColors.BorderSoft))
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Outlined.Dns, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        CircleIcon(Icons.Outlined.Article, onLogs)
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(58.dp).clickable(onClick = onClick), shape = CircleShape, color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MythosColors.Text, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun ConnectionLabel(vpn: VpnSnapshot) {
    var now by remember(vpn.status, vpn.connectedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vpn.status, vpn.connectedAt) {
        while (vpn.status == VpnStatus.CONNECTED) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val label = when (vpn.status) {
        VpnStatus.DISCONNECTED -> "Disconnected"
        VpnStatus.CONNECTING -> "Connecting"
        VpnStatus.CONNECTED -> {
            val seconds = if (vpn.connectedAt > 0) (now - vpn.connectedAt).coerceAtLeast(0L) / 1000 else 0
            "Connected · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
        VpnStatus.ERROR -> "Error"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (vpn.status == VpnStatus.CONNECTED) MythosColors.Text else MythosColors.TextMuted))
        Spacer(Modifier.width(8.dp))
        Text(label, color = MythosColors.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun HomeComposer(
    modifier: Modifier,
    selectedProfile: ProxyProfile?,
    mode: ProtocolMode,
    vpn: VpnSnapshot,
    onOpenOptions: () -> Unit,
    onOpenModes: () -> Unit,
    onStart: () -> Unit
) {
    Surface(modifier = modifier.fillMaxWidth().height(172.dp), shape = RoundedCornerShape(34.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.Border)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, null, tint = MythosColors.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedProfile?.name ?: "No profile", color = MythosColors.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selectedProfile?.let { "${it.protocol.label} · ${it.detail}" } ?: "Import a configuration to begin", color = MythosColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                selectedProfile?.latencyMs?.let { Text("$it ms", color = MythosColors.TextMuted, fontSize = 11.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BarePlusButton(onOpenOptions)
                ModeButton(mode, onOpenModes)
                StartButton(vpn.status, onStart)
            }
        }
    }
}

@Composable
private fun BarePlusButton(onClick: () -> Unit) {
    Box(modifier = Modifier.size(58.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Add, null, tint = MythosColors.Text, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun ModeButton(mode: ProtocolMode, onClick: () -> Unit) {
    Surface(modifier = Modifier.height(58.dp).widthIn(min = 145.dp).clickable(onClick = onClick), shape = RoundedCornerShape(29.dp), color = MythosColors.Soft, border = BorderStroke(1.dp, MythosColors.Border)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Layers, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Modes", color = MythosColors.Text, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StartButton(status: VpnStatus, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (status == VpnStatus.CONNECTING) .65f else 1f, label = "start-alpha")
    Surface(modifier = Modifier.size(72.dp).alpha(alpha).clickable(onClick = onClick), shape = CircleShape, color = MythosColors.Soft, border = BorderStroke(1.dp, MythosColors.TextMuted)) {
        Box(contentAlignment = Alignment.Center) {
            if (status == VpnStatus.CONNECTING) CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MythosColors.Text, strokeWidth = 2.dp)
            else Icon(if (status == VpnStatus.CONNECTED) Icons.Outlined.Stop else Icons.Outlined.PowerSettingsNew, null, tint = MythosColors.Text, modifier = Modifier.size(31.dp))
        }
    }
}

@Composable
private fun SheetBase(heightFraction: Float, title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f))) {
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(heightFraction), shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.Border)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp)) {
                Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) { Box(Modifier.width(48.dp).height(5.dp).clip(CircleShape).background(MythosColors.Border)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = MythosColors.Text, fontSize = 27.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.size(54.dp).clickable(onClick = onClose), shape = CircleShape, color = MythosColors.Soft, border = BorderStroke(1.dp, MythosColors.Border)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, tint = MythosColors.Text, modifier = Modifier.size(29.dp)) }
                    }
                }
                Spacer(Modifier.height(22.dp))
                content()
            }
        }
    }
}

@Composable
private fun OptionsSheet(onClose: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    SheetBase(.61f, "Options", onClose) {
        OptionRow(Icons.Outlined.FolderOpen, "Import", "Links, QR images, files, JSON or subscriptions", onImport)
        HorizontalDivider(color = MythosColors.BorderSoft, modifier = Modifier.padding(start = 54.dp))
        OptionRow(Icons.Outlined.IosShare, "Export", "Share link, QR code or JSON file", onExport)
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 23.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(31.dp))
        Spacer(Modifier.width(22.dp))
        Column { Text(title, color = MythosColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 14.sp) }
    }
}

@Composable
private fun ModesSheet(selected: ProtocolMode, viewEnabled: Boolean, onClose: () -> Unit, onSelect: (ProtocolMode) -> Unit, onToggle: (Boolean) -> Unit, onView: () -> Unit) {
    SheetBase(.86f, "Modes", onClose) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 26.dp)) {
            items(ProtocolMode.entries) { item -> ModeRow(item, item == selected, viewEnabled, { onSelect(item) }, onToggle, onView) }
        }
    }
}

@Composable
private fun ModeRow(mode: ProtocolMode, selected: Boolean, viewEnabled: Boolean, onSelect: () -> Unit, onToggle: (Boolean) -> Unit, onView: () -> Unit) {
    if (selected) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MythosColors.Elevated, border = BorderStroke(1.dp, MythosColors.Border)) {
            Column(Modifier.clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(mode.icon(), null, tint = MythosColors.Text, modifier = Modifier.size(33.dp)); Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) { Text(mode.label, color = MythosColors.Text, fontSize = 22.sp); Spacer(Modifier.height(6.dp)); Text(mode.description, color = MythosColors.TextSecondary, fontSize = 14.sp) }
                    Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(20.dp)); HorizontalDivider(color = MythosColors.Border); Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View", color = MythosColors.Text, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onView).weight(1f))
                    Switch(checked = viewEnabled, onCheckedChange = onToggle, colors = monochromeSwitchColors())
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(mode.icon(), null, tint = MythosColors.TextSecondary, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(20.dp))
            Column { Text(mode.label, color = MythosColors.Text, fontSize = 21.sp); Spacer(Modifier.height(5.dp)); Text(mode.description, color = MythosColors.TextSecondary, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun ProfilesScreen(
    profiles: List<ProxyProfile>, selectedId: String?, filterLabel: String, vpnRunning: Boolean,
    onSelect: (ProxyProfile) -> Unit, onDelete: (ProxyProfile) -> Unit, onLatency: (ProxyProfile) -> Unit,
    onImport: () -> Unit, onBack: () -> Unit
) {
    ScreenScaffold("Profiles", onBack, Icons.Outlined.Dns) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(filterLabel, color = MythosColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("+ Import", color = MythosColors.Text, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onImport).padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (profiles.isEmpty()) InfoCard("No profiles", "Import a VLESS, VMess, Trojan or Shadowsocks configuration.")
        profiles.forEach { p ->
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(22.dp), color = if (p.id == selectedId) MythosColors.Elevated else MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(p.protocol.icon(), null, tint = MythosColors.TextSecondary, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(15.dp))
                        Column(Modifier.weight(1f).clickable { onSelect(p) }) {
                            Text(p.name, color = MythosColors.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.protocol.label} · ${p.detail}", color = MythosColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.server.isNotBlank()) Text("${p.server}:${p.port}", color = MythosColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (p.id == selectedId) Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp)); HorizontalDivider(color = MythosColors.BorderSoft); Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.latencyMs?.let { "$it ms" } ?: "Not tested", color = MythosColors.TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Test", color = if (vpnRunning) MythosColors.TextMuted else MythosColors.Text, fontSize = 13.sp, modifier = Modifier.clickable(enabled = !vpnRunning) { onLatency(p) }.padding(8.dp))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.DeleteOutline, null, tint = MythosColors.TextMuted, modifier = Modifier.size(22.dp).clickable { onDelete(p) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, onSettings: (AppSettings) -> Unit, onBack: () -> Unit, onRouting: () -> Unit, onDns: () -> Unit, onLogs: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    ScreenScaffold("Settings", onBack, Icons.Outlined.Settings) {
        SettingsRow(Icons.Outlined.AltRoute, "Routing", settings.routingMode.label, onRouting)
        SettingsRow(Icons.Outlined.Dns, "DNS", if (settings.dnsMode == DnsMode.CUSTOM) settings.customDns.ifBlank { "Custom" } else settings.dnsMode.label, onDns)
        ToggleSettingsRow(Icons.Outlined.Language, "IPv6", "Route IPv6 traffic through the VPN", settings.ipv6) { onSettings(settings.copy(ipv6 = it)) }
        ToggleSettingsRow(Icons.Outlined.Power, "Connect on launch", "Reconnect automatically when VPN permission already exists", settings.connectOnLaunch) { onSettings(settings.copy(connectOnLaunch = it)) }
        SettingsRow(Icons.Outlined.Article, "Logs", "App and Xray runtime logs", onLogs)
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
private fun ModeViewScreen(mode: ProtocolMode, enabled: Boolean, profiles: List<ProxyProfile>, onEnabled: (Boolean) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("${mode.label} view", onBack, mode.icon()) {
        InfoCard(mode.label, mode.description)
        Spacer(Modifier.height(16.dp))
        ToggleSettingsRow(Icons.Outlined.Visibility, "Filter profiles", "When enabled, Profiles shows only ${mode.label} entries", enabled, onEnabled)
        InfoCard("Available profiles", "${profiles.size} ${mode.label} profile(s) currently stored")
        profiles.firstOrNull()?.let { p ->
            Spacer(Modifier.height(16.dp))
            InfoCard("Current ${mode.label}", listOfNotNull(p.server.takeIf { it.isNotBlank() }, p.transport.takeIf { it.isNotBlank() }, p.security.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { p.detail })
        }
    }
}

@Composable
private fun RoutingScreen(selected: RoutingMode, onSelect: (RoutingMode) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Routing", onBack, Icons.Outlined.AltRoute) {
        RoutingMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(mode.label, color = MythosColors.Text, fontSize = 18.sp); Text(mode.description, color = MythosColors.TextSecondary, fontSize = 12.sp) }
                if (mode == selected) Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary)
            }
            HorizontalDivider(color = MythosColors.BorderSoft)
        }
        Spacer(Modifier.height(18.dp)); InfoCard("Applied on next connection", "Routing changes are used when Xray builds the next VPN configuration.")
    }
}

@Composable
private fun DnsScreen(settings: AppSettings, onSettings: (AppSettings) -> Unit, onBack: () -> Unit) {
    var custom by remember(settings.customDns) { mutableStateOf(settings.customDns) }
    ScreenScaffold("DNS", onBack, Icons.Outlined.Dns) {
        DnsMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().clickable { onSettings(settings.copy(dnsMode = mode)) }.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(mode.label, color = MythosColors.Text, fontSize = 18.sp); Text(mode.defaultServer ?: if (mode == DnsMode.SYSTEM) "Use the active network DNS" else "IPv4 or IPv6 address", color = MythosColors.TextSecondary, fontSize = 12.sp) }
                if (settings.dnsMode == mode) Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary)
            }
            HorizontalDivider(color = MythosColors.BorderSoft)
        }
        if (settings.dnsMode == DnsMode.CUSTOM) {
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(custom, { custom = it }, label = { Text("DNS IP address") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PrimaryAction("Save custom DNS", false) {
                if (custom.isNotBlank()) onSettings(settings.copy(customDns = custom.trim()))
            }
        }
        Spacer(Modifier.height(18.dp)); InfoCard("DNS behavior", "Android routes app DNS traffic through the VPN; Xray's own resolver socket is protected from the tunnel to prevent loops.")
    }
}

@Composable
private fun LogsScreen(controller: MythosController, onBack: () -> Unit) {
    var logs by remember { mutableStateOf(controller.combinedLogs()) }
    ScreenScaffold("Logs", onBack, Icons.Outlined.Article) {
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
            MythosMark(72.dp, MythosColors.Text.copy(alpha = .7f)); Spacer(Modifier.height(14.dp))
            Text("mythos", color = MythosColors.Text, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text("${BuildConfig.VERSION_NAME}", color = MythosColors.TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(30.dp))
        InfoCard("Xray-core", core)
        Spacer(Modifier.height(12.dp))
        InfoCard("Engine", "Official XTLS/libXray wrapper. Profiles and settings remain on-device in this build.")
    }
}

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(Icons.Outlined.ArrowBack, onBack); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 27.sp); Text("Mythos", color = MythosColors.TextMuted, fontSize = 12.sp) }
            Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(28.dp))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 34.dp)) { item { Column { content() } } }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 18.sp); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 12.sp) }
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MythosColors.TextMuted)
    }
    HorizontalDivider(color = MythosColors.BorderSoft)
}

@Composable
private fun ToggleSettingsRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 18.sp); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 12.sp) }
        Switch(checked, onChecked, colors = monochromeSwitchColors())
    }
    HorizontalDivider(color = MythosColors.BorderSoft)
}

@Composable
private fun PrimaryAction(text: String, busy: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().height(56.dp).clickable(enabled = !busy, onClick = onClick), shape = RoundedCornerShape(28.dp), color = MythosColors.Text) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MythosColors.Background, strokeWidth = 2.dp)
            else Text(text, color = MythosColors.Background, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MythosColors.Elevated, border = BorderStroke(1.dp, MythosColors.Border)) {
        Column(Modifier.padding(18.dp)) { Text(title, color = MythosColors.Text, fontSize = 18.sp); Spacer(Modifier.height(7.dp)); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 13.sp) }
    }
}

@Composable
private fun monochromeSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MythosColors.Background,
    checkedTrackColor = MythosColors.Text,
    uncheckedThumbColor = MythosColors.TextSecondary,
    uncheckedTrackColor = MythosColors.Background,
    uncheckedBorderColor = MythosColors.TextMuted
)

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
