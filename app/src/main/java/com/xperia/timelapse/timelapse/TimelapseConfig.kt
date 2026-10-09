package com.xperia.timelapse.timelapse
import com.xperia.timelapse.video.*

data class TimelapseConfig(
 val intervalMs: Long = 2000, val totalFrames: Int? = 100,
 val totalDurationMs: Long? = null, val delayMs: Long = 0,
 val lockExposure: Boolean = true, val lockFocus: Boolean = true,
 val lockWhiteBalance: Boolean = true,
 val autoComposeVideo: Boolean = true, val deleteOriginalPhotos: Boolean = true,
 val videoPreference: VideoPreference = VideoPreference.AUTO, val videoQuality: VideoQuality = VideoQuality.STANDARD,
 val videoResolution: VideoResolution = VideoResolution.FULL_HD, val videoFrameRate: Int = 30
) {
 val videoOutput get()=VideoOutput(videoResolution,videoFrameRate)
 init {
  require(videoFrameRate in VideoOutput.SUPPORTED_FPS)
  require(intervalMs in 1000..3600000 && delayMs in 0..86400000)
  require((totalFrames == null) != (totalDurationMs == null))
  require(totalFrames == null || totalFrames in 1..100000)
  require(totalDurationMs == null || totalDurationMs in 1000..86400000)
 }
}
