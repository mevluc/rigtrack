package com.rigtrack.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.ar.core.ArCoreApk
import com.rigtrack.R
import com.rigtrack.core.model.*
import com.rigtrack.data.*
import com.rigtrack.export.*
import com.rigtrack.tracking.RecordingJobs
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.IOException
import java.util.Locale

class MainActivity : ComponentActivity() {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal lateinit var repo: Repository
    internal lateinit var preferences: UserPreferences
    internal lateinit var capture: CaptureCoordinator
    internal var shot by mutableStateOf(ShotSettings())
    internal var shotRevision by mutableIntStateOf(0)
    internal fun duplicateShot(settings: ShotSettings) { shot = settings.copy(name = ""); shotRevision++; go("new") }
    private var confirmedCamera by mutableStateOf<FilmSettings?>(null)
    internal fun areaConfirmed(film: FilmSettings) = confirmedCamera?.let { it.name == film.name && it.sensorWidthMm == film.sensorWidthMm && it.sensorHeightMm == film.sensorHeightMm && it.width == film.width && it.height == film.height } == true
    internal fun confirmArea(film: FilmSettings) { confirmedCamera = film; save("confirmed_camera_area.json", film) }
    internal var detector by mutableStateOf(DetectorSettings())
    internal var selectedMap by mutableStateOf(MarkerMap())
    internal var captureMode = "record"
    internal var viewport by mutableStateOf<FrameLayout?>(null)
    internal var selected by mutableStateOf<File?>(null)
    internal var errorDetail by mutableStateOf("")
    internal var notice by mutableIntStateOf(0)
    internal var error by mutableIntStateOf(0)
    private var permissionExplanation by mutableStateOf(false)
    private var permissionDenied by mutableStateOf(false)
    internal val routes = mutableStateListOf("home")
    internal var nestedBack: (() -> Unit)? = null
    private var exportFile: File? = null
    private var exportKind = "zip"
    private var exportIncludeReferenceVideo = false
    internal var exporting by mutableStateOf(false)
    private var installRequested = false
    private val document = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == RESULT_OK }
        val source = exportFile; val kind = exportKind
        if (uri != null && source != null) scope.launch {
            exporting = true
            runCatching { withContext(Dispatchers.IO) { try {
                contentResolver.openOutputStream(uri)?.use { out -> when (kind) {
                    "zip" -> TrackExporter.zip(source, out, exportIncludeReferenceVideo)
                    "csv" -> File(source, "blender_camera_refined.csv").takeIf{it.isFile}?.let{selected->selected.inputStream().use{it.copyTo(out)}}
                        ?: File(source, "blender_camera.csv").inputStream().use { it.copyTo(out) }
                    else -> source.inputStream().use { it.copyTo(out) }
                } } ?: error("Cannot open export")
                if (kind != "pdf") File(source, "exported.flag").writeText(System.currentTimeMillis().toString())
            } catch(t:Throwable){throw IOException("EXPORT_FAILED",t)} } }.onSuccess { notice = R.string.export_complete }.onFailure(::report)
            exporting = false
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) openCapture() else permissionDenied = true }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); repo = Repository(this); preferences = UserPreferences(this)
        detector = repo.settings(); shot = repo.load("shot_defaults.json", ShotSettings::class.java) ?: ShotSettings()
        confirmedCamera = repo.load("confirmed_camera_area.json", FilmSettings::class.java)
        shotRevision = state?.getInt("shot_revision") ?: 0
        capture = CaptureCoordinator(this, repo, scope, ::report)
        state?.getStringArrayList("routes")?.filter { it !in setOf("capture", "summary") }?.takeIf { it.isNotEmpty() }?.let { routes.clear(); routes.addAll(it) }
        setContent { RigTheme { Application() } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("shot_revision", shotRevision); outState.putStringArrayList("routes", ArrayList(routes)); super.onSaveInstanceState(outState) }
    internal fun go(route: String) { if (routes.lastOrNull() != route) routes.add(route) }
    internal fun back() { if (routes.size > 1) { if (routes.last() == "capture") { capture.close(); viewport = null }; routes.removeAt(routes.lastIndex) } }
    internal fun home() { capture.close(); viewport = null; routes.clear(); routes.add("home") }
    internal fun save(name: String, value: Any, announce: Boolean = false) { scope.launch { runCatching { withContext(Dispatchers.IO) { repo.save(name, value) } }.onSuccess { if (announce) notice = R.string.saved }.onFailure(::report) } }
    internal fun guard(action: () -> Unit) { runCatching(action).onFailure(::report) }
    internal fun report(t: Throwable) {
        if (Looper.myLooper() != Looper.getMainLooper()) { runOnUiThread { report(t) }; return }
        errorDetail = "${t.javaClass.simpleName}: ${t.message}"
        error = when {
            t.message?.contains("STORAGE_CRITICAL") == true -> R.string.storage_critical
            t.message?.contains("SET ORIGIN") == true -> R.string.origin_required
            t.message?.contains("Stop recording") == true || t.message?.contains("before recording") == true -> R.string.stop_first
            t.message?.contains("Start recording first") == true -> R.string.record_first
            t.message?.contains("marker", true) == true && t is IllegalStateException -> R.string.mapped_required
            t.message?.contains("AR_UNSUPPORTED") == true -> R.string.unsupported
            t.message?.contains("EXPORT_FAILED") == true -> R.string.export_failed
            t is NumberFormatException || t is IllegalArgumentException -> R.string.invalid_value
            else -> R.string.error_generic
        }
    }
    internal fun begin(mode: String = "record") {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0)
        captureMode = mode; save("shot_defaults.json", shot); save("settings.json", detector)
        if (!shot.synthetic && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissionExplanation = true else openCapture()
    }
    private fun openCapture() = guard {
        if (!shot.synthetic) {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            if (ArCoreApk.getInstance().requestInstall(this, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) { installRequested = true; return@guard }
        }
        try { viewport = capture.open(shot, detector, selectedMap, captureMode); go("capture") } catch (t: Throwable) { capture.close(); throw t }
    }
    internal fun finishRecording(leave: Boolean = false) {
        capture.controller ?: return
        RecordingJobs.scope.launch {
            runCatching { capture.stop() }.onSuccess { file -> withContext(Dispatchers.Main) {
                if (!isDestroyed && file != null) {
                    selected = file; capture.close(); viewport = null
                    if (routes.lastOrNull() == "capture") routes.removeAt(routes.lastIndex)
                    if (!leave) go("summary")
                }
            } }.onFailure { runOnUiThread { report(it) } }
        }
    }
    internal fun export(file: File, kind: String = "zip", includeReferenceVideo: Boolean = false) {
        exportFile = file; exportKind = kind; exportIncludeReferenceVideo = includeReferenceVideo
        val spec=ExportDocuments.spec(file,kind)
        document.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE);type=spec.mimeType
            putExtra(Intent.EXTRA_TITLE,spec.displayName)
        })
    }
    @Composable private fun Application() {
        val prefs by preferences.state.collectAsState(initial = null)
        val p = prefs ?: return
        val deviceConfiguration = LocalConfiguration.current
        val configuration = remember(p.language, deviceConfiguration) { Configuration(deviceConfiguration).apply { setLocale(Locale.forLanguageTag(p.language)) } }
        val context = remember(configuration) { createConfigurationContext(configuration) }
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
            SideEffect { if (p.keepAwake || capture.controller?.writer != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            var confirmExit by remember { mutableStateOf(false) }; var lastBack by remember { mutableLongStateOf(0) }
            val savedScreens = rememberSaveableStateHolder()
            val route = routes.last()
            fun navigateBack() {
                if (nestedBack != null) nestedBack?.invoke()
                else if (route == "capture" && (capture.controller?.writer != null || capture.controller?.stopping == true)) confirmExit = true
                else if (routes.size > 1) back()
                else { val now = SystemClock.elapsedRealtime(); if (now - lastBack <= 2000 && lastBack != 0L) finish() else { lastBack = now; Toast.makeText(context, R.string.double_back, Toast.LENGTH_SHORT).show() } }
            }
            BackHandler { navigateBack() }
            if (!p.onboardingDone) Onboarding { scope.launch { preferences.flag("onboarding_done", true) } }
            else Scaffold(containerColor = RigBackground, topBar = { if (route != "capture" && route != "home") TopBar(title(route), s(R.string.back), if (routes.size > 1) ({ navigateBack() }) else null) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { savedScreens.SaveableStateProvider(if (route == "new") "new:$shotRevision" else route) { when (route) {
                    "home" -> HomeScreen(this@MainActivity); "new" -> NewShotScreen(this@MainActivity, p)
                    "presets" -> PresetsScreen(this@MainActivity); "capture" -> CaptureScreen(this@MainActivity, p, ::navigateBack)
                    "rigs" -> RigsScreen(this@MainActivity); "maps" -> MapsScreen(this@MainActivity)
                    "calibration" -> CalibrationSetupScreen(this@MainActivity); "recordings" -> RecordingsScreen(this@MainActivity)
                    "summary" -> SummaryScreen(this@MainActivity); "settings" -> SettingsScreen(this@MainActivity, p)
                    "diagnostics" -> DiagnosticsScreen(this@MainActivity); "generator" -> GeneratorScreen(this@MainActivity)
                    "guide" -> GuideScreen(this@MainActivity); "about" -> AboutScreen(this@MainActivity)
                    "video" -> ReferenceVideoPlayerScreen(this@MainActivity)
                } } }
            }
            if (confirmExit) ConfirmationDialog(s(R.string.record_exit_title), s(R.string.record_exit_body), s(R.string.stop_save), s(R.string.cancel), { confirmExit = false; finishRecording(true) }, { confirmExit = false })
            if (permissionExplanation) ConfirmationDialog(s(R.string.permission_title), s(R.string.permission_body), s(R.string.continue_action), s(R.string.cancel), { permissionExplanation = false; cameraPermission.launch(Manifest.permission.CAMERA) }, { permissionExplanation = false })
            if (permissionDenied) ConfirmationDialog(s(R.string.permission_title), s(R.string.permission_denied), s(R.string.open_settings), s(R.string.cancel), { permissionDenied = false; startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }, { permissionDenied = false })
            if (error != 0 || notice != 0) AlertDialog(onDismissRequest = { error = 0; notice = 0 }, title = { Text(s(if (error != 0) R.string.error_title else R.string.done)) }, text = { Text(s(if (error != 0) error else notice)) }, confirmButton = { TextButton({ error = 0; notice = 0 }) { Text(s(R.string.done)) } })
        }
    }
    @Composable private fun title(route: String): String = s(when (route) {
        "home" -> R.string.home; "new" -> R.string.new_shot; "presets" -> R.string.camera_preset; "rigs" -> R.string.rig_profiles; "maps" -> R.string.marker_maps
        "calibration" -> R.string.calibration; "recordings" -> R.string.recordings; "summary" -> R.string.summary; "settings" -> R.string.settings
        "generator" -> R.string.generator; "guide" -> R.string.guide; "about" -> R.string.about; "video" -> R.string.reference_video; else -> R.string.diagnostics
    })
    override fun onPause() { capture.pause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::capture.isInitialized) capture.resume(); if (installRequested) { installRequested = false; openCapture() } }
    override fun onDestroy() { capture.destroy(); scope.cancel(); super.onDestroy() }
}

