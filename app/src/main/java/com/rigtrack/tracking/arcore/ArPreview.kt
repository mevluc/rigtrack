package com.rigtrack.tracking.arcore

import android.content.Context
import android.opengl.*
import android.os.SystemClock
import android.view.Surface
import com.google.ar.core.*
import com.rigtrack.core.math.*
import com.rigtrack.core.model.*
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArPreview(context:Context,val session:Session,private val onFrame:(Frame,ArPoseSample)->Unit,private val onError:(String)->Unit,private val onOrigin:()->Unit = {}):GLSurfaceView(context),GLSurfaceView.Renderer {
    private var texture=0;private var program=0;private var widthPx=1;private var heightPx=1
    private var anchor:Anchor?=null
    private var lastTimestamp=0L
    private var lastErrorNs=0L
    @Volatile private var originRequested=false
    @Volatile private var referenceOrigin:Rigid?=null
    @Volatile var originSet=false;private set
    private val vertices=buffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val uv=buffer(FloatArray(8))
    init {setEGLContextClientVersion(2);preserveEGLContextOnPause=true;setRenderer(this);renderMode=RENDERMODE_CONTINUOUSLY}
    fun setOrigin(worldReference:Rigid?=null) {referenceOrigin=worldReference;originRequested=true}
    fun clearOrigin() {queueEvent {anchor?.detach();anchor=null;originSet=false}}
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);texture=ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 uv; varying vec2 v; void main(){gl_Position=vec4(p,0.,1.);v=uv;}")
        val fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES camera; varying vec2 v; void main(){gl_FragColor=texture2D(camera,v);}")
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int){widthPx=width;heightPx=height;GLES20.glViewport(0,0,width,height)}
    override fun onDrawFrame(gl:GL10?) {
        try {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            session.setCameraTextureName(texture)
            session.setDisplayGeometry(display?.rotation?:Surface.ROTATION_0,widthPx,heightPx)
            val frame=session.update(); if(frame.timestamp==0L)return
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"camera"),0)
            val p=GLES20.glGetAttribLocation(program,"p");val u=GLES20.glGetAttribLocation(program,"uv")
            vertices.position(0);uv.position(0);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,uv);GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(u);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            if(frame.timestamp==lastTimestamp)return;lastTimestamp=frame.timestamp
            val camera=frame.camera
            if(originRequested&&camera.trackingState==TrackingState.TRACKING){anchor?.detach();val ref=referenceOrigin;val originPose=if(ref==null)camera.pose else Pose(floatArrayOf(ref.t.x.toFloat(),ref.t.y.toFloat(),ref.t.z.toFloat()),floatArrayOf(ref.q.x.toFloat(),ref.q.y.toFloat(),ref.q.z.toFloat(),ref.q.w.toFloat()));anchor=session.createAnchor(originPose);originRequested=false;originSet=true;onOrigin()}
            val k=camera.imageIntrinsics;val f=k.focalLength;val c=k.principalPoint;val d=k.imageDimensions
            onFrame(frame,ArPoseSample(frame.timestamp,SystemClock.elapsedRealtimeNanos(),frame.androidCameraTimestamp,camera.pose.rigid(),anchor?.pose?.rigid(),camera.trackingState.name,camera.trackingFailureReason.name,Intrinsics(f[0].toDouble(),f[1].toDouble(),c[0].toDouble(),c[1].toDouble(),d[0],d[1]),anchor?.trackingState==TrackingState.TRACKING))
        } catch(e:com.google.ar.core.exceptions.SessionPausedException) { /* lifecycle pause boundary */ }
        catch(e:Exception){val now=SystemClock.elapsedRealtimeNanos();if(now-lastErrorNs>1_000_000_000){lastErrorNs=now;onError("ARCore: ${e.javaClass.simpleName}: ${e.message}")}}
    }
    private fun shader(type:Int,source:String):Int {val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetShaderInfoLog(s)};return s}
    companion object {
        fun buffer(v:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(v);position(0)}
        fun Pose.rigid()=Rigid(V3(tx().toDouble(),ty().toDouble(),tz().toDouble()),Q(qx().toDouble(),qy().toDouble(),qz().toDouble(),qw().toDouble()).normalized())
    }
}
