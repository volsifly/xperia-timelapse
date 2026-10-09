package com.xperia.timelapse.video
import com.xperia.timelapse.timelapse.TimelapseConfig
import org.junit.Assert.*
import org.junit.Test
class VideoOutputTest {
 @Test fun defaultsStay1080p30() {val o=TimelapseConfig().videoOutput;assertEquals(1920,o.width);assertEquals(1080,o.height);assertEquals(30,o.fps)}
 @Test fun everyResolutionIsEvenAndSixteenNine() {VideoResolution.values().forEach {assertEquals(0,it.width%2);assertEquals(0,it.height%2);assertEquals(it.width*9,it.height*16)}}
 @Test fun frameTimestampsDoNotAccumulateRoundingDrift() {VideoOutput.SUPPORTED_FPS.forEach {fps -> val o=VideoOutput(fps=fps);assertEquals(1_000_000L,o.presentationTimeUs(fps));assertEquals(100_000_000L,o.presentationTimeUs(100*fps))}}
 @Test fun incorrectFrameRateFailsValidation() {val o=VideoOutput(fps=60);assertFalse(o.timestampMatches(1,33_333));assertTrue(o.timestampMatches(1,16_667))}
 @Test fun harmlessMuxerRoundingAllowed() {val o=VideoOutput(fps=24);assertTrue(o.timestampMatches(7,o.presentationTimeUs(7)+100));assertFalse(o.timestampMatches(7,o.presentationTimeUs(7)+10_000))}
 @Test fun invalidFpsRejectedAtConfigurationBoundary() {assertThrows(IllegalArgumentException::class.java) {TimelapseConfig(videoFrameRate=0)};assertThrows(IllegalArgumentException::class.java) {VideoOutput(fps=120)}}
 @Test fun fourKHasFourTimesBaselineBitrate() {assertEquals(16_000_000,VideoEncodingPolicy.bitrate(VideoEncoding.HEVC,VideoQuality.STANDARD,VideoOutput(VideoResolution.UHD,30)))}
 @Test fun sixtyFpsScalesBitrateAndShortensPlayback() {val o=VideoOutput(fps=60);assertEquals(8_000_000,VideoEncodingPolicy.bitrate(VideoEncoding.HEVC,VideoQuality.STANDARD,o));assertEquals(1_000_000L,o.presentationTimeUs(60))}
 @Test fun estimateUsesSelectedFrameRate() {assertEquals(1_000_000L,VideoOutput(fps=30).estimatedBytes(60,4_000_000));assertEquals(500_000L,VideoOutput(fps=60).estimatedBytes(60,4_000_000))}
 @Test fun largeProjectEstimateDoesNotOverflow() {assertEquals(20_000_000_000L,VideoOutput(VideoResolution.UHD,60).estimatedBytes(100000,96_000_000))}
 @Test fun estimatesRoundUpToReserveEnoughSpace() {assertEquals(16_667L,VideoOutput().estimatedBytes(1,4_000_000))}
}
