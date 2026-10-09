package com.xperia.timelapse.video

import android.graphics.*
import android.media.*
import android.os.SystemClock
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Produces a temporary MP4 and fully decodes it before publishing. Never deletes photos. */
class VideoComposer {
 private fun format(encoding:VideoEncoding,quality:VideoQuality,output:VideoOutput)=MediaFormat.createVideoFormat(encoding.mime,output.width,output.height).apply {
  setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
  setInteger(MediaFormat.KEY_BIT_RATE,VideoEncodingPolicy.bitrate(encoding,quality,output));setInteger(MediaFormat.KEY_FRAME_RATE,output.fps)
  setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
  setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT601_NTSC)
  setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)
 }
 private fun encoder(encoding:VideoEncoding,quality:VideoQuality,output:VideoOutput):String? {
  // MP4 AV1 muxing requires API 34; avoid a slow software AV1 encoder on phones.
  if(encoding==VideoEncoding.AV1 && Build.VERSION.SDK_INT<34)return null
  val f=format(encoding,quality,output)
  return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
   .filter {it.isEncoder && it.supportedTypes.any {mime->mime.equals(encoding.mime,true)} && (encoding!=VideoEncoding.AV1 || it.isHardwareAccelerated)}
   .sortedByDescending {it.isHardwareAccelerated}
   .firstOrNull {runCatching {it.getCapabilitiesForType(encoding.mime).isFormatSupported(f)}.getOrDefault(false)}?.name
 }
 fun available(quality:VideoQuality=VideoQuality.STANDARD,output:VideoOutput=VideoOutput()):Set<VideoEncoding> = VideoEncoding.values().filter {encoder(it,quality,output)!=null}.toSet()
 fun compose(frames: List<File>, destination: File, preference:VideoPreference, quality:VideoQuality, output:VideoOutput, cancelled: ()->Boolean, progress:(String)->Unit):VideoEncodingResult {
  require(frames.size>=2)
  check(!destination.exists()) {"视频已存在，禁止覆盖"}
  val temp=File(destination.parentFile,"preview.partial.mp4")
  val candidates=VideoEncodingPolicy.candidates(preference,available(quality,output))
  check(candidates.isNotEmpty()) {"设备不支持所选分辨率、帧率与编码组合，照片已保留"}
  var lastError:Exception?=null
  for((attempt,encoding) in candidates.withIndex()) {
   check(!cancelled()) {"合成已取消，原始照片已保留"}
   val name=checkNotNull(encoder(encoding,quality,output))
   val requested=when(preference){VideoPreference.AV1->VideoEncoding.AV1;VideoPreference.AVC->VideoEncoding.AVC;else->VideoEncoding.HEVC}
   val fallback=encoding!=requested || attempt>0
   progress("${if(fallback) "回退至 " else "使用 "}${encoding.label}")
   try {
    encode(frames,temp,encoding,quality,output,name,cancelled) {progress("${encoding.label} · $it")}
    java.io.FileOutputStream(temp,true).use {it.fd.sync()}
    validate(temp,frames.size,encoding,output,cancelled,progress)
    check(!cancelled()) {"合成已取消，原始照片已保留"}
    check(temp.renameTo(destination)) {"无法发布视频，原始照片已保留"}
    return VideoEncodingResult(encoding,VideoEncodingPolicy.bitrate(encoding,quality,output),name,fallback)
   } catch(e:Exception) {
    lastError=e
    if(cancelled())throw e
   } finally {temp.delete()}
  }
  throw IllegalStateException("视频合成失败，照片已保留：${lastError?.message}",lastError)
 }
 private fun encode(frames:List<File>,file:File,encoding:VideoEncoding,quality:VideoQuality,output:VideoOutput,name:String,cancelled:()->Boolean,progress:(String)->Unit) {
  val width=output.width;val height=output.height
  val format=format(encoding,quality,output)
  val codec=MediaCodec.createByCodecName(name)
  var muxer:MediaMuxer?=null;var started=false;var codecStarted=false
  try {
   codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();codecStarted=true
   val mux=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=mux
   val info=MediaCodec.BufferInfo();var track=-1;var input=0;var inputDone=false;var outputDone=false
   var lastProgress=SystemClock.elapsedRealtime()
   val pixels=IntArray(width*height)
   while(!outputDone) {
    check(!cancelled()) {"合成已取消，原始照片已保留"}
    if(!inputDone) {
     val index=codec.dequeueInputBuffer(10_000)
     if(index>=0) {
      if(input==frames.size) {
       codec.queueInputBuffer(index,0,0,output.presentationTimeUs(input),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true
      } else {
       val bitmap=loadFrame(frames[input],width,height)
       try {bitmap.getPixels(pixels,0,width,0,0,width,height)} finally {bitmap.recycle()}
       val inputSize=checkNotNull(codec.getInputBuffer(index)).capacity()
       val image=codec.getInputImage(index) ?: error("编码器无法提供可写图像")
       try { Yuv420.write(pixels,width,height,image.planes.map {Yuv420.Plane(it.buffer,it.rowStride,it.pixelStride)}) } finally {image.close()}
       codec.queueInputBuffer(index,0,inputSize,output.presentationTimeUs(input),0)
       input++;progress("合成视频 $input / ${frames.size}");lastProgress=SystemClock.elapsedRealtime()
      }
     }
    }
    val index=codec.dequeueOutputBuffer(info,10_000)
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {check(!started);track=mux.addTrack(codec.outputFormat);mux.start();started=true}
    else if(index>=0) {
     val buffer=checkNotNull(codec.getOutputBuffer(index))
     if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size=0
     if(info.size>0) {check(started);buffer.position(info.offset);buffer.limit(info.offset+info.size);mux.writeSampleData(track,buffer,info)}
     outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
     codec.releaseOutputBuffer(index,false);lastProgress=SystemClock.elapsedRealtime()
    }
    check(SystemClock.elapsedRealtime()-lastProgress<30_000) {"视频编码器超时"}
   }
   check(started) {"视频没有有效内容"};mux.stop();started=false
  } finally {
   if(codecStarted) runCatching {codec.stop()};codec.release()
   if(started)runCatching {muxer?.stop()};muxer?.release()
  }
 }
 private fun loadFrame(file:File,width:Int,height:Int):Bitmap {
  val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,bounds)
  check(bounds.outWidth>0 && bounds.outHeight>0) {"照片无法解码：${file.name}"}
  val options=BitmapFactory.Options().apply {
   inPreferredConfig=Bitmap.Config.ARGB_8888
   var sample=1;while(bounds.outWidth/(sample*2)>=width && bounds.outHeight/(sample*2)>=height)sample*=2;inSampleSize=sample
  }
  val source=BitmapFactory.decodeFile(file.absolutePath,options) ?: error("照片无法解码")
  val exif=ExifInterface(file)
  val transform=Matrix().apply {if(exif.isFlipped)postScale(-1f,1f);postRotate(exif.rotationDegrees.toFloat())}
  val oriented=Bitmap.createBitmap(source,0,0,source.width,source.height,transform,true)
  if(oriented!==source)source.recycle()
  try {
   val target=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
   val scale=minOf(width.toFloat()/oriented.width,height.toFloat()/oriented.height)
   val w=oriented.width*scale;val h=oriented.height*scale
   Canvas(target).apply {drawColor(Color.BLACK);drawBitmap(oriented,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))}
   return target
  } finally {oriented.recycle()}
 }
 private fun validate(file:File,expectedFrames:Int,encoding:VideoEncoding,output:VideoOutput,cancelled:()->Boolean,progress:(String)->Unit) {
  check(file.length()>0) {"视频文件为空"}
  val extractor=MediaExtractor();var decoder:MediaCodec?=null;var started=false
  try {
   extractor.setDataSource(file.absolutePath)
   val track=(0 until extractor.trackCount).firstOrNull {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true} ?: error("没有视频轨道")
   val format=extractor.getTrackFormat(track)
   check(format.getInteger(MediaFormat.KEY_WIDTH)==output.width && format.getInteger(MediaFormat.KEY_HEIGHT)==output.height)
   check(format.getString(MediaFormat.KEY_MIME)==encoding.mime) {"视频编码格式校验失败"}
   extractor.selectTrack(track)
   val codec=MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)));decoder=codec
   codec.configure(format,null,null,0);codec.start();started=true
   var inputDone=false;var outputDone=false;var count=0;var lastProgress=SystemClock.elapsedRealtime();val info=MediaCodec.BufferInfo()
   while(!outputDone) {
    check(!cancelled()) {"合成已取消，原始照片已保留"}
    if(!inputDone) {
     val index=codec.dequeueInputBuffer(10_000)
     if(index>=0) {
      val buffer=checkNotNull(codec.getInputBuffer(index));buffer.clear()
      val size=extractor.readSampleData(buffer,0)
      if(size<0) {codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
      else {codec.queueInputBuffer(index,0,size,extractor.sampleTime,0);extractor.advance()}
     }
    }
    val index=codec.dequeueOutputBuffer(info,10_000)
    if(index>=0) {
     if(info.size>0) {check(output.timestampMatches(count,info.presentationTimeUs)) {"视频帧率校验失败"};count++;progress("校验视频 $count / $expectedFrames")}
     outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
     codec.releaseOutputBuffer(index,false);lastProgress=SystemClock.elapsedRealtime()
    }
    check(SystemClock.elapsedRealtime()-lastProgress<30_000) {"视频校验超时"}
   }
   check(count==expectedFrames) {"视频校验失败：解码 $count 帧，预期 $expectedFrames 帧"}
  } finally {if(started)runCatching {decoder?.stop()};decoder?.release();extractor.release()}
 }
}
