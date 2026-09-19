package com.rigtrack.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.google.ar.core.*
import com.rigtrack.core.model.*
import com.rigtrack.core.math.*
import com.rigtrack.tracking.arcore.ArPreview
import java.util.concurrent.atomic.AtomicReference

class MarkerOverlay(context:Context):View(context) {
    private val appContext=context
    data class Shape(val id:Int,val error:Double,val confidence:Double,val mapped:Boolean,val points:FloatArray,val decision:MarkerUseDecision?)
    @Volatile var mappedIds:Set<Int> = emptySet()
    private val shapes=AtomicReference<List<Shape>>(emptyList())
    private var transitionFrom:List<Shape> = emptyList();private var transitionStartNs=0L
    private val transitionNs=80_000_000L
    private val axes=AtomicReference<FloatArray?>(null)
    @Volatile var showMarkers=true
    @Volatile var showAxes=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{strokeWidth=3f;textSize=30f}
    @Synchronized fun update(frame:Frame,sample:ArPoseSample,markers:List<MarkerObservation>,decisions:Map<Int,MarkerUseDecision> = emptyMap()){
        val visible=markers.filter{sample.timestampNs-it.timestampNs in 0..300_000_000L}
        val now=System.nanoTime();transitionFrom=displayShapes(now);transitionStartNs=now
        shapes.set(visible.map{o->val input=ArPreview.buffer(o.corners.map{it.toFloat()}.toFloatArray());val output=ArPreview.buffer(FloatArray(8));frame.transformCoordinates2d(Coordinates2d.IMAGE_PIXELS,input,Coordinates2d.VIEW,output);val p=FloatArray(8);output.position(0);output.get(p);Shape(o.id,o.reprojectionError,o.confidence,o.id in mappedIds,p,decisions[o.id])})
        if(showAxes&&sample.worldAnchor!=null){
            val view=FloatArray(16);val proj=FloatArray(16);val vp=FloatArray(16);frame.camera.getViewMatrix(view,0);frame.camera.getProjectionMatrix(proj,0,.05f,100f);android.opengl.Matrix.multiplyMM(vp,0,proj,0,view,0)
            val p=listOf(V3(),V3(.2,0.0,0.0),V3(0.0,.2,0.0),V3(0.0,0.0,.2)).flatMap{local->val world=sample.worldAnchor.t+sample.worldAnchor.q.rotate(local);val clip=FloatArray(4);android.opengl.Matrix.multiplyMV(clip,0,vp,0,floatArrayOf(world.x.toFloat(),world.y.toFloat(),world.z.toFloat(),1f),0);if(clip[3]>.01f)listOf((clip[0]/clip[3]+1)*width/2,(1-clip[1]/clip[3])*height/2)else listOf(Float.NaN,Float.NaN)}.toFloatArray();axes.set(p)
        }else axes.set(null)
        postInvalidateOnAnimation()
    }
    @Synchronized private fun displayShapes(now:Long):List<Shape>{
        val target=shapes.get();val alpha=((now-transitionStartNs).toDouble()/transitionNs).coerceIn(0.0,1.0)
        if(alpha>=1||transitionFrom.isEmpty())return target
        val old=transitionFrom.associateBy{it.id}
        return target.map{s->old[s.id]?.let{from->s.copy(points=FloatArray(8){i->from.points[i]+(s.points[i]-from.points[i])*alpha.toFloat()})}?:s}
    }
    private fun decisionLabel(d:MarkerUseDecision?,mapped:Boolean)=when{
        d?.usedForCorrection==true->appContext.getString(com.rigtrack.R.string.marker_used)
        d==null&&mapped->appContext.getString(com.rigtrack.R.string.marker_mapped)
        d==null->""
        else->appContext.getString(when(d.rejectionReason){
            MarkerRejectionReason.LOW_CONFIDENCE->com.rigtrack.R.string.reject_low_confidence
            MarkerRejectionReason.HIGH_REPROJECTION_ERROR->com.rigtrack.R.string.reject_reprojection
            MarkerRejectionReason.OUTLIER_TRANSLATION->com.rigtrack.R.string.reject_translation
            MarkerRejectionReason.OUTLIER_ROTATION->com.rigtrack.R.string.reject_rotation
            MarkerRejectionReason.MARKER_MOVED->com.rigtrack.R.string.reject_moved
            MarkerRejectionReason.MAP_QUALITY_LOW->com.rigtrack.R.string.reject_map_quality
            MarkerRejectionReason.ARCORE_NOT_TRACKING->com.rigtrack.R.string.reject_tracking
            MarkerRejectionReason.NOT_MAPPED->com.rigtrack.R.string.reject_not_mapped
            MarkerRejectionReason.AWAITING_CONFIRMATION->com.rigtrack.R.string.awaiting_confirmation
            MarkerRejectionReason.NONE->com.rigtrack.R.string.marker_valid
        })
    }
    override fun onDraw(canvas:Canvas){super.onDraw(canvas)
        val now=System.nanoTime();val display=displayShapes(now)
        if(showMarkers){for(s in display){val d=s.decision;paint.color=when{d?.usedForCorrection==true->Color.rgb(62,235,183);d!=null&&!d.valid->Color.rgb(244,112,112);s.mapped->Color.rgb(112,205,190);else->Color.rgb(245,193,96)};for(i in 0..3){val j=(i+1)%4;canvas.drawLine(s.points[2*i],s.points[2*i+1],s.points[2*j],s.points[2*j+1],paint)};val status=decisionLabel(d,s.mapped);canvas.drawText("ID ${s.id} $status • %.2f px • %.0f%%".format(java.util.Locale.US,s.error,s.confidence*100),s.points[0],(s.points[1]-8).coerceAtLeast(30f),paint)}}
        if(now-transitionStartNs<transitionNs)postInvalidateOnAnimation()
        if(showAxes)axes.get()?.let{a->if(a.all{it.isFinite()})for(i in 1..3){paint.color=listOf(Color.RED,Color.GREEN,Color.BLUE)[i-1];canvas.drawLine(a[0],a[1],a[i*2],a[i*2+1],paint);canvas.drawText(listOf("X","Y","Z")[i-1],a[i*2],a[i*2+1],paint)}}
    }
}
