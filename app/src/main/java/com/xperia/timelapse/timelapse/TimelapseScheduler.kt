package com.xperia.timelapse.timelapse

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One sequential writer. Monotonic deadlines skip missed slots, never burst to catch up. */
class TimelapseScheduler(
 private val scope: CoroutineScope, private val now: () -> Long,
 private val capture: suspend (Int) -> Unit,
 private val finish: suspend (TimelapseState) -> Unit,
 private val log: suspend (String) -> Unit = {}
) {
 private val mutableState = MutableStateFlow(TimelapseState())
 val state = mutableState.asStateFlow()
 private var job: Job? = null
 private var paused = false
 private var stopped = false
 fun start(config: TimelapseConfig, path: String) {
  check(job?.isActive != true)
  paused = false; stopped = false
  mutableState.value = TimelapseState(Phase.COUNTDOWN, targetFrames=config.totalFrames, projectPath=path)
  job = scope.launch {
   var active = 0L; var last = now(); var next = config.delayMs; var count = 0
   var terminal: TimelapseState? = null
   try {
    while (!stopped) {
     val time = now(); if (!paused) active += (time-last).coerceAtLeast(0); last=time
     if (paused) {
      mutableState.value = mutableState.value.copy(phase=Phase.PAUSED, estimatedEndMs=null)
      delay(100); continue
     }
     if (config.totalDurationMs != null && active >= config.delayMs + config.totalDurationMs) break
     if (config.totalFrames != null && count >= config.totalFrames) break
     val remaining = (next-active).coerceAtLeast(0)
     val remainingSession = config.totalDurationMs?.let { (config.delayMs+it-active).coerceAtLeast(0) }
      ?: (remaining + ((config.totalFrames!!-count-1).coerceAtLeast(0))*config.intervalMs)
     mutableState.value = mutableState.value.copy(
      phase=if (active < config.delayMs) Phase.COUNTDOWN else Phase.RUNNING,
      nextCaptureInMs=remaining, activeElapsedMs=(active-config.delayMs).coerceAtLeast(0),
      estimatedEndMs=System.currentTimeMillis()+remainingSession)
     if (remaining > 0) { delay(minOf(100, remaining)); continue }
     var failure: Exception? = null
     for (attempt in 1..3) {
      try { capture(count+1); failure=null; break }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) { failure=e; log("frame=${count+1} attempt=$attempt: ${e.message}"); if (stopped) break; delay(300) }
     }
     failure?.let { throw it }
     count++
     mutableState.value = mutableState.value.copy(capturedFrames=count)
     // Capture + disk time is included in the same monotonic schedule.
     val after = now(); if (!paused) active += (after-last).coerceAtLeast(0); last=after
     next += config.intervalMs
     if (next < active) next = config.delayMs + ((active-config.delayMs)/config.intervalMs+1)*config.intervalMs
    }
    terminal = mutableState.value.copy(phase=if(stopped) Phase.STOPPED else Phase.COMPLETED, nextCaptureInMs=0, estimatedEndMs=null)
   } catch (e: Exception) {
    terminal=mutableState.value.copy(phase=Phase.ERROR,error=e.message ?: e.javaClass.simpleName, estimatedEndMs=null)
   } finally {
    withContext(NonCancellable) {
     val result=terminal ?: mutableState.value.copy(phase=Phase.STOPPED)
     try { finish(result); mutableState.value=result }
     catch(e:Exception) { mutableState.value=result.copy(phase=Phase.ERROR,error="结束处理失败：${e.message}") }
    }
   }
  }
 }
 fun pause() { if(state.value.busy && state.value.phase != Phase.STOPPING) paused=true }
 fun resume() { paused=false }
 fun stop() { stopped=true; paused=false; if(state.value.busy) mutableState.value=state.value.copy(phase=Phase.STOPPING) }
 suspend fun awaitStopped() { stop(); job?.join() }
}
