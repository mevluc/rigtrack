package com.rigtrack.recording

import com.google.gson.GsonBuilder
import com.rigtrack.core.model.*
import com.rigtrack.core.math.*
import com.rigtrack.export.*
import java.io.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

class SessionWriter(val directory:File,val settings:ShotSettings,val startNs:Long,device:Map<String,Any?>,scope:CoroutineScope,private val snapshots:Map<String,Any?> = emptyMap(),private val onError:(String)->Unit) {
    private sealed interface Event {
        data class Ar(val s:ArPoseSample,val corrected:Rigid?,val confidence:Double):Event
        data class Imu(val s:ImuSample):Event
        data class Marker(val s:MarkerObservation,val known:Boolean,val decision:MarkerUseDecision?):Event
        data class Note(val ns:Long,val name:String,val detail:String):Event
    }
    private val queue=ArrayBlockingQueue<Event>(16384)
    private val gate=Any()
    @Volatile private var accepting=true
    @Volatile var failure:String?=null;private set
    val dropped=AtomicLong()
    val arCount=AtomicLong(); val imuCount=AtomicLong(); val markerCount=AtomicLong()
    private val gson=GsonBuilder().setPrettyPrinting().create()
    private val metadata=device.toMutableMap().apply { putAll(mapOf("format_version" to 1,"app_version" to com.rigtrack.BuildConfig.VERSION_NAME,"shot" to settings,"record_start_monotonic_ns" to startNs,"units" to "meters, radians, integer nanoseconds","clock_mapping" to "AR frame clock aligned by first frame callback receipt; offset estimate includes pipeline latency. Original timestamps preserved. No assumption of shared clocks.","coordinate_system" to "T_A_B maps B to A; AR camera +X right +Y up -Z forward. Blender world = Rx(+90deg) * anchor world; camera local axes unchanged.")) }
    private val writers=mutableMapOf<String,BufferedWriter>()
    private var arOffset:Long?=null
    private var lastAr=0L; private var firstAr=0L; private var lastFlush=System.nanoTime()
    private var trackingLostStart:Long?=null;private var lostNs=0L;private var longestLost=0L;private var losses=0;private var arGaps=0
    private val sensorFirst=mutableMapOf<Int,Long>();private val sensorLast=mutableMapOf<Int,Long>();private val sensorCounts=mutableMapOf<Int,Long>()
    private var errorSum=0.0;private var errorMax=0.0;private val ids=mutableSetOf<Int>();private var maxCorrection=0.0;private var maxAngle=0.0
    private val headers=linkedMapOf(
        "ar_pose.csv" to "timestamp_ns,received_elapsed_ns,android_camera_timestamp_ns,time_s,frame_index,tracking_state,tracking_failure_reason,raw_world_tx,raw_world_ty,raw_world_tz,raw_world_qx,raw_world_qy,raw_world_qz,raw_world_qw,anchor_tx,anchor_ty,anchor_tz,anchor_qx,anchor_qy,anchor_qz,anchor_qw,relative_tx,relative_ty,relative_tz,relative_qx,relative_qy,relative_qz,relative_qw,quality,valid,anchor_tracking",
        "phone_camera_fused.csv" to "timestamp_ns,time_s,tx,ty,tz,qx,qy,qz,qw,quality,valid",
        "film_camera_raw.csv" to "timestamp_ns,time_s,tx,ty,tz,qx,qy,qz,qw,quality,valid",
        "film_camera_fused.csv" to "timestamp_ns,time_s,tx,ty,tz,qx,qy,qz,qw,quality,valid,correction_translation_magnitude,correction_rotation_degrees,marker_confidence",
        "imu.csv" to "timestamp_ns,received_elapsed_ns,time_s,sensor_type,x,y,z,w_or_bias_x,bias_y,bias_z,accuracy,values_count",
        "intrinsics.csv" to "timestamp_ns,fx,fy,cx,cy,image_width,image_height",
        "markers.csv" to "timestamp_ns,image_timestamp_ns,received_elapsed_ns,time_s,marker_id,known_marker,size_m,corner0_x,corner0_y,corner1_x,corner1_y,corner2_x,corner2_y,corner3_x,corner3_y,rvec_x,rvec_y,rvec_z,tvec_x,tvec_y,tvec_z,camera_marker_tx,camera_marker_ty,camera_marker_tz,camera_marker_qx,camera_marker_qy,camera_marker_qz,camera_marker_qw,reprojection_error,pixel_area,view_angle,confidence,fx,fy,cx,cy,width,height,calibrated,mapped,valid_for_correction,used_for_correction,rejection_reason,innovation_translation_m,innovation_rotation_deg",
        "events.csv" to "timestamp_ns,time_s,event,detail"
    )
    private val worker=scope.launch(Dispatchers.IO) {
        try {
            check(directory.mkdirs()||directory.isDirectory)
            saveMetadata()
            saveJson("rig.json",settings.rig)
            snapshots.forEach{(name,value)->saveJson(name,value)}
            for((name,header) in headers) writers[name]=File(directory,name).bufferedWriter(Charsets.UTF_8,65536).apply {appendLine(header)}
            write(Event.Note(startNs,"RECORD_START","elapsedRealtimeNanos; physical cue onset is not measured"))
            while(accepting||queue.isNotEmpty()) {
                queue.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS)?.let(::write)
                if(System.nanoTime()-lastFlush>1_000_000_000) {writers.values.forEach{it.flush()};lastFlush=System.nanoTime()}
            }
        } catch(e:Exception) { failure=e.message?:e.javaClass.simpleName;accepting=false;onError("Recording I/O failed: $failure") }
        finally { writers.values.forEach{runCatching{it.close()}} }
    }
    private fun offer(e:Event) = synchronized(gate) { if(accepting&&!queue.offer(e)) {dropped.incrementAndGet();failure="Writer queue overflow; recording interrupted";accepting=false;onError(failure!!)} }
    fun ar(s:ArPoseSample,corrected:Rigid?,confidence:Double=0.0)=offer(Event.Ar(s,corrected,confidence))
    fun imu(s:ImuSample)=offer(Event.Imu(s))
    fun marker(s:MarkerObservation,known:Boolean,decision:MarkerUseDecision?=null)=offer(Event.Marker(s,known,decision))
    fun event(ns:Long,name:String,detail:String="")=offer(Event.Note(ns,name,detail))
    private fun line(name:String,values:List<Any?>) {writers.getValue(name).appendLine(Csv.row(*values.toTypedArray()))}
    private fun time(ns:Long)= (ns-startNs).toDouble()/1e9
    private fun write(e:Event) { when(e) {
        is Event.Note -> line("events.csv",listOf(e.ns,time(e.ns),e.name,e.detail))
        is Event.Imu -> {val s=e.s;line("imu.csv",listOf(s.timestampNs,s.receivedNs,time(s.timestampNs),s.type)+(0..5).map{s.values.getOrNull(it)}+listOf(s.accuracy,s.values.size));imuCount.incrementAndGet();sensorFirst.putIfAbsent(s.type,s.timestampNs);sensorLast[s.type]=s.timestampNs;sensorCounts[s.type]=(sensorCounts[s.type]?:0)+1}
        is Event.Ar -> {
            val s=e.s
            if(arOffset==null) {arOffset=s.receivedNs-s.timestampNs;updateMetadata(mapOf("ar_to_elapsed_estimated_offset_ns" to arOffset));firstAr=s.timestampNs;saveMetadata()}
            if(s.timestampNs<=lastAr)return
            if(lastAr!=0L&&s.timestampNs-lastAr>75_000_000)arGaps++
            lastAr=s.timestampNs;val n=arCount.getAndIncrement();val ts=time(s.timestampNs+arOffset!!);val rel=s.relative;val valid=s.valid;val quality=if(valid)s.qualityScore else 0.0
            if(!valid&&trackingLostStart==null){trackingLostStart=s.timestampNs;losses++}
            if(valid&&trackingLostStart!=null){val loss=s.timestampNs-trackingLostStart!!;lostNs+=loss;longestLost=maxOf(longestLost,loss);trackingLostStart=null}
            val empty=List<Any?>(7){null}
            line("ar_pose.csv",listOf(s.timestampNs,s.receivedNs,s.androidCameraTimestampNs,ts,n,s.state,s.failure)+Csv.pose(s.worldCamera)+(s.worldAnchor?.let(Csv::pose)?:empty)+(rel?.let(Csv::pose)?:empty)+listOf(quality,valid,s.anchorTracking))
            val intr=s.intrinsics;line("intrinsics.csv",listOf(s.timestampNs,intr.fx,intr.fy,intr.cx,intr.cy,intr.width,intr.height))
            val corrected=e.corrected?:rel
            val correction=if(corrected!=null&&rel!=null)corrected*rel.inverse() else Rigid()
            maxCorrection=maxOf(maxCorrection,correction.t.norm());maxAngle=maxOf(maxAngle,Math.toDegrees(correction.q.angle(Q())))
            for((name,p) in listOf("phone_camera_fused.csv" to corrected,"film_camera_raw.csv" to rel?.times(settings.rig.T_phoneCamera_filmCamera),"film_camera_fused.csv" to corrected?.times(settings.rig.T_phoneCamera_filmCamera))) {
                line(name,listOf(s.timestampNs,ts)+(p?.let(Csv::pose)?:empty)+listOf(quality,valid)+if(name=="film_camera_fused.csv")listOf(correction.t.norm(),Math.toDegrees(correction.q.angle(Q())),e.confidence) else emptyList())
            }
        }
        is Event.Marker -> {val s=e.s;val k=s.intrinsics;val d=e.decision;line("markers.csv",listOf(s.timestampNs,s.imageTimestampNs,s.receivedNs,arOffset?.let{time(s.timestampNs+it)},s.id,e.known,s.sizeM)+s.corners+listOf(s.rvec.x,s.rvec.y,s.rvec.z,s.tvec.x,s.tvec.y,s.tvec.z)+Csv.pose(s.T_camera_marker)+listOf(s.reprojectionError,s.pixelArea,s.viewAngle,s.confidence,k.fx,k.fy,k.cx,k.cy,k.width,k.height,s.calibrated,d?.mapped,d?.valid,d?.usedForCorrection,d?.rejectionReason?.name,d?.innovationTranslationM,d?.innovationRotationDeg));markerCount.incrementAndGet();ids+=s.id;errorSum+=s.reprojectionError;errorMax=maxOf(errorMax,s.reprojectionError)}
    } }
    fun saveJson(name:String,value:Any?) {val target=File(directory,name);val temp=File(directory,"$name.tmp");temp.writeText(gson.toJson(value),Charsets.UTF_8);if(!temp.renameTo(target)){check(!target.exists()||target.delete());check(temp.renameTo(target)){"Cannot replace $name"}}}
    fun updateMetadata(values:Map<String,Any?>) = synchronized(metadata) { metadata.putAll(values) }
    private fun saveMetadata() = saveJson("metadata.json", synchronized(metadata) { metadata.toMap() })
    suspend fun stop(ns:Long,extra:Map<String,Any?> = emptyMap(),extraMetadata:Map<String,Any?> = emptyMap()):File {
        event(ns,"RECORD_STOP");synchronized(gate){accepting=false};worker.join()
        if(trackingLostStart!=null){val d=lastAr-trackingLostStart!!;lostNs+=d;longestLost=maxOf(longestLost,d)}
        updateMetadata(extraMetadata + mapOf("record_stop_monotonic_ns" to ns,"duration_s" to time(ns),"writer_failure" to failure));saveMetadata()
        val rates=sensorCounts.mapValues{(type,count)->val span=(sensorLast.getValue(type)-sensorFirst.getValue(type))/1e9;if(span>0)(count-1)/span else 0.0}
        val diag=mutableMapOf<String,Any?>("duration_s" to time(ns),"ar_frames" to arCount.get(),"ar_average_fps" to if(lastAr>firstAr)(arCount.get()-1)*1e9/(lastAr-firstAr) else 0.0,"ar_gap_count" to arGaps,"imu_samples" to imuCount.get(),"sensor_average_hz" to rates,"marker_observations" to markerCount.get(),"unique_markers" to ids,"average_reprojection_error" to if(markerCount.get()>0)errorSum/markerCount.get() else null,"maximum_reprojection_error" to errorMax,"tracking_loss_events" to losses,"tracking_lost_duration_s" to lostNs/1e9,"longest_tracking_loss_s" to longestLost/1e9,"maximum_correction_distance_m" to maxCorrection,"maximum_correction_rotation_deg" to maxAngle,"dropped_writer_events" to dropped.get(),"writer_failure" to failure)
        diag.putAll(extra);saveJson("diagnostics.json",diag)
        TrackExporter.process(directory)
        val complete=File(directory.parentFile,directory.name.removeSuffix(".partial"));check(directory.renameTo(complete)){"Unable to finalize recording; partial retained"};return complete
    }
}
