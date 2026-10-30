package app.trampis.timed

import android.os.Bundle
import android.content.res.Configuration
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caverock.androidsvg.SVG as AndroidSvg
import app.trampis.timed.data.SessionEntity
import app.trampis.timed.data.SolveEntity
import app.trampis.timed.domain.CubeEvents
import app.trampis.timed.domain.Penalty
import app.trampis.timed.domain.Statistics
import app.trampis.timed.domain.TimedResult
import app.trampis.timed.domain.CalculatorMode
import app.trampis.timed.domain.SolveCalculator
import app.trampis.timed.domain.ScrambleProvider
import app.trampis.timed.ui.TimerViewModel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import android.widget.Toast

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        setContent { CubeTimeAppThemeHost() }
    }
}

@Composable
private fun CubeTimeAppThemeHost(vm: TimerViewModel = viewModel()) {
    val override by vm.appearanceOverride.collectAsState()
    val darkSetting by vm.appearanceDark.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    TrampisTheme(if (override) darkSetting else systemDark) { CubeTimeApp(vm) }
}

@Composable
private fun TrampisTheme(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as? Activity)?.window
            if (window != null) WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val accent = Color(0xFF5179F6)
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF4D74F5), onPrimary = Color.White,
        background = Color(0xFF000000), surface = Color(0xFF1E1F21), onSurface = Color(0xFFF7F7F7),
        surfaceVariant = Color(0xFF303032), onSurfaceVariant = Color(0xFFCAC8C6),
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        background = Color(0xFFF0F2FA), surface = Color.White, onSurface = Color(0xFF17181A),
        surfaceVariant = Color(0xFFF8F9FD), onSurfaceVariant = Color(0xFF77797D),
    )
    // CubeTime uses the platform system face for its controls. Recursive is
    // reserved for timer/scramble readouts below, matching the original app.
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun CubeTimeApp(vm: TimerViewModel = viewModel()) {
    val selected by vm.selectedSession.collectAsState()
    val scramble by vm.scramble.collectAsState()
    val error by vm.error.collectAsState()
    var minimumDisplayElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(950); minimumDisplayElapsed = true }
    val dataReady = selected != null && (!scramble.startsWith("Preparing official") || error != null)
    val showApp = minimumDisplayElapsed && dataReady
    AnimatedContent(
        targetState = showApp,
        transitionSpec = {
            (fadeIn(androidx.compose.animation.core.tween(300)) + androidx.compose.animation.scaleIn(initialScale = .985f, animationSpec = androidx.compose.animation.core.tween(380))) togetherWith
                (fadeOut(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.scaleOut(targetScale = 1.015f, animationSpec = androidx.compose.animation.core.tween(260)))
        },
        label = "launch-transition",
    ) { ready -> if (ready) CubeTimeMainContent(vm) else BrandedLoadingScreen() }
}

@Composable
private fun BrandedLoadingScreen() {
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) (view.context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, view).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        }
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading-logo")
    val pulse by transition.animateFloat(
        initialValue = .975f, targetValue = 1.025f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1250, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "logo-pulse",
    )
    val rotation by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "logo-tilt",
    )
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center,
    ) {
        val logoSize = (maxWidth * .56f).coerceIn(150.dp, 224.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "Trampis Timed logo",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(logoSize).graphicsLayer { scaleX = pulse; scaleY = pulse; rotationZ = rotation },
            )
            Spacer(Modifier.height(26.dp))
            Text("TRAMPIS TIMED", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            Text("CUBE TIMER", color = Color(0xFFAEB1BC), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.2.sp, modifier = Modifier.padding(top = 7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 34.dp)) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(
                        initialValue = .26f, targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(700, delayMillis = index * 180),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
                        ), label = "loading-dot-$index",
                    )
                    Box(Modifier.size(7.dp).background(Color(0xFF6B8EFF).copy(alpha = alpha), RoundedCornerShape(50)))
                }
            }
        }
    }
}

