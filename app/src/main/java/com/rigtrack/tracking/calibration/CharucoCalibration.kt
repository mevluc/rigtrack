package com.rigtrack.tracking.calibration

import android.os.SystemClock
import com.rigtrack.core.model.*
import com.rigtrack.tracking.marker.PoseEstimator
import org.opencv.core.*
import org.opencv.objdetect.*
import org.opencv.calib3d.Calib3d
import kotlin.math.*

class CharucoCalibration(private val settings:DetectorSettings,private val key:String) {
    private val board=CharucoBoard(Size(settings.boardX.toDouble(),settings.boardY.toDouble()),(settings.squareMm/1000).toFloat(),(settings.boardMarkerMm/1000).toFloat(),Objdetect.getPredefinedDictionary(settings.dictionary))
    private val detector=CharucoDetector(board)
    private val objects=mutableListOf<Mat>();private val pixels=mutableListOf<Mat>();private val descriptors=mutableListOf<DoubleArray>()
    private var resolution:Size?=null
    @Volatile var feedback="Need more samples: show a 5×7 ChArUco board";private set
    @Volatile var count=0;private set
    private fun detect(gray:Mat):Pair<MatOfPoint3f,MatOfPoint2f>? {
        val corners=Mat();val ids=Mat()
        try {
            detector.detectBoard(gray,corners,ids)
            if(ids.rows()<6||board.checkCharucoCornersCollinear(ids))return null
            val chessboard=board.chessboardCorners
            try {
                val all=chessboard.toArray();val objectPoints=Array(ids.rows()){all[ids.get(it,0)[0].toInt()]};val imagePoints=Array(ids.rows()){val p=corners.get(it,0);Point(p[0],p[1])}
                return MatOfPoint3f(*objectPoints) to MatOfPoint2f(*imagePoints)
            }finally{chessboard.release()}
        }finally{corners.release();ids.release()}
    }
    @Synchronized fun observe(gray:Mat,k:Intrinsics) {
        if(count>=60){feedback="60 samples collected; run calibration";return}
        val pair=detect(gray)?:run{feedback="Need more samples • show at least 6 board corners";return}
        val (obj,pix)=pair;var retained=false
        try {
            if(resolution!=null&&(resolution!!.width!=gray.cols().toDouble()||resolution!!.height!=gray.rows().toDouble())){feedback="Camera resolution changed; restart calibration";return}
            val pts=pix.toArray();val minX=pts.minOf{it.x};val maxX=pts.maxOf{it.x};val minY=pts.minOf{it.y};val maxY=pts.maxOf{it.y}
            val area=(maxX-minX)*(maxY-minY)/(gray.cols()*gray.rows())
            if(area<.035){feedback="Board too small • Move board closer";return}
            val pose=PoseEstimator.solve(obj,pix,k,emptyList())?:run{feedback="Change angle • pose could not be solved";return}
            val d=doubleArrayOf((minX+maxX)/2/gray.cols(),(minY+maxY)/2/gray.rows(),sqrt(area),pose.rvec.x,pose.rvec.y,pose.rvec.z)
            if(descriptors.any{old->abs(old[0]-d[0])<.08&&abs(old[1]-d[1])<.08&&abs(old[2]-d[2])<.06&&sqrt((3..5).sumOf{(old[it]-d[it]).pow(2)})<.20}){feedback="Too similar to previous sample • Change angle";return}
            objects+=obj;pixels+=pix;descriptors+=d;retained=true;resolution=Size(gray.cols().toDouble(),gray.rows().toDouble());count=objects.size
            feedback="Good sample • $count / 25 recommended (${maxOf(0,15-count)} more required)"
        }finally{if(!retained){obj.release();pix.release()}}
    }
    @Synchronized fun calibrate(name:String):CalibrationProfile {
        require(objects.size>=15){"Need more samples: at least 15 diverse observations"}
        val size=resolution!!;val camera=Mat.eye(3,3,CvType.CV_64F);val dist=Mat();val rvecs=mutableListOf<Mat>();val tvecs=mutableListOf<Mat>()
        try {
            val rms=Calib3d.calibrateCamera(objects,pixels,size,camera,dist,rvecs,tvecs)
            require(rms.isFinite()&&rms<3.0){"Calibration RMS $rms px is too high; collect sharper, varied samples"}
            val fx=camera.get(0,0)[0];val fy=camera.get(1,1)[0];val cx=camera.get(0,2)[0];val cy=camera.get(1,2)[0]
            require(fx>0&&fy>0&&cx in 0.0..size.width&&cy in 0.0..size.height){"Invalid camera calibration"}
            val coefficients=DoubleArray((dist.total()*dist.channels()).toInt());dist.get(0,0,coefficients)
            feedback="Calibration saved • RMS %.3f px".format(java.util.Locale.US,rms)
            return CalibrationProfile(name,key,Intrinsics(fx,fy,cx,cy,size.width.toInt(),size.height.toInt()),coefficients.toList(),rms,count,java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",java.util.Locale.US).format(java.util.Date()))
        }finally{camera.release();dist.release();rvecs.forEach{it.release()};tvecs.forEach{it.release()}}
    }
    @Synchronized fun boardPose(gray:Mat,k:Intrinsics,profile:CalibrationProfile?,sample:ArPoseSample,imageNs:Long):MarkerObservation? {
        val (obj,pix)=detect(gray)?:return null
        try {
            val p=PoseEstimator.solve(obj,pix,profile?.intrinsics?:k,profile?.distortion?:emptyList())?:return null
            val pts=pix.toArray();val left=pts.minOf{it.x};val right=pts.maxOf{it.x};val top=pts.minOf{it.y};val bottom=pts.maxOf{it.y};val area=(right-left)*(bottom-top)
            val confidence=(1-p.error/settings.maxError).coerceIn(0.0,1.0)*minOf(1.0,area/(settings.minPixelArea*4))*cos(Math.toRadians(p.angle)).coerceAtLeast(0.0)*(if(profile==null).85 else 1.0)
            return MarkerObservation(sample.timestampNs,imageNs,SystemClock.elapsedRealtimeNanos(),-1,settings.squareMm*settings.boardX/1000,listOf(left,top,right,top,right,bottom,left,bottom),p.rvec,p.tvec,p.T_arCamera_object,p.error,area,p.angle,confidence,profile?.intrinsics?:k,profile!=null)
        }finally{obj.release();pix.release()}
    }
    @Synchronized fun close(){objects.forEach{it.release()};pixels.forEach{it.release()};objects.clear();pixels.clear();detector.clear()}
}
