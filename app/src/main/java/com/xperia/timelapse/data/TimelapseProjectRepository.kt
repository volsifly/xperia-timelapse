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
import kotlinx.coroutines.*

class TimelapseProjectRepository(context: Context) {
 private val root=StorageHelper.root(context)
 private val photos=VerifiedVideoPublisher(MediaStorePhotoStore(context))
 private val gallery=VerifiedVideoPublisher(MediaStoreVideoStore(context))
 private val metadataGuard=Any()
 private val writes=OrderedIoQueue(CoroutineScope(SupervisorJob()+Dispatchers.IO))
 private lateinit var directory: File
 private lateinit var metadata: JSONObject
 private lateinit var config: TimelapseConfig
 suspend fun create(config: TimelapseConfig, cameraId: String,deferMetadata:Boolean=false): File = withContext(Dispatchers.IO) {
  this@TimelapseProjectRepository.config=config
  StorageHelper.requireSpace(root)
  directory=File(root, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))+"_"+System.nanoTime().toString().takeLast(6))
  check(directory.mkdirs()) { "无法创建项目目录" }
  metadata=JSONObject().put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("android",Build.VERSION.RELEASE)
   .put("project_name",directory.name).put("camera_id",cameraId).put("interval_ms",config.intervalMs)
   .put("start_time",Instant.now().toString()).put("frame_count",0).put("format",if(config.saveRaw)"DNG" else "JPEG").put("color_style",config.colorLook.style.name).put("color_strength",config.colorLook.strength)
   .put("target_frames",config.totalFrames ?: JSONObject.NULL).put("duration_ms",config.totalDurationMs ?: JSONObject.NULL)
   .put("requested_capture_long_edge",config.videoResolution.width).put("requested_video_fps",config.videoFrameRate)
   .put("video_preference",config.videoPreference.name).put("video_quality",config.videoQuality.name)
   .put("auto_compose_video",config.autoComposeVideo).put("delete_original_photos",config.deleteOriginalPhotos)
   .put("status","running")
  if(!deferMetadata)write(); directory
 }
 suspend fun parameters(values: JSONObject) = withContext(Dispatchers.IO) { synchronized(metadataGuard) {metadata.put("parameters", values);write()} }
 fun frame(index: Int): File { StorageHelper.requireSpace(directory); return File(directory,"frame_%06d.jpg".format(index)) }
 suspend fun captured(index: Int, width: Int, height: Int,aperture:Float?=null,parameters:JSONObject?=null) = withContext(Dispatchers.IO) {
  synchronized(metadataGuard) {
  parameters?.let {metadata.put("parameters",it)}
  metadata.put("frame_count",index).put("resolution","${width}x${height}")
  val values=metadata.optJSONArray("captured_apertures") ?: org.json.JSONArray().also {metadata.put("captured_apertures",it)}
  values.put(org.json.JSONObject().put("frame",index).put("f_number",aperture ?: org.json.JSONObject.NULL))
  write()
  }
 }
 suspend fun publishPhotos(state:TimelapseState) = withContext(Dispatchers.IO) {
  if(state.phase !in listOf(Phase.COMPLETED,Phase.STOPPED) || state.capturedFrames<1)return@withContext
  if(config.autoComposeVideo && state.capturedFrames>=2 && !config.saveRaw)return@withContext
  val uris=org.json.JSONArray()
  for(index in 1..state.capturedFrames) {
   val source=File(directory,(if(config.saveRaw)"frame_%06d.dng" else "frame_%06d.jpg").format(index))
   if(!config.saveRaw) {
    val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
    android.graphics.BitmapFactory.decodeFile(source.absolutePath,bounds)
    check(bounds.outWidth>0 && bounds.outHeight>0) {"照片无法读取，原片已保留"}
   }
   check(source.isFile && source.length()>0) {"照片文件缺失，原片已保留"}
   val uri=photos.save(source,"${directory.name}_${source.name}",true,{false})
   synchronized(metadataGuard) {uris.put(uri);metadata.put("gallery_photo_uris",uris);write()}
  }
  synchronized(metadataGuard) {metadata.put("gallery_photos_saved",true).put("raw_originals_preserved",config.saveRaw);write()}
  awaitMetadata()
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
   awaitMetadata()
   progress("正在自动保存到相册")
   val uri=gallery.save(video,"${directory.name}.mp4",verified=true,cancelled=cancelled)
   metadata.put("gallery_saved",true).put("gallery_video_uri",uri).put("gallery_video_name","${directory.name}.mp4");write()
   awaitMetadata()
   if(config.deleteOriginalPhotos) {
    check(!cancelled()) {"清理已取消，原始照片已保留"}
    progress("视频校验通过，正在清理原始照片")
    val deleted=VerifiedPhotoCleanup.delete(frames,video,verified=true)
    metadata.put("deleted_original_count",if(config.saveRaw)0 else deleted).put("original_photos_deleted",!config.saveRaw).put("deleted_video_intermediate_count",if(config.saveRaw)deleted else 0);write()
   }
   awaitMetadata()
  } catch(e:Exception) {
   if(metadata.optString("video_status")!="verified")metadata.put("video_status","failed")
   metadata.put("video_error",e.message).put("remaining_original_count",frames.count {it.isFile})
   runCatching {write();awaitMetadata()};throw e
  }
 }
 suspend fun regenerateVideo(folder:File,cancelled:()->Boolean,progress:(String)->Unit):TimelapseState = withContext(Dispatchers.IO) {
  check(folder.isDirectory && File(folder,"metadata.json").isFile) {"项目文件不存在或元数据缺失"}
  directory=folder
  metadata=JSONObject(File(folder,"metadata.json").readText())
  val frames=(folder.listFiles()?.mapNotNull {file->
   Regex("frame_(\\d{6})\\.jpg").matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()?.let {it to file}
  }?.toMap().orEmpty())
  var captured=0
  while(frames[captured+1]?.let {it.isFile && it.length()>0}==true)captured++
  check(captured>=2) {"至少需要两张已保存的照片才能生成视频"}
  check(!File(folder,"preview.mp4").exists()) {"项目视频已存在，请先从项目详情查看或分享"}
  File(folder,"preview.partial.mp4").delete()
  val requestedLongEdge=metadata.optInt("requested_capture_long_edge",1920)
  val resolution=when {requestedLongEdge>=3000->VideoResolution.UHD;requestedLongEdge<=1280->VideoResolution.HD;else->VideoResolution.FULL_HD}
  val preference=runCatching {VideoPreference.valueOf(metadata.optString("video_preference","AUTO"))}.getOrDefault(VideoPreference.AUTO)
  val quality=runCatching {VideoQuality.valueOf(metadata.optString("video_quality","STANDARD"))}.getOrDefault(VideoQuality.STANDARD)
  val fps=metadata.optInt("requested_video_fps",30).takeIf {it in VideoOutput.SUPPORTED_FPS} ?: 30
  config=TimelapseConfig(saveRaw=false,intervalMs=metadata.optLong("interval_ms",2000).coerceIn(1000,3600000),totalFrames=captured,
   autoComposeVideo=true,deleteOriginalPhotos=metadata.optBoolean("delete_original_photos",false),videoPreference=preference,videoQuality=quality,videoResolution=resolution,videoFrameRate=fps)
  metadata.put("frame_count",captured).put("status","stopped").put("video_status","encoding").put("regeneration",true)
  write();awaitMetadata()
  val state=TimelapseState(phase=Phase.STOPPED,capturedFrames=captured,projectPath=folder.absolutePath)
  composeVideo(state,cancelled,progress)
  state
 }
 suspend fun finish(state: TimelapseState) = withContext(Dispatchers.IO) {
  synchronized(metadataGuard) {
   metadata.put("status",state.phase.name.lowercase()).put("frame_count",state.capturedFrames)
    .put("end_time",Instant.now().toString()).put("error",state.error ?: JSONObject.NULL);write()
  }
  awaitMetadata()
 }
 fun log(message:String) {
  val file=File(directory,"capture.log")
  val line="${Instant.now()} $message\n"
  writes.submit(file.absolutePath) {runCatching {file.appendText(line)}.onFailure {android.util.Log.w("XperiaStorage","诊断日志写入失败",it)};Unit}
 }
 suspend fun awaitMetadata() {writes.await(File(directory,"metadata.json").absolutePath)}
 private fun write() {
  val folder=directory
  val snapshot=synchronized(metadataGuard) {metadata.toString(2).toByteArray(Charsets.UTF_8)}
  writes.submit(File(folder,"metadata.json").absolutePath) {
   val temp=File(folder,"metadata.tmp")
   java.io.FileOutputStream(temp).use {it.write(snapshot);it.fd.sync()}
   check(temp.renameTo(File(folder,"metadata.json"))) {"无法写入项目元数据"}
  }
 }
}