@Composable
private fun CubeTimeMainContent(vm: TimerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by vm.sessions.collectAsState()
    val selected by vm.selectedSession.collectAsState()
    val solves by vm.solves.collectAsState()
    val running by vm.isRunning.collectAsState()
    val inspecting by vm.isInspecting.collectAsState()
    val preparing by vm.isPreparing.collectAsState()
    val inspectionEnabled by vm.inspectionEnabled.collectAsState()
    val inspectionCountdown by vm.inspectionCountdownEnabled.collectAsState()
    val holdTime by vm.holdTimeMillis.collectAsState()
    val haptics by vm.hapticsEnabled.collectAsState()
    val inspectionAlerts by vm.inspectionAlertsEnabled.collectAsState()
    val timerStats by vm.timerStatsEnabled.collectAsState()
    val scrambleDrawing by vm.scrambleDrawingEnabled.collectAsState()
    val decimalPlaces by vm.displayDecimalPlaces.collectAsState()
    val appearanceOverride by vm.appearanceOverride.collectAsState()
    val appearanceDark by vm.appearanceDark.collectAsState()
    val staticGradient by vm.staticGradient.collectAsState()
    val graphGlow by vm.graphGlow.collectAsState()
    val graphAnimation by vm.graphAnimation.collectAsState()
    val showConfetti by vm.showConfetti.collectAsState()
    val elapsed by vm.elapsedNanos.collectAsState()
    val inspectionElapsed by vm.inspectionNanos.collectAsState()
    val phaseSplits by vm.phaseSplitsNanos.collectAsState()
    val scramble by vm.scramble.collectAsState()
    val scrambleSvg by vm.scrambleSvg.collectAsState()
    val scrambleLocked by vm.isScrambleLocked.collectAsState()
    val error by vm.error.collectAsState()
    var page by remember { mutableStateOf("Timer") }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var showSessions by remember { mutableStateOf(false) }
    var showNewSession by remember { mutableStateOf(false) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val sessionId = selected?.id
        if (uri != null && sessionId != null) scope.launch {
            runCatching {
                val csv = vm.exportSessionCsv(sessionId)
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(csv) }
            }.onSuccess { Toast.makeText(context, "Session exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val exportCsTimer = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = vm.exportCsTimerJson()
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(json) }
            }.onSuccess { Toast.makeText(context, "csTimer export saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val exportOds = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.oasis.opendocument.spreadsheet")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = vm.exportOds()
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
            }.onSuccess { Toast.makeText(context, "OpenDocument export saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Session.csv"
                vm.importSessions(text, filename)
            }.onSuccess { count -> Toast.makeText(context, "Imported $count session${if (count == 1) "" else "s"}", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { if (page != "Tools") CubeTabBar(page) { page = it; selectedTool = null } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
          AnimatedContent(targetState = page, transitionSpec = {
              (fadeIn(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { it / 16 }) togetherWith
                  (fadeOut(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200)) { -it / 24 })
          }, label = "cube-page") { visiblePage ->
            when (visiblePage) {
                "Timer" -> TimerPage(vm, selected, solves, scramble, scrambleSvg, scrambleLocked, running, inspecting, preparing, elapsed, inspectionElapsed, inspectionCountdown, phaseSplits, holdTime, haptics, timerStats, scrambleDrawing, decimalPlaces, error) { showSessions = true }
                "Solves" -> Column(Modifier.fillMaxSize()) { PageHeading("Times"); SolvesPage(solves, sessions, selected?.id, vm) }
                "Stats" -> StatsPage(solves, selected, staticGradient, graphGlow, graphAnimation) { showSessions = true }
                "Sessions" -> Column(Modifier.fillMaxSize()) {
                    PageHeading("Sessions", action = { showNewSession = true })
                    SessionsPage(
                        sessions, selected,
                        onSelect = { vm.selectSession(it); page = "Timer" },
                        onCreate = { showNewSession = true },
                        onPin = { vm.setSessionPinned(it.id, !it.pinned) },
                        onUpdate = vm::updateSession,
                        onDelete = vm::deleteSession,
                        onExportCsv = { exportCsv.launch("${safeFileName(selected?.name ?: "Session")}.csv") },
                        onExportCsTimer = { exportCsTimer.launch("Trampis Timed - csTimer.json") },
                        onExportOds = { exportOds.launch("Trampis Timed - CubeTime Export.ods") },
                        onImport = { importDocument.launch(arrayOf("text/*", "application/json", "application/octet-stream")) },
                    )
                }
                "Tools" -> ToolsPage(selectedTool, onBack = { if (selectedTool != null) selectedTool = null else page = "Settings" }, onSelect = { selectedTool = it }, holdTime = holdTime)
                "Appearance" -> AppearancePage(appearanceOverride, appearanceDark, staticGradient, graphGlow, graphAnimation, showConfetti, vm,
                    onBack = { page = "Settings" })
                else -> Column(Modifier.fillMaxSize()) { PageHeading("Settings"); MorePage(inspectionEnabled, inspectionCountdown, holdTime, haptics, inspectionAlerts, timerStats, scrambleDrawing, decimalPlaces, vm, { page = "Tools" }, { page = "Appearance" }) }
            }
          }
        }
    }
    if (showSessions) SessionDialog(sessions, selected, onDismiss = { showSessions = false }, onSelect = { vm.selectSession(it); showSessions = false }, onNew = { showSessions = false; showNewSession = true })
    if (showNewSession) NewSessionDialog(onDismiss = { showNewSession = false }, onCreate = { name, event, kind, phases, target -> vm.addSession(name, event, kind, phases, target); showNewSession = false })
}

@Composable
private fun CubeTabBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        "Timer" to (Icons.Outlined.Timer to Icons.Filled.Timer),
        "Solves" to (Icons.Outlined.History to Icons.Filled.History),
        "Stats" to (Icons.Outlined.PieChart to Icons.Filled.PieChart),
        "Sessions" to (Icons.Outlined.List to Icons.Filled.List),
        "Settings" to (Icons.Outlined.Settings to Icons.Filled.Settings),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(if (selected == "Timer") MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .height(50.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, icons) ->
            val active = selected == label
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (active) 1.08f else 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 438f),
                label = "tab-icon-$label",
            )
            IconButton(onClick = { onSelect(label) }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Icon(if (active) icons.second else icons.first, contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (label == "Solves") 25.dp else 24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = action) { Text("＋") }
    }
}

@Composable
private fun SessionsPage(sessions: List<SessionEntity>, selected: SessionEntity?, onSelect: (Long) -> Unit, onCreate: () -> Unit, onPin: (SessionEntity) -> Unit, onUpdate: (SessionEntity) -> Unit, onDelete: (Long) -> Unit, onExportCsv: () -> Unit, onExportCsTimer: () -> Unit, onExportOds: () -> Unit, onImport: () -> Unit) {
    var editSession by remember { mutableStateOf<SessionEntity?>(null) }
    var deleteSession by remember { mutableStateOf<SessionEntity?>(null) }
    var menuSessionId by remember { mutableStateOf<Long?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(sessions, key = { _, session -> session.id }) { _, session ->
            ListItem(
                headlineContent = { Text(session.name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("${CubeEvents.fromId(session.eventId).name} · ${session.sessionType.replaceFirstChar { it.uppercase() }}") },
                leadingContent = { EventIcon(session.eventId, 35) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPin(session) }) { Text(if (session.pinned) "★" else "☆", color = if (session.pinned) Color(0xFFF2B544) else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Box {
                            TextButton(onClick = { menuSessionId = session.id }) { Text("⋮") }
                            DropdownMenu(expanded = menuSessionId == session.id, onDismissRequest = { menuSessionId = null }) {
                                DropdownMenuItem(text = { Text("Customize") }, onClick = { editSession = session; menuSessionId = null })
                                DropdownMenuItem(text = { Text("Delete session") }, onClick = { deleteSession = session; menuSessionId = null })
                            }
                        }
                        if (session.id == selected?.id) Icon(Icons.Filled.Timer, contentDescription = "Current session", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).clickable { onSelect(session.id) }.padding(vertical = 2.dp)
            )
        }
        item { TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("＋  New session") } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onExportCsv, enabled = selected != null) { Text("Export CSV") }
                    TextButton(onClick = onExportCsTimer, enabled = sessions.isNotEmpty()) { Text("Export csTimer") }
                }
                TextButton(onClick = onExportOds, enabled = sessions.isNotEmpty(), modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Export ODS (all sessions)") }
                TextButton(onClick = onImport, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Import CSV / csTimer JSON") }
            }
        }
    }
    editSession?.let { session -> SessionEditDialog(session, onDismiss = { editSession = null }, onSave = { onUpdate(it); editSession = null }) }
    deleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteSession = null },
            title = { Text("Delete session?") },
            text = { Text("${session.name} and all of its solves will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { onDelete(session.id); deleteSession = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteSession = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionEditDialog(session: SessionEntity, onDismiss: () -> Unit, onSave: (SessionEntity) -> Unit) {
    var name by remember(session.id) { mutableStateOf(session.name) }
    var eventId by remember(session.id) { mutableStateOf(session.eventId) }
    var phases by remember(session.id) { mutableIntStateOf(session.phaseCount.coerceIn(2, 8)) }
    var target by remember(session.id) { mutableStateOf(session.targetMillis?.let { formatMillis(it) } ?: "") }
    var pinned by remember(session.id) { mutableStateOf(session.pinned) }
    var eventMenu by remember { mutableStateOf(false) }
    val targetMillis = target.toDoubleOrNull()?.let { (it * 1000).toLong() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Session") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EventIcon(eventId, 74)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Session name") }, singleLine = true)
                if (session.sessionType == "playground") Box {
                    TextButton(onClick = { eventMenu = true }) { Text("Puzzle: ${CubeEvents.fromId(eventId).name} ▾") }
                    DropdownMenu(expanded = eventMenu, onDismissRequest = { eventMenu = false }) {
                        CubeEvents.all.forEach { event -> DropdownMenuItem(text = { Text(event.name) }, onClick = { eventId = event.id; eventMenu = false }) }
                    }
                }
                if (session.sessionType == "multiphase") Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Phases: $phases", modifier = Modifier.weight(1f))
                    TextButton(onClick = { phases = (phases - 1).coerceAtLeast(2) }) { Text("−") }
                    TextButton(onClick = { phases = (phases + 1).coerceAtMost(8) }) { Text("+") }
                }
                if (session.sessionType == "compsim") OutlinedTextField(value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Target time (seconds)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pin session", modifier = Modifier.weight(1f))
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && (session.sessionType != "compsim" || targetMillis != null), onClick = {
            onSave(session.copy(name = name.trim(), eventId = eventId, phaseCount = phases, targetMillis = targetMillis, pinned = pinned))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun TimerPage(vm: TimerViewModel, session: SessionEntity?, solves: List<SolveEntity>, scramble: String, scrambleSvg: String?, scrambleLocked: Boolean, running: Boolean, inspecting: Boolean, preparing: Boolean, elapsed: Long, inspectionElapsed: Long, inspectionCountdown: Boolean, phaseSplits: List<Long>, holdTime: Long, hapticsEnabled: Boolean, showStats: Boolean, showScrambleDrawing: Boolean, decimalPlaces: Int, error: String?, onShowSessions: () -> Unit) {
    var showScramble by remember { mutableStateOf(false) }
    var showScrambleMenu by remember { mutableStateOf(false) }
    var showEventPicker by remember { mutableStateOf(false) }
    var showPenaltyOptions by remember { mutableStateOf(false) }
    var deleteLastSolve by remember { mutableStateOf<SolveEntity?>(null) }
    var confirmUnlockScramble by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var spaceHeld by remember { mutableStateOf(false) }
    var spaceHeldLongEnough by remember { mutableStateOf(false) }
    var spaceStoppedTimer by remember { mutableStateOf(false) }
    var spaceHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val screenConfiguration = LocalConfiguration.current
    val compactLayout = screenConfiguration.screenHeightDp < 700 || screenConfiguration.fontScale > 1.25f
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)).clickable {
                    if (session?.sessionType == "playground") showEventPicker = true else onShowSessions()
                }.padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventIcon(session?.eventId ?: "333", 35)
                Spacer(Modifier.width(8.dp))
                Text(session?.name ?: "Loading session…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            if (scrambleLocked) IconButton(onClick = { vm.toggleScrambleLock() }, enabled = !running) { Icon(Icons.Filled.Lock, contentDescription = "Unlock scramble", tint = MaterialTheme.colorScheme.primary) }
        }
        Box(Modifier.fillMaxWidth().weight(if (compactLayout) 0.28f else 0.4f), contentAlignment = Alignment.Center) {
            if (!running && !inspecting && scramble.isNotBlank()) {
                AnimatedContent(targetState = scramble, transitionSpec = {
                    (fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 8 }) togetherWith
                        (fadeOut(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { -it / 8 })
                }, label = "scramble-transition") { displayedScramble ->
                    Text(displayedScramble, fontFamily = FontFamily(Font(R.font.recursive)), fontSize = if (compactLayout) 16.sp else 18.sp, lineHeight = if (compactLayout) 20.sp else 22.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).combinedClickable(onClick = { showScramble = true }, onLongClick = { showScrambleMenu = true }), maxLines = 5)
                }
                DropdownMenu(expanded = showScrambleMenu, onDismissRequest = { showScrambleMenu = false }) {
                    DropdownMenuItem(text = { Text("Copy Scramble") }, onClick = { clipboard.setText(AnnotatedString(scramble)); showScrambleMenu = false })
                    DropdownMenuItem(text = { Text(if (scrambleLocked) "Unlock Scramble" else "Lock Scramble") }, onClick = { vm.toggleScrambleLock(); showScrambleMenu = false })
                    if (!scrambleLocked) DropdownMenuItem(text = { Text("Next Scramble") }, onClick = { vm.nextScramble(); showScrambleMenu = false })
                }
            }
        }
        val timeText = when {
            running -> formatTime(elapsed, decimalPlaces)
            inspecting && inspectionCountdown -> countdownValue(inspectionElapsed)
            inspecting -> "${(inspectionElapsed / 1_000_000_000L)}"
            else -> formatTime(elapsed, decimalPlaces)
        }
        val timerColor = when {
            running -> MaterialTheme.colorScheme.onBackground
            inspecting && inspectionElapsed >= 17_000_000_000L -> Color(0xFFE34F53)
            inspecting && inspectionElapsed >= 15_000_000_000L -> Color(0xFFF28D36)
            preparing -> Color(0xFF49A867)
            else -> MaterialTheme.colorScheme.onBackground
        }
        val animatedTimerColor by androidx.compose.animation.animateColorAsState(
            targetValue = timerColor,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 438f),
            label = "timer-state-color",
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(if (compactLayout) 0.95f else 1.2f).focusable().onPreviewKeyEvent { event ->
            val control = event.nativeKeyEvent.isCtrlPressed
            if (control && event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.N -> { if (!scrambleLocked && !running) vm.nextScramble(); true }
                    Key.Backspace, Key.Z -> { deleteLastSolve = solves.firstOrNull(); true }
                    Key.One -> { solves.firstOrNull()?.let { vm.applyPenalty(it.id, Penalty.NONE) }; true }
                    Key.Two -> { solves.firstOrNull()?.let { vm.applyPenalty(it.id, Penalty.PLUS_TWO) }; true }
                    Key.Three -> { solves.firstOrNull()?.let { vm.applyPenalty(it.id, Penalty.DNF) }; true }
                    else -> false
                }
            } else if (event.key == Key.Spacebar) {
                if (event.type == KeyEventType.KeyDown && !spaceHeld) {
                    spaceHeld = true
                    spaceHeldLongEnough = false
                    spaceStoppedTimer = running
                    if (running) vm.stopAtTouch() else spaceHoldJob = scope.launch {
                        delay(holdTime)
                        if (spaceHeld) {
                            spaceHeldLongEnough = true
                            vm.setPreparing(true)
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    true
                } else if (event.type == KeyEventType.KeyUp && spaceHeld) {
                    spaceHeld = false
                    spaceHoldJob?.cancel()
                    vm.setPreparing(false)
                    if (!spaceStoppedTimer) {
                        if (spaceHeldLongEnough) vm.startFromHold() else vm.shortPress()
                    }
                    true
                } else true
            } else false
        }.pointerInput(running, inspecting, holdTime) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (running) { vm.stopAtTouch(); down.consume(); return@awaitEachGesture }
                vm.setPreparing(false)
                var swiped: String? = null
                var releasedBeforeHold = false
                val finished = withTimeoutOrNull(holdTime) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: continue
                        val delta = change.position - down.position
                        if (delta.getDistance() >= viewConfiguration.touchSlop * 2.5f) {
                            swiped = if (kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)) if (delta.x > 0) "right" else "left" else if (delta.y > 0) "down" else "up"
                            change.consume()
                            return@withTimeoutOrNull true
                        }
                        if (!change.pressed) { releasedBeforeHold = true; change.consume(); return@withTimeoutOrNull true }
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (swiped != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (swiped) {
                        "down" -> showPenaltyOptions = solves.isNotEmpty()
                        "up" -> showPenaltyOptions = false
                        "left" -> deleteLastSolve = solves.firstOrNull()
                        "right" -> if (scrambleLocked) confirmUnlockScramble = true else vm.nextScramble()
                    }
                    return@awaitEachGesture
                }
                if (finished != null && releasedBeforeHold) {
                    vm.setPreparing(false)
                    vm.shortPress()
                    return@awaitEachGesture
                } else {
                    vm.setPreparing(true)
                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val up = waitForUpOrCancellation()
                    vm.setPreparing(false)
                    if (up != null) { vm.startFromHold(); up.consume() }
                }
            }
        }, contentAlignment = Alignment.Center) {
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val timerSize = (maxWidth.value * 0.19f / fontScale).coerceIn(38f, 64f).sp
            val timerScale by androidx.compose.animation.core.animateFloatAsState(if (preparing) 1.055f else if (running) .985f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = .68f, stiffness = 320f), label = "timer-scale")
            Row(Modifier.graphicsLayer { scaleX = timerScale; scaleY = timerScale }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(timeText, fontFamily = FontFamily(Font(R.font.recursive)), fontSize = timerSize, fontWeight = FontWeight.Light, color = animatedTimerColor, maxLines = 1, softWrap = false)
                if (inspecting && inspectionElapsed >= 15_000_000_000L) {
                    Text(if (inspectionElapsed >= 17_000_000_000L) " [DNF]" else " [+2]", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = timerColor)
                }
            }
        }
        if (session?.sessionType == "multiphase" && running) Text("PHASE ${phaseSplits.size + 1} / ${session.phaseCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (inspecting) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Inspection", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.cancelInspection() }) { Text("Cancel") }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        if (showStats || showScrambleDrawing) {
            val widgetHeight = if (compactLayout) (110f * screenConfiguration.fontScale.coerceIn(1f, 1.55f)).coerceAtMost(170f).dp else 120.dp
            Row(Modifier.fillMaxWidth().height(widgetHeight).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showScrambleDrawing) Card(Modifier.weight(1f).fillMaxHeight().clickable { showScramble = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ScrambleDrawing(scrambleSvg, session?.eventId ?: "333", Modifier.fillMaxSize().padding(5.dp))
            }
            if (showStats) Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                TimerStatsPanel(solves, session)
            }
            }
        }
    }
    if (showScramble) AlertDialog(onDismissRequest = { showScramble = false }, title = { Text("Scramble") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ScrambleDrawing(scrambleSvg, session?.eventId ?: "333", Modifier.fillMaxWidth().height(150.dp))
            Text(scramble, fontFamily = FontFamily(Font(R.font.recursive)), modifier = Modifier.padding(top = 12.dp))
        }
    }, confirmButton = { TextButton(onClick = { showScramble = false }) { Text("Done") } })
    if (showPenaltyOptions) AlertDialog(onDismissRequest = { showPenaltyOptions = false }, title = { Text("Last solve penalty") }, text = { Text("${solves.firstOrNull()?.let(::formatSolve) ?: "No solve"} · ${solves.firstOrNull()?.let { formatDate(it.createdAt) } ?: ""}") }, confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { solves.firstOrNull()?.let { vm.applyPenalty(it.id, Penalty.NONE) }; showPenaltyOptions = false }) { Text("OK") }
            TextButton(onClick = { solves.firstOrNull()?.let { vm.applyPenalty(it.id, Penalty.PLUS_TWO) }; showPenaltyOptions = false }) { Text("+2") }
            TextButton(onClick = { solves.firstOrNull()?.let { vm.applyPenalty(it.id, Penalty.DNF) }; showPenaltyOptions = false }) { Text("DNF") }
        }
    })
    deleteLastSolve?.let { target -> AlertDialog(onDismissRequest = { deleteLastSolve = null }, title = { Text("Delete last solve?") }, text = { Text("${formatSolve(target)} will be removed from this session.") }, confirmButton = { TextButton(onClick = { vm.deleteSolve(target.id); deleteLastSolve = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteLastSolve = null }) { Text("Cancel") } }) }
    if (confirmUnlockScramble) AlertDialog(onDismissRequest = { confirmUnlockScramble = false }, title = { Text("Unlock scramble?") }, text = { Text("Unlocking will generate a new scramble.") }, confirmButton = { TextButton(onClick = { vm.toggleScrambleLock(); confirmUnlockScramble = false }) { Text("Unlock") } }, dismissButton = { TextButton(onClick = { confirmUnlockScramble = false }) { Text("Cancel") } })
    if (showEventPicker) AlertDialog(onDismissRequest = { showEventPicker = false }, title = { Text("Choose Puzzle") }, text = {
        LazyColumn { itemsIndexed(CubeEvents.all) { _, event -> ListItem(headlineContent = { Text(event.name) }, leadingContent = { EventIcon(event.id, 32) }, modifier = Modifier.clickable { vm.setPlaygroundEvent(event.id); showEventPicker = false }) } }
    }, confirmButton = { TextButton(onClick = { showEventPicker = false }) { Text("Done") } })
}

