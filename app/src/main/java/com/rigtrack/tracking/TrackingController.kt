package com.rigtrack.tracking

import android.content.Context
import android.hardware.camera2.*
import android.os.*
import android.util.Log
import com.google.ar.core.*
import com.rigtrack.BuildConfig
import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import com.rigtrack.data.*
import com.rigtrack.recording.*
import com.rigtrack.tracking.imu.*
import com.rigtrack.tracking.marker.*
import com.rigtrack.tracking.fusion.*
import kotlinx.coroutines.*
import org.opencv.core.Core
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** App-lifetime scope lets a lifecycle interruption finish flushing after the Activity is destroyed. */
object RecordingJobs {val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)}

class TrackingController(private val context:Context,val repo:Repository,val settings:ShotSettings,val detectorSettings:DetectorSettings,@Volatile var markerMap:MarkerMap,private val cameraMetadata:Map<String,Any?>,val cameraKey:String,private val notifyError:(String)->Unit) {
    @Volatile var latest:ArPoseSample?=null;private set
    private data class Detection(val sample:ArPoseSample,val observations:List<MarkerObservation>)
    @Volatile private var detection:Detection?=null
    val markers:List<MarkerObservation> get()=detection?.observations?:emptyList()
    @Volatile var markerDecisions:Map<Int,MarkerUseDecision> = emptyMap();private set
    @Volatile var writer:SessionWriter?=null;private set
    @Volatile var lastError="";private set
    @Volatile var calibratingMap=false
    @Volatile var markerMode="Aruco"
    @Volatile var stopping=false;private set
    val worldLock=WorldLock();val mapCalibration=MarkerMapCalibrator();val arRate=SampleRate()
    private val pendingScope=CoroutineScope(SupervisorJob()+Dispatchers.Default+CoroutineExceptionHandler{_,failure->error("Marker worker stopped: ${failure.message}")})
    private var lastMarkerNs=0L
    @Volatile private var originEpochNs=0L
    val missingImages=AtomicLong()
    private val calibration=repo.calibrations().lastOrNull{it.key==cameraKey}
    val markerWorker:MarkerWorker?=if(settings.synthetic)null else runCatching{MarkerWorker(pendingScope,detectorSettings,cameraKey,calibration,{markerMap},::onMarkers,::error)}.getOrElse{error("OpenCV initialization failed: ${it.message}");null}
    val imu=ImuRecorder(context){writer?.imu(it)}
    init{imu.start(detectorSettings.sensorPeriodUs);log("session_created",cameraKey);val missing=listOf(android.hardware.Sensor.TYPE_GYROSCOPE to "gyroscope",android.hardware.Sensor.TYPE_ACCELEROMETER to "accelerometer").filter{(type,_)->imu.sensors.none{it.type==type}};if(missing.isNotEmpty())error("Unavailable raw sensors: ${missing.joinToString{it.second}}")}
    private fun error(message:String){lastError=message;log("error",message);notifyError(message)}
    fun arFailure(message:String){latest=latest?.copy(state="PAUSED",failure=message,qualityScore=0.0);writer?.event(SystemClock.elapsedRealtimeNanos(),"AR_SESSION_EXCEPTION",message);error(message)}
    fun log(event:String,detail:String=""){if(BuildConfig.DEBUG||detectorSettings.debug||event=="error")Log.i("RigTrack","event=$event elapsed_ns=${SystemClock.elapsedRealtimeNanos()} detail=$detail")}
    fun frame(frame:Frame?,sample:ArPoseSample){
        val previous=latest;arRate.add(sample.timestampNs)
        val recentMarker=markers.filter{sample.timestampNs-it.timestampNs in 0..500_000_000L&&markerMap.references.any{r->r.id==it.id}}.maxOfOrNull{it.confidence}?:0.0
        val quality=if(!sample.valid)0.0 else 70+10*(arRate.hz()/30).coerceIn(0.0,1.0)+if(worldLock.active)20*recentMarker else 0.0
        latest=sample.copy(qualityScore=quality)
        if(previous?.state!=sample.state){log("tracking_state",sample.state);writer?.event(sample.receivedNs,if(sample.valid)"TRACKING_RECOVERED" else "TRACKING_LOST",sample.failure)}
        if(previous?.worldAnchor==null&&sample.worldAnchor!=null)log("origin_set")
        val corrected=worldLock.apply(sample,detectorSettings.correctionStrength,detectorSettings)
        writer?.ar(latest!!,corrected,worldLock.confidence)
        if(frame!=null&&markerWorker!=null&&sample.timestampNs-lastMarkerNs>=1_000_000_000L/detectorSettings.fps){
            lastMarkerNs=sample.timestampNs
            try{markerWorker.submit(frame.acquireCameraImage(),sample)}catch(_:com.google.ar.core.exceptions.NotYetAvailableException){missingImages.incrementAndGet()}catch(e:Exception){error("CPU image unavailable: ${e.message}")}
        }
    }
    private fun onMarkers(sample:ArPoseSample,found:List<MarkerObservation>){
        if(sample.receivedNs<originEpochNs)return
        detection=Detection(sample,found)
        val decisions=worldLock.observe(sample,found,markerMap,detectorSettings);markerDecisions=decisions
        for(o in found){val decision=decisions[o.id];writer?.marker(o,markerMap.references.any{it.id==o.id},decision);if(detectorSettings.debug)log(if(decision?.usedForCorrection==true)"marker_used" else "marker_rejected","id=${o.id} error=${o.reprojectionError} reason=${decision?.rejectionReason}")}
        if(calibratingMap)mapCalibration.add(sample,found,detectorSettings)
    }
    fun relocalizationWorldOrigin():Rigid {
        check(writer==null&&!stopping){"Relocalize before recording"}
        val observation=detection?:throw IllegalStateException("No marker observations")
        check(SystemClock.elapsedRealtimeNanos()-observation.sample.receivedNs<500_000_000){"Show a mapped marker"}
        val camera=worldLock.cameraFromMap(observation.observations,markerMap,detectorSettings.minConfidence)?:throw IllegalStateException("No good mapped marker")
        return observation.sample.worldCamera*camera.inverse()
    }
    fun originReset(){originEpochNs=SystemClock.elapsedRealtimeNanos();calibratingMap=false;worldLock.reset();mapCalibration.clear();if(markerMap.references.isNotEmpty())worldLock.active=false}
    fun start():SessionWriter{
        check(writer==null&&!stopping){"Recording is busy"};check(latest?.valid==true){"Wait for TRACKING and SET ORIGIN"}
        check(!calibratingMap){"Save or finish marker calibration before recording"}
        check(markerWorker?.calibrationMode!=true){"Finish camera calibration, then open New Shot to record"}
        val ns=SystemClock.elapsedRealtimeNanos()
        val device=mutableMapOf<String,Any?>("device_manufacturer" to Build.MANUFACTURER,"device_model" to Build.MODEL,"android_version" to Build.VERSION.RELEASE,"sdk" to Build.VERSION.SDK_INT,"opencv_version" to Core.VERSION,"arcore_version" to runCatching{context.packageManager.getPackageInfo("com.google.ar.core",0).versionName}.getOrNull(),"camera_configuration" to cameraMetadata,"camera_profile_key" to cameraKey,"sensor_availability" to imu.metadata(),"sensor_errors" to imu.registrationErrors,"startup_elapsed_ns" to ns,"startup_uptime_ms" to SystemClock.uptimeMillis(),"startup_wall_time_ms" to System.currentTimeMillis(),"marker_world_lock_active" to worldLock.active,"marker_dictionary" to detectorSettings.dictionary,"marker_dictionary_name" to MarkerDictionaries.label(detectorSettings.dictionary),"detector_settings" to detectorSettings,"calibrated_lens" to (calibration!=null),"sync_semantics" to "Timestamp is cue request. Audio/visual/haptic onset latency not measured. Align with recorded film audio; multiple cues can estimate clock drift.")
        val created=SessionWriter(File(repo.sessions,"${System.currentTimeMillis()}.partial"),settings,ns,device,RecordingJobs.scope,mapOf("calibration.json" to calibration,"marker_map.json" to markerMap)){error(it)}
        writer=created;log("record_started");return created
    }
    suspend fun stop(interrupted:Boolean=false,requestedNs:Long=SystemClock.elapsedRealtimeNanos(),finalizeAuxiliary:(suspend (SessionWriter)->Map<String,Any?>)={emptyMap()}):File? {
        val w=synchronized(this){if(stopping)return null;val current=writer?:return null;writer=null;stopping=true;current}
        try {
            if(interrupted)w.event(requestedNs,"RECORDING_INTERRUPTED","App paused or recording pipeline stopped")
            val auxiliary=runCatching{finalizeAuxiliary(w)}.getOrElse{failure->w.event(SystemClock.elapsedRealtimeNanos(),"REFERENCE_VIDEO_FAILED",failure.toString());mapOf("reference_video_available" to false,"reference_video_error" to failure.toString())}
            val marker=markerWorker
            val file=w.stop(requestedNs,mapOf("marker_detection_fps" to (marker?.rate?.hz()?:0.0),"marker_input_fps" to (marker?.inputRate?.hz()?:0.0),"marker_frames_submitted" to (marker?.submitted?.get()?:0),"marker_frames_processed" to (marker?.processed?.get()?:0),"dropped_marker_frames" to (marker?.dropped?.get()?:0),"cpu_images_unavailable" to missingImages.get(),"marker_dictionary" to detectorSettings.dictionary,"marker_dictionary_name" to MarkerDictionaries.label(detectorSettings.dictionary),"last_cpu_image_width" to (marker?.lastImageWidth?:0),"last_cpu_image_height" to (marker?.lastImageHeight?:0),"last_cpu_image_timestamp_ns" to (marker?.lastImageTimestampNs?:0),"last_marker_pose_timestamp_ns" to (marker?.lastPoseTimestampNs?:0),"last_marker_candidates" to (marker?.lastDetectedCandidateCount?:0),"last_marker_rejected_candidates" to (marker?.lastRejectedCandidateCount?:0),"last_marker_ids" to (marker?.lastDetectedIds?:emptyList<Int>()),"last_marker_processing_ms" to (marker?.lastProcessingMs?:0.0),"average_marker_processing_ms" to (marker?.averageProcessingMs?:0.0),"cpu_intrinsics_matched_image" to (marker?.lastIntrinsicsMatchedImage?:false),"last_marker_exception" to marker?.lastException.orEmpty(),"world_lock_corrections" to worldLock.corrections,"world_lock_used_observations" to worldLock.usedObservations,"marker_relocks" to worldLock.relocks,"rejected_corrections" to worldLock.rejected,"marker_rejection_reasons" to worldLock.rejectionCounts.mapKeys{it.key.name},"moved_marker_ids" to worldLock.movedMarkerIds,"uncalibrated_lens" to (calibration==null)),auxiliary)
            log("record_stopped",file.name);return file
        }finally{stopping=false}
    }
    suspend fun close(){imu.stop();markerWorker?.close();pendingScope.cancel()}
    companion object {
        fun cameraMetadata(context:Context,session:Session):Map<String,Any?> {
            val c=session.cameraConfig;val manager=context.getSystemService(Context.CAMERA_SERVICE)as CameraManager
            val meta=mutableMapOf<String,Any?>("camera_id" to c.cameraId,"cpu_width" to c.imageSize.width,"cpu_height" to c.imageSize.height,"texture_width" to c.textureSize.width,"texture_height" to c.textureSize.height,"fps_lower" to c.fpsRange.lower,"fps_upper" to c.fpsRange.upper)
            runCatching{val k=manager.getCameraCharacteristics(c.cameraId);meta["sensor_physical_size_mm"]=k[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.let{listOf(it.width,it.height)};meta["pixel_array_size"]=k[CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE]?.let{listOf(it.width,it.height)};meta["focal_lengths_mm"]=k[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.toList();meta["timestamp_source"]=k[CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE];if(Build.VERSION.SDK_INT>=28)meta["lens_distortion"]=k[CameraCharacteristics.LENS_DISTORTION]?.toList()}
            return meta
        }
        fun cameraKey(meta:Map<String,Any?>)="${Build.MANUFACTURER}/${Build.MODEL}/${meta["camera_id"]}/${meta["cpu_width"]}x${meta["cpu_height"]}/${meta["fps_lower"]}-${meta["fps_upper"]}"
    }
}
