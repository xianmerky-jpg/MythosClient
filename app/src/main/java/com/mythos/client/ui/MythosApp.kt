package com.mythos.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class Screen { Home, Profiles, Settings, Import, Export, ModeView, Routing, Dns, Logs, About }
private enum class HomeSheet { None, Options, Modes }
private enum class ConnectionState { Disconnected, Connecting, Connected }
private enum class ProtocolMode(val label: String, val description: String, val icon: ImageVector) {
    VLESS("VLESS", "Fast, modern, and highly efficient protocol", Icons.Outlined.NearMe),
    VMESS("VMESS", "Flexible and feature-rich protocol", Icons.Outlined.Widgets),
    TROJAN("Trojan", "Secure TLS-based protocol", Icons.Outlined.Shield),
    SHADOWSOCKS("Shadowsocks", "Lightweight and widely compatible", Icons.Outlined.Send)
}

private data class MockProfile(
    val name: String,
    val protocol: String,
    val detail: String,
    val latency: String
)

private val profiles = listOf(
    MockProfile("Singapore 01", "VLESS", "Reality · Vision", "42 ms"),
    MockProfile("Tokyo Edge", "VMESS", "WebSocket · TLS", "71 ms"),
    MockProfile("Frankfurt 02", "Trojan", "TLS · TCP", "94 ms"),
    MockProfile("US West", "Shadowsocks", "2022-blake3-aes-128-gcm", "138 ms")
)

@Composable
fun MythosApp() {
    MythosTheme {
        var screen by remember { mutableStateOf(Screen.Home) }
        var sheet by remember { mutableStateOf(HomeSheet.None) }
        var mode by remember { mutableStateOf(ProtocolMode.VLESS) }
        var selectedProfile by remember { mutableStateOf(profiles.first()) }
        var connection by remember { mutableStateOf(ConnectionState.Disconnected) }
        var viewEnabled by remember { mutableStateOf(false) }
        var firstLaunch by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            delay(700)
            firstLaunch = false
        }
        LaunchedEffect(connection) {
            if (connection == ConnectionState.Connecting) {
                delay(1150)
                connection = ConnectionState.Connected
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MythosColors.Background) {
            if (firstLaunch) {
                SplashScreen()
            } else {
                Crossfade(targetState = screen, label = "screen") { target ->
                    when (target) {
                        Screen.Home -> HomeScreen(
                            selectedProfile = selectedProfile,
                            mode = mode,
                            connection = connection,
                            sheet = sheet,
                            viewEnabled = viewEnabled,
                            onOpenOptions = { sheet = HomeSheet.Options },
                            onOpenModes = { sheet = HomeSheet.Modes },
                            onCloseSheet = { sheet = HomeSheet.None },
                            onSelectMode = { mode = it },
                            onViewToggle = { viewEnabled = it },
                            onModeView = { sheet = HomeSheet.None; screen = Screen.ModeView },
                            onImport = { sheet = HomeSheet.None; screen = Screen.Import },
                            onExport = { sheet = HomeSheet.None; screen = Screen.Export },
                            onProfiles = { sheet = HomeSheet.None; screen = Screen.Profiles },
                            onSettings = { sheet = HomeSheet.None; screen = Screen.Settings },
                            onStart = {
                                connection = when (connection) {
                                    ConnectionState.Disconnected -> ConnectionState.Connecting
                                    ConnectionState.Connecting -> ConnectionState.Disconnected
                                    ConnectionState.Connected -> ConnectionState.Disconnected
                                }
                            }
                        )
                        Screen.Profiles -> ProfilesScreen(
                            selected = selectedProfile,
                            onSelect = { profile ->
                                selectedProfile = profile
                                screen = Screen.Home
                            },
                            onBack = { screen = Screen.Home }
                        )
                        Screen.Settings -> SettingsScreen(
                            onBack = { screen = Screen.Home },
                            onRouting = { screen = Screen.Routing },
                            onDns = { screen = Screen.Dns },
                            onLogs = { screen = Screen.Logs },
                            onAbout = { screen = Screen.About }
                        )
                        Screen.Import -> ImportScreen(onBack = { screen = Screen.Home })
                        Screen.Export -> ExportScreen(selectedProfile, onBack = { screen = Screen.Home })
                        Screen.ModeView -> ModeViewScreen(mode, viewEnabled, { viewEnabled = it }) { screen = Screen.Home }
                        Screen.Routing -> SimpleSettingsScreen("Routing", Icons.Outlined.AltRoute, listOf("Global", "Rule based", "Direct", "Block")) { screen = Screen.Settings }
                        Screen.Dns -> SimpleSettingsScreen("DNS", Icons.Outlined.Dns, listOf("System DNS", "Custom DNS", "DoH / DoT", "FakeDNS")) { screen = Screen.Settings }
                        Screen.Logs -> LogsScreen { screen = Screen.Settings }
                        Screen.About -> AboutScreen { screen = Screen.Settings }
                    }
                }
            }
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
    selectedProfile: MockProfile,
    mode: ProtocolMode,
    connection: ConnectionState,
    sheet: HomeSheet,
    viewEnabled: Boolean,
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
    onStart: () -> Unit
) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp)) {
        TopBar(connection, selectedProfile, onSettings, onProfiles)

        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MythosMark(74.dp, MythosColors.Text.copy(alpha = .12f))
            Spacer(Modifier.height(12.dp))
            Text("mythos", color = MythosColors.Text.copy(alpha = .31f), fontSize = 49.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            Spacer(Modifier.height(16.dp))
            ConnectionLabel(connection)
        }

        HomeComposer(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedProfile = selectedProfile,
            mode = mode,
            connection = connection,
            onOpenOptions = onOpenOptions,
            onOpenModes = onOpenModes,
            onStart = onStart
        )

        when (sheet) {
            HomeSheet.None -> Unit
            HomeSheet.Options -> OptionsSheet(onCloseSheet, onImport, onExport)
            HomeSheet.Modes -> ModesSheet(mode, viewEnabled, onCloseSheet, onSelectMode, onViewToggle, onModeView)
        }
    }
}

