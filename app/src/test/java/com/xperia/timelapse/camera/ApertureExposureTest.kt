package com.xperia.timelapse.camera

import org.junit.Assert.*
import org.junit.Test

class ApertureExposureTest {
 private fun plan(sample:ApertureExposure,f:Float,ev:Float=sample.ev,maxNs:Long=100_000_000)=
  ApertureExposurePolicy.adjust(sample,f,ev,100_000,maxNs,100,3200)
 @Test fun stoppingFromF2ToF4PreservesBrightnessWithTwoMoreStops() {
  val s=ApertureExposure(2f,4_000_000,100,0f)
  val p=plan(s,4f)
  assertEquals(16_000_000,p.exposureNs);assertEquals(100,p.iso)
  assertEquals(s.exposureNs*s.iso/4.0,p.exposureNs*p.iso/16.0,.01)
  assertEquals(s,plan(p,2f))
 }
 @Test fun evChangesWorkWithoutSystemAutoExposure() {
  val s=ApertureExposure(4f,4_000_000,200,0f)
  assertEquals(8_000_000,plan(s,4f,1f).exposureNs)
  assertEquals(2_000_000,plan(s,4f,-1f).exposureNs)
 }
 @Test fun shutterLimitTransfersRemainingExposureToIso() {
  val p=plan(ApertureExposure(2f,10_000_000,100,0f),4f,maxNs=20_000_000)
  assertEquals(20_000_000,p.exposureNs);assertEquals(200,p.iso)
 }
 @Test fun hardwareLimitsStayBoundedWhenSceneCannotBeExposed() {
  val p=plan(ApertureExposure(2f,100_000_000,3200,0f),4f,3f)
  assertEquals(100_000_000,p.exposureNs);assertEquals(3200,p.iso)
 }
}
