package com.xperia.timelapse.power
import com.xperia.timelapse.timelapse.Phase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureLockScheduleTest {
 @Test fun frameUpdatesDoNotDelayOrRepeatTheLock()=runTest {
  var locks=0;val s=CaptureLockSchedule(this) {locks++}
  s.update(Phase.RUNNING,true);runCurrent()
  repeat(29) {advanceTimeBy(1000);s.update(Phase.RUNNING,true)}
  assertEquals(0,locks);advanceTimeBy(1000);runCurrent();assertEquals(1,locks)
  s.update(Phase.RUNNING,true);advanceTimeBy(60000);assertEquals(1,locks)
 }
 @Test fun allTerminalStatesCancelPendingLock()=runTest {
  var locks=0;val s=CaptureLockSchedule(this) {locks++}
  for(phase in listOf(Phase.STOPPING,Phase.STOPPED,Phase.COMPLETED,Phase.ERROR,Phase.PROCESSING)) {
   s.update(Phase.RUNNING,true);advanceTimeBy(10000);s.update(phase,true);advanceUntilIdle()
  }
  assertEquals(0,locks)
 }
 @Test fun pauseCancelsAndResumeGetsAFreshDeadline()=runTest {
  var locks=0;val s=CaptureLockSchedule(this) {locks++}
  s.update(Phase.RUNNING,true);advanceTimeBy(20000);s.update(Phase.PAUSED,true);advanceTimeBy(40000);assertEquals(0,locks)
  s.update(Phase.RUNNING,true);advanceTimeBy(29999);assertEquals(0,locks);advanceTimeBy(1);runCurrent();assertEquals(1,locks)
 }
 @Test fun disabledFeatureNeverLocksAndDestroyCancels()=runTest {
  var locks=0;val s=CaptureLockSchedule(this) {locks++}
  s.update(Phase.RUNNING,false);advanceTimeBy(60000);assertEquals(0,locks)
  s.update(Phase.RUNNING,true);runCurrent();s.cancel();advanceUntilIdle();assertEquals(0,locks)
 }
}