private fun countdownValue(elapsedNanos: Long): String {
    val seconds = elapsedNanos / 1_000_000_000L
    return when {
        seconds >= 16 -> "−"
        else -> (15 - seconds).coerceAtLeast(0).toString()
    }
}

@Composable
private fun EventIcon(eventId: String, size: Int) {
    val icon = when (eventId) {
        "222" -> R.drawable.event_222; "333" -> R.drawable.event_333; "444" -> R.drawable.event_444
        "555" -> R.drawable.event_555; "666" -> R.drawable.event_666; "777" -> R.drawable.event_777
        "sq1" -> R.drawable.event_sq1; "minx" -> R.drawable.event_minx; "pyram" -> R.drawable.event_pyram
        "clock" -> R.drawable.event_clock; "skewb" -> R.drawable.event_skewb; "333oh" -> R.drawable.event_333oh
        "333bld" -> R.drawable.event_333bld; "444bld" -> R.drawable.event_444bld; "555bld" -> R.drawable.event_555bld
        else -> R.drawable.event_333
    }
    androidx.compose.foundation.Image(painterResource(icon), contentDescription = CubeEvents.fromId(eventId).name, modifier = Modifier.size(size.dp))
}

@Composable
private fun ScrambleDrawing(svg: String?, eventId: String, modifier: Modifier = Modifier) {
    val bitmap = remember(svg) {
        if (svg.isNullOrBlank()) null else runCatching {
            val document = AndroidSvg.getFromString(svg)
            val width = document.documentWidth.roundToInt().coerceIn(1, 1200)
            val height = document.documentHeight.roundToInt().coerceIn(1, 1200)
            val picture = document.renderToPicture(width, height)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawPicture(picture) }
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "${CubeEvents.fromId(eventId).name} scramble drawing", modifier = modifier)
    else Box(modifier, contentAlignment = Alignment.Center) { EventIcon(eventId, 74) }
}

