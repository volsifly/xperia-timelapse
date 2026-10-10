package com.xperia.timelapse.timelapse
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class)
class TimelapseSchedulerTest {
 @Test fun photoIgnoresSixtySecondIntervalAndTimerSettings()=runTest {
  val times=mutableListOf<Long>()
  val scheduler=TimelapseScheduler(this,{testScheduler.currentTime},{times+=testScheduler.currentTime},{})
  scheduler.start(com.xperia.timelapse.ui.CaptureSettings(mode=com.xperia.timelapse.ui.CaptureMode.PHOTO,interval="60",timerSeconds="60").config(),"test")
  advanceUntilIdle();assertEquals(listOf(0L),times);assertEquals(0L,testScheduler.currentTime)
 }
 @Test fun timerIgnoresIntervalAndCountdownAndFinishesImmediately()=runTest {
  val times=mutableListOf<Long>()
  val scheduler=TimelapseScheduler(this,{testScheduler.currentTime},{times+=testScheduler.currentTime},{})
  scheduler.start(com.xperia.timelapse.ui.CaptureSettings(mode=com.xperia.timelapse.ui.CaptureMode.TIMER,interval="60",timerSeconds="60").config(),"test")
  advanceUntilIdle();assertEquals(listOf(0L),times);assertEquals(0L,testScheduler.currentTime)
 }
 @Test fun compensatesCaptureTime()=runTest {
  val times=mutableListOf<Long>(); var final:TimelapseState?=null
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{times+=testScheduler.currentTime;delay(470)},{final=it})
  s.start(TimelapseConfig(totalFrames=3),"test");advanceUntilIdle()
  assertEquals(listOf(0L,2000L,4000L),times);assertEquals(Phase.COMPLETED,final!!.phase)
 }
 @Test fun skipsMissedSlotsRatherThanBursting()=runTest {
  val times=mutableListOf<Long>()
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{times+=testScheduler.currentTime;delay(3500)},{})
  s.start(TimelapseConfig(totalFrames=3),"test");advanceUntilIdle()
  assertEquals(listOf(0L,4000L,8000L),times)
 }
 @Test fun stopDuringCountdownWritesNoFrame()=runTest {
  var count=0;var final:TimelapseState?=null
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{count++},{final=it})
  s.start(TimelapseConfig(delayMs=10000),"test");advanceTimeBy(1000);s.stop();advanceUntilIdle()
  assertEquals(0,count);assertEquals(Phase.STOPPED,final!!.phase)
 }
 @Test fun retriesFailedFrameWithoutIncreasingCount()=runTest {
  var attempts=0;var final:TimelapseState?=null
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{attempts++;if(attempts<3)error("busy")},{final=it})
  s.start(TimelapseConfig(totalFrames=1),"test");advanceUntilIdle()
  assertEquals(3,attempts);assertEquals(1,final!!.capturedFrames)
 }
 @Test fun durationLimitStopsAtBoundary()=runTest {
  var count=0
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{count++},{})
  s.start(TimelapseConfig(totalFrames=null,totalDurationMs=6000),"test");advanceUntilIdle()
  assertEquals(3,count)
 }
 @Test fun pauseFreezesDurationAndResumes()=runTest {
  var count=0
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{count++},{})
  s.start(TimelapseConfig(totalFrames=null,totalDurationMs=6000),"test")
  advanceTimeBy(1000);s.pause();advanceTimeBy(10000);assertEquals(1,count)
  s.resume();advanceUntilIdle();assertEquals(3,count)
 }
 @Test fun persistentFailureEndsSession()=runTest {
  var final:TimelapseState?=null
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{error("camera disconnected")},{final=it})
  s.start(TimelapseConfig(),"test");advanceUntilIdle()
  assertEquals(Phase.ERROR,final!!.phase);assertEquals(0,final!!.capturedFrames)
 }
 @Test fun stopWaitsForCurrentPhotoAndPreventsNext()=runTest {
  var count=0;var final:TimelapseState?=null
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{delay(700);count++},{final=it})
  s.start(TimelapseConfig(),"test");advanceTimeBy(100);s.stop();advanceUntilIdle()
  assertEquals(1,count);assertEquals(1,final!!.capturedFrames);assertEquals(Phase.STOPPED,final!!.phase)
 }
 @Test fun completedSessionCanStartAgain()=runTest {
  var count=0
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{count++},{})
  repeat(2) {s.start(TimelapseConfig(totalFrames=1),"test");advanceUntilIdle()}
  assertEquals(2,count);assertEquals(1,s.state.value.capturedFrames)
 }
 @Test fun metadataFailureIsVisible()=runTest {
  val s=TimelapseScheduler(this,{testScheduler.currentTime},{},{error("disk full")})
  s.start(TimelapseConfig(totalFrames=1),"test");advanceUntilIdle()
  assertEquals(Phase.ERROR,s.state.value.phase);assertTrue(s.state.value.error!!.contains("disk full"))
 }
 @Test fun invalidConfigRejected() { assertThrows(IllegalArgumentException::class.java) {TimelapseConfig(intervalMs=0)} }
}