@Composable
private fun TopBar(connection: ConnectionState, profile: MockProfile, onSettings: () -> Unit, onProfiles: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(Icons.Outlined.AccountCircle, onSettings)
        Spacer(Modifier.width(14.dp))
        Surface(
            modifier = Modifier.weight(1f).height(58.dp).clickable(onClick = onProfiles),
            shape = RoundedCornerShape(30.dp),
            color = MythosColors.Panel,
            border = BorderStroke(1.dp, MythosColors.Border)
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = MythosColors.Text, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    when (connection) {
                        ConnectionState.Disconnected -> "Not connected"
                        ConnectionState.Connecting -> "Connecting…"
                        ConnectionState.Connected -> profile.name
                    },
                    color = MythosColors.TextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(Modifier.height(28.dp).width(1.dp).background(MythosColors.BorderSoft))
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Outlined.Dns, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        CircleIcon(Icons.Outlined.Article, onProfiles)
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(58.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = MythosColors.Panel,
        border = BorderStroke(1.dp, MythosColors.Border)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MythosColors.Text, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun ConnectionLabel(state: ConnectionState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (state == ConnectionState.Disconnected) MythosColors.TextMuted else MythosColors.Text))
        Spacer(Modifier.width(8.dp))
        Text(
            when (state) {
                ConnectionState.Disconnected -> "Disconnected"
                ConnectionState.Connecting -> "Connecting"
                ConnectionState.Connected -> "Connected"
            },
            color = MythosColors.TextMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HomeComposer(
    modifier: Modifier,
    selectedProfile: MockProfile,
    mode: ProtocolMode,
    connection: ConnectionState,
    onOpenOptions: () -> Unit,
    onOpenModes: () -> Unit,
    onStart: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(172.dp),
        shape = RoundedCornerShape(34.dp),
        color = MythosColors.Panel,
        border = BorderStroke(1.dp, MythosColors.Border)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, null, tint = MythosColors.TextMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedProfile.name, color = MythosColors.TextSecondary, fontSize = 15.sp)
                    Text("${mode.label} · ${selectedProfile.detail}", color = MythosColors.TextMuted, fontSize = 11.sp)
                }
                Icon(Icons.Outlined.Tune, null, tint = MythosColors.TextMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BarePlusButton(onOpenOptions)
                ModeButton(mode, onOpenModes)
                StartButton(connection, onStart)
            }
        }
    }
}

