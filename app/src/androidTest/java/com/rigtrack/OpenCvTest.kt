package com.rigtrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rigtrack.core.model.*
import com.rigtrack.tracking.marker.PoseEstimator
import com.rigtrack.tracking.calibration.CharucoCalibration
import com.rigtrack.tracking.marker.MarkerWorker
import com.rigtrack.core.math.Rigid
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.calib3d.Calib3d
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvTest {
    @Before fun load(){assertTrue(OpenCVLoader.initLocal())}
    @Test fun markerWorkerClosesImagesOnCancellation()=runBlocking {
        val reader=android.media.ImageReader.newInstance(64,64,android.graphics.ImageFormat.YUV_420_888,6)
        val producer=android.media.ImageWriter.newInstance(reader.surface,6)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val worker=MarkerWorker(scope,DetectorSettings(),"resource-test",null,{MarkerMap()},{_,_->},{})
        val acquired=mutableListOf<android.media.Image>()
        try {
            repeat(3){index->
                val image=producer.dequeueInputImage();image.timestamp=1000L+index
                image.planes.forEach{plane->val buffer=plane.buffer;while(buffer.hasRemaining())buffer.put(0.toByte())}
                producer.queueInputImage(image)
                var cpu:android.media.Image?=null
                repeat(50){if(cpu==null){cpu=reader.acquireNextImage();if(cpu==null)delay(10)}}
                val owned=cpu?:error("ImageReader did not receive a frame");acquired+=owned
                worker.submit(owned,ArPoseSample(1000L+index,1000L+index,1000L+index,Rigid(),Rigid(),"TRACKING","NONE",Intrinsics(60.0,60.0,32.0,32.0,64,64)))
            }
            scope.cancel();worker.close()
            acquired.forEach{image->assertThrows(IllegalStateException::class.java){image.planes}}
        }finally{scope.cancel();worker.close();producer.close();reader.close()}
    }
    @Test fun arucoDetectionAndPoseDirection(){
        val dictionary=Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50);val marker=Mat();val scene=Mat(600,800,CvType.CV_8UC1,Scalar(255.0));val corners=mutableListOf<Mat>();val ids=Mat();val detector=ArucoDetector(dictionary)
        try {
            Objdetect.generateImageMarker(dictionary,12,200,marker)
            val roi=scene.submat(Rect(300,200,200,200));marker.copyTo(roi);roi.release()
            detector.detectMarkers(scene,corners,ids);assertEquals(12,ids.get(0,0)[0].toInt())
            val pixels=MatOfPoint2f(*(0..3).map{corners[0].get(0,it).let{p->Point(p[0],p[1])}}.toTypedArray());val h=.075
            val objects=MatOfPoint3f(Point3(-h,h,0.0),Point3(h,h,0.0),Point3(h,-h,0.0),Point3(-h,-h,0.0))
            try{val p=PoseEstimator.solve(objects,pixels,Intrinsics(800.0,800.0,400.0,300.0,800,600),emptyList(),Calib3d.SOLVEPNP_IPPE_SQUARE)!!;assertTrue(p.error<1.0);assertTrue(p.tvec.z>0);assertTrue(p.T_arCamera_object.t.z<0);assertEquals(.6,p.tvec.z,.02)}finally{pixels.release();objects.release()}
        }finally{marker.release();scene.release();ids.release();corners.forEach{it.release()};detector.clear()}
    }
    @Test fun defaultAprilTagDetectsIdOneAtCenterEdgePerspectiveAndRotation(){
        val dictionary=Objdetect.getPredefinedDictionary(MarkerDictionaries.DEFAULT);val marker=Mat();Objdetect.generateImageMarker(dictionary,1,240,marker)
        val detector=ArucoDetector(dictionary)
        fun scene(points:Array<Point>):Mat{
            val canvas=Mat(600,800,CvType.CV_8UC1,Scalar(255.0));val src=MatOfPoint2f(Point(0.0,0.0),Point(239.0,0.0),Point(239.0,239.0),Point(0.0,239.0));val dst=MatOfPoint2f(*points);val h=Imgproc.getPerspectiveTransform(src,dst)
            try{Imgproc.warpPerspective(marker,canvas,h,canvas.size(),Imgproc.INTER_NEAREST,Core.BORDER_TRANSPARENT)}finally{src.release();dst.release();h.release()};return canvas
        }
        fun assertDetected(image:Mat){val ids=Mat();val corners=mutableListOf<Mat>();val rejected=mutableListOf<Mat>();try{detector.detectMarkers(image,corners,ids,rejected);assertTrue("rejected=${rejected.size}",ids.rows()>0);assertEquals(1,ids.get(0,0)[0].toInt())}finally{ids.release();corners.forEach{it.release()};rejected.forEach{it.release()}}}
        val images=listOf(
            scene(arrayOf(Point(280.0,180.0),Point(520.0,180.0),Point(520.0,420.0),Point(280.0,420.0))),
            scene(arrayOf(Point(10.0,30.0),Point(230.0,20.0),Point(240.0,250.0),Point(15.0,260.0))),
            scene(arrayOf(Point(250.0,120.0),Point(555.0,175.0),Point(500.0,455.0),Point(210.0,395.0))))
        val rotated=Mat();Core.rotate(images[0],rotated,Core.ROTATE_90_CLOCKWISE)
        try{images.forEach(::assertDetected);assertDetected(rotated)}finally{images.forEach{it.release()};rotated.release();marker.release();detector.clear()}
    }
    @Test fun charucoCalibrationOnProjectedBoard(){
        val settings=DetectorSettings(dictionary=Objdetect.DICT_4X4_50);val calibration=CharucoCalibration(settings,"test-camera")
        val board=CharucoBoard(Size(5.0,7.0),.04f,.03f,Objdetect.getPredefinedDictionary(0));val texture=Mat();board.generateImage(Size(1000.0,1400.0),texture,0,1)
        val k=Intrinsics(1000.0,1000.0,640.0,480.0,1280,960);val camera=PoseEstimator.matrix(k);val distortion=MatOfDouble(0.0,0.0,0.0,0.0,0.0)
        val objects=MatOfPoint3f(Point3(0.0,0.0,0.0),Point3(.2,0.0,0.0),Point3(.2,.28,0.0),Point3(0.0,.28,0.0))
        val source=MatOfPoint2f(Point(0.0,0.0),Point(999.0,0.0),Point(999.0,1399.0),Point(0.0,1399.0))
        try {
            for(i in 0..47){
                val r=Mat(3,1,CvType.CV_64F);r.put(0,0,((i%4)-1.5)*.15,((i/4%4)-1.5)*.18,((i%3)-1)*.08)
                val t=Mat(3,1,CvType.CV_64F);t.put(0,0,-.1+((i%3)-1)*.06,-.14+((i/3%3)-1)*.055,.52+(i%4)*.07)
                val destination=MatOfPoint2f();val image=Mat();var homography:Mat?=null
                try{Calib3d.projectPoints(objects,r,t,camera,distortion,destination);homography=Imgproc.getPerspectiveTransform(source,destination);Imgproc.warpPerspective(texture,image,homography,Size(1280.0,960.0),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,Scalar(255.0));calibration.observe(image,k)}finally{r.release();t.release();destination.release();image.release();homography?.release()}
            }
            assertTrue("${calibration.count} samples: ${calibration.feedback}",calibration.count>=15)
            val profile=calibration.calibrate("synthetic lens")
            assertTrue(profile.rms<1.0);assertEquals(1000.0,profile.intrinsics.fx,35.0);assertEquals(1000.0,profile.intrinsics.fy,35.0)
        }finally{calibration.close();texture.release();camera.release();distortion.release();objects.release();source.release()}
    }
}
