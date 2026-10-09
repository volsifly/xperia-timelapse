package com.xperia.timelapse.power

import com.xperia.timelapse.timelapse.Phase
import kotlinx.coroutines.*

/** One lock per running segment. Frame updates must never restart the deadline. */
class CaptureLockSchedule(private val scope:CoroutineScope,private val lock:()->Unit) {
 private var previous:Pair<Phase,Boolean>?=null
 private var job:Job?=null
 fun update(phase:Phase,enabled:Boolean) {
  val next=phase to enabled
  if(previous==next)return
  previous=next;job?.cancel();job=null
  if(phase==Phase.RUNNING && enabled)job=scope.launch {delay(30000);lock()}
 }
 fun cancel() {job?.cancel();job=null;previous=null}
}
