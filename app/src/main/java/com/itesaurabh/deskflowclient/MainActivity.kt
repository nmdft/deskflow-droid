// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itesaurabh.deskflowclient.ui.theme.DeskflowClientTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Below this the log is a bottom drawer; at or above it, a second column. */
private val TWO_PANE_WIDTH = 840.dp

/** Enough for the header row and one line of log. */
private val LOG_PEEK_HEIGHT = 56.dp

// Connection state reads the same in either theme, so these are fixed rather than from the scheme.
private val StatusConnected = Color(0xFF22C55E)
private val StatusConnecting = Color(0xFFF59E0B)
private val StatusDisconnected = Color(0xFFEF4444)

enum class ThemeMode { System, Light, Dark }

class MainActivity : ComponentActivity() {
    // Refreshed on resume, since the way this changes is the user going to Settings and back.
    private val serviceEnabled = mutableStateOf(false)
    private val themeMode = mutableStateOf(ThemeMode.System)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Logs.warn("notification permission denied — the connection notification stays hidden")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreLegacyOrientation(this)
        enableEdgeToEdge()

        // The ongoing notification is what holds the process up while connected, so ask early.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = getSharedPreferences("deskflow", Context.MODE_PRIVATE)
        themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString("theme", ThemeMode.System.name)!!)
        }.getOrDefault(ThemeMode.System)

        setContent {
            val mode = themeMode.value
            DeskflowClientTheme(
                darkTheme = when (mode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            ) {
                AppScaffold(
                    serviceEnabled = serviceEnabled.value,
                    themeMode = mode,
                    onThemeMode = {
                        themeMode.value = it
                        prefs.edit().putString("theme", it.name).apply()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = accessibilityEnabled(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) InjectorService.instance?.publishClipboard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    serviceEnabled: Boolean,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit
) {
    var showAbout by remember { mutableStateOf(false) }
    var showFingerprints by remember { mutableStateOf(false) }

    // Pinned rather than enterAlways: the bar now carries the connection state, which is the one
    // thing worth keeping on screen. Scrolling still recolours the container, as M3 expects.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    BackHandler(enabled = showAbout) { showAbout = false }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { if (showAbout) Text("About") else TitleWithStatus() },
                actions = {
                    IconButton(onClick = { showFingerprints = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fingerprint),
                            contentDescription = "Fingerprints"
                        )
                    }
                    ThemeMenu(themeMode, onThemeMode)
                    TextButton(onClick = { showAbout = !showAbout }) {
                        Text(if (showAbout) "Done" else "About")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (showFingerprints) {
            FingerprintDialog(onDismiss = { showFingerprints = false })
        }

        AnimatedContent(
            targetState = showAbout,
            transitionSpec = {
                val forward = targetState
                val offset: (Int) -> Int = { if (forward) it / 5 else -it / 5 }
                val exit: (Int) -> Int = { if (forward) -it / 5 else it / 5 }
                (slideInHorizontally(tween(260), offset) + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(260), exit) + fadeOut(tween(160)))
            },
            label = "screen"
        ) { about ->
            if (about) {
                AboutScreen(Modifier.padding(innerPadding))
            } else {
                ConnectionScreen(serviceEnabled, Modifier.padding(innerPadding))
            }
        }
    }
}

/** The connection state lives here so it survives scrolling and costs no extra vertical space. */
@Composable
private fun TitleWithStatus() {
    val phase = ClientState.phase
    val accent = when (phase) {
        ClientState.Phase.Connected -> StatusConnected
        ClientState.Phase.Connecting -> StatusConnecting
        ClientState.Phase.Idle -> StatusDisconnected
    }
    val label = when (phase) {
        ClientState.Phase.Connected -> "Connected"
        ClientState.Phase.Connecting -> "Connecting"
        ClientState.Phase.Idle -> "Not connected"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(accent, pulsing = phase == ClientState.Phase.Connecting)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "Deskflow Droid",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W600
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent)
        }
    }
}

/** Form fields, kept in one place so both layouts can render the same form. */
private class FormState(prefs: SharedPreferences, pinnedFingerprint: String?) {
    var host by mutableStateOf(prefs.getString("host", "").orEmpty())
    var port by mutableStateOf(prefs.getString("port", Protocol.DEFAULT_PORT.toString()).orEmpty())
    var name by mutableStateOf(prefs.getString("name", "android").orEmpty())
    var useTls by mutableStateOf(prefs.getBoolean("tls", false))
    var pinned by mutableStateOf(pinnedFingerprint)

    val resolvedPort: Int get() = port.trim().toIntOrNull() ?: Protocol.DEFAULT_PORT

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString("host", host.trim())
            .putString("port", port.trim())
            .putString("name", name.trim())
            .putBoolean("tls", useTls)
            .apply()
    }
}