@Composable
private fun TimerStatsPanel(solvesDescending: List<SolveEntity>, session: SessionEntity? = null) {
    val results = solvesDescending.reversed().map { solve ->
        TimedResult(solve.timeMillis, when (solve.penalty) { Penalty.PLUS_TWO.ordinal -> Penalty.PLUS_TWO; Penalty.DNF.ordinal -> Penalty.DNF; else -> Penalty.NONE })
    }
    val ao5 = results.takeLast(5).let(Statistics::average)?.let { if (it.isDnf) "DNF" else it.millis?.let { value -> formatMillis(value.toLong()) } }
    val ao12 = results.takeLast(12).let(Statistics::average)?.let { if (it.isDnf) "DNF" else it.millis?.let { value -> formatMillis(value.toLong()) } }
    val ao100 = results.takeLast(100).let(Statistics::average)?.let { if (it.isDnf) "DNF" else it.millis?.let { value -> formatMillis(value.toLong()) } }
    val meanValues = results.map { it.adjustedMillis }.filter { it != Long.MAX_VALUE }
    val mean = meanValues.takeIf { it.isNotEmpty() }?.average()?.toLong()?.let(::formatMillis)
    if (session?.sessionType == "compsim" && session.targetMillis != null) {
        val progress = Statistics.compSimProgress(results, session.targetMillis)
        val bpa = progress.currentBpaMillis?.let { if (progress.currentBpaIsDnf) "DNF · ${formatMillis(it.toLong())}" else formatMillis(it.toLong()) }
        val wpa = progress.currentWpaMillis?.let { if (progress.currentWpaIsDnf) "DNF · ${formatMillis(it.toLong())}" else formatMillis(it.toLong()) }
        val target = when {
            progress.targetGuaranteed -> "Guaranteed"
            progress.targetImpossible -> "Not possible"
            progress.neededForTargetMillis?.let { formatMillis(it.toLong()) } != null -> progress.neededForTargetMillis?.let { formatMillis(it.toLong()) }
            else -> "—"
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { MiniStat("BPA", bpa); MiniStat("WPA", wpa) }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            MiniStat("TO REACH TARGET · ${formatMillis(session.targetMillis)}", target)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { MiniStat("AO5", ao5); MiniStat("AO12", ao12) }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { MiniStat("AO100", ao100); MiniStat("MEAN", mean) }
    }
}

@Composable
private fun MiniStat(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(value ?: "-", fontFamily = FontFamily(Font(R.font.recursive)), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SolvesPage(solves: List<SolveEntity>, sessions: List<SessionEntity>, currentSessionId: Long?, vm: TimerViewModel) {
    var query by remember { mutableStateOf("") }
    var detailSolve by remember { mutableStateOf<SolveEntity?>(null) }
    var selectMode by remember(currentSessionId) { mutableStateOf(false) }
    var selectedIds by remember(currentSessionId) { mutableStateOf(emptySet<Long>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var batchMenu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    var copyMenu by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf(false) }
    var sortMode by remember(currentSessionId) { mutableStateOf("Newest first") }
    if (solves.isEmpty()) {
        EmptyState("No solves yet", "Your times will appear here.")
    } else {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search times and scrambles") },
                leadingIcon = { Text("⌕", fontSize = 22.sp) },
            )
            val matches = solves.filter {
                query.isBlank() || formatSolve(it).contains(query, ignoreCase = true) ||
                    it.scramble.contains(query, ignoreCase = true) || it.comment.contains(query, ignoreCase = true)
            }
            val filtered = when (sortMode) {
                "Oldest first" -> matches.sortedWith(compareBy<SolveEntity> { it.createdAt }.thenBy { it.id })
                "Fastest first" -> matches.sortedWith(compareBy<SolveEntity> { if (it.penalty == Penalty.DNF.ordinal) Long.MAX_VALUE else it.timeMillis + if (it.penalty == Penalty.PLUS_TWO.ordinal) 2_000 else 0 })
                "Slowest first" -> matches.sortedWith(compareByDescending<SolveEntity> { if (it.penalty == Penalty.DNF.ordinal) Long.MAX_VALUE else it.timeMillis + if (it.penalty == Penalty.PLUS_TWO.ordinal) 2_000 else 0 })
                else -> matches.sortedWith(compareByDescending<SolveEntity> { it.createdAt }.thenByDescending { it.id })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} solves", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (selectMode) {
                    TextButton(onClick = { selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet() }) { Text(if (selectedIds.size == filtered.size) "Deselect all" else "Select all") }
                    Box {
                        TextButton(onClick = { batchMenu = true }, enabled = selectedIds.isNotEmpty()) { Text("Actions") }
                        DropdownMenu(expanded = batchMenu, onDismissRequest = { batchMenu = false }) {
                            DropdownMenuItem(text = { Text("No Penalty") }, onClick = { vm.applyPenalty(selectedIds.toList(), Penalty.NONE); selectedIds = emptySet(); batchMenu = false })
                            DropdownMenuItem(text = { Text("+2") }, onClick = { vm.applyPenalty(selectedIds.toList(), Penalty.PLUS_TWO); selectedIds = emptySet(); batchMenu = false })
                            DropdownMenuItem(text = { Text("DNF") }, onClick = { vm.applyPenalty(selectedIds.toList(), Penalty.DNF); selectedIds = emptySet(); batchMenu = false })
                            DropdownMenuItem(text = { Text("Move to…") }, onClick = { moveMenu = true; batchMenu = false })
                            DropdownMenuItem(text = { Text("Copy to…") }, onClick = { copyMenu = true; batchMenu = false })
                            DropdownMenuItem(text = { Text("Delete selected") }, onClick = { deleteConfirmation = true; batchMenu = false })
                            DropdownMenuItem(text = { Text("Cancel selection") }, onClick = { selectedIds = emptySet(); selectMode = false; batchMenu = false })
                        }
                    }
                } else {
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Text(sortMode) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("Newest first", "Oldest first", "Fastest first", "Slowest first").forEach { mode ->
                                DropdownMenuItem(text = { Text(mode) }, onClick = { sortMode = mode; sortMenu = false })
                            }
                        }
                        TextButton(onClick = { selectMode = true }) { Text("Select") }
                    }
                }
            }
            if (filtered.isEmpty()) EmptyState("No matching solves", "Try a different time or scramble.")
            else LazyColumn {
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, solve ->
                    var penaltyMenu by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("${filtered.size - index}.  ${formatSolve(solve)}", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("${formatDate(solve.createdAt)}\n${solve.scramble}", maxLines = 3) },
                        trailingContent = {
                            Box {
                                if (selectMode) {
                                    Checkbox(checked = solve.id in selectedIds, onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + solve.id else selectedIds - solve.id
                                    })
                                } else {
                                    TextButton(onClick = { penaltyMenu = true }) {
                                        Text(when (solve.penalty) {
                                            Penalty.PLUS_TWO.ordinal -> "+2"
                                            Penalty.DNF.ordinal -> "DNF"
                                            else -> "Penalty"
                                        })
                                    }
                                }
                                DropdownMenu(expanded = penaltyMenu, onDismissRequest = { penaltyMenu = false }) {
                                    listOf("No Penalty" to Penalty.NONE, "+2" to Penalty.PLUS_TWO, "DNF" to Penalty.DNF).forEach { (label, penalty) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = {
                                            vm.applyPenalty(solve.id, penalty)
                                            penaltyMenu = false
                                        })
                                    }
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                        vm.deleteSolve(solve.id)
                                        penaltyMenu = false
                                    })
                                }
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (selectMode) selectedIds = if (solve.id in selectedIds) selectedIds - solve.id else selectedIds + solve.id
                                else detailSolve = solve
                            },
                            onLongClick = { selectMode = true; selectedIds = selectedIds + solve.id },
                        ),
                    )
                }
            }
            val moveTargets = sessions.filter { it.id != currentSessionId && it.eventId == sessions.firstOrNull { session -> session.id == currentSessionId }?.eventId }
            val destinationTargets = moveTargets
            Box {
                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                    destinationTargets.forEach { destination -> DropdownMenuItem(text = { Text(destination.name) }, onClick = {
                        vm.moveSolves(selectedIds.toList(), destination)
                        selectedIds = emptySet(); selectMode = false; moveMenu = false
                    }) }
                    if (destinationTargets.isEmpty()) DropdownMenuItem(text = { Text("No compatible sessions") }, enabled = false, onClick = {})
                }
                DropdownMenu(expanded = copyMenu, onDismissRequest = { copyMenu = false }) {
                    destinationTargets.forEach { destination -> DropdownMenuItem(text = { Text(destination.name) }, onClick = {
                        vm.copySolves(selectedIds.toList(), destination)
                        selectedIds = emptySet(); selectMode = false; copyMenu = false
                    }) }
                    if (destinationTargets.isEmpty()) DropdownMenuItem(text = { Text("No compatible sessions") }, enabled = false, onClick = {})
                }
            }
        }
    }
    if (deleteConfirmation) AlertDialog(
        onDismissRequest = { deleteConfirmation = false },
        title = { Text("Delete selected solves?") },
        text = { Text("This removes ${selectedIds.size} solve${if (selectedIds.size == 1) "" else "s"} permanently.") },
        confirmButton = { TextButton(onClick = { vm.deleteSolves(selectedIds.toList()); selectedIds = emptySet(); selectMode = false; deleteConfirmation = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Cancel") } },
    )
    detailSolve?.let { solve ->
        SolveDetailDialog(solve, vm) { detailSolve = null }
    }
}

@Composable
private fun SolveDetailDialog(solve: SolveEntity, vm: TimerViewModel, onDismiss: () -> Unit) {
    var comment by remember(solve.id) { mutableStateOf(solve.comment) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { vm.setComment(solve.id, comment); onDismiss() },
        title = { Text(formatSolve(solve)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(formatDate(solve.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Scramble", style = MaterialTheme.typography.titleSmall)
                Text(solve.scramble, modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { clipboard.setText(AnnotatedString(solve.scramble)) }) { Text("Copy scramble") }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    placeholder = { Text("Comment something…") },
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.setComment(solve.id, comment); onDismiss() }) { Text("Done") } },
    )
}

@Composable
private fun StatsPage(solves: List<SolveEntity>, session: SessionEntity? = null, staticGradient: Boolean = true, graphGlow: Boolean = true, graphAnimation: Boolean = true, onChooseSession: () -> Unit = {}) {
    val results = solves.reversed().map { solve ->
        TimedResult(solve.timeMillis, when (solve.penalty) { Penalty.PLUS_TWO.ordinal -> Penalty.PLUS_TWO; Penalty.DNF.ordinal -> Penalty.DNF; else -> Penalty.NONE })
    }
    val best = Statistics.bestSingle(results)
    val valid = results.map { it.adjustedMillis }.filter { it != Long.MAX_VALUE }
    val mean = results.takeIf { it.isNotEmpty() && it.none { r -> r.penalty == Penalty.DNF } }
        ?.map { it.adjustedMillis.toDouble() }?.average()?.toLong()
    val median = Statistics.median(results)?.toLong()
    val ao5 = results.takeLast(5).let(Statistics::average)
    val ao12 = results.takeLast(12).let(Statistics::average)
    val ao100 = results.takeLast(100).let(Statistics::average)
    val bestAo5 = Statistics.bestAverage(results, 5)
    val bestAo12 = Statistics.bestAverage(results, 12)
    val bestAo100 = Statistics.bestAverage(results, 100)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp)) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onChooseSession).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("▢", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(session?.name ?: "Default Session", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (session?.sessionType == "compsim" && session.targetMillis != null) {
            val progress = Statistics.compSimProgress(results, session.targetMillis)
            val complete = progress.groups.filter { it.average != null }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("CURRENT", formatAverage(progress.groups.lastOrNull()?.average), Modifier.weight(1f), height = 158)
                StatsCard("AVERAGES", complete.size.toString(), Modifier.weight(1f), height = 158)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("TARGET", formatMillis(session.targetMillis), Modifier.weight(1f))
                StatsCard("REACHED", complete.count { it.average?.let { avg -> !avg.isDnf && (avg.millis ?: Double.POSITIVE_INFINITY) <= session.targetMillis } == true }.toString(), Modifier.weight(1f))
            }
            StatsTrendChart(solves)
            StatsDistributionChart(solves)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsCard("CURRENT STATS", listOf("AO5" to formatAverage(ao5), "AO12" to formatAverage(ao12), "AO100" to formatAverage(ao100)), Modifier.weight(1f), height = 164)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("SOLVE COUNT", solves.size.toString(), Modifier.fillMaxWidth(), height = 77)
                StatsCard("SESSION MEAN", mean?.let(::formatMillis) ?: "—", Modifier.fillMaxWidth(), height = 77)
            }
        }
        val bestWindow = results.takeIf { it.size >= 5 }?.windowed(5)?.minByOrNull { w ->
            Statistics.average(w)?.let { if (it.isDnf) Double.POSITIVE_INFINITY else it.millis ?: Double.POSITIVE_INFINITY } ?: Double.POSITIVE_INFINITY
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("BEST SINGLE", best?.let(::formatMillis) ?: "—", Modifier.fillMaxWidth(), accent = true, dynamic = !staticGradient)
                StatsCard("BEST STATS", listOf("AO12" to formatAverage(bestAo12), "AO100" to formatAverage(bestAo100)), Modifier.fillMaxWidth(), height = 150)
            }
            StatsCard("BEST AO5", bestAo5?.let(::formatAverage) ?: "—", Modifier.weight(1f), height = 235,
                details = bestWindow?.mapIndexed { i, r ->
                    val text = if (r.penalty == Penalty.DNF) "DNF" else formatMillis(r.adjustedMillis)
                    if (Statistics.average(bestWindow)?.trimmed?.contains(i) == true) "($text)" else text
                }.orEmpty())
        }
        StatsTrendChart(solves, graphGlow, graphAnimation)
        StatsDistributionChart(solves)
    }
}

