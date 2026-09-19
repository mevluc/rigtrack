package com.rigtrack.tracking.marker

import android.media.Image
import android.os.SystemClock
import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import com.rigtrack.tracking.calibration.*
import com.rigtrack.tracking.imu.SampleRate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.opencv.core.*
import org.opencv.calib3d.Calib3d
import org.opencv.objdetect.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

class MarkerWorker(scope:CoroutineScope,val settings:DetectorSettings,private val cameraKey:String,private val profile:CalibrationProfile?,private val map:()->MarkerMap,private val result:(ArPoseSample,List<MarkerObservation>)->Unit,private val error:(String)->Unit) {
    private data class Input(val image:Image,val sample:ArPoseSample)
    val submitted=AtomicLong();val processed=AtomicLong();val dropped=AtomicLong();private val processingNs=AtomicLong();val rate=SampleRate();val inputRate=SampleRate()
    val averageProcessingMs:Double get()=processed.get().takeIf{it>0}?.let{processingNs.get()/it/1e6}?:0.0
    @Volatile var lastImageWidth=0;private set
    @Volatile var lastImageHeight=0;private set
    @Volatile var lastImageTimestampNs=0L;private set
    @Volatile var lastPoseTimestampNs=0L;private set
    @Volatile var lastDetectedCandidateCount=0;private set
    @Volatile var lastRejectedCandidateCount=0;private set
    @Volatile var lastDetectedIds:List<Int> = emptyList();private set
    @Volatile var lastProcessingMs=0.0;private set
    @Volatile var lastIntrinsicsMatchedImage=false;private set
    @Volatile var lastException="";private set
    val calibration=CharucoCalibration(settings,cameraKey)
    @Volatile var calibrationMode=false
    @Volatile var boardReferenceMode=false
    private val queue=Channel<Input>(Channel.CONFLATED,onUndeliveredElement={it.image.close();dropped.incrementAndGet()})
    private val job=scope.launch(Dispatchers.Default){
        var detector:ArucoDetector?=null
        try {
            val dictionary=Objdetect.getPredefinedDictionary(settings.dictionary)
            val params=DetectorParameters().apply{set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)}
            detector=ArucoDetector(dictionary,params)
            for(input in queue){try{process(input,detector);lastException=""}catch(e:Exception){lastException="${e.javaClass.simpleName}: ${e.message}";error("Marker detector: $lastException")}finally{input.image.close()}}
        }finally{detector?.clear()}
    }
    // Also runs if the scope is cancelled before the coroutine ever starts.
    init {job.invokeOnCompletion{queue.cancel();calibration.close()}}
    fun submit(image:Image,sample:ArPoseSample){submitted.incrementAndGet();inputRate.add(sample.timestampNs);val sent=queue.trySend(Input(image,sample));if(sent.isFailure){image.close();dropped.incrementAndGet()}}
    suspend fun close(){queue.close();job.join()}
    private fun process(input:Input,detector:ArucoDetector){
        val began=SystemClock.elapsedRealtimeNanos()
        val image=input.image;val sample=input.sample;val plane=image.planes[0];val crop=image.cropRect
        val width=crop.width();val height=crop.height();val gray=Mat(height,width,CvType.CV_8UC1)
        val ids=Mat();val corners=mutableListOf<Mat>();val rejected=mutableListOf<Mat>()
        try {
            val bytes=CpuImage.copyLuma(plane.buffer,width,height,plane.rowStride,plane.pixelStride,crop.left,crop.top)
            gray.put(0,0,bytes)
            val raw=sample.intrinsics
            val k=CpuImage.croppedIntrinsics(raw,image.width,image.height,crop.left,crop.top,width,height)
            lastImageWidth=width;lastImageHeight=height;lastImageTimestampNs=image.timestamp;lastPoseTimestampNs=sample.timestampNs
            lastIntrinsicsMatchedImage=raw.width==image.width&&raw.height==image.height
            val usable=profile?.takeIf{it.key==cameraKey&&it.intrinsics.width==width&&it.intrinsics.height==height&&crop.left==0&&crop.top==0}
            if(calibrationMode)calibration.observe(gray,k)
            detector.detectMarkers(gray,corners,ids,rejected)
            lastDetectedCandidateCount=ids.rows();lastRejectedCandidateCount=rejected.size
            lastDetectedIds=(0 until ids.rows()).map{ids.get(it,0)[0].toInt()}
            val found=mutableListOf<MarkerObservation>()
            val refs=map().references.associateBy{it.id}
            // Board markers have a different size from standalone markers. Board mode uses only
            // the joint ChArUco solution, preventing accidental 150 mm estimates for 30 mm cells.
            if(!boardReferenceMode)for(i in 0 until ids.rows()){
                val id=ids.get(i,0)[0].toInt();val size=refs[id]?.sizeM?:map().markerSizesM[id]?:settings.markerSizeMm/1000
                val pts=(0..3).map{j->corners[i].get(0,j).let{Point(it[0],it[1])}}
                estimate(sample,image.timestamp,id,size,pts,k,usable)?.let{found+=it}
            }
            if(boardReferenceMode)calibration.boardPose(gray,k,usable,sample,image.timestamp)?.let{found+=it}
            rate.add(SystemClock.elapsedRealtimeNanos());result(sample,found)
        }finally{
            val duration=SystemClock.elapsedRealtimeNanos()-began;processingNs.addAndGet(duration);processed.incrementAndGet();lastProcessingMs=duration/1e6
            gray.release();ids.release();corners.forEach{it.release()};rejected.forEach{it.release()}
        }
    }
    private fun estimate(s:ArPoseSample,imageNs:Long,id:Int,size:Double,points:List<Point>,k:Intrinsics,profile:CalibrationProfile?):MarkerObservation? {
        val h=size/2
        val objects=MatOfPoint3f(Point3(-h,h,0.0),Point3(h,h,0.0),Point3(h,-h,0.0),Point3(-h,-h,0.0))
        val pixels=MatOfPoint2f(*points.toTypedArray())
        return try{PoseEstimator.solve(objects,pixels,profile?.intrinsics?:k,profile?.distortion?:emptyList(),Calib3d.SOLVEPNP_IPPE_SQUARE)?.let{p->
            val area=abs(points.indices.sumOf{i->val a=points[i];val b=points[(i+1)%4];a.x*b.y-b.x*a.y})/2
            val edge=points.minOf{minOf(it.x,it.y,k.width-it.x,k.height-it.y)}
            val confidence=(1-p.error/settings.maxError).coerceIn(0.0,1.0)*minOf(1.0,area/(settings.minPixelArea*4))*cos(Math.toRadians(p.angle)).coerceAtLeast(0.0)*minOf(1.0,edge/30).coerceAtLeast(0.0)*(if(profile==null).85 else 1.0)
            MarkerObservation(s.timestampNs,imageNs,SystemClock.elapsedRealtimeNanos(),id,size,points.flatMap{listOf(it.x,it.y)},p.rvec,p.tvec,p.T_arCamera_object,p.error,area,p.angle,confidence,profile?.intrinsics?:k,profile!=null)
        }}finally{objects.release();pixels.release()}
    }
}

