package com.xperia.timelapse.video

enum class VideoEncoding(val label:String,val mime:String) {
 HEVC("H.265 / HEVC","video/hevc"), AVC("H.264","video/avc"), AV1("AV1","video/av01")
}
enum class VideoPreference(val label:String) { AUTO("自动 · 优先 H.265"), HEVC("H.265"), AVC("H.264"), AV1("AV1") }
enum class VideoQuality(val label:String) { SMALL("节省空间"), STANDARD("标准"), HIGH("高画质") }
object VideoEncodingPolicy {
 fun candidates(preference:VideoPreference,available:Set<VideoEncoding>):List<VideoEncoding> {
  val priority=when(preference) {
   VideoPreference.AUTO,VideoPreference.HEVC -> listOf(VideoEncoding.HEVC,VideoEncoding.AVC)
   VideoPreference.AVC -> listOf(VideoEncoding.AVC)
   VideoPreference.AV1 -> listOf(VideoEncoding.AV1,VideoEncoding.HEVC,VideoEncoding.AVC)
  }
  return priority.filter {it in available}
 }
 fun bitrate(encoding:VideoEncoding,quality:VideoQuality,output:VideoOutput=VideoOutput()):Int {
  val base=when(encoding) {
  VideoEncoding.HEVC -> when(quality){VideoQuality.SMALL->2_000_000;VideoQuality.STANDARD->4_000_000;VideoQuality.HIGH->8_000_000}
  VideoEncoding.AVC -> when(quality){VideoQuality.SMALL->4_000_000;VideoQuality.STANDARD->8_000_000;VideoQuality.HIGH->12_000_000}
  VideoEncoding.AV1 -> when(quality){VideoQuality.SMALL->1_500_000;VideoQuality.STANDARD->3_000_000;VideoQuality.HIGH->6_000_000}
  }
  // Keep the default 1080p/30 rates; scale for pixel count and output frame rate.
  return (base.toLong()*output.width*output.height*output.fps/(1920L*1080*30)).coerceAtLeast(500_000).toInt()
 }
}
data class VideoEncodingResult(val encoding:VideoEncoding,val bitrate:Int,val encoderName:String,val fallback:Boolean)