@Composable fun s(id: Int): String = LocalContext.current.getString(id)
@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().align(androidx.compose.ui.Alignment.TopCenter).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
}
@Composable fun Input(label: Int, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val numeric = label in setOf(R.string.focal, R.string.sensor_width, R.string.sensor_height, R.string.resolution_width, R.string.resolution_height, R.string.focus, R.string.marker_size, R.string.min_area, R.string.max_error, R.string.correction, R.string.confidence, R.string.gap, R.string.sensor_period, R.string.first_id, R.string.last_id, R.string.board_columns, R.string.board_rows, R.string.square_size, R.string.board_marker_size)
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(s(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PresetSelector(label: String, values: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(values.getOrElse(selected) { "—" }, {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded, { expanded = false }) { values.forEachIndexed { index, text -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(index); expanded = false }) } }
    }
}
@Composable private fun Onboarding(done: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(R.string.onboard_1, R.string.onboard_2, R.string.onboard_3)
    val texts = listOf(R.string.onboard_1_body, R.string.onboard_2_body, R.string.onboard_3_body)
    Surface(Modifier.fillMaxSize(), color = RigBackground) { BoxWithConstraints(Modifier.fillMaxSize()) {
        val fallback = maxHeight < 650.dp || LocalConfiguration.current.fontScale > 1.25f
        val scroll = if (fallback) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().align(androidx.compose.ui.Alignment.TopCenter).then(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(16.dp)); Text("RIGTRACK", color = RigTeal, style = MaterialTheme.typography.labelLarge)
            OnboardingVisual(page); Text(s(titles[page]), style = MaterialTheme.typography.headlineLarge); Text(s(texts[page]), style = MaterialTheme.typography.bodyLarge); Text("${page + 1} / 3", color = RigTeal)
            PrimaryButton(s(if (page == 2) R.string.get_started else R.string.continue_action), Modifier.fillMaxWidth().testTag("onboarding_next")) { if (page == 2) done() else page++ }
            SecondaryButton(s(R.string.skip), Modifier.fillMaxWidth().testTag("onboarding_skip"), onClick=done)
        }
    } }
}

@Composable private fun OnboardingVisual(page: Int) {
    SectionCard("") {
        when (page) {
            0 -> RigDiagram()
            1 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusChip("ARCore"); Text("+"); StatusChip(s(R.string.markers)); Text("→"); StatusChip("6DoF")
            }
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusChip("RigTrack"); Text("→"); StatusChip("Blender")
            }
        }
    }
}