private fun formatAverage(average: app.trampis.timed.domain.Average?): String = when {
    average == null -> "—"
    average.isDnf -> "DNF"
    average.millis == null -> "—"
    else -> formatMillis(average.millis!!.toLong())
}

@Composable
private fun StatsCard(title: String, values: List<Pair<String, String>>, modifier: Modifier = Modifier, height: Int = 140) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.45f)
    Column(modifier.height((height * scale).dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        values.forEach { (label, value) ->
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily(Font(R.font.recursive)), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, value: String, modifier: Modifier = Modifier, height: Int = 88, accent: Boolean = false, details: List<String> = emptyList(), dynamic: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    val color = MaterialTheme.colorScheme.surface
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.45f)
    val gradientColors = if (dynamic) listOf(Color(0xFF27C4B8), Color(0xFFB6D944), Color(0xFFFFB52E), Color(0xFFE84F82), Color(0xFF7158E8)) else listOf(Color(0xFF83A6FF), Color(0xFF4169E1))
    Column((if (accent) modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(gradientColors), shape) else modifier.background(color, shape)).height((height * scale).dp)
        .padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (accent) Color.White.copy(alpha = .85f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (details.isEmpty()) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily(Font(R.font.recursive)), fontWeight = FontWeight.Bold,
                color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).wrapContentHeight(Alignment.Bottom), maxLines = 1, softWrap = false)
        } else {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily(Font(R.font.recursive)), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            details.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily(Font(R.font.recursive)), color = if (it.startsWith("(")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1) }
        }
    }
}

