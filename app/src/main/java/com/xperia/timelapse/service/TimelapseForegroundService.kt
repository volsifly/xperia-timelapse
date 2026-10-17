package com.xperia.timelapse.service
import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import java.util.concurrent.atomic.AtomicBoolean
import androidx.lifecycle.LifecycleService
import com.xperia.timelapse.camera.*
import com.xperia.timelapse.data.*
import com.xperia.timelapse.timelapse.*
import com.xperia.timelapse.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TimelapseForegroundService: LifecycleService() {
 inner class LocalBinder: Binder() { val service get()=this@TimelapseForegroundService }
 private val binder=LocalBinder()
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 lateinit var camera: CameraXController; private set
 private lateinit var projects: TimelapseProjectRepository
 private lateinit var scheduler: TimelapseScheduler
 private var wake: PowerManager.WakeLock?=null
 private var shutterTimestamp=0L
 private var focusRequests=0
 private val metering=MutableStateFlow(false)
 val focusBusy=metering.asStateFlow()
 private var queuedCapture:(()->Unit)?=null
 private var pendingParameters:org.json.JSONObject?=null
 private val preparationTrace=mutableListOf<String>()
 private var starting=false
 private var preparation:Job?=null
 private var stopRequested=false
 private var lastNotification=0L
 private var foreground=false
 private var autoVideo=false
 private var regenerating=false
 private var savedVideo=false
 private lateinit var indicator:CaptureIndicator
 private var indicatorEnabled=true
 private var autoLock=false
 private lateinit var lockSchedule:com.xperia.timelapse.power.CaptureLockSchedule
 private val cancelComposition=AtomicBoolean(false)
 private val uiState=MutableStateFlow(TimelapseState())
 val state=uiState.asStateFlow()
 var cameraId=""; private set
 var aperture:Float?=null; private set
 private var ready=false
 val previewReady get()=ready
 override fun onCreate() {
  super.onCreate()
  indicator=CaptureIndicator(this)
  val lockManager=com.xperia.timelapse.power.CaptureLockManager(this)
  lockSchedule=com.xperia.timelapse.power.CaptureLockSchedule(scope) {
   runCatching {lockManager.lock()}.onFailure {android.util.Log.e("XperiaPower","自动锁屏失败",it)}
  }
  camera=CameraXController(this);projects=TimelapseProjectRepository(this)
  camera.onCaptureStage={stage,time->scope.launch {
   val elapsed=time-shutterTimestamp
   if(uiState.value.busy && !autoVideo)uiState.value=uiState.value.copy(processingMessage=when(stage) {
    "request"->"已发出拍摄请求"
    "capture_started"->"相机已开始拍摄"
    "camera_saved"->"照片已拍下，正在处理色彩"
    "processed"->"正在保存到相册"
    else->uiState.value.processingMessage ?: "正在拍摄"
   })
   if(stage=="request") {
    val trace=preparationTrace.toList();preparationTrace.clear()
    runCatching {projects.log(trace.joinToString("\n"))}
   }
   runCatching {projects.log("shutter_elapsed_ms=$elapsed stage=$stage")}
  }}
  scheduler=TimelapseScheduler(scope,{SystemClock.elapsedRealtime()}, { index ->
   val resolution=camera.capture(projects.frame(index)); projects.captured(index,resolution.first,resolution.second,camera.lastCapturedAperture,pendingParameters);pendingParameters=null
  }, { s ->
   lockSchedule.update(s.phase,autoLock)
   var keepPreview=!autoVideo && s.phase in listOf(Phase.COMPLETED,Phase.STOPPED)
   if(!keepPreview) {camera.close();ready=false}
   try {
    coroutineScope {
     val metadata=async {projects.finish(s)}
     val photos=async {projects.publishPhotos(s)}
     awaitAll(metadata,photos)
    }
    projects.awaitMetadata()
    if(autoVideo && s.capturedFrames>=2 && s.phase in listOf(Phase.COMPLETED,Phase.STOPPED)) {
     val initial=s.copy(phase=Phase.PROCESSING,nextCaptureInMs=0,estimatedEndMs=null,processingMessage="准备合成视频")
     uiState.value=initial;indicator.update(initial.phase,indicatorEnabled)
     ServiceCompat.startForeground(this,10,notification(initial),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
     projects.composeVideo(s,{cancelComposition.get()}) { message ->
      val processing=s.copy(phase=Phase.PROCESSING,nextCaptureInMs=0,estimatedEndMs=null,processingMessage=message)
      uiState.value=processing;notifyState(processing)
     }
     savedVideo=true
    }
   } catch(e:Exception) {
    keepPreview=false
    runCatching {projects.finish(s.copy(phase=Phase.ERROR,error=e.message))};throw e
   } finally {
    wake?.let { if(it.isHeld) it.release() }; wake=null
    if(!keepPreview) {camera.close();ready=false}
    if(foreground) stopForeground(STOP_FOREGROUND_REMOVE)
    foreground=false;stopSelf()
   }
  }, { projects.log(it) })
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture","延时拍摄",NotificationManager.IMPORTANCE_LOW))
  scope.launch { scheduler.state.collect {
   val displayed=if(savedVideo && it.phase in listOf(Phase.COMPLETED,Phase.STOPPED))it.copy(processingMessage="视频已保存到相册") else if(!autoVideo && it.phase in listOf(Phase.COMPLETED,Phase.STOPPED))it.copy(processingMessage=camera.apertureWarning ?: camera.lastCapturedAperture?.let {f->"照片已保存 · 实际 F$f"}) else it
   uiState.value=displayed;lockSchedule.update(displayed.phase,autoLock);indicator.update(displayed.phase,indicatorEnabled);if(foreground)notifyState(displayed)
  } }
 }
 override fun onBind(intent:Intent): IBinder { super.onBind(intent);return binder }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  super.onStartCommand(intent,flags,startId)
  when(intent?.action) {
   "STOP"->stop()
   "REGENERATE_PROJECT"->intent.getStringExtra("project_path")?.let(::regenerateProject)
  }
  return START_NOT_STICKY
 }
 private fun regenerateProject(path:String) {
  if(regenerating || state.value.busy)return
  regenerating=true;cancelComposition.set(false);autoVideo=true;savedVideo=false
  val frameCount=runCatching {org.json.JSONObject(java.io.File(path,"metadata.json").readText()).optInt("frame_count")}.getOrDefault(0)
  val initial=TimelapseState(phase=Phase.PROCESSING,capturedFrames=frameCount,processingMessage="准备重新生成视频",projectPath=path)
  uiState.value=initial
  ServiceCompat.startForeground(this,10,notification(initial),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
  foreground=true
  scope.launch {
   try {
    val result=projects.regenerateVideo(java.io.File(path),{cancelComposition.get()}) {message->
     val processing=uiState.value.copy(phase=Phase.PROCESSING,processingMessage=message)
     uiState.value=processing;notifyState(processing)
    }
    savedVideo=true
    val completed=result.copy(processingMessage="视频已重新生成并保存到相册")
    uiState.value=completed;notifyState(completed)
   } catch(e:Exception) {
    val failed=uiState.value.copy(phase=Phase.ERROR,error=e.message ?: "视频生成失败",processingMessage=null)
    uiState.value=failed;notifyState(failed)
   } finally {
    regenerating=false;autoVideo=false
    if(foreground)stopForeground(STOP_FOREGROUND_REMOVE)
    foreground=false;stopSelf()
   }
  }
 }
 suspend fun openCamera(id:String,selectedAperture:Float?=null,tuning:CameraTuning=camera.tuning) {
  if(state.value.busy || starting) return
  ready=false;camera.open(this,id);camera.setAperture(selectedAperture);camera.setTuning(tuning);cameraId=id;aperture=selectedAperture;ready=true
 }
 suspend fun setAperture(value:Float?) {
  check(ready && !starting && !state.value.busy && focusRequests==0) {"请等待对焦测光完成后切换光圈"}
  camera.setAperture(value);aperture=value
 }
 suspend fun prepareCapture(config:TimelapseConfig) {
  check(ready && !starting && !state.value.busy && focusRequests==0) {"请等待对焦测光完成后准备相机"}
  ready=false
  try {camera.prepareFormat(this,config)}finally {ready=true}
 }
 suspend fun setTuning(value:CameraTuning):CameraTuning {
  check(ready && !starting && !state.value.busy && focusRequests==0) {"请等待对焦测光完成后调整白平衡和 EV"}
  return camera.setTuning(value)
 }
 suspend fun focus(point:androidx.camera.core.MeteringPoint):Boolean {
  check(ready && !starting && !state.value.busy) {"拍摄中对焦已锁定，请结束拍摄后调整"}
  focusRequests++;metering.value=true
  var completed=false
  try {return camera.focus(point).also {completed=true}}
  finally {
   focusRequests--;metering.value=focusRequests>0
   if(focusRequests==0) {
    val pending=queuedCapture;queuedCapture=null
    if(completed)pending?.invoke()
    else if(pending!=null)uiState.value=uiState.value.copy(processingMessage="对焦测光未完成，未拍摄，请重试")
   }
  }
 }
 fun begin(config:TimelapseConfig,showIndicator:Boolean=true,lockAfter30Seconds:Boolean=false,requestedAt:Long=SystemClock.elapsedRealtime()) {
  if(starting || state.value.busy) return
  if(focusRequests>0) {queuedCapture={begin(config,showIndicator,lockAfter30Seconds,requestedAt)};uiState.value=uiState.value.copy(processingMessage="对焦测光完成后拍摄");return}
  if(!ready) { uiState.value=TimelapseState(Phase.ERROR,error="等待相机预览就绪后再拍摄");return }
  pendingParameters=null;preparationTrace.clear()
  shutterTimestamp=requestedAt
  autoLock=lockAfter30Seconds
  lockSchedule.update(Phase.COUNTDOWN,autoLock)
  indicatorEnabled=showIndicator
  starting=true;stopRequested=false;savedVideo=false;cancelComposition.set(false);autoVideo=config.autoComposeVideo
  try {
   // Called only from the visible activity; acquire foreground status before it backgrounds.
   ServiceCompat.startForeground(this,10,notification(TimelapseState(Phase.COUNTDOWN)),if(Build.VERSION.SDK_INT>=30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
   foreground=true; startService(Intent(this,javaClass))
   val planned=config.delayMs+(config.totalDurationMs ?: config.totalFrames!!.toLong()*config.intervalMs)
   wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"XperiaTimelapse:capture").apply { acquire(planned+24*60*60*1000L) }
  } catch(e:Exception) {
   starting=false;uiState.value=TimelapseState(Phase.ERROR,error=e.message)
   wake?.let {if(it.isHeld)it.release()};wake=null
   if(foreground)stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();return
  }
  uiState.value=TimelapseState(Phase.COUNTDOWN)
  val shutterTime=SystemClock.elapsedRealtime()
  preparation=scope.launch {
   var projectPath:String?=null
   try {
    camera.prepareFormat(this@TimelapseForegroundService,config)
    camera.captureResolution=config.videoResolution
    camera.colorLook=config.colorLook
    val path=projects.create(config,cameraId,deferMetadata=!config.autoComposeVideo)
    projectPath=path.absolutePath
    preparationTrace+="shutter_elapsed_ms=${SystemClock.elapsedRealtime()-shutterTimestamp} stage=prepared"
    val parameters=withTimeoutOrNull(15_000) { camera.lock(config) }
     ?: throw IllegalStateException("相机准备超时，请重新开始拍摄")
    if(config.autoComposeVideo) {projects.parameters(parameters);projects.awaitMetadata()} else pendingParameters=parameters
    if(stopRequested) {
     val terminal=TimelapseState(Phase.STOPPED,projectPath=path.absolutePath)
     projects.finish(terminal);wake?.let {if(it.isHeld)it.release()};wake=null;camera.close();ready=false;stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf()
     starting=false;uiState.value=terminal;return@launch
    }
    // Timer deadline begins at the shutter press, not after camera preparation.
    val remainingDelay=(config.delayMs-(SystemClock.elapsedRealtime()-shutterTime)).coerceAtLeast(0)
    preparationTrace+="shutter_elapsed_ms=${SystemClock.elapsedRealtime()-shutterTimestamp} stage=scheduler_start delay_ms=$remainingDelay"
    scheduler.start(config.copy(delayMs=remainingDelay),path.absolutePath)
   } catch(e:Exception) {
    val terminal=TimelapseState(if(stopRequested)Phase.STOPPED else Phase.ERROR,projectPath=projectPath,error=if(stopRequested)null else e.message)
    withContext(NonCancellable) {
     if(projectPath!=null)runCatching { projects.finish(terminal) }
     wake?.let {if(it.isHeld)it.release()};wake=null;camera.close();ready=false;stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf()
     starting=false;uiState.value=terminal
    }
   } finally { starting=false;preparation=null }
  }
 }
 fun pause()=scheduler.pause()
 fun resume()=scheduler.resume()
 fun stop() { queuedCapture=null;if(state.value.phase==Phase.PROCESSING) {cancelComposition.set(true);return}; stopRequested=true; if(starting)preparation?.cancel() else scheduler.stop() }
 private fun notification(s:TimelapseState):Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,1,Intent(this,javaClass).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_camera)
   .setContentTitle("Xperia 延时拍摄 · ${s.capturedFrames} 张")
   .setContentText("${s.processingMessage ?: "${s.phase.label} · 下一张 ${(s.nextCaptureInMs+999)/1000} 秒后"}")
   .setContentIntent(open).setOngoing(true).addAction(0,if(s.phase==Phase.PROCESSING)"取消合成" else "停止拍摄",stop).build()
 }
 private fun notifyState(s:TimelapseState) {
  val time=SystemClock.elapsedRealtime()
  if(time-lastNotification>=1000) {lastNotification=time;getSystemService(NotificationManager::class.java).notify(10,notification(s))}
 }
 override fun onDestroy() {
  lockSchedule.cancel();indicator.clear();cancelComposition.set(true);scheduler.stop();camera.close();scope.cancel()
  wake?.let { if(it.isHeld) it.release() };super.onDestroy()
 }
}