@Composable
private fun ConnectionScreen(serviceEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deskflow", Context.MODE_PRIVATE) }
    val form = remember { FormState(prefs, Tls.pinnedFingerprint(context)) }

    var logExpanded by remember { mutableStateOf(false) }
    val idle = ClientState.phase == ClientState.Phase.Idle

    fun connect() {
        form.save(prefs)
        val service = InjectorService.instance
        if (service != null) {
            service.connect(form.host.trim(), form.resolvedPort, form.name.trim(), form.useTls)
        } else {
            // Enabled but not bound yet: the system is still bringing the service back up after
            // the process was killed. Queue it rather than telling the user to enable something
            // that is already on.
            InjectorService.pendingConnect = InjectorService.PendingConnect(
                form.host.trim(), form.resolvedPort, form.name.trim(), form.useTls
            )
            ClientState.report("Waiting for the accessibility service to start")
        }
    }

    ClientState.untrustedFingerprint?.let { fingerprint ->
        TrustDialog(
            fingerprint = fingerprint,
            onTrust = {
                Tls.pin(context, fingerprint)
                form.pinned = fingerprint
                Logs.add("pinned server fingerprint")
                ClientState.untrusted(null)
                connect()
            },
            onCancel = { ClientState.untrusted(null) }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TWO_PANE_WIDTH
        val fullHeight = maxHeight

        @Composable
        fun Form(scrollModifier: Modifier) {
            ConnectionForm(
                form = form,
                serviceEnabled = serviceEnabled,
                idle = idle,
                onConnect = ::connect,
                onDisconnect = { InjectorService.instance?.disconnect() },
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onForgetServer = {
                    Tls.forget(context)
                    form.pinned = null
                    Logs.add("forgot the trusted server certificate")
                },
                modifier = scrollModifier
            )
        }

        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Form(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
                LogList(
                    header = {
                        SectionLabel("Log")
                        Spacer(Modifier.weight(1f))
                        CopyLogButton()
                        TextButton(onClick = { Logs.clear() }) { Text("Clear") }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            BackHandler(enabled = logExpanded) { logExpanded = false }

            Form(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = LOG_PEEK_HEIGHT + 24.dp)
            )

            val drawerHeight by animateDpAsState(
                targetValue = if (logExpanded) fullHeight else LOG_PEEK_HEIGHT,
                animationSpec = tween(280),
                label = "log drawer"
            )
            LogDrawer(
                expanded = logExpanded,
                onToggle = { logExpanded = !logExpanded },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(drawerHeight)
            )
        }
    }
}

@Composable
private fun ConnectionForm(
    form: FormState,
    serviceEnabled: Boolean,
    idle: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onForgetServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deskflow", Context.MODE_PRIVATE) }
    var pointerMode by remember { mutableStateOf(prefs.getInt("pointer_mode", -1)) }
    var pointerReversed by remember { mutableStateOf(prefs.getBoolean("pointer_reversed", false)) }
    var wakeOnMouseEntry by remember { mutableStateOf(prefs.getBoolean("wake_on_mouse_entry", true)) }
    // Text fields stop being readable long before a tablet runs out of width, so the form is
    // capped, and then centred, or it strands itself against the left edge of a wide pane.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val column = Modifier.fillMaxWidth()

            Column(modifier = column, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("鼠标操作方向")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((mode, title) in listOf(-1 to "跟随画面", 1 to "横向", 0 to "竖向")) {
                        FilterChip(selected = pointerMode == mode, onClick = {
                            pointerMode = mode
                            prefs.edit().putInt("pointer_mode", mode).apply()
                            InjectorService.instance?.setPointerMode(mode, pointerReversed)
                        }, label = { Text(title) })
                    }
                }
                if (pointerMode != -1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("反向 180°", modifier = Modifier.weight(1f))
                        Switch(checked = pointerReversed, onCheckedChange = {
                            pointerReversed = it
                            prefs.edit().putBoolean("pointer_reversed", it).apply()
                            InjectorService.instance?.setPointerMode(pointerMode, it)
                        })
                    }
                }
                if (pointerMode != -1) Text(
                    if (pointerMode == 1) "手机顶部朝${if (pointerReversed) "右" else "左"}"
                    else "手机顶部朝${if (pointerReversed) "下" else "上"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(modifier = column, verticalAlignment = Alignment.CenterVertically) {
                Text("鼠标移入时亮屏", modifier = Modifier.weight(1f))
                Switch(checked = wakeOnMouseEntry, onCheckedChange = {
                    wakeOnMouseEntry = it
                    prefs.edit().putBoolean("wake_on_mouse_entry", it).apply()
                })
            }

            Column(modifier = column, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("服务器")
                OutlinedTextField(
                    value = form.host,
                    onValueChange = { form.host = it },
                    label = { Text("地址") },
                    singleLine = true,
                    enabled = idle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.port,
                        onValueChange = { form.port = it.filter(Char::isDigit) },
                        label = { Text("端口") },
                        singleLine = true,
                        enabled = idle,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form.name = it },
                        label = { Text("屏幕名称") },
                        singleLine = true,
                        enabled = idle,
                        modifier = Modifier.weight(2f)
                    )
                }
            }

        Column(modifier = column, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("安全")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TLS 加密", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            !form.useTls -> "服务器也需关闭 TLS"
                            form.pinned != null -> "已信任 ${form.pinned!!.take(17)}…"
                            else -> "尚未信任服务器"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = form.useTls, onCheckedChange = { form.useTls = it }, enabled = idle)
            }
            if (form.useTls && form.pinned != null) {
                TextButton(onClick = onForgetServer, enabled = idle) { Text("重置信任") }
            }
        }

        Box(modifier = column) {
            if (serviceEnabled) {
                if (idle) {
                    Button(
                        onClick = onConnect,
                        enabled = form.host.isNotBlank() && form.name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("连接")
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("断开")
                    }
                }
            } else {
                PermissionCard(onOpenSettings)
            }
        }
        }
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    // The pulse says "still working on it" through the several seconds a TLS connect can take.
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "connecting")
        val animated by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "alpha"
        )
        animated
    } else {
        1f
    }

    Canvas(modifier = Modifier.size(10.dp)) {
        drawCircle(color = color.copy(alpha = alpha))
    }
}

