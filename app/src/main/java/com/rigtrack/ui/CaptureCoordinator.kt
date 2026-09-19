package com.rigtrack.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.*
import com.rigtrack.core.model.*
import com.rigtrack.data.*
import com.rigtrack.recording.*
import com.rigtrack.tracking.*
import com.rigtrack.tracking.arcore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** Owns the original ARCore/IMU pipeline; Compose only observes and sends commands. */
class CaptureCoordinator(private val activity: Activity, private val repo: Repository,
    private val scope: CoroutineScope, private val onError: (Throwable) -> Unit) {
    var controller: TrackingController? = null; private set
    var preview: ArPreview? = null; private set
    var overlay: MarkerOverlay? = null; private set
    private var session: Session? = null
    private var synthetic: SyntheticTracker? = null
    private var referenceVideo: ReferenceVideoRecorder? = null
    var referenceVideoState by mutableStateOf(ReferenceVideoState()); private set
    private fun publishReferenceVideoState(value: ReferenceVideoState) {
        if (Looper.myLooper() == Looper.getMainLooper()) referenceVideoState = value
        else activity.runOnUiThread { referenceVideoState = value }
    }
    private var relocalizing = false
    private var syncCount = 0
    private var motion = "Circle"
    private val stopMutex = Mutex()
    @Volatile private var lifecyclePaused = false
    private var lifecycleJob: Job? = null
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 95) }.getOrNull()

    fun open(settings: ShotSettings, detector: DetectorSettings, map: MarkerMap, mode: String): FrameLayout {
        check(controller == null)
        val effectiveDetector = if (mode == "map" || map.references.isNotEmpty() || map.markerSizesM.isNotEmpty()) detector.copy(dictionary = map.dictionary) else detector
        var meta: Map<String, Any?> = mapOf("mode" to "synthetic")
        if (!settings.synthetic) {
            check(!ArCoreApk.getInstance().checkAvailability(activity).isUnsupported) { "AR_UNSUPPORTED" }
            val s = Session(activity); session = s
            val configs = s.getSupportedCameraConfigs(CameraConfigFilter(s))
            val wanted = if (effectiveDetector.preferredFps == 0) 60 else effectiveDetector.preferredFps
            val quality = settings.referenceVideoQuality ?: ReferenceVideoQuality.P720
            val matchingFps = configs.filter { it.fpsRange.upper == wanted }.ifEmpty { configs }
            val chosen = if (quality == ReferenceVideoQuality.OFF) {
                matchingFps.minByOrNull { it.imageSize.width * it.imageSize.height }
            } else matchingFps.minByOrNull {
                val size = it.imageSize
                abs(size.width.toLong() * size.height - quality.targetWidth.toLong() * quality.targetHeight) +
                    (abs(size.width.toDouble() / size.height - quality.targetWidth.toDouble() / quality.targetHeight) * 1_000_000).toLong()
            } ?: configs.maxByOrNull { it.fpsRange.upper }
            if (chosen != null) s.cameraConfig = chosen
            s.configure(Config(s).apply {
                focusMode = Config.FocusMode.AUTO; planeFindingMode = Config.PlaneFindingMode.DISABLED
                lightEstimationMode = Config.LightEstimationMode.DISABLED; updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            })
            meta = TrackingController.cameraMetadata(activity, s) + mapOf("available_configurations" to configs.map {
                mapOf("camera_id" to it.cameraId, "width" to it.imageSize.width, "height" to it.imageSize.height,
                    "fps_min" to it.fpsRange.lower, "fps_max" to it.fpsRange.upper)
            })
            s.resume()
            referenceVideo = ReferenceVideoRecorder(s, quality) { referenceVideoRotation(activity, s.cameraConfig.cameraId) }
        }
        val ctl = TrackingController(activity, repo, settings, effectiveDetector, map, meta, TrackingController.cameraKey(meta)) {
            activity.runOnUiThread { onError(IllegalStateException(it)) }
        }
        controller = ctl; syncCount = 0; motion = settings.motion; publishReferenceVideoState(ReferenceVideoState())
        val viewport = FrameLayout(activity).apply { setBackgroundColor(Color.rgb(12, 28, 37)) }
        if (settings.synthetic) {
            synthetic = SyntheticTracker(scope) { ctl.frame(null, it) }.also { it.start(motion) }
        } else {
            val markerOverlay = MarkerOverlay(activity); overlay = markerOverlay
            preview = ArPreview(activity, session!!, { frame, sample ->
                ctl.frame(frame, sample); markerOverlay.update(frame, sample, ctl.markers, ctl.markerDecisions)
            }, { error ->
                ctl.arFailure(error)
                RecordingJobs.scope.launch { runCatching { stop(true) }.onFailure { activity.runOnUiThread { onError(it) } } }
            }, { ctl.worldLock.active = relocalizing; relocalizing = false })
            viewport.addView(preview, FrameLayout.LayoutParams(-1, -1))
            viewport.addView(markerOverlay, FrameLayout.LayoutParams(-1, -1)); preview?.onResume()
            ctl.markerWorker?.calibrationMode = mode == "calibration"
        }
        return viewport
    }
    fun origin() {
        val c = controller ?: return
        check(c.writer == null && !c.stopping) { "Stop recording before changing origin" }
        c.originReset(); relocalizing = false
        if (synthetic != null) synthetic?.setOrigin() else preview?.setOrigin()
    }
    fun relocalize() {
        val c = controller ?: return
        val worldOrigin = c.relocalizationWorldOrigin(); c.originReset(); relocalizing = true; preview?.setOrigin(worldOrigin)
    }
    fun start() {
        check(repo.sessions.usableSpace > 100L * 1024 * 1024) { "STORAGE_CRITICAL" }
        val writer = controller?.start() ?: return
        publishReferenceVideoState(referenceVideo?.start(writer)
            ?: if ((controller?.settings?.referenceVideoQuality ?: ReferenceVideoQuality.P720) == ReferenceVideoQuality.OFF) ReferenceVideoState(ReferenceVideoStatus.OFF)
            else ReferenceVideoState(ReferenceVideoStatus.FAILED, "ARCore reference recording unavailable"))
        if (referenceVideo == null && referenceVideoState.status == ReferenceVideoStatus.FAILED) {
            writer.event(SystemClock.elapsedRealtimeNanos(), "REFERENCE_VIDEO_FAILED", referenceVideoState.error.orEmpty())
            writer.updateMetadata(mapOf("referenceVideoAvailable" to false,"reference_video_enabled" to true,"reference_video_requested_quality" to (controller?.settings?.referenceVideoQuality ?: ReferenceVideoQuality.P720).persisted,"reference_video_error" to referenceVideoState.error))
        }
    }
    suspend fun stop(interrupted: Boolean = false): File? = controller?.let { stopController(it, interrupted) }
    private suspend fun stopController(target: TrackingController, interrupted: Boolean): File? = stopMutex.withLock {
        val stopRequestNs = SystemClock.elapsedRealtimeNanos()
        referenceVideo?.finalizingState()?.let(::publishReferenceVideoState)
        target.stop(interrupted, stopRequestNs) { writer ->
            val result = referenceVideo?.stop(writer, stopRequestNs)
            if (result == null) emptyMap() else { publishReferenceVideoState(result.first); result.second }
        }
    }
    fun sync(sound: Boolean, haptic: Boolean) {
        val c = controller ?: return; val writer = c.writer ?: error("Start recording first")
        syncCount++
        writer.event(SystemClock.elapsedRealtimeNanos(), "SYNC_%03d".format(Locale.US, syncCount),
            "cue_request; unmeasured audio/visual/haptic onset latency")
        if (sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        if (haptic) {
            @Suppress("DEPRECATION") val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            else { @Suppress("DEPRECATION") vibrator.vibrate(60) }
        }
        c.log("sync", "$syncCount")
    }
    fun pause() {
        lifecyclePaused = true
        val c = controller
        val currentSession = session
        preview?.onPause(); c?.imu?.stop(); synthetic?.stop()
        lifecycleJob = RecordingJobs.scope.launch {
            if (c?.writer != null || c?.stopping == true) {
                runCatching { stopController(c, true) }.onFailure { activity.runOnUiThread { onError(it) } }
            }
            withContext(Dispatchers.Main.immediate) {
                if (lifecyclePaused && session === currentSession) runCatching { currentSession?.pause() }
            }
        }
    }
    fun resume() {
        lifecyclePaused = false
        val pending = lifecycleJob
        lifecycleJob = RecordingJobs.scope.launch {
            pending?.join()
            if (!lifecyclePaused) withContext(Dispatchers.Main.immediate) {
                if (!lifecyclePaused) {
                    runCatching {
                        session?.resume(); preview?.onResume(); controller?.let { it.imu.start(it.detectorSettings.sensorPeriodUs) }
                        synthetic?.start(motion)
                    }.onFailure(onError)
                }
            }
        }
    }
    fun close() {
        lifecyclePaused = true
        synthetic?.stop(); synthetic = null; preview?.onPause(); preview = null
        val old = session; session = null; overlay = null
        val c = controller; controller = null
        val pending = lifecycleJob
        lifecycleJob = RecordingJobs.scope.launch {
            pending?.join()
            try {
                if (c != null) stopController(c, true)
                c?.close()
            } finally {
                withContext(Dispatchers.Main.immediate) { runCatching { old?.pause() } }
                old?.close(); referenceVideo = null
            }
        }
    }
    fun destroy() { close(); tone?.release() }

    @Suppress("DEPRECATION")
    private fun referenceVideoRotation(context: Context, cameraId: String): Int {
        val displayDegrees = when (activity.windowManager.defaultDisplay.rotation) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val sensor = manager.getCameraCharacteristics(cameraId)[android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION] ?: 90
        return (sensor - displayDegrees + 360) % 360
    }
}
