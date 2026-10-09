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
 private var starting=false
 private var preparation:Job?=null
 private var stopRequested=false
 private var lastNotification=0L
 private var foreground=false
 private var autoVideo=false
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
 override fun onCreate() {
  super.onCreate()
  indicator=CaptureIndicator(this)
  val lockManager=com.xperia.timelapse.power.CaptureLockManager(this)
  lockSchedule=com.xperia.timelapse.power.CaptureLockSchedule(scope) {
   runCatching {lockManager.lock()}.onFailure {android.util.Log.e("XperiaPower","自动锁屏失败",it)}
  }
  camera=CameraXController(this);projects=TimelapseProjectRepository(this)
  scheduler=TimelapseScheduler(scope,{SystemClock.elapsedRealtime()}, { index ->
   val resolution=camera.capture(projects.frame(index)); projects.captured(index,resolution.first,resolution.second)
  }, { s ->
   lockSchedule.update(s.phase,autoLock)
   camera.close();ready=false
   try {
    projects.finish(s)
    projects.publishPhotos(s)
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
    runCatching {projects.finish(s.copy(phase=Phase.ERROR,error=e.message))};throw e
   } finally {
    wake?.let { if(it.isHeld) it.release() }; wake=null
    camera.close();ready=false
    if(foreground) stopForeground(STOP_FOREGROUND_REMOVE)
    foreground=false;stopSelf()
   }
  }, { projects.log(it) })
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture","延时拍摄",NotificationManager.IMPORTANCE_LOW))
  scope.launch { scheduler.state.collect {
   val displayed=if(savedVideo && it.phase in listOf(Phase.COMPLETED,Phase.STOPPED))it.copy(processingMessage="视频已保存到相册") else it
   uiState.value=displayed;lockSchedule.update(displayed.phase,autoLock);indicator.update(displayed.phase,indicatorEnabled);if(foreground)notifyState(displayed)
  } }
 }
 override fun onBind(intent:Intent): IBinder { super.onBind(intent);return binder }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  super.onStartCommand(intent,flags,startId)
  if(intent?.action=="STOP") stop()
  return START_NOT_STICKY
 }
 suspend fun openCamera(id:String,selectedAperture:Float?=null,tuning:CameraTuning=camera.tuning) {
  if(state.value.busy || starting) return
  ready=false;camera.open(this,id);camera.setAperture(selectedAperture);camera.setTuning(tuning);cameraId=id;aperture=selectedAperture;ready=true
 }
 suspend fun setTuning(value:CameraTuning):CameraTuning {
  check(ready && !starting && !state.value.busy) {"请结束拍摄后调整白平衡和 EV"}
  return camera.setTuning(value)
 }
 suspend fun focus(point:androidx.camera.core.MeteringPoint):Boolean {
  check(ready && !starting && !state.value.busy) {"拍摄中对焦已锁定，请结束拍摄后调整"}
  return camera.focus(point)
 }
 fun begin(config:TimelapseConfig,showIndicator:Boolean=true,lockAfter30Seconds:Boolean=false) {
  if(starting || state.value.busy) return
  if(!ready) { uiState.value=TimelapseState(Phase.ERROR,error="等待相机预览就绪后再拍摄");return }
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
  preparation=scope.launch {
   var projectPath:String?=null
   try {
    val path=projects.create(config,cameraId)
    projectPath=path.absolutePath
    val parameters=withTimeoutOrNull(15_000) { camera.lock(config) }
     ?: throw IllegalStateException("相机准备超时，请重新开始拍摄")
    projects.parameters(parameters)
    if(stopRequested) {
     val terminal=TimelapseState(Phase.STOPPED,projectPath=path.absolutePath)
     projects.finish(terminal);wake?.let {if(it.isHeld)it.release()};wake=null;camera.close();ready=false;stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf()
     starting=false;uiState.value=terminal;return@launch
    }
    scheduler.start(config,path.absolutePath)
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
 fun stop() { if(state.value.phase==Phase.PROCESSING) {cancelComposition.set(true);return}; stopRequested=true; if(starting)preparation?.cancel() else scheduler.stop() }
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
