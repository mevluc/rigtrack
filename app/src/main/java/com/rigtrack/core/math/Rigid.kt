package com.rigtrack.core.math

import kotlin.math.*

data class V3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    operator fun plus(b: V3) = V3(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: V3) = V3(x-b.x,y-b.y,z-b.z)
    operator fun times(s: Double) = V3(x*s,y*s,z*s)
    fun dot(b: V3) = x*b.x+y*b.y+z*b.z
    fun cross(b: V3) = V3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun norm() = sqrt(dot(this))
    fun lerp(b: V3, a: Double) = this*(1-a)+b*a
}

data class Q(val x: Double=0.0,val y: Double=0.0,val z: Double=0.0,val w: Double=1.0) {
    fun dot(b: Q) = x*b.x+y*b.y+z*b.z+w*b.w
    fun normalized(): Q { val n=sqrt(dot(this)); require(n>1e-15 && n.isFinite()); return Q(x/n,y/n,z/n,w/n) }
    fun inverse() = Q(-x,-y,-z,w).normalized()
    operator fun unaryMinus() = Q(-x,-y,-z,-w)
    operator fun times(b: Q) = Q(w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z).normalized()
    fun rotate(v: V3): V3 { val q=normalized(); val u=V3(q.x,q.y,q.z); return v+u.cross(v)*(2*q.w)+u.cross(u.cross(v))*2.0 }
    fun angle(b: Q) = 2*acos(abs(normalized().dot(b.normalized())).coerceIn(0.0,1.0))
    fun slerp(other: Q, alpha: Double): Q {
        val a=normalized(); var b=other.normalized(); var d=a.dot(b)
        if(d<0) { b=-b; d=-d }; d=d.coerceIn(-1.0,1.0)
        val t=alpha.coerceIn(0.0,1.0)
        val aa: Double; val bb: Double
        if(d>0.9995) { aa=1-t; bb=t } else { val theta=acos(d); aa=sin((1-t)*theta)/sin(theta); bb=sin(t*theta)/sin(theta) }
        return Q(aa*a.x+bb*b.x,aa*a.y+bb*b.y,aa*a.z+bb*b.z,aa*a.w+bb*b.w).normalized()
    }
    companion object {
        fun axis(axis: V3, radians: Double): Q { val n=axis.norm(); require(n>0); val s=sin(radians/2)/n; return Q(axis.x*s,axis.y*s,axis.z*s,cos(radians/2)) }
        /** Active local XYZ rotations: Rz * Ry * Rx. */
        fun eulerXYZ(x: Double,y: Double,z: Double)=axis(V3(0.0,0.0,1.0),z)*axis(V3(0.0,1.0,0.0),y)*axis(V3(1.0,0.0,0.0),x)
        fun fromMatrix(m: DoubleArray): Q {
            require(m.size==16)
            val tr=m[0]+m[5]+m[10]
            return when {
                tr>0 -> { val s=sqrt(tr+1)*2; Q((m[9]-m[6])/s,(m[2]-m[8])/s,(m[4]-m[1])/s,s/4) }
                m[0]>m[5] && m[0]>m[10] -> { val s=sqrt(1+m[0]-m[5]-m[10])*2; Q(s/4,(m[1]+m[4])/s,(m[2]+m[8])/s,(m[9]-m[6])/s) }
                m[5]>m[10] -> { val s=sqrt(1+m[5]-m[0]-m[10])*2; Q((m[1]+m[4])/s,s/4,(m[6]+m[9])/s,(m[2]-m[8])/s) }
                else -> { val s=sqrt(1+m[10]-m[0]-m[5])*2; Q((m[2]+m[8])/s,(m[6]+m[9])/s,s/4,(m[4]-m[1])/s) }
            }.normalized()
        }
    }
}

/** T_A_B maps points expressed in B into A. Row-major 4x4, column vectors, meters. */
data class Rigid(val t: V3=V3(),val q: Q=Q()) {
    operator fun times(b: Rigid) = Rigid(t+q.rotate(b.t),q*b.q)
    fun inverse(): Rigid { val r=q.inverse(); return Rigid(r.rotate(t*-1.0),r) }
    fun interpolate(b: Rigid, a: Double)=Rigid(t.lerp(b.t,a),q.slerp(b.q,a))
    fun matrix(): DoubleArray {
        val (x,y,z,w)=q.normalized()
        return doubleArrayOf(1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w),t.x,2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w),t.y,2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y),t.z,0.0,0.0,0.0,1.0)
    }
    companion object { fun fromMatrix(m: DoubleArray)=Rigid(V3(m[3],m[7],m[11]),Q.fromMatrix(m)) }
}

object CoordinateConversion {
    // AR world X,Y,Z -> Blender X,Z,-Y; camera local axes already agree (-Z forward, +Y up).
    val T_blenderWorld_anchor = Rigid(q=Q.axis(V3(1.0,0.0,0.0),Math.PI/2))
    val T_arCamera_cvCamera = Rigid(q=Q.axis(V3(1.0,0.0,0.0),Math.PI))
    fun toBlenderCamera(T_anchor_camera: Rigid) = Rigid.fromMatrix((T_blenderWorld_anchor*T_anchor_camera).matrix())
}
