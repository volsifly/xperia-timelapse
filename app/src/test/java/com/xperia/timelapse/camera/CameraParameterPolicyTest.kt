package com.xperia.timelapse.camera

import org.junit.Assert.*
import org.junit.Test

class CameraParameterPolicyTest {
 @Test fun fitCenterFocusRejectsBarsAndMapsVisibleFrame() {
  assertNull(CameraParameterPolicy.previewPoint(100f,500f,2000,1000,4000,3000))
  val center=CameraParameterPolicy.previewPoint(1000f,500f,2000,1000,4000,3000)!!
  assertEquals(.5f,center.first,.001f);assertEquals(.5f,center.second,.001f)
  assertNull(CameraParameterPolicy.previewPoint(500f,100f,1000,2000,4000,3000))
  assertNull(CameraParameterPolicy.previewPoint(0f,0f,0,0,4000,3000))
 }
 @Test fun focusRejectsLetterboxAndNonFiniteCoordinates() {assertFalse(CameraParameterPolicy.validMeteringPoint(-.1f,.5f));assertFalse(CameraParameterPolicy.validMeteringPoint(.5f,1.1f));assertFalse(CameraParameterPolicy.validMeteringPoint(Float.NaN,.5f));assertTrue(CameraParameterPolicy.validMeteringPoint(0f,1f));assertTrue(CameraParameterPolicy.validMeteringPoint(.7f,.3f))}
 @Test fun sonyReportedFocusBeyondAdvertisedLimitIsClamped() {
  assertEquals(6.6666665f,CameraParameterPolicy.focusDistance(6.828616f,6.6666665f)!!,0f)
 }
 @Test fun validPositionAndInfinityFocusArePreserved() {
  assertEquals(3.842448f,CameraParameterPolicy.focusDistance(3.842448f,6.6666665f)!!,0f)
  assertEquals(0f,CameraParameterPolicy.focusDistance(0f,6.6666665f)!!,0f)
 }
 @Test fun negativeMeasuredDistanceIsClampedToInfinity() {
  assertEquals(0f,CameraParameterPolicy.focusDistance(-.001f,6.6666665f)!!,0f)
 }
 @Test fun missingFixedFocusAndNonFiniteValuesDoNotIssueManualFocus() {
  assertNull(CameraParameterPolicy.focusDistance(null,6f))
  assertNull(CameraParameterPolicy.focusDistance(3f,null))
  assertNull(CameraParameterPolicy.focusDistance(3f,0f))
  assertNull(CameraParameterPolicy.focusDistance(Float.NaN,6f))
  assertNull(CameraParameterPolicy.focusDistance(3f,Float.POSITIVE_INFINITY))
 }
}
