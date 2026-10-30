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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import app.trampis.timed.data.SessionImportPreview
import app.trampis.timed.domain.CubeEvents
import app.trampis.timed.domain.InspectionRules
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
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        setContent { CubeTimeAppThemeHost() }
    }
}

private val LocalMotionEnabled = staticCompositionLocalOf { true }
private data class PendingImport(val text: String, val filename: String, val preview: SessionImportPreview)

@Composable
private fun CubeTimeAppThemeHost(vm: TimerViewModel = viewModel()) {
    val override by vm.appearanceOverride.collectAsState()
    val darkSetting by vm.appearanceDark.collectAsState()
    val accentPreset by vm.accentPreset.collectAsState()
    val customAccent by vm.customAccent.collectAsState()
    val contrast by vm.highContrast.collectAsState()
    val uiFont by vm.uiFont.collectAsState()
    val motionEnabled by vm.reducedMotion.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    CompositionLocalProvider(LocalMotionEnabled provides !motionEnabled) {
        TrampisTheme(if (override) darkSetting else systemDark, accentPreset, customAccent, contrast, uiFont, !motionEnabled) { CubeTimeApp(vm) }
    }
}

@Composable
private fun TrampisTheme(dark: Boolean, accentPreset: String, customAccent: Int, highContrast: Boolean, uiFont: String, motionEnabled: Boolean, content: @Composable () -> Unit) {
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
    val accent = when (accentPreset) {
        "violet" -> Color(0xFF9B72F2)
        "mint" -> Color(0xFF31B9A3)
        "amber" -> Color(0xFFF0A83A)
        "custom" -> Color(customAccent)
        else -> Color(0xFF5179F6)
    }
    val targetColors = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color.White,
        background = if (highContrast) Color.Black else Color(0xFF080A12), surface = if (highContrast) Color(0xFF050505) else Color(0xFF151926), onSurface = Color.White,
        surfaceVariant = if (highContrast) Color(0xFF171717) else Color(0xFF202638), onSurfaceVariant = if (highContrast) Color(0xFFE2E2E2) else Color(0xFFADB5C9),
        outlineVariant = if (highContrast) Color(0xFFB7B7B7) else Color(0xFF3A4152),
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        background = if (highContrast) Color.White else Color(0xFFF0F2FA), surface = Color.White, onSurface = Color(0xFF111111),
        surfaceVariant = if (highContrast) Color(0xFFF2F2F2) else Color(0xFFF8F9FD), onSurfaceVariant = if (highContrast) Color(0xFF252525) else Color(0xFF65676B),
        outlineVariant = if (highContrast) Color(0xFF555555) else Color(0xFFCACDD4),
    )
    val colorTween = androidx.compose.animation.core.tween<Color>(if (motionEnabled) 450 else 0, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val primary by androidx.compose.animation.animateColorAsState(targetColors.primary, colorTween, label = "theme-primary")
    val background by androidx.compose.animation.animateColorAsState(targetColors.background, colorTween, label = "theme-background")
    val surface by androidx.compose.animation.animateColorAsState(targetColors.surface, colorTween, label = "theme-surface")
    val onSurface by androidx.compose.animation.animateColorAsState(targetColors.onSurface, colorTween, label = "theme-on-surface")
    val surfaceVariant by androidx.compose.animation.animateColorAsState(targetColors.surfaceVariant, colorTween, label = "theme-surface-variant")
    val onSurfaceVariant by androidx.compose.animation.animateColorAsState(targetColors.onSurfaceVariant, colorTween, label = "theme-on-surface-variant")
    val outlineVariant by androidx.compose.animation.animateColorAsState(targetColors.outlineVariant, colorTween, label = "theme-outline")
    val colors = targetColors.copy(primary = primary, background = background, surface = surface, onSurface = onSurface, surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, outlineVariant = outlineVariant)
    // CubeTime uses the platform system face for its controls. Recursive is
    // reserved for timer/scramble readouts below, matching the original app.
    val typography = remember(uiFont) {
        if (uiFont != "rubik") Typography() else {
            val face = FontFamily(Font(R.font.rubik))
            val base = Typography()
            base.copy(
                displayLarge = base.displayLarge.copy(fontFamily = face), displayMedium = base.displayMedium.copy(fontFamily = face), displaySmall = base.displaySmall.copy(fontFamily = face),
                headlineLarge = base.headlineLarge.copy(fontFamily = face), headlineMedium = base.headlineMedium.copy(fontFamily = face), headlineSmall = base.headlineSmall.copy(fontFamily = face),
                titleLarge = base.titleLarge.copy(fontFamily = face), titleMedium = base.titleMedium.copy(fontFamily = face), titleSmall = base.titleSmall.copy(fontFamily = face),
                bodyLarge = base.bodyLarge.copy(fontFamily = face), bodyMedium = base.bodyMedium.copy(fontFamily = face), bodySmall = base.bodySmall.copy(fontFamily = face),
                labelLarge = base.labelLarge.copy(fontFamily = face), labelMedium = base.labelMedium.copy(fontFamily = face), labelSmall = base.labelSmall.copy(fontFamily = face),
            )
        }
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
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
    val motionEnabled = LocalMotionEnabled.current
    if (!motionEnabled) {
        if (showApp) CubeTimeMainContent(vm) else BrandedLoadingScreen()
        return
    }
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
    if (!LocalMotionEnabled.current) {
        Box(Modifier.fillMaxSize().background(Color(0xFF080A12)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.splash_logo), "Trampis Timed logo", contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.size(180.dp))
        }
        return
    }
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
        initialValue = -4f, targetValue = 4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "logo-tilt",
    )
    val orbit by transition.animateFloat(0f, 360f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit")
    val reveal by androidx.compose.animation.core.animateFloatAsState(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "loader-reveal")
    BoxWithConstraints(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF11182B), Color(0xFF080A12), Color(0xFF090B14)))),
        contentAlignment = Alignment.Center,
    ) {
        val logoSize = (maxWidth * .49f).coerceIn(138.dp, 204.dp)
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .38f
            drawCircle(Color(0xFF496BFF).copy(alpha = .13f), radius = radius, center = Offset(size.width / 2, size.height * .43f))
            drawCircle(Color(0xFF6E8FFF).copy(alpha = .25f), radius = radius * .72f, center = Offset(size.width / 2, size.height * .43f), style = Stroke(width = 1.dp.toPx()))
            val angle = Math.toRadians(orbit.toDouble())
            drawCircle(Color(0xFF9BB3FF), radius = 3.5.dp.toPx(), center = Offset(size.width / 2 + kotlin.math.cos(angle).toFloat() * radius * .72f, size.height * .43f + kotlin.math.sin(angle).toFloat() * radius * .72f))
        }
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = reveal; translationY = (1 - reveal) * 28.dp.toPx() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(logoSize + 32.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFF5278FF).copy(alpha = .14f), size.minDimension * .48f) }
                Image(painterResource(R.drawable.splash_logo), "Trampis Timed logo", contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.size(logoSize).graphicsLayer { scaleX = pulse; scaleY = pulse; rotationZ = rotation })
            }
            Spacer(Modifier.height(22.dp))
            Text("TRAMPIS TIMED", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.4.sp)
            Text("READY. SET. SOLVE.", color = Color(0xFFAAB7D8), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.5.sp, modifier = Modifier.padding(top = 9.dp))
            Spacer(Modifier.height(42.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(.3f, 1f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(620, delayMillis = index * 170), androidx.compose.animation.core.RepeatMode.Reverse), label = "loading-dot-$index")
                    Box(Modifier.size(if (index == 1) 7.dp else 5.dp).background(Color(0xFF8EAAFF).copy(alpha = alpha), RoundedCornerShape(50)))
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
    val sessionSolveCounts by vm.sessionSolveCounts.collectAsState()
    val selected by vm.selectedSession.collectAsState()
    val solves by vm.solves.collectAsState()
    val allSolves by vm.allSolves.collectAsState()
    val lastBackupAt by vm.lastBackupAt.collectAsState()
    val running by vm.isRunning.collectAsState()
    val inspecting by vm.isInspecting.collectAsState()
    val preparing by vm.isPreparing.collectAsState()
    val inspectionEnabled by vm.inspectionEnabled.collectAsState()
    val inspectionCountdown by vm.inspectionCountdownEnabled.collectAsState()
    val holdTime by vm.holdTimeMillis.collectAsState()
    val haptics by vm.hapticsEnabled.collectAsState()
    val inspectionAlerts by vm.inspectionAlertsEnabled.collectAsState()
    val solveSounds by vm.solveSoundsEnabled.collectAsState()
    val timerStats by vm.timerStatsEnabled.collectAsState()
    val scrambleDrawing by vm.scrambleDrawingEnabled.collectAsState()
    val decimalPlaces by vm.displayDecimalPlaces.collectAsState()
    val appearanceOverride by vm.appearanceOverride.collectAsState()
    val appearanceDark by vm.appearanceDark.collectAsState()
    val staticGradient by vm.staticGradient.collectAsState()
    val gradientStyle by vm.gradientStyle.collectAsState()
    val customGradientStart by vm.customGradientStart.collectAsState()
    val customGradientEnd by vm.customGradientEnd.collectAsState()
    val graphGlow by vm.graphGlow.collectAsState()
    val graphAnimation by vm.graphAnimation.collectAsState()
    val showConfetti by vm.showConfetti.collectAsState()
    val boldTimerDigits by vm.boldTimerDigits.collectAsState()
    val timerFontWeight by vm.timerFontWeight.collectAsState()
    val statsCardMotion by vm.statsCardMotion.collectAsState()
    val timerSizeScale by vm.timerSizeScale.collectAsState()
    val scrambleTextSize by vm.scrambleTextSize.collectAsState()
    val timerFontPreset by vm.timerFontPreset.collectAsState()
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
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val offerUndo: (String) -> Unit = { message -> scope.launch {
        if (snackbarHostState.showSnackbar(message, "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) vm.undoLastDelete()
    } }
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
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = vm.exportBackupJson()
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(json) }
            }.onSuccess { vm.markBackupCreated(); Toast.makeText(context, "Full backup saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
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
                val preview = vm.previewImport(text, filename)
                PendingImport(text, filename, preview)
            }.onSuccess { pendingImport = it }
                .onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val motionEnabled = LocalMotionEnabled.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar = { if (page != "Tools") CubeTabBar(page, haptics) { page = it; selectedTool = null } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
          AnimatedContent(targetState = page, modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp), transitionSpec = {
              if (!motionEnabled) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
              else fadeIn(androidx.compose.animation.core.tween(125, delayMillis = 55)) togetherWith fadeOut(androidx.compose.animation.core.tween(55)) using null
          }, label = "cube-page") { visiblePage ->
            when (visiblePage) {
                "Timer" -> TimerPage(vm, selected, solves, scramble, scrambleSvg, scrambleLocked, running, inspecting, preparing, elapsed, inspectionElapsed, inspectionCountdown, phaseSplits, holdTime, haptics, timerStats, scrambleDrawing, decimalPlaces, boldTimerDigits, timerFontWeight, timerSizeScale, scrambleTextSize, timerFontPreset, error) { showSessions = true }
                "Solves" -> Column(Modifier.fillMaxSize()) { PageHeading("Times"); SolvesPage(solves, allSolves, sessions, selected?.id, vm, offerUndo) }
                "Stats" -> StatsPage(solves, selected, gradientStyle, customGradientStart, customGradientEnd, graphGlow, graphAnimation, statsCardMotion, vm) { showSessions = true }
                "Sessions" -> Column(Modifier.fillMaxSize()) {
                    PageHeading("Sessions")
                    SessionsPage(
                        sessions, selected, sessionSolveCounts,
                        onSelect = { vm.selectSession(it); page = "Timer" },
                        onCreate = { showNewSession = true },
                        onPin = { vm.setSessionPinned(it.id, !it.pinned) },
                        onDuplicate = vm::duplicateSession,
                        onArchive = { session, archived -> vm.setSessionArchived(session.id, archived) },
                        onMoveToTop = { vm.moveSessionToTop(it.id) },
                        onUpdate = vm::updateSession,
                        onDelete = { id -> vm.deleteSessionWithUndo(id); offerUndo("Session and its solves deleted") },
                        onExportCsv = { exportCsv.launch("${safeFileName(selected?.name ?: "Session")}.csv") },
                        onExportCsTimer = { exportCsTimer.launch("Trampis Timed - csTimer.json") },
                        onExportOds = { exportOds.launch("Trampis Timed - CubeTime Export.ods") },
                        onBackup = { exportBackup.launch("Trampis-Timed-backup.json") },
                        onImport = { importDocument.launch(arrayOf("text/*", "application/json", "application/octet-stream")) },
                        lastBackupAt = lastBackupAt,
                    )
                }
                "Tools" -> ToolsPage(selectedTool, onBack = { if (selectedTool != null) selectedTool = null else page = "Settings" }, onSelect = { selectedTool = it }, holdTime = holdTime)
                "Appearance" -> AppearancePage(appearanceOverride, appearanceDark, staticGradient, graphGlow, graphAnimation, showConfetti, boldTimerDigits, timerFontWeight, statsCardMotion, timerSizeScale, scrambleTextSize, timerFontPreset, vm,
                    onBack = { page = "Settings" })
                else -> Column(Modifier.fillMaxSize()) { PageHeading("Settings"); MorePage(inspectionEnabled, inspectionCountdown, holdTime, haptics, inspectionAlerts, solveSounds, timerStats, scrambleDrawing, decimalPlaces, vm, { page = "Tools" }, { page = "Appearance" }) }
            }
          }
        }
    }
    if (showSessions) SessionDialog(sessions.filterNot { it.archived }, selected, onDismiss = { showSessions = false }, onSelect = { vm.selectSession(it); showSessions = false }, onNew = { showSessions = false; showNewSession = true })
    if (showNewSession) NewSessionDialog(onDismiss = { showNewSession = false }, onCreate = { name, event, kind, phases, target -> vm.addSession(name, event, kind, phases, target); showNewSession = false })
    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Review import") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${pending.preview.format} · ${pending.preview.sessions} sessions · ${pending.preview.solves} solves", fontWeight = FontWeight.SemiBold)
                    Text("Imported sessions are added alongside your existing data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    pending.preview.names.forEach { Text("•  $it", modifier = Modifier.padding(top = 5.dp)) }
                    if (pending.preview.sessions > pending.preview.names.size) Text("and ${pending.preview.sessions - pending.preview.names.size} more…", modifier = Modifier.padding(top = 5.dp))
                }
            },
            confirmButton = { Button(onClick = {
                pendingImport = null
                scope.launch {
                    runCatching { vm.importSessions(pending.text, pending.filename) }
                        .onSuccess { count -> Toast.makeText(context, "Imported $count session${if (count == 1) "" else "s"}", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CubeTabBar(selected: String, hapticsEnabled: Boolean, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val motionEnabled = LocalMotionEnabled.current
    val compactTabs = LocalConfiguration.current.screenWidthDp < 360
    val items = listOf(
        "Timer" to (Icons.Outlined.Timer to Icons.Filled.Timer),
        "Solves" to (Icons.Outlined.History to Icons.Filled.History),
        "Stats" to (Icons.Outlined.PieChart to Icons.Filled.PieChart),
        "Sessions" to (Icons.Outlined.List to Icons.Filled.List),
        "Settings" to (Icons.Outlined.Settings to Icons.Filled.Settings),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .98f), RoundedCornerShape(24.dp))
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, icons) ->
            val active = selected == label
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (active) 1.08f else 1f,
                animationSpec = if (motionEnabled) androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 438f) else androidx.compose.animation.core.snap(),
                label = "tab-icon-$label",
            )
            val pill by androidx.compose.animation.animateColorAsState(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.Transparent, androidx.compose.animation.core.tween(if (motionEnabled) 220 else 0), label = "tab-pill-$label")
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 5.dp)
                .background(pill, RoundedCornerShape(18.dp)).clickable {
                    if (label != selected && hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(label)
                },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(if (active) icons.second else icons.first, contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                androidx.compose.animation.AnimatedVisibility(active, enter = if (motionEnabled) fadeIn() + androidx.compose.animation.expandVertically() else androidx.compose.animation.EnterTransition.None, exit = if (motionEnabled) fadeOut() + androidx.compose.animation.shrinkVertically() else androidx.compose.animation.ExitTransition.None) {
                    Text(if (compactTabs) when (label) { "Solves" -> "Times"; "Sessions" -> "Sets"; "Settings" -> "Setup"; else -> label } else label,
                        fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PageHeading(title: String, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = action) { Text("＋") }
    }
}

@Composable
private fun SessionsPage(sessions: List<SessionEntity>, selected: SessionEntity?, solveCounts: Map<Long, Int>, onSelect: (Long) -> Unit, onCreate: () -> Unit, onPin: (SessionEntity) -> Unit, onDuplicate: (SessionEntity) -> Unit, onArchive: (SessionEntity, Boolean) -> Unit, onMoveToTop: (SessionEntity) -> Unit, onUpdate: (SessionEntity) -> Unit, onDelete: (Long) -> Unit, onExportCsv: () -> Unit, onExportCsTimer: () -> Unit, onExportOds: () -> Unit, onBackup: () -> Unit, onImport: () -> Unit, lastBackupAt: Long) {
    var editSession by remember { mutableStateOf<SessionEntity?>(null) }
    var deleteSession by remember { mutableStateOf<SessionEntity?>(null) }
    var showDataMenu by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val visibleSessions = remember(sessions, showArchived) { sessions.filter { it.archived == showArchived } }
    val pinned = remember(visibleSessions) { visibleSessions.filter { it.pinned } }
    val regular = remember(visibleSessions) { visibleSessions.filterNot { it.pinned } }
    val totalSolves = solveCounts.values.sum()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR PRACTICE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${sessions.size} sessions · $totalSolves solves", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                if (lastBackupAt == 0L || System.currentTimeMillis() - lastBackupAt >= 30L * 24L * 60L * 60L * 1000L) {
                    Text("Backup recommended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Box {
                OutlinedButton(onClick = { showDataMenu = true }, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                    Text("Data  ⋯", fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(expanded = showDataMenu, onDismissRequest = { showDataMenu = false }) {
                    DropdownMenuItem(text = { Text("Export current session · CSV") }, enabled = selected != null, onClick = { showDataMenu = false; onExportCsv() })
                    DropdownMenuItem(text = { Text("Export all · csTimer") }, enabled = sessions.isNotEmpty(), onClick = { showDataMenu = false; onExportCsTimer() })
                    DropdownMenuItem(text = { Text("Export all · ODS") }, enabled = sessions.isNotEmpty(), onClick = { showDataMenu = false; onExportOds() })
                    DropdownMenuItem(text = { Text("Create full backup") }, enabled = sessions.isNotEmpty(), onClick = { showDataMenu = false; onBackup() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Import CSV, csTimer, or backup") }, onClick = { showDataMenu = false; onImport() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Active ${sessions.count { !it.archived }}") })
            FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archived ${sessions.count { it.archived }}") })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pinned.isNotEmpty()) {
                item(key = "pinned-header") { SessionSectionHeader("PINNED", pinned.size) }
                itemsIndexed(pinned, key = { _, item -> "pinned-${item.id}" }) { _, session ->
                    SessionCardRow(session, selected?.id == session.id, solveCounts[session.id] ?: 0,
                        onSelect = { onSelect(session.id) }, onPin = { onPin(session) }, onArchive = { onArchive(session, !session.archived) }, onMoveToTop = { onMoveToTop(session) },
                        onDuplicate = { onDuplicate(session) },
                        onCustomize = { editSession = session }, onDelete = { deleteSession = session })
                }
            }
            item(key = "sessions-header") { SessionSectionHeader(if (pinned.isEmpty()) "ALL SESSIONS" else "SESSIONS", regular.size) }
            itemsIndexed(regular, key = { _, item -> "session-${item.id}" }) { _, session ->
                SessionCardRow(session, selected?.id == session.id, solveCounts[session.id] ?: 0,
                    onSelect = { onSelect(session.id) }, onPin = { onPin(session) }, onArchive = { onArchive(session, !session.archived) }, onMoveToTop = { onMoveToTop(session) },
                    onDuplicate = { onDuplicate(session) },
                    onCustomize = { editSession = session }, onDelete = { deleteSession = session })
            }
            if (visibleSessions.isEmpty()) item { EmptyState(if (showArchived) "No archived sessions" else "No sessions yet", if (showArchived) "Archive sessions to keep your active list focused." else "Create a session to start tracking your solves.") }
        }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
            Text("＋   New Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
private fun SessionSectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 5.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun SessionCardRow(session: SessionEntity, selected: Boolean, solveCount: Int, onSelect: () -> Unit, onPin: () -> Unit, onDuplicate: () -> Unit, onArchive: () -> Unit, onMoveToTop: () -> Unit, onCustomize: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val motionEnabled = LocalMotionEnabled.current
    val background by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .13f) else MaterialTheme.colorScheme.surface,
        androidx.compose.animation.core.tween(if (motionEnabled) 220 else 0), label = "session-card-background-${session.id}",
    )
    val outline by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .62f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f),
        androidx.compose.animation.core.tween(if (motionEnabled) 220 else 0), label = "session-card-outline-${session.id}",
    )
    Row((if (motionEnabled) Modifier.animateContentSize(androidx.compose.animation.core.spring(dampingRatio = .82f, stiffness = 360f)) else Modifier).fillMaxWidth()
        .background(background, RoundedCornerShape(22.dp)).border(1.dp, outline, RoundedCornerShape(22.dp))
        .clickable(enabled = !session.archived, onClick = onSelect).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(62.dp).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(54.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .84f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            EventIcon(session.eventId, 38)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                androidx.compose.animation.AnimatedVisibility(selected, enter = if (motionEnabled) fadeIn() + androidx.compose.animation.scaleIn() else androidx.compose.animation.EnterTransition.None, exit = if (motionEnabled) fadeOut() + androidx.compose.animation.scaleOut() else androidx.compose.animation.ExitTransition.None) {
                    Text("CURRENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Text("${CubeEvents.fromId(session.eventId).name}  ·  ${sessionTypeLabel(session)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            if (session.sessionType != "compsim" && session.targetMillis != null) Text("AO5 goal · ${formatMillis(session.targetMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(5.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(solveCount.toString(), fontFamily = recursiveFontFamily(740), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
            Text(if (solveCount == 1) "solve" else "solves", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) { Text("⋮", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Customize") }, onClick = { menuOpen = false; onCustomize() })
                DropdownMenuItem(text = { Text("Duplicate empty session") }, onClick = { menuOpen = false; onDuplicate() })
                if (!session.archived) DropdownMenuItem(text = { Text("Move to top") }, onClick = { menuOpen = false; onMoveToTop() })
                DropdownMenuItem(text = { Text(if (session.archived) "Restore to active" else "Archive session") }, onClick = { menuOpen = false; onArchive() })
                DropdownMenuItem(text = { Text(if (session.pinned) "Unpin session" else "Pin session") }, onClick = { menuOpen = false; onPin() })
                DropdownMenuItem(text = { Text("Delete session", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

private fun sessionTypeLabel(session: SessionEntity): String = when (session.sessionType) {
    "multiphase" -> "MULTIPHASE · ${session.phaseCount} PHASES"
    "playground" -> "PLAYGROUND"
    "compsim" -> "COMP SIM"
    "timerOnly" -> "TIMER ONLY"
    "algtrainer" -> "ALGORITHM TRAINER"
    else -> "STANDARD"
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
                else if (session.sessionType != "timerOnly") OutlinedTextField(value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Average goal (seconds, optional)") }, singleLine = true)
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
private fun TimerPage(vm: TimerViewModel, session: SessionEntity?, solves: List<SolveEntity>, scramble: String, scrambleSvg: String?, scrambleLocked: Boolean, running: Boolean, inspecting: Boolean, preparing: Boolean, elapsed: Long, inspectionElapsed: Long, inspectionCountdown: Boolean, phaseSplits: List<Long>, holdTime: Long, hapticsEnabled: Boolean, showStats: Boolean, showScrambleDrawing: Boolean, decimalPlaces: Int, boldTimerDigits: Boolean, timerFontWeight: Int, timerSizeScale: Float, scrambleTextSize: Int, timerFontPreset: String, error: String?, onShowSessions: () -> Unit) {
    val motionEnabled = LocalMotionEnabled.current
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
    val landscapeLayout = screenConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ambient = androidx.compose.animation.core.rememberInfiniteTransition(label = "timer-ambient")
    val ambientAlpha by ambient.animateFloat(.04f, if (motionEnabled) .12f else .04f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "ambient-glow")
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = ambientAlpha), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background,
    ))).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (landscapeLayout) 48.dp else 54.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).clickable {
                    if (session?.sessionType == "playground") showEventPicker = true else onShowSessions()
                }.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EventIcon(session?.eventId ?: "333", 34)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(session?.name ?: "Loading session…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${CubeEvents.fromId(session?.eventId ?: "333").name} · ${solves.size} solves${session?.targetMillis?.takeIf { session.sessionType != "compsim" }?.let { " · Ao5 goal ${formatMillis(it)}" }.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (!scrambleLocked && !running) IconButton(onClick = { vm.nextScramble(playCue = true) }) { Icon(Icons.Filled.Refresh, contentDescription = "Next scramble", tint = MaterialTheme.colorScheme.primary) }
            if (scrambleLocked) IconButton(onClick = { vm.toggleScrambleLock() }, enabled = !running) { Icon(Icons.Filled.Lock, contentDescription = "Unlock scramble", tint = MaterialTheme.colorScheme.primary) }
        }
        Box(Modifier.fillMaxWidth().then(if (landscapeLayout) Modifier.heightIn(min = 72.dp).weight(.7f) else Modifier.weight(if (compactLayout) 0.28f else 0.4f)), contentAlignment = Alignment.Center) {
            if (!running && !inspecting && scramble.isNotBlank()) {
                AnimatedContent(targetState = scramble, transitionSpec = {
                    if (!motionEnabled) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    else (fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 8 }) togetherWith
                        (fadeOut(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { -it / 8 })
                }, label = "scramble-transition") { displayedScramble ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .76f), RoundedCornerShape(20.dp))
                        .combinedClickable(onClick = { showScramble = true }, onLongClick = { showScrambleMenu = true }).padding(horizontal = 16.dp, vertical = 13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SCRAMBLE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.7.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("TAP TO EXPAND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f))
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(displayedScramble, fontFamily = FontFamily(Font(R.font.recursive)), fontSize = scrambleTextSize.sp, lineHeight = (scrambleTextSize * 1.32f).sp, maxLines = 5)
                    }
                }
                DropdownMenu(expanded = showScrambleMenu, onDismissRequest = { showScrambleMenu = false }) {
                    DropdownMenuItem(text = { Text("Copy Scramble") }, onClick = { clipboard.setText(AnnotatedString(scramble)); showScrambleMenu = false })
                    DropdownMenuItem(text = { Text(if (scrambleLocked) "Unlock Scramble" else "Lock Scramble") }, onClick = { vm.toggleScrambleLock(); showScrambleMenu = false })
                    if (!scrambleLocked) DropdownMenuItem(text = { Text("Next Scramble") }, onClick = { vm.nextScramble(playCue = true); showScrambleMenu = false })
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
            inspecting && !inspectionCountdown && inspectionElapsed >= InspectionRules.DNF_NANOS -> Color(0xFFE34F53)
            inspecting && !inspectionCountdown && inspectionElapsed >= InspectionRules.PLUS_TWO_NANOS -> Color(0xFFF28D36)
            preparing -> Color(0xFF49A867)
            else -> MaterialTheme.colorScheme.onBackground
        }
        val animatedTimerColor by androidx.compose.animation.animateColorAsState(
            targetValue = timerColor,
            animationSpec = if (motionEnabled) androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 438f) else androidx.compose.animation.core.snap(),
            label = "timer-state-color",
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(if (landscapeLayout) 1f else if (compactLayout) 0.95f else 1.2f).focusable().onPreviewKeyEvent { event ->
            val control = event.nativeKeyEvent.isCtrlPressed
            if (control && event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.N -> { if (!scrambleLocked && !running) vm.nextScramble(playCue = true); true }
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
                        "right" -> if (scrambleLocked) confirmUnlockScramble = true else vm.nextScramble(playCue = true)
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
            val timerSize = (maxWidth.value * 0.19f / fontScale * timerSizeScale).coerceIn(34f, 76f).sp
            val timerScale by androidx.compose.animation.core.animateFloatAsState(if (preparing) 1.055f else if (running) .985f else 1f, animationSpec = if (motionEnabled) androidx.compose.animation.core.spring(dampingRatio = .68f, stiffness = 320f) else androidx.compose.animation.core.snap(), label = "timer-scale")
            if (running || preparing || inspecting) {
                val phase = if (inspecting) (inspectionElapsed / InspectionRules.COUNTDOWN_NANOS.toFloat()).coerceIn(0f, 1f) else ((elapsed / 1_000_000L) % 10_000L) / 10_000f
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = if (preparing) .75f else .5f }) {
                    val diameter = size.minDimension * .88f
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                    val ring = if (preparing) Color(0xFF77E5A6) else animatedTimerColor
                    drawCircle(ring.copy(alpha = .10f), radius = diameter * .52f, center = Offset(size.width / 2, size.height / 2), style = Stroke(width = 14.dp.toPx()))
                    drawArc(ring.copy(alpha = .24f), -90f, 360f * phase.coerceAtLeast(if (preparing) .15f else .01f), false, topLeft, androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Row(Modifier.graphicsLayer { scaleX = timerScale; scaleY = timerScale }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(timeText, fontFamily = if (timerFontPreset == "system") FontFamily.Default else recursiveFontFamily(if (boldTimerDigits) timerFontWeight else 500), fontSize = timerSize, fontWeight = FontWeight.Normal, color = animatedTimerColor, maxLines = 1, softWrap = false)
                if (inspecting && !inspectionCountdown && inspectionElapsed >= InspectionRules.PLUS_TWO_NANOS) {
                    Text(if (inspectionElapsed >= InspectionRules.DNF_NANOS) " [DNF]" else " [+2]", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = timerColor)
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
            val widgetHeight = if (landscapeLayout) 72.dp else if (compactLayout) (110f * screenConfiguration.fontScale.coerceIn(1f, 1.55f)).coerceAtMost(170f).dp else 120.dp
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
        seconds >= InspectionRules.COUNTDOWN_SECONDS -> "0"
        else -> (InspectionRules.COUNTDOWN_SECONDS - seconds).toString()
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
    val compactStats = LocalConfiguration.current.screenWidthDp < 380 || LocalDensity.current.fontScale > 1.25f
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
        Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            if (compactStats) {
                MiniStatLine("BPA", bpa); MiniStatLine("WPA", wpa); MiniStatLine("TARGET", target)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { MiniStat("BPA", bpa); MiniStat("WPA", wpa) }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                MiniStat("TO REACH TARGET · ${formatMillis(session.targetMillis)}", target)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        if (compactStats) {
            MiniStatLine("AO5", ao5); MiniStatLine("AO12", ao12); MiniStatLine("AO100", ao100); MiniStatLine("MEAN", mean)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { MiniStat("AO5", ao5); MiniStat("AO12", ao12) }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { MiniStat("AO100", ao100); MiniStat("MEAN", mean) }
        }
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
private fun MiniStatLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value ?: "—", fontFamily = FontFamily(Font(R.font.recursive)), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SolvesPage(solves: List<SolveEntity>, allSolves: List<SolveEntity>, sessions: List<SessionEntity>, currentSessionId: Long?, vm: TimerViewModel, offerUndo: (String) -> Unit) {
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
    var sessionFilter by remember(currentSessionId) { mutableStateOf(currentSessionId) }
    var sessionFilterMenu by remember { mutableStateOf(false) }
    val sessionFilteredSolves = if (sessionFilter == null) allSolves else allSolves.filter { it.sessionId == sessionFilter }
    if (allSolves.isEmpty()) {
        EmptyState("No solves yet", "Your times will appear here.")
    } else {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search solves") },
                leadingIcon = { Text("⌕", fontSize = 22.sp) },
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    FilterChip(selected = sessionFilter != null, onClick = { sessionFilterMenu = true }, label = {
                        Text(if (sessionFilter == null) "All sessions" else sessions.firstOrNull { it.id == sessionFilter }?.name ?: "Session", maxLines = 1)
                    })
                    DropdownMenu(expanded = sessionFilterMenu, onDismissRequest = { sessionFilterMenu = false }) {
                        DropdownMenuItem(text = { Text("All sessions · ${allSolves.size}") }, onClick = { sessionFilter = null; sessionFilterMenu = false; selectedIds = emptySet(); selectMode = false })
                        sessions.forEach { session ->
                            DropdownMenuItem(text = { Text("${session.name} · ${allSolves.count { it.sessionId == session.id }}") }, onClick = {
                                sessionFilter = session.id; sessionFilterMenu = false; selectedIds = emptySet(); selectMode = false
                            })
                        }
                    }
                }
            }
            val matches = sessionFilteredSolves.filter {
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
                Text(if (selectMode) "${selectedIds.size} selected · ${filtered.size} solves" else "${filtered.size} solves", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (selectMode) {
                    TextButton(onClick = { selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet() }) { Text(if (selectedIds.size == filtered.size) "Clear" else "All", maxLines = 1) }
                    Box {
                        TextButton(onClick = { batchMenu = true }, enabled = selectedIds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("⋮", fontSize = 22.sp, maxLines = 1) }
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
                        TextButton(onClick = { sortMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Sort · ${sortMode.substringBefore(' ')}  ▾", maxLines = 1, softWrap = false) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("Newest first", "Oldest first", "Fastest first", "Slowest first").forEach { mode ->
                                DropdownMenuItem(text = { Text(mode) }, onClick = { sortMode = mode; sortMenu = false })
                            }
                        }
                    }
                    OutlinedButton(onClick = { selectMode = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), modifier = Modifier.padding(start = 4.dp)) { Text("Select", maxLines = 1) }
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
                                        vm.deleteSolvesWithUndo(listOf(solve.id))
                                        offerUndo("Solve deleted")
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
            val selectedSolves = filtered.filter { it.id in selectedIds }
            val sourceSessionIds = selectedSolves.map { it.sessionId }.toSet()
            val selectedEventIds = selectedSolves.mapNotNull { solve -> sessions.firstOrNull { it.id == solve.sessionId }?.eventId }.toSet()
            val destinationEvent = selectedEventIds.singleOrNull()
            val moveTargets = sessions.filter { it.id !in sourceSessionIds && destinationEvent != null && it.eventId == destinationEvent }
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
        confirmButton = { TextButton(onClick = { vm.deleteSolvesWithUndo(selectedIds.toList()); offerUndo("${selectedIds.size} solves deleted"); selectedIds = emptySet(); selectMode = false; deleteConfirmation = false }) { Text("Delete") } },
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
private fun StatsPage(solves: List<SolveEntity>, session: SessionEntity? = null, gradientStyle: String = "blue", customGradientStart: Int = 0xFF83A6FF.toInt(), customGradientEnd: Int = 0xFF4169E1.toInt(), graphGlow: Boolean = true, graphAnimation: Boolean = true, cardMotion: Boolean = true, vm: TimerViewModel, onChooseSession: () -> Unit = {}) {
    var rangeDays by remember(session?.id) { mutableIntStateOf(0) }
    var focusedSolve by remember { mutableStateOf<SolveEntity?>(null) }
    val now = System.currentTimeMillis()
    val windowMillis = rangeDays * 24L * 60L * 60L * 1000L
    val currentStart = if (rangeDays == 0) Long.MIN_VALUE else now - windowMillis
    val scopedSolves = if (rangeDays == 0) solves else solves.filter { it.createdAt >= currentStart }
    val previousSolves = if (rangeDays == 0) emptyList() else solves.filter { it.createdAt >= currentStart - windowMillis && it.createdAt < currentStart }
    val results = scopedSolves.reversed().map { solve ->
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
    val compactStats = LocalConfiguration.current.screenWidthDp < 440 || LocalDensity.current.fontScale > 1.3f
    val effectiveCardMotion = cardMotion && LocalMotionEnabled.current
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = .09f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background,
    ))).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp)) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
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
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "All time", 30 to "30 days", 7 to "7 days").forEach { (days, label) ->
                FilterChip(selected = rangeDays == days, onClick = { rangeDays = days }, label = { Text(label) })
            }
        }
        if (rangeDays > 0) {
            fun periodMean(items: List<SolveEntity>): Long? = items.map { solve ->
                when (solve.penalty) { Penalty.PLUS_TWO.ordinal -> solve.timeMillis + 2_000; Penalty.DNF.ordinal -> Long.MAX_VALUE; else -> solve.timeMillis }
            }.filter { it != Long.MAX_VALUE }.takeIf { it.isNotEmpty() }?.average()?.toLong()
            val priorMean = periodMean(previousSolves)
            val recentMean = periodMean(scopedSolves)
            val comparison = when {
                recentMean == null || priorMean == null -> "Not enough solves in both periods yet"
                recentMean <= priorMean -> "${formatMillis(priorMean - recentMean)} faster than the previous ${rangeDays} days"
                else -> "${formatMillis(recentMean - priorMean)} slower than the previous ${rangeDays} days"
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PERIOD COMPARISON", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(comparison, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                }
                Text("${scopedSolves.size} solves", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (session?.sessionType != "compsim") AverageGoalCard(session?.targetMillis, ao5)
        if (session?.sessionType == "compsim" && session.targetMillis != null) {
            val progress = Statistics.compSimProgress(results, session.targetMillis)
            val complete = progress.groups.filter { it.average != null }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("CURRENT", formatAverage(progress.groups.lastOrNull()?.average), Modifier.weight(1f), height = 158, motionEnabled = effectiveCardMotion)
                StatsCard("AVERAGES", complete.size.toString(), Modifier.weight(1f), height = 158, motionEnabled = effectiveCardMotion)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("TARGET", formatMillis(session.targetMillis), Modifier.weight(1f), motionEnabled = effectiveCardMotion)
                StatsCard("REACHED", complete.count { it.average?.let { avg -> !avg.isDnf && (avg.millis ?: Double.POSITIVE_INFINITY) <= session.targetMillis } == true }.toString(), Modifier.weight(1f), motionEnabled = effectiveCardMotion)
            }
            StatsTrendChart(scopedSolves, graphGlow, graphAnimation) { focusedSolve = it }
            StatsDistributionChart(scopedSolves, graphAnimation)
            return@Column
        }
        val bestWindow = results.takeIf { it.size >= 5 }?.windowed(5)?.minByOrNull { w ->
            Statistics.average(w)?.let { if (it.isDnf) Double.POSITIVE_INFINITY else it.millis ?: Double.POSITIVE_INFINITY } ?: Double.POSITIVE_INFINITY
        }
        if (compactStats) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("CURRENT STATS", listOf("AO5" to formatAverage(ao5), "AO12" to formatAverage(ao12), "AO100" to formatAverage(ao100)), Modifier.fillMaxWidth(), height = 184, motionEnabled = effectiveCardMotion)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatsCard("SOLVE COUNT", scopedSolves.size.toString(), Modifier.weight(1f), height = 92, motionEnabled = effectiveCardMotion)
                    StatsCard("SESSION MEAN", mean?.let(::formatMillis) ?: "—", Modifier.weight(1f), height = 92, motionEnabled = effectiveCardMotion)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatsCard("BEST SINGLE", best?.let(::formatMillis) ?: "—", Modifier.weight(1f), height = 105, accent = true, motionEnabled = effectiveCardMotion, customGradient = gradientPalette(gradientStyle, customGradientStart, customGradientEnd, MaterialTheme.colorScheme.primary))
                    StatsCard("BEST AO5", bestAo5?.let(::formatAverage) ?: "—", Modifier.weight(1f), height = if (bestAo5 == null) 105 else 245,
                        details = bestWindow?.mapIndexed { i, result ->
                            val text = if (result.penalty == Penalty.DNF) "DNF" else formatMillis(result.adjustedMillis)
                            if (Statistics.average(bestWindow)?.trimmed?.contains(i) == true) "($text)" else text
                        }.orEmpty(), motionEnabled = effectiveCardMotion)
                }
                StatsCard("BEST STATS", listOf("AO12" to formatAverage(bestAo12), "AO100" to formatAverage(bestAo100)), Modifier.fillMaxWidth(), height = 132, motionEnabled = effectiveCardMotion)
            }
        } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("CURRENT STATS", listOf("AO5" to formatAverage(ao5), "AO12" to formatAverage(ao12), "AO100" to formatAverage(ao100)), Modifier.weight(1f), height = 164, motionEnabled = cardMotion)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsCard("SOLVE COUNT", scopedSolves.size.toString(), Modifier.fillMaxWidth(), height = 77, motionEnabled = cardMotion)
                StatsCard("SESSION MEAN", mean?.let(::formatMillis) ?: "—", Modifier.fillMaxWidth(), height = 77, motionEnabled = cardMotion)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsCard("BEST SINGLE", best?.let(::formatMillis) ?: "—", Modifier.fillMaxWidth(), accent = true, motionEnabled = cardMotion, customGradient = gradientPalette(gradientStyle, customGradientStart, customGradientEnd, MaterialTheme.colorScheme.primary))
                StatsCard("BEST STATS", listOf("AO12" to formatAverage(bestAo12), "AO100" to formatAverage(bestAo100)), Modifier.fillMaxWidth(), height = 150, motionEnabled = cardMotion)
            }
            StatsCard("BEST AO5", bestAo5?.let(::formatAverage) ?: "—", Modifier.weight(1f), height = if (bestAo5 == null) 105 else 235,
                details = bestWindow?.mapIndexed { i, r ->
                    val text = if (r.penalty == Penalty.DNF) "DNF" else formatMillis(r.adjustedMillis)
                    if (Statistics.average(bestWindow)?.trimmed?.contains(i) == true) "($text)" else text
                }.orEmpty(), motionEnabled = cardMotion)
        }
        }
        StatsTrendChart(scopedSolves, graphGlow, graphAnimation) { focusedSolve = it }
        StatsDistributionChart(scopedSolves, graphAnimation)
    }
    focusedSolve?.let { solve -> SolveDetailDialog(solve, vm) { focusedSolve = null } }
}

@Composable
private fun AverageGoalCard(targetMillis: Long?, average: app.trampis.timed.domain.Average?) {
    if (targetMillis == null) return
    val target = targetMillis.toDouble()
    val current = average?.takeUnless { it.isDnf }?.millis
    val progress = current?.let { (target / it.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) } ?: 0f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .76f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SESSION AVERAGE GOAL", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(if (current == null) "${formatMillis(targetMillis)} · Complete an Ao5 to track progress" else {
                val currentText = formatMillis(current.toLong())
                if (current <= target) "$currentText · Goal reached" else "$currentText · ${formatMillis((current - target).toLong())} to goal"
            }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            androidx.compose.animation.AnimatedVisibility(visible = current != null, enter = fadeIn() + androidx.compose.animation.expandVertically()) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp), trackColor = MaterialTheme.colorScheme.surface, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Ao5 ≤ ${formatMillis(targetMillis)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun formatAverage(average: app.trampis.timed.domain.Average?): String = when {
    average == null -> "—"
    average.isDnf -> "DNF"
    average.millis == null -> "—"
    else -> formatMillis(average.millis!!.toLong())
}

@Composable
private fun StatsCard(title: String, values: List<Pair<String, String>>, modifier: Modifier = Modifier, height: Int = 140, motionEnabled: Boolean = true) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val animate = motionEnabled && LocalMotionEnabled.current
    val cardShape = RoundedCornerShape(22.dp)
    var visible by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title, animate) { if (animate) { visible = false; delay(40L + (title.length % 5) * 55L) }; visible = true }
    androidx.compose.animation.AnimatedVisibility(visible, modifier = modifier.height((height * scale).dp),
        enter = if (animate) fadeIn(androidx.compose.animation.core.tween(360)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(420)) { it / 12 } + androidx.compose.animation.scaleIn(initialScale = .95f, animationSpec = androidx.compose.animation.core.tween(420)) else androidx.compose.animation.EnterTransition.None,
        label = "stats-card-enter-$title") {
        Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .76f), MaterialTheme.colorScheme.surface,
        )), cardShape).padding(horizontal = 15.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            values.forEach { (label, value) ->
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedContent(value, transitionSpec = { if (!animate) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None else fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideInVertically { it / 3 } togetherWith fadeOut(androidx.compose.animation.core.tween(100)) }, label = "stat-value") { shown ->
                        Text(shown, style = MaterialTheme.typography.titleLarge, fontFamily = recursiveFontFamily(700), fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, value: String, modifier: Modifier = Modifier, height: Int = 88, accent: Boolean = false, details: List<String> = emptyList(), dynamic: Boolean = false, motionEnabled: Boolean = true, customGradient: List<Color>? = null) {
    val shape = RoundedCornerShape(22.dp)
    val color = MaterialTheme.colorScheme.surface
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val animate = motionEnabled && LocalMotionEnabled.current
    val gradientColors = customGradient ?: if (dynamic) listOf(Color(0xFF27C4B8), Color(0xFFB6D944), Color(0xFFFFB52E), Color(0xFFE84F82), Color(0xFF7158E8)) else listOf(Color(0xFF83A6FF), Color(0xFF4169E1))
    var visible by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title, animate) { if (animate) { visible = false; delay(40L + (title.length % 5) * 55L) }; visible = true }
    androidx.compose.animation.AnimatedVisibility(visible, modifier = modifier.height((height * scale).dp),
        enter = if (animate) fadeIn(androidx.compose.animation.core.tween(360)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(420)) { it / 12 } + androidx.compose.animation.scaleIn(initialScale = .95f, animationSpec = androidx.compose.animation.core.tween(420)) else androidx.compose.animation.EnterTransition.None,
        label = "stats-card-enter-$title") {
        Column(Modifier.fillMaxSize().background(if (accent) androidx.compose.ui.graphics.Brush.linearGradient(gradientColors) else androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .76f), color)), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (accent) Color.White.copy(alpha = .85f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (details.isEmpty()) {
                AnimatedContent(value, transitionSpec = { if (!animate) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None else fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideInVertically { it / 3 } togetherWith fadeOut(androidx.compose.animation.core.tween(100)) }, label = "stat-card-value") { shown ->
                    Text(shown, style = MaterialTheme.typography.headlineMedium, fontFamily = recursiveFontFamily(700), fontWeight = FontWeight.Normal,
                        color = if (accent) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).wrapContentHeight(Alignment.Bottom), maxLines = 1, softWrap = false)
                }
            } else {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontFamily = recursiveFontFamily(700), fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                Spacer(Modifier.weight(1f))
                details.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, fontFamily = recursiveFontFamily(560), color = if (it.startsWith("(")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, maxLines = 1) }
            }
        }
    }
}

private data class TrendPoint(val millis: Long, val solve: SolveEntity)

private fun trendPoints(solvesDescending: List<SolveEntity>, metric: String): List<TrendPoint> {
    val chronological = solvesDescending.asReversed()
    val results = chronological.map { solve ->
        TimedResult(solve.timeMillis, when (solve.penalty) {
            Penalty.PLUS_TWO.ordinal -> Penalty.PLUS_TWO
            Penalty.DNF.ordinal -> Penalty.DNF
            else -> Penalty.NONE
        })
    }
    return when (metric) {
        "Ao5", "Ao12" -> {
            val width = if (metric == "Ao5") 5 else 12
            results.windowed(width).mapIndexedNotNull { index, window ->
                val average = Statistics.average(window)?.takeUnless { it.isDnf }?.millis?.toLong()
                average?.let { TrendPoint(it, chronological[index + width - 1]) }
            }
        }
        else -> chronological.zip(results).mapNotNull { (solve, result) ->
            result.adjustedMillis.takeUnless { it == Long.MAX_VALUE }?.let { TrendPoint(it, solve) }
        }
    }
}

@Composable
private fun StatsTrendChart(solvesDescending: List<SolveEntity>, glowEnabled: Boolean = true, animationEnabled: Boolean = true, onPointSelected: (SolveEntity) -> Unit = {}) {
    var metric by remember(solvesDescending) { mutableStateOf("Singles") }
    val chartPoints = remember(solvesDescending, metric) { trendPoints(solvesDescending, metric).takeLast(80) }
    val values = chartPoints.map { it.millis }
    var selected by remember(values) { mutableIntStateOf(values.lastIndex) }
    val motionEnabled = LocalMotionEnabled.current
    val shouldAnimate = animationEnabled && motionEnabled
    val accent = MaterialTheme.colorScheme.primary
    val chartSurface = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = labelColor.copy(alpha = 0.30f)
    val reveal = remember(values, shouldAnimate) { androidx.compose.animation.core.Animatable(if (shouldAnimate) 0f else 1f) }
    LaunchedEffect(values, shouldAnimate) {
        if (shouldAnimate) reveal.animateTo(1f, androidx.compose.animation.core.tween(850, easing = androidx.compose.animation.core.FastOutSlowInEasing)) else reveal.snapTo(1f)
    }
    val selectedValue = values.getOrNull(selected)
    val rangeMin = values.minOrNull()?.toFloat() ?: 0f
    val rangeMax = values.maxOrNull()?.toFloat() ?: 1f
    val spread = (rangeMax - rangeMin).coerceAtLeast(1f)
    val yMin = (rangeMin - spread * .08f).coerceAtLeast(0f)
    val yMax = rangeMax + spread * .08f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), RoundedCornerShape(24.dp)).padding(18.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Singles", "Ao5", "Ao12").forEach { option ->
                FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(option) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TIME TREND", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = labelColor)
                AnimatedContent(targetState = selectedValue, transitionSpec = { if (shouldAnimate) fadeIn() togetherWith fadeOut() else androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None }, label = "trend-selected-value") { value ->
                    Text(value?.let(::formatMillis) ?: "Record solves to see your trend", style = MaterialTheme.typography.headlineSmall, fontFamily = recursiveFontFamily(700), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
            Text("${values.size} SOLVES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent,
                modifier = Modifier.background(accent.copy(alpha = .12f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
        }
        if (values.isEmpty()) {
            Text(if (metric == "Singles") "Record solves to see your trend" else "Complete an $metric to see this trend", style = MaterialTheme.typography.bodySmall, color = labelColor, modifier = Modifier.padding(top = 10.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(42.dp).height(176.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                    (0..4).forEach { i ->
                        val v = yMax - (yMax - yMin) * i / 4f
                        Text(formatMillis(v.toLong(), 1), style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
                    }
                }
                Canvas(Modifier.weight(1f).height(176.dp).padding(start = 8.dp).pointerInput(values) {
                    detectTapGestures { offset -> if (values.isNotEmpty()) {
                        val index = if (values.size == 1) 0 else ((offset.x / size.width) * (values.size - 1)).roundToInt().coerceIn(values.indices)
                        selected = index
                        chartPoints.getOrNull(index)?.solve?.let(onPointSelected)
                    } }
                }) {
                    val left = 6f; val right = size.width - 5f; val top = 6f; val bottom = size.height - 7f
                    val ys = (0..4).map { top + (bottom - top) * it / 4f }
                    ys.forEach { y -> drawLine(grid, Offset(left, y), Offset(right, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))) }
                    drawLine(grid.copy(alpha = .7f), Offset(left, top), Offset(left, bottom), 1.dp.toPx())
                    val points = values.mapIndexed { index, value -> Offset(if (values.size == 1) (left + right) / 2 else left + (right - left) * index / (values.size - 1), bottom - (bottom - top) * ((value - yMin) / (yMax - yMin).coerceAtLeast(1f))) }
                    val visible = (points.size * reveal.value).toInt().coerceIn(1, points.size)
                    val visiblePoints = points.take(visible)
                    val line = Path().apply {
                        moveTo(visiblePoints.first().x, visiblePoints.first().y)
                        for (i in 1 until visiblePoints.size) {
                            val prev = visiblePoints[i - 1]; val curr = visiblePoints[i]
                            val midX = (prev.x + curr.x) / 2f
                            quadraticTo(midX, prev.y, midX, (prev.y + curr.y) / 2f)
                            if (i == visiblePoints.lastIndex) quadraticTo(midX, curr.y, curr.x, curr.y)
                        }
                    }
                    val area = Path().apply { addPath(line); lineTo(visiblePoints.last().x, bottom); lineTo(visiblePoints.first().x, bottom); close() }
                    drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = .25f), accent.copy(alpha = .015f)), top, bottom))
                    if (glowEnabled) drawPath(line, accent.copy(alpha = .24f), style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(line, accent, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
                    val focus = points.getOrNull(selected)
                    if (focus != null && selected < visible) {
                        drawLine(accent.copy(alpha = .55f), Offset(focus.x, top), Offset(focus.x, bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                        drawCircle(accent.copy(alpha = .2f), 10.dp.toPx(), focus)
                        drawCircle(accent, 4.5.dp.toPx(), focus)
                        drawCircle(chartSurface, 1.8.dp.toPx(), focus)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 50.dp, top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("OLDER", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = labelColor)
                Text("NEWER", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = labelColor)
            }
        }
    }
}

@Composable
private fun StatsDistributionChart(solvesDescending: List<SolveEntity>, animationEnabled: Boolean = true) {
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
    val shouldAnimate = animationEnabled && LocalMotionEnabled.current
    val barReveal = remember(bins, shouldAnimate) { androidx.compose.animation.core.Animatable(if (shouldAnimate) 0f else 1f) }
    LaunchedEffect(bins, shouldAnimate) {
        if (shouldAnimate) barReveal.animateTo(1f, androidx.compose.animation.core.tween(720, easing = androidx.compose.animation.core.FastOutSlowInEasing)) else barReveal.snapTo(1f)
    }
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
                    val stagger = if (animationEnabled) (.82f + .18f * (index + 1) / bins.size) else 1f
                    val height = (bottom - top) * bin.count / maxCount * barReveal.value * stagger
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
private fun MorePage(inspectionEnabled: Boolean, inspectionCountdown: Boolean, holdTime: Long, haptics: Boolean, inspectionAlerts: Boolean, solveSounds: Boolean, timerStats: Boolean, scrambleDrawing: Boolean, decimalPlaces: Int, vm: TimerViewModel, onTools: () -> Unit, onAppearance: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingGroup("Appearance") {
            ListItem(headlineContent = { Text("Appearance", fontWeight = FontWeight.Medium) }, supportingContent = { Text("Theme, graph style, and timer preview") }, trailingContent = { Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp) }, modifier = Modifier.clickable(onClick = onAppearance))
        }
        SettingGroup("Timer Settings") {
            SettingSwitch("Use Inspection", "Enable the WCA 15 second inspection period.", inspectionEnabled, vm::setInspectionEnabled)
            if (inspectionEnabled) {
                SettingSwitch("Inspection Counts Down", "Count down from 15 seconds, then start the solve timer automatically.", inspectionCountdown, vm::setInspectionCountdownEnabled)
                SettingSwitch("Inspection Alerts", "Play CubeTime's audio cues at 8 and 12 seconds.", inspectionAlerts, vm::setInspectionAlertsEnabled)
            }
            SettingSwitch("Solve Sounds", "Play short cues for solves, new bests, sessions, and scramble refreshes.", solveSounds, vm::setSolveSoundsEnabled)
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
private fun AppearancePage(override: Boolean, dark: Boolean, staticGradient: Boolean, graphGlow: Boolean, graphAnimation: Boolean, confetti: Boolean, boldTimerDigits: Boolean, timerFontWeight: Int, statsCardMotion: Boolean, timerSizeScale: Float, scrambleTextSize: Int, timerFontPreset: String, vm: TimerViewModel, onBack: () -> Unit) {
    var showReset by remember { mutableStateOf(false) }
    val currentAccent by vm.accentPreset.collectAsState()
    val customAccent by vm.customAccent.collectAsState()
    val gradientStyle by vm.gradientStyle.collectAsState()
    val customStart by vm.customGradientStart.collectAsState()
    val customEnd by vm.customGradientEnd.collectAsState()
    val reducedMotion by vm.reducedMotion.collectAsState()
    val highContrast by vm.highContrast.collectAsState()
    val uiFont by vm.uiFont.collectAsState()
    var accentHue by remember(customAccent) { mutableFloatStateOf(colorHue(customAccent)) }
    var startHue by remember(customStart) { mutableFloatStateOf(colorHue(customStart)) }
    var endHue by remember(customEnd) { mutableFloatStateOf(colorHue(customEnd)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Settings") }
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingGroup("Color Mode") {
                Text("Choose how Trampis Timed follows your device theme.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark mode").forEach { (mode, label) ->
                        val selected = when (mode) { "system" -> !override; "dark" -> override && dark; else -> override && !dark }
                        FilterChip(selected = selected, onClick = {
                            vm.setAppearanceOverride(mode != "system")
                            if (mode != "system") vm.setAppearanceDark(mode == "dark")
                        }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            SettingGroup("Accent Color") {
                Text("Pick the app highlight color or tune a custom hue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                val choices = listOf("blue" to "Blue", "violet" to "Violet", "mint" to "Mint", "amber" to "Amber", "custom" to "Custom")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { (key, label) ->
                        val tint = when (key) { "violet" -> Color(0xFF9B72F2); "mint" -> Color(0xFF31B9A3); "amber" -> Color(0xFFF0A83A); "custom" -> Color(customAccent); else -> Color(0xFF5179F6) }
                        FilterChip(selected = currentAccent == key, onClick = { vm.setAccentPreset(key) },
                            label = { Text(label) }, leadingIcon = { Box(Modifier.size(12.dp).background(tint, RoundedCornerShape(50))) })
                    }
                }
                if (currentAccent == "custom") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom accent", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Box(Modifier.size(24.dp).background(Color(customAccent), RoundedCornerShape(50)))
                    }
                    Slider(value = accentHue, onValueChange = { accentHue = it }, onValueChangeFinished = { vm.setCustomAccent(hueColor(accentHue)) }, valueRange = 0f..360f, modifier = Modifier.padding(horizontal = 10.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            SettingGroup("Stats Gradients") {
                Text("Choose a color blend for personal-best cards. Match Accent follows your selected accent color.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                val gradients = listOf("blue" to "CubeTime", "accent" to "Match Accent", "rainbow" to "Rainbow", "sunset" to "Sunset", "ocean" to "Ocean", "aurora" to "Aurora", "custom" to "Custom")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    gradients.forEach { (style, label) ->
                        FilterChip(selected = gradientStyle == style, onClick = { vm.setGradientStyle(style) }, label = { Text(label) },
                            leadingIcon = { Box(Modifier.size(17.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(gradientPalette(style, customStart, customEnd, MaterialTheme.colorScheme.primary)), RoundedCornerShape(5.dp))) })
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).height(48.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(gradientPalette(gradientStyle, customStart, customEnd, MaterialTheme.colorScheme.primary)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("Gradient preview", color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (gradientStyle == "custom") {
                    Text("First color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 14.dp))
                    Slider(value = startHue, onValueChange = { startHue = it }, onValueChangeFinished = { vm.setCustomGradientStart(hueColor(startHue)) }, valueRange = 0f..360f, modifier = Modifier.padding(horizontal = 10.dp))
                    Text("Second color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 14.dp))
                    Slider(value = endHue, onValueChange = { endHue = it }, onValueChangeFinished = { vm.setCustomGradientEnd(hueColor(endHue)) }, valueRange = 0f..360f, modifier = Modifier.padding(horizontal = 10.dp))
                }
                Spacer(Modifier.height(8.dp))
                SettingSwitch("Graph Glow", "Turn the glow effect on or off on graphs.", graphGlow, vm::setGraphGlow)
                SettingSwitch("Graph Animation", "Animate the time trend line.", graphAnimation, vm::setGraphAnimation)
                SettingSwitch("Stats Card Motion", "Animate statistic cards when Stats opens.", statsCardMotion, vm::setStatsCardMotion)
                SettingSwitch("Show Confetti", "Celebrate a new best time.", confetti, vm::setShowConfetti)
            }
            SettingGroup("Accessibility") {
                SettingSwitch("Reduce Motion", "Turn off decorative movement and use instant screen changes.", reducedMotion, vm::setReducedMotion)
                SettingSwitch("High Contrast", "Increase text, divider, and surface contrast.", highContrast, vm::setHighContrast)
                Text("App Typeface", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = uiFont == "system", onClick = { vm.setUiFont("system") }, label = { Text("System") })
                    FilterChip(selected = uiFont == "rubik", onClick = { vm.setUiFont("rubik") }, label = { Text("Rubik") })
                }
                Spacer(Modifier.height(4.dp))
            }
            SettingGroup("Timer Typography") {
                SettingSwitch("Bold Timer Digits", "Use a stronger, clearer weight for the main timer.", boldTimerDigits, vm::setBoldTimerDigits)
                Text("Timer Typeface", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = timerFontPreset == "recursive", onClick = { vm.setTimerFontPreset("recursive") }, label = { Text("Recursive") })
                    FilterChip(selected = timerFontPreset == "system", onClick = { vm.setTimerFontPreset("system") }, label = { Text("System") })
                }
                Text("00.000", fontFamily = if (timerFontPreset == "system") FontFamily.Default else recursiveFontFamily(if (boldTimerDigits) timerFontWeight else 500), fontSize = 34.sp,
                    fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))
                Text("Font Weight · $timerFontWeight", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp))
                Slider(value = timerFontWeight.toFloat(), onValueChange = { vm.setTimerFontWeight(it.roundToInt()) }, valueRange = 500f..900f, steps = 7, enabled = boldTimerDigits, modifier = Modifier.padding(horizontal = 12.dp))
                Text("Timer Size · ${(timerSizeScale * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp))
                Slider(value = timerSizeScale, onValueChange = vm::setTimerSizeScale, valueRange = .85f..1.2f, steps = 6, modifier = Modifier.padding(horizontal = 12.dp))
                Text("Scramble Size · ${scrambleTextSize}sp", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp))
                Slider(value = scrambleTextSize.toFloat(), onValueChange = { vm.setScrambleTextSize(it.roundToInt()) }, valueRange = 14f..23f, steps = 8, modifier = Modifier.padding(horizontal = 12.dp))
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
        Column(Modifier.verticalScroll(rememberScrollState())) {
            sessions.forEach { session -> TextButton(onClick = { onSelect(session.id) }, modifier = Modifier.fillMaxWidth()) { Text((if (session.id == selected?.id) "✓  " else "") + session.name) } }
        }
    }, confirmButton = { TextButton(onClick = onNew) { Text("New session") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun NewSessionDialog(onDismiss: () -> Unit, onCreate: (String, String, String, Int, Long?) -> Unit) {
    val motionEnabled = LocalMotionEnabled.current
    var name by remember { mutableStateOf("") }
    var eventId by remember { mutableStateOf("333") }
    var expanded by remember { mutableStateOf(false) }
    var sessionType by remember { mutableStateOf("standard") }
    var phaseCount by remember { mutableIntStateOf(2) }
    var targetText by remember { mutableStateOf("") }
    val config = LocalConfiguration.current
    val compactDialog = config.screenWidthDp < 360
    val event = CubeEvents.fromId(eventId)
    val sessionTypes = listOf(
        Triple("standard", "Standard", "One puzzle, one continuous session."),
        Triple("multiphase", "Multiphase", "Split solves into timed phases."),
        Triple("playground", "Playground", "Switch puzzle events as you practice."),
        Triple("compsim", "Comp Sim", "Practice groups of five against a target."),
        Triple("timerOnly", "Timer Only", "Use the timer without scramble tracking."),
        Triple("algtrainer", "Alg Trainer", "Keep focused algorithm practice together."),
    )
    val selectedType = sessionTypes.first { it.first == sessionType }
    val targetSeconds = targetText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val targetMillis = targetSeconds?.let { (it * 1000).toLong().takeIf { millis -> millis > 0L } }
    val canCreate = sessionType != "compsim" || targetMillis != null
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compactDialog) 12.dp else 18.dp).widthIn(max = 480.dp)
                .heightIn(max = (config.screenHeightDp - 48).coerceAtLeast(420).dp),
            shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp, shadowElevation = 18.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        ) {
            Column(Modifier.padding(if (compactDialog) 14.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compactDialog) 44.dp else 52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                        EventIcon(eventId, if (compactDialog) 30 else 34)
                    }
                    Column(Modifier.weight(1f).padding(start = if (compactDialog) 9.dp else 12.dp)) {
                        Text(if (compactDialog) "SESSION SETUP" else "CREATE SESSION", style = MaterialTheme.typography.labelSmall, letterSpacing = if (compactDialog) 1.sp else 1.6.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (compactDialog) "New" else "New Session", style = if (compactDialog) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                    IconButton(onClick = onDismiss, modifier = if (compactDialog) Modifier.size(36.dp) else Modifier) { Text("×", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = (config.screenHeightDp * .62f).dp)
                        .verticalScroll(rememberScrollState()).padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Session name") },
                        placeholder = { Text(if (sessionType == "timerOnly") "e.g. Warm-up" else "e.g. Evening 3×3") },
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Text("SESSION STYLE", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sessionTypes.chunked(2).forEach { rowOptions ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowOptions.forEach { option ->
                                    val tileLabel = if (!compactDialog) option.second else when (option.first) {
                                        "multiphase" -> "Multi"; "playground" -> "Play"; "compsim" -> "Comp Sim"; "timerOnly" -> "Timer"; "algtrainer" -> "Algs"; else -> "Standard"
                                    }
                                    SessionTypeTile(tileLabel, option.first == sessionType, Modifier.weight(1f)) { sessionType = option.first }
                                }
                                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Text(selectedType.third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sessionType != "timerOnly") {
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)) {
                                EventIcon(eventId, 24)
                                Text("${event.name}  ·  Change event", modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("⌄", fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                CubeEvents.all.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { eventId = option.id; expanded = false }) }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(sessionType == "multiphase", enter = if (motionEnabled) fadeIn() + androidx.compose.animation.expandVertically() else androidx.compose.animation.EnterTransition.None, exit = if (motionEnabled) fadeOut() + androidx.compose.animation.shrinkVertically() else androidx.compose.animation.ExitTransition.None) {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Solve phases", fontWeight = FontWeight.Bold)
                                Text("Split each solve into timed sections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { phaseCount = (phaseCount - 1).coerceAtLeast(2) }, enabled = phaseCount > 2) { Text("−", fontSize = 22.sp) }
                            Text("$phaseCount", fontFamily = recursiveFontFamily(700), fontWeight = FontWeight.Normal, fontSize = 18.sp)
                            IconButton(onClick = { phaseCount = (phaseCount + 1).coerceAtMost(8) }, enabled = phaseCount < 8) { Text("+", fontSize = 20.sp) }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(sessionType != "timerOnly", enter = if (motionEnabled) fadeIn() + androidx.compose.animation.expandVertically() else androidx.compose.animation.EnterTransition.None, exit = if (motionEnabled) fadeOut() + androidx.compose.animation.shrinkVertically() else androidx.compose.animation.ExitTransition.None) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { input ->
                                val cleaned = input.filter { it.isDigit() || it == '.' }.take(9)
                                if (cleaned.count { it == '.' } <= 1) targetText = cleaned
                            },
                            label = { Text(if (sessionType == "compsim") "Competition target · seconds" else "Ao5 goal · optional seconds") },
                            leadingIcon = { Text("◎", color = MaterialTheme.colorScheme.primary) },
                            supportingText = { Text(if (sessionType == "compsim") "Required to run a competition simulation." else "Track progress toward your average goal.") },
                            singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), RoundedCornerShape(15.dp)).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✦", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                        Text("${if (sessionType == "timerOnly") "Timer only" else event.name}  ·  ${selectedType.second}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 9.dp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (targetMillis != null) Text(formatMillis(targetMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { onCreate(name.trim().ifBlank { if (sessionType == "timerOnly") "Timer Only" else event.name }, eventId, sessionType, phaseCount, targetMillis) },
                        enabled = canCreate, modifier = Modifier.weight(1.35f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                    ) { Text(if (compactDialog) "Create" else "Create Session", fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun SessionTypeTile(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val motionEnabled = LocalMotionEnabled.current
    val background by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        androidx.compose.animation.core.tween(if (motionEnabled) 180 else 0), label = "session-type-$label",
    )
    val outline by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .75f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f),
        androidx.compose.animation.core.tween(if (motionEnabled) 180 else 0), label = "session-type-outline-$label",
    )
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp).border(1.dp, outline, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp), color = background,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "●" else "○", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(label, modifier = Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, modifier = Modifier.padding(top = 8.dp)) } }
@Composable
private fun StatRow(label: String, value: String) { ListItem(headlineContent = { Text(label) }, trailingContent = { Text(value, fontWeight = FontWeight.SemiBold) }) }
private fun formatTime(nanos: Long, dp: Int = 3): String = formatMillis(nanos / 1_000_000L, dp)
private fun safeFileName(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }.trim().replace(' ', '_').ifBlank { "Session" }
private fun formatDate(epochMillis: Long): String = java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
private fun formatSolve(solve: SolveEntity): String = when (solve.penalty) { Penalty.DNF.ordinal -> "DNF"; Penalty.PLUS_TWO.ordinal -> "${formatMillis(solve.timeMillis + 2_000)}+"; else -> formatMillis(solve.timeMillis) }
@Composable
private fun recursiveFontFamily(weight: Int): FontFamily {
    val context = LocalContext.current
    val typeface = remember(weight) {
        runCatching {
            android.graphics.Typeface.Builder(context.assets, "fonts/recursive.ttf")
                .setFontVariationSettings("'wght' ${weight.coerceIn(300, 1000)}, 'CASL' 0, 'CRSV' 0")
                .build()
        }.getOrElse { android.graphics.Typeface.create("sans-serif-monospace", android.graphics.Typeface.NORMAL) }
    }
    return remember(typeface) { FontFamily(typeface) }
}

private fun gradientPalette(style: String, start: Int, end: Int, accent: Color = Color(0xFF5179F6)): List<Color> = when (style) {
    "accent" -> listOf(
        Color(accent.red * .68f, accent.green * .68f, accent.blue * .68f),
        accent,
        Color((accent.red * .78f + .22f).coerceAtMost(1f), (accent.green * .78f + .22f).coerceAtMost(1f), (accent.blue * .78f + .22f).coerceAtMost(1f)),
    )
    "rainbow" -> listOf(Color(0xFFFF4D6D), Color(0xFFFFA62B), Color(0xFFFFD93D), Color(0xFF42D392), Color(0xFF3B82F6), Color(0xFF9B5DE5))
    "sunset" -> listOf(Color(0xFFFF6B6B), Color(0xFFF06595), Color(0xFF845EC2))
    "ocean" -> listOf(Color(0xFF00B4D8), Color(0xFF4361EE), Color(0xFF3A0CA3))
    "aurora" -> listOf(Color(0xFF00D2A0), Color(0xFF20A4F3), Color(0xFF9B5DE5))
    "custom" -> listOf(Color(start), Color(end))
    else -> listOf(Color(0xFF83A6FF), Color(0xFF4169E1))
}

private fun hueColor(hue: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(hue.coerceIn(0f, 360f), .78f, .98f))

private fun colorHue(color: Int): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }[0]

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
