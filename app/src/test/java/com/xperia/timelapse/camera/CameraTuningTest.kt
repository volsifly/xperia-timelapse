package com.xperia.timelapse.camera

import org.junit.Assert.*
import org.junit.Test

class CameraTuningTest {
 private val caps=CameraTuningCapabilities(listOf(WhiteBalanceMode.AUTO,WhiteBalanceMode.DAYLIGHT),-6,6,1f/3f)
 @Test fun convertsStopsToCameraIndex() {assertEquals(3,caps.indexFor(1f));assertEquals(-3,caps.indexFor(-1f))}
 @Test fun clampsUnsupportedExposure() {assertEquals(6,caps.indexFor(8f));assertEquals(-6,caps.indexFor(-8f))}
 @Test fun snapsToDeviceStep() {assertEquals(1f/3f,caps.normalize(CameraTuning(ev=.4f)).ev,.0001f)}
 @Test fun unsupportedWhiteBalanceFallsBackToAuto() {assertEquals(WhiteBalanceMode.AUTO,caps.normalize(CameraTuning(WhiteBalanceMode.SHADE)).whiteBalance)}
 @Test fun preservesSupportedWhiteBalance() {assertEquals(WhiteBalanceMode.DAYLIGHT,caps.normalize(CameraTuning(WhiteBalanceMode.DAYLIGHT)).whiteBalance)}
 @Test fun unsupportedEvAndInvalidStepAreNeutral() {
  for(step in listOf(0f,Float.NaN,-1f))assertEquals(0f,caps.copy(evStep=step).normalize(CameraTuning(ev=1f)).ev,0f)
  assertEquals(0f,CameraTuningCapabilities().normalize(CameraTuning(ev=1f)).ev,0f)
 }
 @Test fun invalidStoredEvIsNeutral() {assertEquals(0,caps.indexFor(Float.NaN));assertEquals(0,caps.indexFor(Float.POSITIVE_INFINITY))}
}