/** A circle split light/dark, the usual shorthand for a theme control. */
@Composable
private fun ThemeGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(20.dp)) {
        val radius = size.width / 2f
        drawCircle(color = color, radius = radius, style = Stroke(width = 1.8.dp.toPx()))
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset.Zero,
            size = size
        )
    }
}

@Composable
private fun ThemeMenu(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) { ThemeGlyph() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when (option) {
                                ThemeMode.System -> "Follow system"
                                ThemeMode.Light -> "Light"
                                ThemeMode.Dark -> "Dark"
                            },
                            color = if (option == mode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onMode(option)
                        open = false
                    }
                )
            }
        }
    }
}

/**
 * The counterpart of Deskflow's "Local Fingerprints" window. Shows this device's own fingerprint,
 * which is what the desktop displays when it asks whether to trust the incoming client. The art is
 * generated by the same algorithm, so the two pictures can be compared directly.
 */
@Composable
private fun FingerprintDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showHash by remember { mutableStateOf(false) }

    // RSA key generation on first run takes a moment, so never on the main thread.
    val local by produceState<Pair<String, String>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val digest = Tls.localFingerprintBytes(context)
            digest.joinToString(":") { "%02X".format(it) } to Randomart.of(digest)
        }
    }
    val pinned = remember { Tls.pinnedFingerprint(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This device's fingerprint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = when {
                                local == null -> "Generating…"
                                showHash -> local!!.first
                                else -> local!!.second
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (showHash) 12.sp else 13.sp,
                            lineHeight = if (showHash) 18.sp else 17.sp,
                            textAlign = if (showHash) TextAlign.Start else TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
                Text(
                    text = "Deskflow shows this on the desktop when it asks whether to trust this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pinned != null) {
                    HorizontalDivider()
                    SectionLabel("Trusted server")
                    Text(
                        text = pinned,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { showHash = !showHash }, enabled = local != null) {
                Text(if (showHash) "Show art" else "Show hash")
            }
        }
    )
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "启用无障碍服务",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            FilledTonalButton(onClick = onOpenSettings) { Text("去授权") }
        }
    }
}

