package com.rigtrack.tracking.arcore

import android.os.SystemClock
import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import kotlinx.coroutines.*
import kotlin.math.*

class SyntheticTracker(private val scope:CoroutineScope,private val consumer:(ArPoseSample)->Unit) {
    private var job:Job?=null
    @Volatile var origin:Rigid?=null
    @Volatile private var current=Rigid()
    fun setOrigin(){origin=current}
    fun start(motion:String){val start=SystemClock.elapsedRealtimeNanos();job=scope.launch(Dispatchers.Default){while(isActive){val now=SystemClock.elapsedRealtimeNanos();val t=(now-start)/1e9;current=trajectory(t,motion);consumer(ArPoseSample(now,now,now,current,origin,"TRACKING","SYNTHETIC",Intrinsics(900.0,900.0,640.0,360.0,1280,720)));delay(16)}}}
    fun stop(){job?.cancel();job=null}
    companion object {
        fun trajectory(t:Double,motion:String):Rigid=when(motion){
            "Forward/back"->Rigid(V3(0.0,0.0,-sin(t)))
            "Pan"->Rigid(q=Q.axis(V3(0.0,1.0,0.0),sin(t)*.7))
            "Orbit"->Rigid(V3(sin(t*.4),0.0,cos(t*.4)-1),Q.axis(V3(0.0,1.0,0.0),t*.4))
            "Test trajectory"->when{t<1->Rigid(V3(0.0,0.0,-t));t<2->Rigid(V3(t-1,0.0,-1.0));else->Rigid(V3(1.0,0.0,-1.0),Q.axis(V3(0.0,1.0,0.0),(t-2).coerceIn(0.0,1.0)*PI/2))}
            else->Rigid(V3(sin(t*.5),.1*sin(t),cos(t*.5)-1))
        }
    }
}