object PoseEstimator {
    data class Result(val rvec:V3,val tvec:V3,val T_arCamera_object:Rigid,val error:Double,val angle:Double)
    fun matrix(k:Intrinsics)=Mat.eye(3,3,CvType.CV_64F).apply{put(0,0,k.fx);put(1,1,k.fy);put(0,2,k.cx);put(1,2,k.cy)}
    fun solve(objects:MatOfPoint3f,pixels:MatOfPoint2f,k:Intrinsics,distortion:List<Double>,method:Int=Calib3d.SOLVEPNP_ITERATIVE):Result? {
        val camera=matrix(k);val dist=MatOfDouble(*distortion.ifEmpty{List(5){0.0}}.toDoubleArray());val r=Mat();val t=Mat();val rotation=Mat();val projected=MatOfPoint2f()
        try {
            if(!Calib3d.solvePnP(objects,pixels,camera,dist,r,t,false,method))return null
            val rv=V3(r.get(0,0)[0],r.get(1,0)[0],r.get(2,0)[0]);val tv=V3(t.get(0,0)[0],t.get(1,0)[0],t.get(2,0)[0]);if(tv.z<=0)return null
            Calib3d.Rodrigues(r,rotation);val m=Rigid().matrix();for(y in 0..2)for(x in 0..2)m[y*4+x]=rotation.get(y,x)[0];m[3]=tv.x;m[7]=tv.y;m[11]=tv.z
            val T_cvCamera_object=Rigid.fromMatrix(m)
            // Reject behind-camera solutions for every object point, not only the origin.
            if(objects.toArray().any{T_cvCamera_object.q.rotate(V3(it.x,it.y,it.z)).z+tv.z<=0})return null
            Calib3d.projectPoints(objects,r,t,camera,dist,projected)
            val a=pixels.toArray();val b=projected.toArray();val error=sqrt(a.indices.sumOf{val dx=a[it].x-b[it].x;val dy=a[it].y-b[it].y;dx*dx+dy*dy}/a.size)
            val normal=T_cvCamera_object.q.rotate(V3(0.0,0.0,1.0));val angle=Math.toDegrees(acos(abs(normal.dot(tv)/tv.norm()).coerceIn(0.0,1.0)))
            return Result(rv,tv,CoordinateConversion.T_arCamera_cvCamera*T_cvCamera_object,error,angle)
        }finally{camera.release();dist.release();r.release();t.release();rotation.release();projected.release()}
    }
}
