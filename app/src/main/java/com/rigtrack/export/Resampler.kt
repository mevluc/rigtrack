package com.rigtrack.export

import com.rigtrack.core.model.*
import com.rigtrack.core.math.*

/** Streaming, bounded memory. Invalid intervals are explicit and never extrapolated. */
object Resampler {
    data class Frame(val index:Long,val timeS:Double,val pose:Rigid?,val quality:Double,val interpolatedGap:Boolean)
    fun frames(source:Sequence<TimedPose>, fps:Fps,startNs:Long,endNs:Long,maxGapNs:Long):Sequence<Frame> = sequence {
        val it=source.iterator(); var left:TimedPose?=null; var right:TimedPose?=null; var gap=false
        fun advance() { right=null; while(it.hasNext()) {val p=it.next(); if(p.valid) {right=p;break} else gap=true} }
        advance(); var n=0L
        while(startNs+fps.nanos(n)<=endNs) {
            val ts=startNs+fps.nanos(n)
            while(right!=null && right!!.timestampNs<ts) { left=right;gap=false;advance() }
            val l=left;val r=right
            val exact=r!=null&&r.timestampNs==ts
            val supported=l!=null&&r!=null&&r.timestampNs>l.timestampNs&&r.timestampNs-l.timestampNs<=maxGapNs
            val p=if(exact) r!!.pose else if(supported) l!!.pose.interpolate(r!!.pose,(ts-l.timestampNs).toDouble()/(r.timestampNs-l.timestampNs)) else null
            yield(Frame(n+1,fps.seconds(n),p,if(p==null)0.0 else if(exact)r!!.quality else minOf(l!!.quality,r!!.quality)*(if(gap)0.5 else 1.0),gap&&supported));n++
        }
    }
    /** Symmetric 3-sample quaternion-aware offline filter; no causal phase lag, no crossing gaps. */
    fun smooth(source:Sequence<TimedPose>,strength:Double,maxGapNs:Long):Sequence<TimedPose> = sequence {
        val it=source.iterator(); if(!it.hasNext())return@sequence
        var a=it.next(); yield(a);if(!it.hasNext())return@sequence
        var b=it.next()
        while(it.hasNext()) { val c=it.next(); val ok=a.valid&&b.valid&&c.valid&&c.timestampNs-a.timestampNs<=maxGapNs
            yield(if(ok)b.copy(pose=b.pose.interpolate(a.pose.interpolate(c.pose,(b.timestampNs-a.timestampNs).toDouble()/(c.timestampNs-a.timestampNs)),strength)) else b);a=b;b=c }
        yield(b)
    }
}
