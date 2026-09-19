package com.rigtrack.recording

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import com.google.ar.core.RecordingConfig
import com.google.ar.core.Session
import com.rigtrack.core.model.ReferenceVideoQuality
import java.io.File

enum class ReferenceVideoStatus { OFF, IDLE, RECORDING, FINALIZING, READY, FAILED }

data class ReferenceVideoState(
    val status: ReferenceVideoStatus = ReferenceVideoStatus.IDLE,
    val error: String? = null,
)

/** Records the ARCore-owned camera stream. It never opens a second camera session. */
class ReferenceVideoRecorder(
    private val session: Session?,
    private val quality: ReferenceVideoQuality,
    private val rotationDegrees: () -> Int,
) {
    private var output: File? = null
    private var startNs: Long? = null
    private var recordingRotation: Int? = null

    fun start(writer: SessionWriter): ReferenceVideoState {
        if (quality == ReferenceVideoQuality.OFF) {
            writer.updateMetadata(unavailableMetadata())
            return ReferenceVideoState(ReferenceVideoStatus.OFF)
        }
        val arSession = session ?: return fail(writer, "ARCore session recording is unavailable")
        return runCatching {
            writer.directory.mkdirs()
            val file = File(writer.directory, FILENAME)
            val rotation = rotationDegrees()
            val config = RecordingConfig(arSession)
                .setMp4DatasetUri(Uri.fromFile(file))
                .setAutoStopOnPause(false)
                .setRecordingRotation(rotation)
            arSession.startRecording(config)
            val now = SystemClock.elapsedRealtimeNanos()
            output = file; startNs = now; recordingRotation = rotation
            val camera = arSession.cameraConfig
            val expectedWidth=if(rotation==90||rotation==270)camera.imageSize.height else camera.imageSize.width
            val expectedHeight=if(rotation==90||rotation==270)camera.imageSize.width else camera.imageSize.height
            writer.event(now, "REFERENCE_VIDEO_START", "requested=${quality.persisted}; arcore_session_recording")
            writer.updateMetadata(mapOf(
                "referenceVideoAvailable" to false,
                "referenceVideoFilename" to FILENAME,
                "reference_video_enabled" to true,
                "reference_video_requested_quality" to quality.persisted,
                "reference_video_width" to camera.imageSize.width,
                "reference_video_height" to camera.imageSize.height,
                "reference_video_fps" to camera.fpsRange.upper,
                "reference_video_codec" to "video/avc",
                "reference_video_rotation_degrees" to rotation,
                "reference_video_orientation" to orientation(expectedWidth,expectedHeight),
                "reference_video_start_timestamp_ns" to now,
            ))
            ReferenceVideoState(ReferenceVideoStatus.RECORDING)
        }.getOrElse { fail(writer, it.toString()) }
    }

    fun finalizingState() = if (startNs != null) ReferenceVideoState(ReferenceVideoStatus.FINALIZING) else null

    fun stop(writer: SessionWriter, stopRequestNs: Long): Pair<ReferenceVideoState, Map<String, Any?>> {
        val file = output ?: return ReferenceVideoState(if (quality == ReferenceVideoQuality.OFF) ReferenceVideoStatus.OFF else ReferenceVideoStatus.FAILED) to unavailableMetadata()
        val started = startNs ?: return ReferenceVideoState(ReferenceVideoStatus.FAILED) to unavailableMetadata()
        return runCatching {
            session?.stopRecording()
            val finalizedNs = SystemClock.elapsedRealtimeNanos()
            writer.event(stopRequestNs, "REFERENCE_VIDEO_STOP", "finalized_elapsed_ns=$finalizedNs")
            val actual = inspect(file)
            val available = file.isFile && file.length() > 0
            val duration = (actual.durationNs ?: (stopRequestNs - started).coerceAtLeast(0L)) / 1e9
            val metadata = mapOf(
                "referenceVideoAvailable" to available,
                "referenceVideoFilename" to FILENAME,
                "referenceVideoDuration" to duration,
                "referenceVideoResolution" to "${actual.width ?: 0}x${actual.height ?: 0}",
                "referenceVideoFps" to actual.fps,
                "reference_video_enabled" to true,
                "reference_video_width" to actual.width,
                "reference_video_height" to actual.height,
                "reference_video_fps" to actual.fps,
                "reference_video_codec" to actual.codec,
                "reference_video_rotation_degrees" to recordingRotation,
                "reference_video_orientation" to orientation(actual.width,actual.height),
                "reference_video_duration" to duration,
                "reference_video_start_timestamp_ns" to started,
                "reference_video_stop_timestamp_ns" to stopRequestNs,
                "reference_video_finalize_timestamp_ns" to finalizedNs,
            )
            ReferenceVideoState(if (available) ReferenceVideoStatus.READY else ReferenceVideoStatus.FAILED) to metadata
        }.getOrElse { failure ->
            writer.event(SystemClock.elapsedRealtimeNanos(), "REFERENCE_VIDEO_FAILED", failure.toString())
            val partial = File(file.parentFile, PARTIAL_FILENAME)
            if (file.exists()) file.renameTo(partial)
            ReferenceVideoState(ReferenceVideoStatus.FAILED, failure.toString()) to (unavailableMetadata() + mapOf("reference_video_error" to failure.toString()))
        }.also { output = null; startNs = null; recordingRotation = null }
    }

    private fun fail(writer: SessionWriter, message: String): ReferenceVideoState {
        writer.event(SystemClock.elapsedRealtimeNanos(), "REFERENCE_VIDEO_FAILED", message)
        writer.updateMetadata(unavailableMetadata() + mapOf("reference_video_error" to message))
        output = null; startNs = null; recordingRotation = null
        return ReferenceVideoState(ReferenceVideoStatus.FAILED, message)
    }

    private fun unavailableMetadata() = mapOf(
        "referenceVideoAvailable" to false,
        "referenceVideoFilename" to FILENAME,
        "reference_video_enabled" to (quality != ReferenceVideoQuality.OFF),
        "reference_video_requested_quality" to quality.persisted,
    )

    private data class Actual(val width: Int?, val height: Int?, val fps: Float?, val codec: String?, val durationNs: Long?)
    private fun inspect(file: File): Actual {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var chosen: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { chosen = format; break }
            }
            Actual(
                chosen?.integer(MediaFormat.KEY_WIDTH), chosen?.integer(MediaFormat.KEY_HEIGHT),
                chosen?.float(MediaFormat.KEY_FRAME_RATE), chosen?.getString(MediaFormat.KEY_MIME),
                chosen?.long(MediaFormat.KEY_DURATION)?.times(1_000L),
            )
        } finally { extractor.release() }
    }

    private fun MediaFormat.integer(key: String) = if (containsKey(key)) getInteger(key) else null
    private fun MediaFormat.float(key: String) = if (containsKey(key)) runCatching { getFloat(key) }.getOrElse { getInteger(key).toFloat() } else null
    private fun MediaFormat.long(key: String) = if (containsKey(key)) getLong(key) else null
    private fun orientation(width:Int?,height:Int?)=when {width==null||height==null->"unknown";width>height->"landscape";height>width->"portrait";else->"square"}

    companion object {
        const val FILENAME = "reference_video.mp4"
        const val PARTIAL_FILENAME = "reference_video.partial.mp4"
        fun estimatedBytes(quality: ReferenceVideoQuality, minutes: Int = 10): Long = when (quality) {
            ReferenceVideoQuality.OFF -> 0L
            ReferenceVideoQuality.P720 -> 40L * 1024 * 1024 * minutes
            ReferenceVideoQuality.P1080 -> 90L * 1024 * 1024 * minutes
        }
    }
}
