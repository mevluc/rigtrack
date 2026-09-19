package com.rigtrack.tracking.marker

import com.rigtrack.core.model.Intrinsics
import java.nio.ByteBuffer

/** Copies only visible luma pixels; Android Y planes may contain row padding and pixel stride. */
object CpuImage {
    fun copyLuma(
        buffer:ByteBuffer,
        width:Int,
        height:Int,
        rowStride:Int,
        pixelStride:Int,
        cropLeft:Int=0,
        cropTop:Int=0,
    ):ByteArray {
        require(width>0&&height>0&&rowStride>0&&pixelStride>0&&cropLeft>=0&&cropTop>=0)
        val source=buffer.duplicate()
        val base=source.position()
        val last=base+(cropTop+height-1).toLong()*rowStride+(cropLeft+width-1).toLong()*pixelStride
        require(last<source.limit().toLong()){ "Y plane buffer is smaller than its stride/crop geometry" }
        val output=ByteArray(Math.multiplyExact(width,height))
        for(y in 0 until height) {
            val row=base+(cropTop+y)*rowStride+cropLeft*pixelStride
            if(pixelStride==1) {
                source.position(row);source.get(output,y*width,width)
            } else for(x in 0 until width) output[y*width+x]=source.get(row+x*pixelStride)
        }
        return output
    }

    /** Scales ARCore CPU-image intrinsics to the Image buffer, then applies its crop rectangle. */
    fun croppedIntrinsics(source:Intrinsics,fullWidth:Int,fullHeight:Int,cropLeft:Int,cropTop:Int,cropWidth:Int,cropHeight:Int):Intrinsics {
        require(fullWidth>0&&fullHeight>0&&cropWidth>0&&cropHeight>0&&source.width>0&&source.height>0)
        val sx=fullWidth.toDouble()/source.width
        val sy=fullHeight.toDouble()/source.height
        return Intrinsics(source.fx*sx,source.fy*sy,source.cx*sx-cropLeft,source.cy*sy-cropTop,cropWidth,cropHeight)
    }
}
