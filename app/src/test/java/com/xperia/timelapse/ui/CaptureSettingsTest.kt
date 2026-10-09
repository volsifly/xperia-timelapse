package com.xperia.timelapse.ui
import com.xperia.timelapse.video.*
import org.junit.Assert.*
import org.junit.Test
class CaptureSettingsTest {
 @Test fun timelapseAlwaysComposesOnFinish() {assertTrue(CaptureSettings().config().autoComposeVideo)}
 @Test fun photoModeAlwaysPreservesOriginals() {val c=CaptureSettings(mode=CaptureMode.PHOTO,deletePhotos=true).config();assertEquals(1,c.totalFrames);assertFalse(c.autoComposeVideo);assertFalse(c.deleteOriginalPhotos)}
 @Test fun timerUsesSelectedDelayAndPreservesOriginals() {val c=CaptureSettings(mode=CaptureMode.TIMER,timerSeconds="10").config();assertEquals(10000L,c.delayMs);assertFalse(c.deleteOriginalPhotos)}
 @Test fun invalidTimerCannotStart() {assertThrows(IllegalArgumentException::class.java) {CaptureSettings(mode=CaptureMode.TIMER,timerSeconds="wrong").config()}}
 @Test fun durationAndFrameLimitsStayMutuallyExclusive() {val c=CaptureSettings(duration=true,limit="5").config();assertNull(c.totalFrames);assertEquals(300000L,c.totalDurationMs)}
 @Test fun menuSelectionsReachCaptureConfig() {val c=CaptureSettings(resolution=VideoResolution.UHD,fps=60,preference=VideoPreference.HEVC,quality=VideoQuality.HIGH,deletePhotos=false).config();assertEquals(VideoResolution.UHD,c.videoResolution);assertEquals(60,c.videoFrameRate);assertEquals(VideoPreference.HEVC,c.videoPreference);assertFalse(c.deleteOriginalPhotos)}
 @Test fun switchingModesRetainsIntervalConfiguration() {val s=CaptureSettings(interval="5",limit="300").copy(mode=CaptureMode.PHOTO).copy(mode=CaptureMode.TIMELAPSE);assertEquals(5000L,s.config().intervalMs);assertEquals(300,s.config().totalFrames)}
 @Test fun invalidIntervalCannotReachScheduler() {assertFalse(CaptureSettings(interval="0").validInterval);assertThrows(IllegalArgumentException::class.java) {CaptureSettings(interval="0").config()}}
}
