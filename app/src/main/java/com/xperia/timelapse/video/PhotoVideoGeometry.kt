package com.xperia.timelapse.video

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

object PhotoVideoGeometry {
 fun output(file:File,fps:Int):VideoOutput {
  val b=BitmapFactory.Options().apply {inJustDecodeBounds=true}
  BitmapFactory.decodeFile(file.absolutePath,b)
  check(b.outWidth>0 && b.outHeight>0) {"照片无法读取，原片已保留"}
  val rotation=ExifInterface(file).rotationDegrees
  val rotated=rotation%180!=0
  return VideoOutput(fps=fps,frameWidth=if(rotated)b.outHeight else b.outWidth,frameHeight=if(rotated)b.outWidth else b.outHeight)
 }
}

object CaptureImageGeometry {
 fun dimensions(width:Int,height:Int,longEdge:Int):Pair<Int,Int> {
  require(width>0 && height>0 && longEdge>0 && longEdge%2==0)
  fun shortEdge(short:Int,long:Int)=(short.toLong()*longEdge/long/2*2).toInt().coerceAtLeast(2)
  return if(width>=height)longEdge to shortEdge(height,width) else shortEdge(width,height) to longEdge
 }
}
