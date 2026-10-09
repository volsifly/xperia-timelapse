package com.xperia.timelapse.video
import org.junit.Assert.*
import org.junit.Test
class CaptureImageGeometryTest {
 @Test fun landscapeRetainsFourThreeInsteadOfLetterboxing() {assertEquals(1920 to 1440,CaptureImageGeometry.dimensions(4032,3024,1920))}
 @Test fun portraitRetainsOrientationAndAspect() {assertEquals(1440 to 1920,CaptureImageGeometry.dimensions(3024,4032,1920))}
 @Test fun presetsControlActualPhotoLongEdge() {VideoResolution.values().forEach {assertEquals(it.width to it.height,CaptureImageGeometry.dimensions(3840,2160,it.width))}}
 @Test fun oddSensorSizeProducesEvenEncoderDimensions() {val size=CaptureImageGeometry.dimensions(4001,3001,1280);assertEquals(1280,size.first);assertEquals(0,size.second%2)}
 @Test fun portraitVideoUsesExactPhotoPixels() {val o=VideoOutput(fps=30,frameWidth=1440,frameHeight=1920);assertEquals(1440,o.width);assertEquals(1920,o.height);assertEquals(1_000_000L,o.presentationTimeUs(30))}
}
