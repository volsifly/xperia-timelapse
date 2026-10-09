package com.xperia.timelapse.data
import android.content.Context
import android.os.Build
import com.xperia.timelapse.util.StorageHelper
import com.xperia.timelapse.timelapse.*
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import com.xperia.timelapse.video.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimelapseProjectRepository(context: Context) {
 private val root=StorageHelper.root(context)
 private val photos=VerifiedVideoPublisher(MediaStorePhotoStore(context))
 private val gallery=VerifiedVideoPublisher(MediaStoreVideoStore(context))
 private lateinit var directory: File
 private lateinit var metadata: JSONObject
 private lateinit var config: TimelapseConfig
 suspend fun create(config: TimelapseConfig, cameraId: String): File = withContext(Dispatchers.IO) {
  this@TimelapseProjectRepository.config=config
  StorageHelper.requireSpace(root)
  directory=File(root, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_"+System.nanoTime().toString().takeLast(6))
  check(directory.mkdirs()) { "无法创建项目目录" }
  metadata=JSONObject().put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("android",Build.VERSION.RELEASE)
   .put("project_name",directory.name).put("camera_id",cameraId).put("interval_ms",config.intervalMs)
   .put("start_time",Instant.now().toString()).put("frame_count",0).put("format","JPEG")
   .put("target_frames",config.totalFrames ?: JSONObject.NULL).put("duration_ms",config.totalDurationMs ?: JSONObject.NULL)
   .put("requested_capture_long_edge",config.videoResolution.width).put("requested_video_fps",config.videoFrameRate)
   .put("video_preference",config.videoPreference.name).put("video_quality",config.videoQuality.name)
   .put("auto_compose_video",config.autoComposeVideo).put("delete_original_photos",config.deleteOriginalPhotos)
   .put("status","running")
  write(); directory
 }
 suspend fun parameters(values: JSONObject) = withContext(Dispatchers.IO) { metadata.put("parameters", values); write() }
 fun frame(index: Int): File { StorageHelper.requireSpace(directory); return File(directory,"frame_%06d.jpg".format(index)) }
 suspend fun captured(index: Int, width: Int, height: Int) = withContext(Dispatchers.IO) {
  metadata.put("frame_count",index).put("resolution","${width}x${height}"); write()
 }
 suspend fun publishPhotos(state:TimelapseState) = withContext(Dispatchers.IO) {
  if(state.phase !in listOf(Phase.COMPLETED,Phase.STOPPED) || state.capturedFrames<1)return@withContext
  if(config.autoComposeVideo && state.capturedFrames>=2)return@withContext
  val uris=org.json.JSONArray()
  for(index in 1..state.capturedFrames) {
   val source=File(directory,"frame_%06d.jpg".format(index))
   val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
   android.graphics.BitmapFactory.decodeFile(source.absolutePath,bounds)
   check(bounds.outWidth>0 && bounds.outHeight>0) {"照片无法读取，原片已保留"}
   uris.put(photos.save(source,"${directory.name}_${source.name}",true,{false}))
   metadata.put("gallery_photo_uris",uris);write()
  }
  metadata.put("gallery_photos_saved",true);write()
 }
 @android.annotation.SuppressLint("UsableSpace")
 suspend fun composeVideo(state: TimelapseState,cancelled:()->Boolean,progress:(String)->Unit) = withContext(Dispatchers.IO) {
  if(!config.autoComposeVideo || state.capturedFrames<2 || state.phase !in listOf(Phase.COMPLETED,Phase.STOPPED)) return@withContext
  val frames=(1..state.capturedFrames).map {File(directory,"frame_%06d.jpg".format(it))}
  check(frames.all {it.isFile}) {"照片序列缺失，原始照片已保留"}
  val video=File(directory,"preview.mp4")
  val output=PhotoVideoGeometry.output(frames.first(),config.videoFrameRate)
  check(frames.all { PhotoVideoGeometry.output(it,config.videoFrameRate)==output }) {"照片尺寸或方向不一致，原片已保留"}
  try {
   val maxBitrate=VideoEncodingPolicy.candidates(config.videoPreference,VideoEncoding.values().toSet()).maxOf {VideoEncodingPolicy.bitrate(it,config.videoQuality,output)}
   check(directory.usableSpace > output.estimatedBytes(state.capturedFrames,maxBitrate)*2+50L*1024*1024) {"没有足够空间合成并保存视频，原始照片已保留"}
   metadata.put("video_status","encoding");write()
   val result=VideoComposer().compose(frames,video,config.videoPreference,config.videoQuality,output,cancelled,progress)
   metadata.put("video_status","verified").put("video_file",video.name).put("video_fps",config.videoFrameRate)
    .put("video_resolution","${output.width}x${output.height}").put("video_codec",result.encoding.label).put("video_mime",result.encoding.mime)
    .put("video_bitrate",result.bitrate).put("video_encoder",result.encoderName).put("video_fallback",result.fallback).put("video_frame_count",frames.size);write()
   progress("正在自动保存到相册")
   val uri=gallery.save(video,"${directory.name}.mp4",verified=true,cancelled=cancelled)
   metadata.put("gallery_saved",true).put("gallery_video_uri",uri).put("gallery_video_name","${directory.name}.mp4");write()
   if(config.deleteOriginalPhotos) {
    check(!cancelled()) {"清理已取消，原始照片已保留"}
    progress("视频校验通过，正在清理原始照片")
    val deleted=VerifiedPhotoCleanup.delete(frames,video,verified=true)
    metadata.put("deleted_original_count",deleted).put("original_photos_deleted",true);write()
   }
  } catch(e:Exception) {
   if(metadata.optString("video_status")!="verified")metadata.put("video_status","failed")
   metadata.put("video_error",e.message).put("remaining_original_count",frames.count {it.isFile})
   runCatching {write()};throw e
  }
 }
 suspend fun finish(state: TimelapseState) = withContext(Dispatchers.IO) {
  metadata.put("status",state.phase.name.lowercase()).put("frame_count",state.capturedFrames)
   .put("end_time",Instant.now().toString()).put("error",state.error ?: JSONObject.NULL); write()
 }
 suspend fun log(message: String) = withContext(Dispatchers.IO) { File(directory,"capture.log").appendText("${Instant.now()} $message\n") }
 private fun write() {
  val temp=File(directory,"metadata.tmp"); java.io.FileOutputStream(temp).use {it.write(metadata.toString(2).toByteArray(Charsets.UTF_8));it.fd.sync()}
  check(temp.renameTo(File(directory,"metadata.json"))) { "无法写入项目元数据" }
 }
}