@Composable
private fun BarePlusButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(58.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Add, null, tint = MythosColors.Text, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun ModeButton(mode: ProtocolMode, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(58.dp).widthIn(min = 145.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(29.dp),
        color = MythosColors.Soft,
        border = BorderStroke(1.dp, MythosColors.Border)
    ) {
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
private fun StartButton(state: ConnectionState, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (state == ConnectionState.Connecting) .6f else 1f, label = "start-alpha")
    Surface(
        modifier = Modifier.size(72.dp).alpha(alpha).clickable(onClick = onClick),
        shape = CircleShape,
        color = MythosColors.Soft,
        border = BorderStroke(1.dp, MythosColors.TextMuted)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                ConnectionState.Connecting -> CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MythosColors.Text, strokeWidth = 2.dp)
                else -> Icon(Icons.Outlined.PowerSettingsNew, null, tint = MythosColors.Text, modifier = Modifier.size(31.dp))
            }
        }
    }
}

@Composable
private fun SheetBase(heightFraction: Float, title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f))) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(heightFraction),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = MythosColors.Panel,
            border = BorderStroke(1.dp, MythosColors.Border)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 26.dp)) {
                Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(48.dp).height(5.dp).clip(CircleShape).background(MythosColors.Border))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = MythosColors.Text, fontSize = 27.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.size(54.dp).clickable(onClick = onClose),
                        shape = CircleShape,
                        color = MythosColors.Soft,
                        border = BorderStroke(1.dp, MythosColors.Border)
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, tint = MythosColors.Text, modifier = Modifier.size(29.dp)) } }
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
        OptionRow(Icons.Outlined.FolderOpen, "Import", "Add configuration from external source", onImport)
        HorizontalDivider(color = MythosColors.BorderSoft, modifier = Modifier.padding(start = 54.dp))
        OptionRow(Icons.Outlined.IosShare, "Export", "Save or share current configuration", onExport)
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 23.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(31.dp))
        Spacer(Modifier.width(22.dp))
        Column {
            Text(title, color = MythosColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MythosColors.TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ModesSheet(
    selected: ProtocolMode,
    viewEnabled: Boolean,
    onClose: () -> Unit,
    onSelect: (ProtocolMode) -> Unit,
    onToggle: (Boolean) -> Unit,
    onView: () -> Unit
) {
    SheetBase(.86f, "Modes", onClose) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 26.dp)) {
            items(ProtocolMode.entries) { item ->
                ModeRow(item, item == selected, viewEnabled, { onSelect(item) }, onToggle, onView)
            }
        }
    }
}