@Composable
private fun TrustDialog(fingerprint: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Trust this server?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Check this fingerprint against the one Deskflow shows on the desktop. Connect only if they match.")
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = fingerprint,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text("Trust and connect") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/**
 * Narrow layout: a drawer pinned to the bottom that opens up to the app bar. Collapsed it still
 * shows the newest line, because that line is usually the answer to "what is it doing".
 */
@Composable
private fun LogDrawer(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val latest = Logs.lines.lastOrNull()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 20.dp, end = 12.dp)
                    .height(LOG_PEEK_HEIGHT)
            ) {
                SectionLabel("Log")
                Spacer(Modifier.width(14.dp))
                if (expanded) {
                    Spacer(Modifier.weight(1f))
                    CopyLogButton()
                    TextButton(onClick = { Logs.clear() }) { Text("Clear") }
                } else {
                    Text(
                        text = latest?.text ?: "Nothing yet",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = entryColor(latest?.level),
                        modifier = Modifier.weight(1f)
                    )
                }
                Chevron(expanded)
            }

            if (expanded) {
                LogList(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                )
            }
        }
    }
}

/** The scrolling list itself, shared by the drawer and the wide layout's second column. */
@Composable
private fun LogList(
    modifier: Modifier = Modifier,
    header: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Logs.lines.size) {
        if (Logs.lines.isNotEmpty()) {
            listState.scrollToItem(Logs.lines.lastIndex)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (header != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = header)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(Logs.lines) { entry ->
                    Row {
                        Text(
                            text = entry.time.take(8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = entry.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = entryColor(entry.level),
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Copies the whole tail, timestamps included, so a log can be pasted somewhere useful. */
@Composable
private fun CopyLogButton() {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    TextButton(
        onClick = {
            val text = Logs.lines.joinToString("\n") { "${it.time}  ${it.text}" }
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(
                context,
                "Copied ${Logs.lines.size} log lines",
                Toast.LENGTH_SHORT
            ).show()
        },
        enabled = Logs.lines.isNotEmpty()
    ) {
        Text("Copy")
    }
}

@Composable
private fun entryColor(level: Logs.Level?): Color = when (level) {
    Logs.Level.Warn -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Chevron(expanded: Boolean) {
    // Points where tapping will send the drawer, not where it came from: up to open, down to close.
    // The path below is drawn as a downward chevron, so collapsed is the rotated state.
    val rotation by animateFloatAsState(if (expanded) 0f else 180f, tween(280), label = "chevron")
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .size(24.dp)
            .rotate(rotation)
    ) {
        val stroke = 2.dp.toPx()
        val left = Offset(size.width * 0.28f, size.height * 0.42f)
        val tip = Offset(size.width * 0.5f, size.height * 0.62f)
        val right = Offset(size.width * 0.72f, size.height * 0.42f)
        drawLine(color, left, tip, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, tip, right, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * Ask the system, rather than inferring it from whether our own service object exists. Killing the
 * app takes the service down with it (same process), so a null instance means
 * "not running right now", which is not the same as "not enabled".
 */
private fun accessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
}
