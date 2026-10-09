package com.xperia.timelapse.camera

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import com.xperia.timelapse.video.*
import java.io.File
import java.io.FileOutputStream

/** Persist display-oriented pixels so photos and video share one geometry. */
object CaptureImageWriter {
 fun normalize(file:File,resolution:VideoResolution):Pair<Int,Int> {
  val exif=ExifInterface(file)
  val source=BitmapFactory.decodeFile(file.absolutePath) ?: error("照片无法解码")
  var oriented:Bitmap?=null
  var resized:Bitmap?=null
  val temp=File(file.parentFile,file.name+".sized")
  try {
   val matrix=Matrix().apply {if(exif.isFlipped)postScale(-1f,1f);postRotate(exif.rotationDegrees.toFloat())}
   val image=Bitmap.createBitmap(source,0,0,source.width,source.height,matrix,true);oriented=image
   val size=CaptureImageGeometry.dimensions(image.width,image.height,resolution.width)
   val output=Bitmap.createScaledBitmap(image,size.first,size.second,true);resized=output
   FileOutputStream(temp).use {check(output.compress(Bitmap.CompressFormat.JPEG,95,it)) {"照片编码失败"};it.fd.sync()}
   val saved=ExifInterface(temp)
   listOf(ExifInterface.TAG_DATETIME,ExifInterface.TAG_DATETIME_ORIGINAL,ExifInterface.TAG_MAKE,ExifInterface.TAG_MODEL,
    ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_F_NUMBER,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_FOCAL_LENGTH,ExifInterface.TAG_WHITE_BALANCE).forEach {tag->exif.getAttribute(tag)?.let {saved.setAttribute(tag,it)}}
   saved.setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL.toString());saved.saveAttributes()
   FileOutputStream(temp,true).use {it.fd.sync()}
   check(temp.renameTo(file)) {"无法保存调整后的照片"}
   return size
  } finally {
   resized?.takeIf {it!==oriented && it!==source}?.recycle()
   oriented?.takeIf {it!==source}?.recycle();source.recycle();temp.delete()
  }
 }
}