@Composable
private fun ModeRow(
    mode: ProtocolMode,
    selected: Boolean,
    viewEnabled: Boolean,
    onSelect: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onView: () -> Unit
) {
    if (selected) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MythosColors.Elevated,
            border = BorderStroke(1.dp, MythosColors.Border)
        ) {
            Column(Modifier.clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(mode.icon, null, tint = MythosColors.Text, modifier = Modifier.size(33.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, color = MythosColors.Text, fontSize = 22.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(mode.description, color = MythosColors.TextSecondary, fontSize = 14.sp)
                    }
                    Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MythosColors.Border)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("View", color = MythosColors.Text, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onView).weight(1f))
                    Switch(
                        checked = viewEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MythosColors.Background,
                            checkedTrackColor = MythosColors.Text,
                            uncheckedThumbColor = MythosColors.TextSecondary,
                            uncheckedTrackColor = MythosColors.Background,
                            uncheckedBorderColor = MythosColors.TextMuted
                        )
                    )
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(mode.icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                Text(mode.label, color = MythosColors.Text, fontSize = 21.sp)
                Spacer(Modifier.height(5.dp))
                Text(mode.description, color = MythosColors.TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProfilesScreen(selected: MockProfile, onSelect: (MockProfile) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("Profiles", onBack, Icons.Outlined.Dns) {
        Text("Servers", color = MythosColors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        profiles.forEach { profile ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onSelect(profile) },
                shape = RoundedCornerShape(22.dp),
                color = if (profile == selected) MythosColors.Elevated else MythosColors.Panel,
                border = BorderStroke(1.dp, MythosColors.BorderSoft)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, color = MythosColors.Text, fontSize = 18.sp)
                        Text("${profile.protocol} · ${profile.detail}", color = MythosColors.TextSecondary, fontSize = 12.sp)
                    }
                    Text(profile.latency, color = MythosColors.TextSecondary, fontSize = 13.sp)
                    if (profile == selected) {
                        Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.Check, null, tint = MythosColors.Text, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            border = BorderStroke(1.dp, MythosColors.Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MythosColors.Text)
        ) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add profile") }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onRouting: () -> Unit, onDns: () -> Unit, onLogs: () -> Unit, onAbout: () -> Unit) {
    var autoConnect by remember { mutableStateOf(false) }
    var haptics by remember { mutableStateOf(true) }
    ScreenScaffold("Settings", onBack, Icons.Outlined.Settings) {
        SettingsRow(Icons.Outlined.AltRoute, "Routing", "Rules and outbound behavior", onRouting)
        SettingsRow(Icons.Outlined.Dns, "DNS", "Resolver and DNS behavior", onDns)
        SettingsRow(Icons.Outlined.Article, "Logs", "View mock connection events", onLogs)
        ToggleSettingsRow(Icons.Outlined.PowerSettingsNew, "Auto connect", "UI preference only", autoConnect) { autoConnect = it }
        ToggleSettingsRow(Icons.Outlined.Vibration, "Haptics", "Touch feedback preference", haptics) { haptics = it }
        SettingsRow(Icons.Outlined.Info, "About", "Mythos UI prototype", onAbout)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 18.sp); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 12.sp) }
        Icon(Icons.Outlined.KeyboardArrowRight, null, tint = MythosColors.TextMuted)
    }
    HorizontalDivider(color = MythosColors.BorderSoft)
}

@Composable
private fun ToggleSettingsRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 18.sp); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 12.sp) }
        Switch(checked, onChecked, colors = monochromeSwitchColors())
    }
    HorizontalDivider(color = MythosColors.BorderSoft)
}