@Composable
private fun StatsTrendChart(solvesDescending: List<SolveEntity>, glowEnabled: Boolean = true, animationEnabled: Boolean = true) {
    val values = solvesDescending.asReversed().takeLast(80).mapNotNull { solve ->
        if (solve.penalty == Penalty.DNF.ordinal) null
        else solve.timeMillis + if (solve.penalty == Penalty.PLUS_TWO.ordinal) 2_000 else 0
    }
    var selected by remember(values) { mutableIntStateOf(values.lastIndex) }
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val reveal = remember(values, animationEnabled) { androidx.compose.animation.core.Animatable(if (animationEnabled) 0f else 1f) }
    LaunchedEffect(values, animationEnabled) {
        if (animationEnabled) reveal.animateTo(1f, androidx.compose.animation.core.tween(650)) else reveal.snapTo(1f)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Text("Time Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(values.getOrNull(selected)?.let(::formatMillis) ?: "Touch a point to inspect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp).pointerInput(values) {
            detectTapGestures { offset -> if (values.isNotEmpty()) selected = ((offset.x / size.width) * (values.size - 1)).roundToInt().coerceIn(values.indices) }
        }) {
            val left = 10f; val right = size.width - 10f; val top = 8f; val bottom = size.height - 10f
            for (index in 0..3) {
                val y = top + (bottom - top) * index / 3f
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            if (values.isNotEmpty()) {
                val min = values.minOrNull()!!.toFloat()
                val max = values.maxOrNull()!!.toFloat()
                val range = (max - min).coerceAtLeast(1f)
                val points = values.mapIndexed { index, value ->
                    Offset(
                        if (values.size == 1) (left + right) / 2 else left + (right - left) * index / (values.size - 1),
                        bottom - (bottom - top) * ((value - min) / range),
                    )
                }
                val visible = (points.size * reveal.value).toInt().coerceAtLeast(if (points.isNotEmpty()) 1 else 0).coerceAtMost(points.size)
                val visiblePoints = points.take(visible)
                if (glowEnabled) visiblePoints.zipWithNext().forEach { (a, b) -> drawLine(accent.copy(alpha = .20f), a, b, strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round) }
                visiblePoints.zipWithNext().forEach { (a, b) -> drawLine(accent, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round) }
                visiblePoints.forEachIndexed { index, point -> drawCircle(if (index == selected) accent else accent.copy(alpha = 0.5f), radius = if (index == selected) 5.dp.toPx() else 2.5.dp.toPx(), center = point) }
            }
        }
    }
}

