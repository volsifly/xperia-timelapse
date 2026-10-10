package com.xperia.timelapse.ui
import com.xperia.timelapse.video.*
import org.junit.Assert.*
import org.junit.Test
class CaptureSettingsTest {
 @Test fun photoAndTimerUseSelectedImageSize() {listOf(CaptureMode.PHOTO,CaptureMode.TIMER).forEach {assertEquals(VideoResolution.HD,CaptureSettings(mode=it,resolution=VideoResolution.HD).config().videoResolution)}}
 @Test fun rawAndColorReachAllCaptureModes() {CaptureMode.values().forEach {val look=com.xperia.timelapse.color.ColorLook(com.xperia.timelapse.color.ColorStyle.CLASSIC_NEG,.7f);val c=CaptureSettings(mode=it,saveRaw=true,colorLook=look).config();assertTrue(c.saveRaw);assertEquals(look,c.colorLook)}}
 @Test fun timelapseAlwaysComposesOnFinish() {assertTrue(CaptureSettings().config().autoComposeVideo)}
 @Test fun photoModeAlwaysPreservesOriginals() {val c=CaptureSettings(mode=CaptureMode.PHOTO,deletePhotos=true).config();assertEquals(1,c.totalFrames);assertFalse(c.autoComposeVideo);assertFalse(c.deleteOriginalPhotos)}
 @Test fun timerIgnoresOldCountdownAndPreservesOriginals() {val c=CaptureSettings(mode=CaptureMode.TIMER,timerSeconds="10").config();assertEquals(0L,c.delayMs);assertFalse(c.deleteOriginalPhotos)}
 @Test fun oldInvalidTimingSettingsDoNotBlockSingleShots() {for(mode in listOf(CaptureMode.PHOTO,CaptureMode.TIMER)) {val c=CaptureSettings(mode=mode,timerSeconds="wrong",interval="wrong",limit="wrong").config();assertEquals(0L,c.delayMs);assertFalse(c.lockExposure);assertFalse(c.lockFocus);assertFalse(c.lockWhiteBalance)}}
 @Test fun durationAndFrameLimitsStayMutuallyExclusive() {val c=CaptureSettings(duration=true,limit="5").config();assertNull(c.totalFrames);assertEquals(300000L,c.totalDurationMs)}
 @Test fun menuSelectionsReachCaptureConfig() {val c=CaptureSettings(resolution=VideoResolution.UHD,fps=60,preference=VideoPreference.HEVC,quality=VideoQuality.HIGH,deletePhotos=false).config();assertEquals(VideoResolution.UHD,c.videoResolution);assertEquals(60,c.videoFrameRate);assertEquals(VideoPreference.HEVC,c.videoPreference);assertFalse(c.deleteOriginalPhotos)}
 @Test fun switchingModesRetainsIntervalConfiguration() {val s=CaptureSettings(interval="5",limit="300").copy(mode=CaptureMode.PHOTO).copy(mode=CaptureMode.TIMELAPSE);assertEquals(5000L,s.config().intervalMs);assertEquals(300,s.config().totalFrames)}
 @Test fun invalidIntervalCannotReachScheduler() {assertFalse(CaptureSettings(interval="0").validInterval);assertThrows(IllegalArgumentException::class.java) {CaptureSettings(interval="0").config()}}
}
