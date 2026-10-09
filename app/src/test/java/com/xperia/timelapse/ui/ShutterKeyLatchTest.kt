package com.xperia.timelapse.ui
import org.junit.Assert.*
import org.junit.Test
class ShutterKeyLatchTest {
 @Test fun completePressFiresOnlyOnRelease() {val k=ShutterKeyLatch();assertFalse(k.event(0));assertTrue(k.event(1));assertFalse(k.event(1))}
 @Test fun longPressFiresOnce() {val k=ShutterKeyLatch();k.event(0);repeat(20){assertFalse(k.event(0,it+1))};assertTrue(k.event(1));assertFalse(k.event(1))}
 @Test fun cancellationDisarmsPress() {val k=ShutterKeyLatch();k.event(0);assertFalse(k.event(1,canceled=true));assertFalse(k.event(1))}
 @Test fun orphanRepeatsAndReleaseDoNotFire() {val k=ShutterKeyLatch();assertFalse(k.event(0,repeat=1));assertFalse(k.event(1))}
 @Test fun losingFocusDisarmsPress() {val k=ShutterKeyLatch();k.event(0);k.reset();assertFalse(k.event(1))}
 @Test fun nextPressWorksAfterCanceledPress() {val k=ShutterKeyLatch();k.event(0);k.event(1,canceled=true);k.event(0);assertTrue(k.event(1))}
}