@Composable
private fun StatsDistributionChart(solvesDescending: List<SolveEntity>) {
    val values = solvesDescending.mapNotNull { solve ->
        when (solve.penalty) {
            Penalty.DNF.ordinal -> null
            Penalty.PLUS_TWO.ordinal -> solve.timeMillis + 2_000
            else -> solve.timeMillis
        }
    }
    val bins = Statistics.distribution(values)
    var selected by remember(bins) { mutableIntStateOf(-1) }
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val medianColor = MaterialTheme.colorScheme.onSurface
    val median = Statistics.median(values.map { TimedResult(it) })
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Text("Time Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            when {
                bins.isEmpty() -> "At least four non-DNF solves are needed"
                selected in bins.indices -> "${formatMillis(bins[selected].centerMillis)} · ${bins[selected].count} solves"
                else -> "Tap a bar · fastest and slowest 10% trimmed"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 10.dp).pointerInput(bins) {
            detectTapGestures { offset -> if (bins.isNotEmpty()) selected = ((offset.x / size.width) * bins.size).toInt().coerceIn(bins.indices) }
        }) {
            val top = 6f
            val bottom = size.height - 8f
            for (step in 0..3) {
                val y = top + (bottom - top) * step / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            if (bins.isNotEmpty()) {
                val maxCount = (bins.maxOf { it.count }).coerceAtLeast(1)
                val slot = size.width / bins.size
                val barWidth = slot * 0.62f
                bins.forEachIndexed { index, bin ->
                    val height = (bottom - top) * bin.count / maxCount
                    val left = index * slot + (slot - barWidth) / 2f
                    drawRoundRect(
                        color = if (index == selected) accent else accent.copy(alpha = 0.68f),
                        topLeft = Offset(left, bottom - height),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                }
                if (values.size > 1 && median != null) {
                    val min = values.minOrNull()!!.toFloat()
                    val max = values.maxOrNull()!!.toFloat()
                    val ratio = ((median.toFloat() - min) / (max - min).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val x = ratio * size.width
                    drawLine(medianColor, Offset(x, top), Offset(x, bottom), 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

private data class CalculatorSolve(val id: Int, val timeMillis: Long, val penalty: Penalty = Penalty.NONE)

@Composable
private fun ToolsPage(selectedTool: String?, onBack: () -> Unit, onSelect: (String) -> Unit, holdTime: Long) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(selectedTool ?: "Tools", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        when (selectedTool) {
            "Timer Only" -> TimerOnlyToolPage(holdTime)
            "Scramble Only" -> ScrambleOnlyToolPage()
            "Scramble Generator" -> ScrambleGeneratorToolPage()
            "Calculator" -> CalculatorToolPage()
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "Timer Only" to "A standalone timer. Results are not saved to sessions.",
                    "Scramble Only" to "Display and cycle through official event scrambles.",
                    "Scramble Generator" to "Generate a batch of scrambles to copy and share.",
                    "Calculator" to "Calculate a WCA average or mean from entered times.",
                ).forEach { (name, description) ->
                    Card(Modifier.fillMaxWidth().clickable { onSelect(name) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) { Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerOnlyToolPage(holdTimeMillis: Long) {
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(0L) }
    LaunchedEffect(running) {
        while (running) { now = SystemClock.elapsedRealtimeNanos(); delay(16) }
    }
    val shown = if (running) (now - startedAt).coerceAtLeast(0L) else elapsed
    Box(Modifier.fillMaxSize().pointerInput(running, holdTimeMillis) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (running) {
                elapsed = (SystemClock.elapsedRealtimeNanos() - startedAt).coerceAtLeast(0L)
                running = false
                down.consume()
            } else {
                val released = withTimeoutOrNull(holdTimeMillis) { waitForUpOrCancellation() }
                if (released != null) {
                    startedAt = SystemClock.elapsedRealtimeNanos(); running = true; released.consume()
                } else {
                    val up = waitForUpOrCancellation()
                    if (up != null) { startedAt = SystemClock.elapsedRealtimeNanos(); running = true; up.consume() }
                }
            }
        }
    }, contentAlignment = Alignment.Center) {
        Text(formatTime(shown), fontFamily = FontFamily(Font(R.font.recursive)), fontSize = 68.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
        if (!running) TextButton(onClick = { elapsed = 0L }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) { Text("Reset") }
    }
}

@Composable
private fun ScrambleOnlyToolPage() {
    var eventId by remember { mutableStateOf("333") }
    var expanded by remember { mutableStateOf(false) }
    var notation by remember { mutableStateOf("") }
    var svg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val provider = remember { ScrambleProvider() }
    fun next() {
        scope.launch {
            busy = true; error = null
            runCatching { provider.nextWithDrawing(eventId) }
                .onSuccess { notation = it.notation; svg = it.drawingSvg }
                .onFailure { error = "Scramble generation failed: ${it.message}" }
            busy = false
        }
    }
    LaunchedEffect(eventId) { next() }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box {
            TextButton(onClick = { expanded = true }) { Text("${CubeEvents.fromId(eventId).name}  ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { CubeEvents.all.forEach { event -> DropdownMenuItem(text = { Text(event.name) }, onClick = { eventId = event.id; expanded = false }) } }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clickable(enabled = !busy) { next() }, contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                else -> Text(notation, fontFamily = FontFamily(Font(R.font.recursive)), fontSize = 23.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        Text("Tap for next scramble", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun ScrambleGeneratorToolPage() {
    var eventId by remember { mutableStateOf("333") }
    var countText by remember { mutableStateOf("25") }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val generated = remember { mutableStateListOf<String>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val provider = remember { ScrambleProvider() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { expanded = true }) { Text(CubeEvents.fromId(eventId).name, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { CubeEvents.all.forEach { event -> DropdownMenuItem(text = { Text(event.name) }, onClick = { eventId = event.id; expanded = false }) } }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = countText, onValueChange = { countText = it.filter(Char::isDigit).take(4) }, label = { Text("Count") }, singleLine = true, modifier = Modifier.width(100.dp), enabled = !busy)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                val count = countText.toIntOrNull()?.coerceIn(1, 5000) ?: 25
                countText = count.toString(); generated.clear(); busy = true; error = null
                job = scope.launch {
                    try {
                        repeat(count) { generated.add(provider.next(eventId)) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = "Generation stopped after ${generated.size} scrambles: ${e.message}"
                    } finally { busy = false; job = null }
                }
            }) { Text("Generate") }
            if (busy) TextButton(onClick = { job?.cancel() }) { Text("Cancel") }
        }
        if (busy) LinearProgressIndicator(progress = { generated.size.toFloat() / (countText.toIntOrNull()?.coerceAtLeast(1) ?: 1) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        if (generated.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(generated.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))) }) { Text("Copy All") }
            TextButton(onClick = { val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "Generated by Trampis Timed\n" + generated.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")); context.startActivity(android.content.Intent.createChooser(intent, "Share scrambles")) }) { Text("Share") }
        }
        if (!busy && generated.isNotEmpty() && generated.size == countText.toIntOrNull()) Text("Success! ${generated.size} scrambles", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(generated) { index, scramble ->
                Card(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(scramble)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", modifier = Modifier.width(34.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(scramble, fontFamily = FontFamily(Font(R.font.recursive)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorToolPage() {
    var mode by remember { mutableStateOf(CalculatorMode.AVERAGE) }
    var widthText by remember { mutableStateOf("5") }
    var input by remember { mutableStateOf("") }
    val solves = remember { mutableStateListOf<CalculatorSolve>() }
    val width = widthText.toIntOrNull()?.coerceIn(1, 100) ?: 5
    val results = solves.map { TimedResult(it.timeMillis, it.penalty) }
    val average = SolveCalculator.result(results, mode, width)
    val trimmed = average?.trimmed?.toSet().orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = mode == CalculatorMode.AVERAGE, onClick = { mode = CalculatorMode.AVERAGE }, label = { Text("Average") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = mode == CalculatorMode.MEAN, onClick = { mode = CalculatorMode.MEAN }, label = { Text("Mean") })
            Spacer(Modifier.weight(1f))
            OutlinedTextField(value = widthText, onValueChange = { widthText = it.filter(Char::isDigit).take(3) }, label = { Text("Count") }, singleLine = true, modifier = Modifier.width(100.dp))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ':' } }, label = { Text("Time · e.g. 12.34 or 1:02.34") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { SolveCalculator.parseTime(input)?.let { solves.add(CalculatorSolve((solves.maxOfOrNull { s -> s.id } ?: 0) + 1, it)); input = "" } }) { Text("Add") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${solves.size} / $width times", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { solves.clear() }, enabled = solves.isNotEmpty()) { Text("Clear") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(solves) { index, solve ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val time = when (solve.penalty) { Penalty.DNF -> "DNF"; Penalty.PLUS_TWO -> "${formatMillis(solve.timeMillis + 2_000)}+"; else -> formatMillis(solve.timeMillis) }
                        Text(if (index in trimmed) "($time)" else time, fontWeight = FontWeight.SemiBold, color = if (index in trimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        TextButton(onClick = { solves[index] = solve.copy(penalty = Penalty.entries[(solve.penalty.ordinal + 1) % Penalty.entries.size]) }) { Text(when (solve.penalty) { Penalty.NONE -> "+2"; Penalty.PLUS_TWO -> "DNF"; Penalty.DNF -> "OK" }) }
                        TextButton(onClick = { solves.removeAt(index) }) { Text("×") }
                    }
                }
            }
        }
        SettingGroup("Result") {
            Text(when {
                solves.size != width -> "Enter $width times to calculate"
                average == null -> "—"
                average.isDnf -> "DNF"
                average.millis == null -> "—"
                else -> average.millis?.let { formatMillis(it.toLong()) } ?: "—"
            }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily(Font(R.font.recursive)))
        }
    }
}

@Composable
private fun MorePage(inspectionEnabled: Boolean, inspectionCountdown: Boolean, holdTime: Long, haptics: Boolean, inspectionAlerts: Boolean, timerStats: Boolean, scrambleDrawing: Boolean, decimalPlaces: Int, vm: TimerViewModel, onTools: () -> Unit, onAppearance: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingGroup("Appearance") {
            ListItem(headlineContent = { Text("Appearance", fontWeight = FontWeight.Medium) }, supportingContent = { Text("Theme, graph style, and timer preview") }, trailingContent = { Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp) }, modifier = Modifier.clickable(onClick = onAppearance))
        }
        SettingGroup("Timer Settings") {
            SettingSwitch("Use Inspection", "Enable the WCA 15 second inspection period.", inspectionEnabled, vm::setInspectionEnabled)
            if (inspectionEnabled) {
                SettingSwitch("Inspection Counts Down", "Display the remaining inspection seconds.", inspectionCountdown, vm::setInspectionCountdownEnabled)
                SettingSwitch("Inspection Alerts", "Play CubeTime's audio cues at 8 and 12 seconds.", inspectionAlerts, vm::setInspectionAlertsEnabled)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Hold Down Time", fontWeight = FontWeight.Medium); Text("${"%.2f".format(holdTime / 1000f)} seconds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { vm.setHoldTimeSeconds((holdTime - 50).coerceAtLeast(0).toFloat() / 1000f) }) { Text("−") }
                TextButton(onClick = { vm.setHoldTimeSeconds((holdTime + 50).coerceAtMost(1000).toFloat() / 1000f) }) { Text("+") }
            }
            SettingSwitch("Haptic Feedback", "Vibrate when the timer is ready to start.", haptics, vm::setHapticsEnabled)
        }
        SettingGroup("Timer Tools") {
            SettingSwitch("Show Timer Statistics", "Show AO5, AO12, AO100 and mean on the timer.", timerStats, vm::setTimerStatsEnabled)
            SettingSwitch("Show Scramble Preview", "Show a puzzle preview card on the timer.", scrambleDrawing, vm::setScrambleDrawingEnabled)
        }
        SettingGroup("Statistics") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Displayed Precision", fontWeight = FontWeight.Medium); Text("Decimal places for solve times", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { vm.setDisplayDecimalPlaces(if (decimalPlaces == 3) 2 else 3) }) { Text("$decimalPlaces d.p.") }
            }
        }
        SettingGroup("Tools") {
            ListItem(headlineContent = { Text("Open CubeTime Tools", fontWeight = FontWeight.Medium) }, supportingContent = { Text("Timer Only, Scramble Only, Scramble Generator, Calculator") }, trailingContent = { Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp) }, modifier = Modifier.clickable(onClick = onTools))
        }
        Text("Trampis Timed", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearancePage(override: Boolean, dark: Boolean, staticGradient: Boolean, graphGlow: Boolean, graphAnimation: Boolean, confetti: Boolean, vm: TimerViewModel, onBack: () -> Unit) {
    var showReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Settings") }
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingGroup("General Appearance") {
                Text("Customise the gradients used in stats. Choose a static CubeTime blue or a gradient that changes throughout the day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).clickable { vm.setStaticGradient(true) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("STATIC GRADIENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().height(54.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF83A6FF), Color(0xFF4169E1))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { if (staticGradient) Text("✓", color = Color.White, fontWeight = FontWeight.Black) }
                    }
                    Column(Modifier.weight(1f).clickable { vm.setStaticGradient(false) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DYNAMIC GRADIENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth().height(54.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF27C4B8), Color(0xFFB6D944), Color(0xFFFFB52E), Color(0xFFE84F82), Color(0xFF7158E8))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { if (!staticGradient) Text("✓", color = Color.White, fontWeight = FontWeight.Black) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Graph Glow", "Turn the glow effect on or off on graphs.", graphGlow, vm::setGraphGlow)
                SettingSwitch("Graph Animation", "Animate the time trend line.", graphAnimation, vm::setGraphAnimation)
                SettingSwitch("Show Confetti", "Celebrate a new best time.", confetti, vm::setShowConfetti)
            }
            SettingGroup("System Settings") {
                SettingSwitch("Override System Appearance", "Use a selected theme instead of your device setting.", override, vm::setAppearanceOverride)
                if (override) SettingSwitch("Dark Mode", "Use CubeTime’s dark appearance.", dark, vm::setAppearanceDark)
            }
            SettingGroup("Font Settings") {
                Text("Scramble Size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
                var scrambleSize by remember { mutableIntStateOf(18) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scrambleSize = (scrambleSize - 1).coerceAtLeast(15) }) { Text("−") }
                    Text("$scrambleSize", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { scrambleSize = (scrambleSize + 1).coerceAtMost(36) }) { Text("+") }
                }
                Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)).clickable { }.padding(12.dp)) {
                    Text("L' D R2 B2 D2 F2 R2 B2 D R2 D R2 U B' R F2 R U' F L2 D'", fontFamily = FontFamily(Font(R.font.recursive)), fontSize = scrambleSize.sp)
                    Text("Timer preview", modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("Font Weight", modifier = Modifier.padding(start = 14.dp, top = 8.dp))
                var weight by remember { mutableFloatStateOf(.55f) }
                Slider(value = weight, onValueChange = { weight = it }, modifier = Modifier.padding(horizontal = 12.dp))
                Text("Font Casualness", modifier = Modifier.padding(start = 14.dp))
                var casualness by remember { mutableFloatStateOf(0f) }
                Slider(value = casualness, onValueChange = { casualness = it }, modifier = Modifier.padding(horizontal = 12.dp))
                var cursive by remember { mutableStateOf(false) }
                SettingSwitch("Cursive Font", "Use cursive timer text.", cursive) { cursive = it }
            }
            OutlinedButton(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) { Text("↻  Reset Appearance Settings", color = Color(0xFFE34F53)) }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showReset) AlertDialog(onDismissRequest = { showReset = false }, title = { Text("Reset appearance settings?") }, text = { Text("Appearance options return to their defaults. Your solves and sessions will be kept.") }, confirmButton = { TextButton(onClick = { vm.resetAppearance(); showReset = false }) { Text("Reset", color = Color(0xFFE34F53)) } }, dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } })
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(vertical = 10.dp)) {
        Text(title, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SessionDialog(sessions: List<SessionEntity>, selected: SessionEntity?, onDismiss: () -> Unit, onSelect: (Long) -> Unit, onNew: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sessions") }, text = {
        Column {
            sessions.forEach { session -> TextButton(onClick = { onSelect(session.id) }, modifier = Modifier.fillMaxWidth()) { Text((if (session.id == selected?.id) "✓  " else "") + session.name) } }
        }
    }, confirmButton = { TextButton(onClick = onNew) { Text("New session") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun NewSessionDialog(onDismiss: () -> Unit, onCreate: (String, String, String, Int, Long?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var eventId by remember { mutableStateOf("333") }
    var expanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var sessionType by remember { mutableStateOf("standard") }
    var phaseCount by remember { mutableIntStateOf(2) }
    var targetText by remember { mutableStateOf("") }
    val event = CubeEvents.fromId(eventId)
    val sessionTypes = listOf("standard" to "Standard Session", "multiphase" to "Multiphase", "playground" to "Playground", "compsim" to "Competition Simulation", "timerOnly" to "Timer Only", "algtrainer" to "Algorithm Trainer")
    val selectedType = sessionTypes.first { it.first == sessionType }.second
    val targetMillis = targetText.toDoubleOrNull()?.let { (it * 1000).toLong() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New $selectedType Session") }, text = {
        Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Session name") }, singleLine = true)
            Box {
                TextButton(onClick = { typeExpanded = true }) { Text("Type: $selectedType  ▾") }
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    sessionTypes.forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { sessionType = id; typeExpanded = false }) }
                }
            }
            Text(when (sessionType) {
                "multiphase" -> "Break each solve into phases. Tap the timer to record each phase."
                "playground" -> "Change the puzzle type at any time within this session."
                "compsim" -> "Practice a competition format with groups of five solves and a target."
                "timerOnly" -> "Time solves without generating or saving scrambles."
                "algtrainer" -> "A session for focused algorithm practice."
                else -> "A standard session uses one puzzle type for all solves."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            if (sessionType == "multiphase") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Phases: $phaseCount", modifier = Modifier.weight(1f))
                TextButton(onClick = { phaseCount = (phaseCount - 1).coerceAtLeast(2) }) { Text("−") }
                TextButton(onClick = { phaseCount = (phaseCount + 1).coerceAtMost(8) }) { Text("+") }
            }
            if (sessionType == "compsim") OutlinedTextField(value = targetText, onValueChange = { targetText = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text("Target time (seconds)") }, singleLine = true)
            if (sessionType != "timerOnly") {
                Box {
                    TextButton(onClick = { expanded = true }) { Text("Event: ${event.name}  ▾") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CubeEvents.all.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { eventId = option.id; expanded = false }) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = sessionType != "compsim" || targetMillis != null, onClick = { onCreate(name.ifBlank { if (sessionType == "timerOnly") "Timer Only" else event.name }, eventId, sessionType, phaseCount, targetMillis) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun EmptyState(title: String, subtitle: String) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, modifier = Modifier.padding(top = 8.dp)) } }
@Composable
private fun StatRow(label: String, value: String) { ListItem(headlineContent = { Text(label) }, trailingContent = { Text(value, fontWeight = FontWeight.SemiBold) }) }
private fun formatTime(nanos: Long, dp: Int = 3): String = formatMillis(nanos / 1_000_000L, dp)
private fun safeFileName(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }.trim().replace(' ', '_').ifBlank { "Session" }
private fun formatDate(epochMillis: Long): String = java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
private fun formatSolve(solve: SolveEntity): String = when (solve.penalty) { Penalty.DNF.ordinal -> "DNF"; Penalty.PLUS_TWO.ordinal -> "${formatMillis(solve.timeMillis + 2_000)}+"; else -> formatMillis(solve.timeMillis) }
private fun formatMillis(millis: Long, dp: Int = 3): String {
    val precision = dp.coerceIn(0, 3)
    val factor = when (precision) { 0 -> 1000L; 1 -> 100L; 2 -> 10L; else -> 1L }
    val shown = millis / factor * factor
    val seconds = shown / 1000L
    val fraction = (shown % 1000L).toInt()
    val pattern = when (precision) { 0 -> "%d"; 1 -> "%d.%01d"; 2 -> "%d.%02d"; else -> "%d.%03d" }
    return if (seconds >= 60) {
        val min = seconds / 60
        val rem = seconds % 60
        when (precision) {
            0 -> "%d:%02d".format(java.util.Locale.US, min, rem)
            1 -> "%d:%02d.%01d".format(java.util.Locale.US, min, rem, fraction / 100)
            2 -> "%d:%02d.%02d".format(java.util.Locale.US, min, rem, fraction / 10)
            else -> "%d:%02d.%03d".format(java.util.Locale.US, min, rem, fraction)
        }
    } else when (precision) {
        0 -> "%d".format(java.util.Locale.US, seconds)
        1 -> "%d.%01d".format(java.util.Locale.US, seconds, fraction / 100)
        2 -> "%d.%02d".format(java.util.Locale.US, seconds, fraction / 10)
        else -> "%d.%03d".format(java.util.Locale.US, seconds, fraction)
    }
}