@Composable
private fun ImportScreen(onBack: () -> Unit) {
    var chosen by remember { mutableStateOf("Clipboard") }
    ScreenScaffold("Import", onBack, Icons.Outlined.FolderOpen) {
        Text("Choose source", color = MythosColors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        val methods = listOf(
            Triple(Icons.Outlined.ContentPaste, "Clipboard", "Paste VLESS / VMess / Trojan / SS link"),
            Triple(Icons.Outlined.QrCodeScanner, "QR code", "Mock scanner preview"),
            Triple(Icons.Outlined.Description, "File / JSON", "Choose a local configuration file"),
            Triple(Icons.Outlined.Link, "Subscription URL", "Add a remote subscription"),
            Triple(Icons.Outlined.Code, "Raw JSON", "Paste an Xray-style JSON config")
        )
        methods.forEach { (icon, title, subtitle) ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { chosen = title },
                shape = RoundedCornerShape(20.dp),
                color = if (chosen == title) MythosColors.Elevated else MythosColors.Panel,
                border = BorderStroke(1.dp, MythosColors.BorderSoft)
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { Text(title, color = MythosColors.Text, fontSize = 17.sp); Text(subtitle, color = MythosColors.TextSecondary, fontSize = 12.sp) }
                    if (chosen == title) Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MockActionCard("Selected: $chosen", "UI prototype only — no configuration will be read yet.")
    }
}

@Composable
private fun ExportScreen(profile: MockProfile, onBack: () -> Unit) {
    ScreenScaffold("Export", onBack, Icons.Outlined.IosShare) {
        MockActionCard(profile.name, "${profile.protocol} · ${profile.detail}")
        Spacer(Modifier.height(18.dp))
        SettingsRow(Icons.Outlined.Link, "Share link", "Preview export as a share URL", {})
        SettingsRow(Icons.Outlined.QrCode, "QR code", "Preview QR export", {})
        SettingsRow(Icons.Outlined.Description, "JSON file", "Preview configuration file export", {})
        SettingsRow(Icons.Outlined.ContentCopy, "Copy", "Copy mock configuration to clipboard", {})
    }
}

@Composable
private fun ModeViewScreen(mode: ProtocolMode, enabled: Boolean, onEnabled: (Boolean) -> Unit, onBack: () -> Unit) {
    var tls by remember { mutableStateOf(true) }
    var mux by remember { mutableStateOf(false) }
    ScreenScaffold("${mode.label} view", onBack, mode.icon) {
        MockActionCard(mode.label, mode.description)
        Spacer(Modifier.height(16.dp))
        ToggleSettingsRow(Icons.Outlined.Visibility, "View enabled", "Controls the selected mode card state", enabled, onEnabled)
        ToggleSettingsRow(Icons.Outlined.Lock, "TLS", "Mock transport security control", tls) { tls = it }
        ToggleSettingsRow(Icons.Outlined.DeviceHub, "Mux", "Mock multiplexing control", mux) { mux = it }
        SettingsRow(Icons.Outlined.SwapHoriz, "Transport", "TCP / WS / gRPC / HTTPUpgrade", {})
        SettingsRow(Icons.Outlined.Fingerprint, "Fingerprint", "Chrome", {})
    }
}

@Composable
private fun SimpleSettingsScreen(title: String, icon: ImageVector, options: List<String>, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(options.first()) }
    ScreenScaffold(title, onBack, icon) {
        options.forEach { option ->
            Row(Modifier.fillMaxWidth().clickable { selected = option }.padding(vertical = 19.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(option, color = MythosColors.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (selected == option) Icon(Icons.Outlined.Check, null, tint = MythosColors.TextSecondary)
            }
            HorizontalDivider(color = MythosColors.BorderSoft)
        }
        Spacer(Modifier.height(18.dp))
        MockActionCard("Prototype", "Selections are visual only and do not affect network traffic.")
    }
}

@Composable
private fun LogsScreen(onBack: () -> Unit) {
    ScreenScaffold("Logs", onBack, Icons.Outlined.Article) {
        val logs = listOf(
            "05:31:02  UI initialized",
            "05:31:04  Loaded 4 mock profiles",
            "05:31:08  Selected VLESS mode",
            "05:31:12  Connection simulation started",
            "05:31:13  Simulation connected"
        )
        Surface(shape = RoundedCornerShape(22.dp), color = MythosColors.Panel, border = BorderStroke(1.dp, MythosColors.BorderSoft)) {
            Column(Modifier.padding(18.dp)) {
                logs.forEach { Text(it, color = MythosColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 7.dp)) }
            }
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    ScreenScaffold("About", onBack, Icons.Outlined.Info) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MythosMark(72.dp, MythosColors.Text.copy(alpha = .7f))
            Spacer(Modifier.height(14.dp))
            Text("mythos", color = MythosColors.Text, fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text("UI prototype 0.1", color = MythosColors.TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(30.dp))
        MockActionCard("UI only", "No VPN tunnel, Xray core, proxy engine, DNS handling, or real configuration parsing is included in this build.")
    }
}

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(Icons.Outlined.ArrowBack, onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = MythosColors.Text, fontSize = 27.sp, fontWeight = FontWeight.Normal)
                Text("Mythos", color = MythosColors.TextMuted, fontSize = 12.sp)
            }
            Icon(icon, null, tint = MythosColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(28.dp))
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 34.dp)) {
            item { Column { content() } }
        }
    }
}

@Composable
private fun MockActionCard(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MythosColors.Elevated,
        border = BorderStroke(1.dp, MythosColors.Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = MythosColors.Text, fontSize = 18.sp)
            Spacer(Modifier.height(7.dp))
            Text(subtitle, color = MythosColors.TextSecondary, fontSize = 13.sp)
        }
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
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * .12f, h * .78f)
            lineTo(w * .25f, h * .18f)
            lineTo(w * .50f, h * .46f)
            lineTo(w * .75f, h * .18f)
            lineTo(w * .88f, h * .78f)
            lineTo(w * .70f, h * .78f)
            lineTo(w * .64f, h * .48f)
            lineTo(w * .50f, h * .66f)
            lineTo(w * .36f, h * .48f)
            lineTo(w * .30f, h * .78f)
            close()
        }
        drawPath(p, color)
    }
}
