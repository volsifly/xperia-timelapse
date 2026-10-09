package com.xperia.timelapse.video
import java.io.File
/** Caller must persist verification before invoking. Restricts deletion to known frame files. */
object VerifiedPhotoCleanup {
 fun delete(frames:List<File>,video:File,verified:Boolean):Int {
  check(verified && video.isFile && video.length()>0) {"视频未通过校验，禁止删除照片"}
  require(frames.all {it.parentFile?.canonicalFile==video.parentFile?.canonicalFile && Regex("frame_\\d{6}\\.jpg").matches(it.name) && it.canonicalFile.parentFile==video.canonicalFile.parentFile})
  var count=0
  frames.forEach {if(it.exists()){check(it.delete()) {"视频已保存，但照片清理失败：${it.name}"};count++}}
  return count
 }
}
