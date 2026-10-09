package com.xperia.timelapse.ui

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.xperia.timelapse.data.CameraCapabilityRepository
import com.xperia.timelapse.camera.CameraCapabilities
import com.xperia.timelapse.service.TimelapseForegroundService
import com.xperia.timelapse.timelapse.*
import com.xperia.timelapse.util.PermissionHelper
import com.xperia.timelapse.video.*
import kotlinx.coroutines.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity:ComponentActivity() {
 private val shutterKey=ShutterKeyLatch()
 private var hardwareShutter:(()->Unit)?=null
 private var hardwareFocus:(()->Unit)?=null
 private var focusKeyDown=false
 private fun logShutterKey(event:android.view.KeyEvent) {
  if(event.keyCode in listOf(android.view.KeyEvent.KEYCODE_CAMERA,android.view.KeyEvent.KEYCODE_FOCUS) || event.device?.name=="gpio-keys")
   android.util.Log.i("XperiaShutter","key=${event.keyCode} scan=${event.scanCode} action=${event.action} repeat=${event.repeatCount}")
 }
 override fun onKeyDown(keyCode:Int,event:android.view.KeyEvent):Boolean {
  logShutterKey(event)
  if(keyCode==android.view.KeyEvent.KEYCODE_FOCUS) {if(!focusKeyDown && event.repeatCount==0) {focusKeyDown=true;hardwareFocus?.invoke()};return true}
  if(keyCode==android.view.KeyEvent.KEYCODE_CAMERA) {shutterKey.event(event.action,event.repeatCount,event.isCanceled);return true}
  return super.onKeyDown(keyCode,event)
 }
 override fun onKeyUp(keyCode:Int,event:android.view.KeyEvent):Boolean {
  logShutterKey(event)
  if(keyCode==android.view.KeyEvent.KEYCODE_FOCUS) {focusKeyDown=false;return true}
  if(keyCode==android.view.KeyEvent.KEYCODE_CAMERA) {if(shutterKey.event(event.action,event.repeatCount,event.isCanceled))hardwareShutter?.invoke();return true}
  return super.onKeyUp(keyCode,event)
 }
 override fun onWindowFocusChanged(hasFocus:Boolean) {super.onWindowFocusChanged(hasFocus);if(!hasFocus) {shutterKey.reset();focusKeyDown=false}}

 private var bound=false
 private var granted by mutableStateOf(false)
 private var service by mutableStateOf<TimelapseForegroundService?>(null)
 private val connection=object:ServiceConnection {
  override fun onServiceConnected(name:ComponentName,binder:IBinder) {service=(binder as TimelapseForegroundService.LocalBinder).service}
  override fun onServiceDisconnected(name:ComponentName) {service=null}
 }
 private fun bindCamera() {if(!bound && PermissionHelper.camera(this))bound=bindService(Intent(this,TimelapseForegroundService::class.java),connection,Context.BIND_AUTO_CREATE)}
 override fun onResume() {super.onResume();granted=PermissionHelper.camera(this);if(granted)bindCamera()}
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  window.statusBarColor=android.graphics.Color.rgb(11,13,16);window.navigationBarColor=android.graphics.Color.rgb(20,24,29)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFFFC14D),background=Color(0xFF0B0D10),surface=Color(0xFF171C22),onSurfaceVariant=Color(0xFFABB5C0))) {
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted=it;if(it)bindCamera()}
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var notificationAsked by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(granted) {if(granted && Build.VERSION.SDK_INT>=33 && !notificationAsked){notificationAsked=true;notification.launch(Manifest.permission.POST_NOTIFICATIONS)}}
    Surface(Modifier.fillMaxSize()) {
     if(!granted) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
      Text("XPERIA",style=MaterialTheme.typography.headlineLarge);Text("TIMELAPSE",color=MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(32.dp));Text("打开相机，记录时间的流动",style=MaterialTheme.typography.titleMedium)
      Text("允许相机权限后即可取景、定时拍照和延时拍摄。",Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
      Button(onClick={permission.launch(Manifest.permission.CAMERA)}) {Text("允许使用相机")}
      TextButton(onClick={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}) {Text("打开权限设置")}
     } else service?.let {CaptureContent(it)} ?: Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
    }
   }
  }
 }
 @Composable private fun CaptureContent(s:TimelapseForegroundService) {
  val state by s.state.collectAsStateWithLifecycle()
  val scope=rememberCoroutineScope()
  val preferences=remember {CapturePreferences(this)}
  var settings by remember {mutableStateOf(preferences.load())}
  fun update(value:CaptureSettings) {settings=value;preferences.save(value)}
  val lockManager=remember {com.xperia.timelapse.power.CaptureLockManager(this)}
  var lockAuthorized by remember {mutableStateOf(lockManager.authorized)}
  var pendingStart by remember {mutableStateOf(false)}
  var authorizationError by remember {mutableStateOf<String?>(null)}
  fun beginCapture() {s.begin(settings.config(),settings.indicator,settings.autoLock)}
  val lockAuthorization=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
   lockAuthorized=lockManager.authorized
   if(pendingStart) {
    pendingStart=false
    if(lockAuthorized)runCatching {beginCapture()}.onFailure {authorizationError=it.message}
    else authorizationError="自动锁屏未授权，可在拍摄设置中授权或关闭自动锁屏。"
   }
  }
  DisposableEffect(lifecycle) {
   val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)lockAuthorized=lockManager.authorized}
   lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
  }
  var error by remember {mutableStateOf<String?>(null)}
  var route by rememberSaveable {mutableStateOf<String?>(null)}
  val repository=remember {CameraCapabilityRepository(this)}
  var cameras by remember {mutableStateOf(emptyList<CameraCapabilities>())}
  var cameraId by remember {mutableStateOf(s.cameraId)}
  var ready by remember {mutableStateOf(s.cameraId.isNotEmpty())}
  var switching by remember {mutableStateOf(false)}
  var tuningBusy by remember {mutableStateOf(false)}
  var tuningCaps by remember {mutableStateOf(com.xperia.timelapse.camera.CameraTuningCapabilities())}
  LaunchedEffect(ready,cameraId,state.busy) {if(ready && !state.busy)tuningCaps=s.camera.tuningCapabilities}
  fun changeTuning(value:com.xperia.timelapse.camera.CameraTuning) {
   if(!ready || switching || tuningBusy || state.busy)return
   tuningBusy=true
   scope.launch {try {val actual=s.setTuning(value);update(settings.copy(whiteBalanceMode=actual.whiteBalance,exposureEv=actual.ev));error=null}
    catch(e:Exception){error=e.message}finally{tuningBusy=false}}
  }
  fun selectLens(id:String,value:Float?) {
   if(state.busy || switching || tuningBusy || (id==cameraId && value==s.aperture && ready))return
   switching=true;ready=false
   scope.launch {try {s.openCamera(id,value,settings.tuning);cameraId=id;update(settings.copy(whiteBalanceMode=s.camera.tuning.whiteBalance,exposureEv=s.camera.tuning.ev));ready=true;error=null}catch(e:Exception){error=e.message}finally{switching=false}}
  }
  fun selectCamera(id:String)=selectLens(id,null)
  var selectedProject by rememberSaveable {mutableStateOf<String?>(null)}
  var black by rememberSaveable {mutableStateOf(false)}
  var focusRequest by remember {mutableIntStateOf(0)}
  LaunchedEffect(state.phase,settings.autoBlack) {
   if(!state.busy)black=false
   else if(state.phase==Phase.RUNNING && settings.autoBlack) {
    delay(5000)
    if(s.state.value.phase==Phase.RUNNING) {route=null;black=true}
   }
  }
  var exporting by remember {mutableStateOf(false)}
  val output=s.camera.photoOutput(settings.resolution,settings.fps)
  var codecs by remember(settings.quality,output) {mutableStateOf<Set<VideoEncoding>?>(null)}
  LaunchedEffect(settings.quality,output) {codecs=withContext(Dispatchers.Default){runCatching {VideoComposer().available(settings.quality,output)}.getOrDefault(emptySet())}}
  LaunchedEffect(s) {
   try {
    cameras=withContext(Dispatchers.IO){repository.all()}
    if(cameraId.isEmpty())cameraId=cameras.filter {it.rear && it.selectable}.sortedWith(compareByDescending<CameraCapabilities>{it.sensorArea}.thenByDescending {it.pixels}).firstOrNull()?.id ?: cameras.first {it.selectable}.id
    if(!state.busy){ready=false;val initialAperture=s.aperture ?: cameras.firstOrNull {it.id==cameraId}?.apertures?.firstOrNull {kotlin.math.abs(it-2f)<.01f};s.openCamera(cameraId,initialAperture,settings.tuning);update(settings.copy(whiteBalanceMode=s.camera.tuning.whiteBalance,exposureEv=s.camera.tuning.ev))};ready=true
   }catch(e:Exception){error=e.message;ready=false}
  }
  LaunchedEffect(state.phase) {
   if(state.phase in listOf(Phase.COMPLETED,Phase.STOPPED,Phase.ERROR) && cameraId.isNotEmpty()) {
    try {ready=false;s.openCamera(cameraId,s.aperture,settings.tuning);ready=true}catch(e:Exception){error=e.message;ready=false}
   }
  }
  val needsVideo=settings.mode==CaptureMode.TIMELAPSE
  val valid=when(settings.mode){CaptureMode.PHOTO->true;CaptureMode.TIMER->settings.validTimer;CaptureMode.TIMELAPSE->settings.validInterval}
  val encodingReady=!needsVideo || codecs?.let {VideoEncodingPolicy.candidates(settings.preference,it).isNotEmpty()}==true
  val hint=when { !ready->"正在准备相机…";!valid->"请在菜单中完成拍摄设置";needsVideo && codecs==null->"正在检测视频输出能力…";!encodingReady->"当前视频组合不可用，请调整菜单设置";else->null }
  fun startCapture() {
   if(!ready || switching || tuningBusy || !valid || !encodingReady || pendingStart)return
   try {
    error=null;authorizationError=null
    if(settings.autoLock && !lockManager.authorized) {pendingStart=true;lockAuthorization.launch(lockManager.authorizationIntent())}
    else beginCapture()
   }catch(e:Exception){error=e.message}
  }
  val currentHardwareShutter by rememberUpdatedState<()->Unit>({
   when(s.state.value.phase) {
    Phase.RUNNING,Phase.PAUSED,Phase.COUNTDOWN->{android.util.Log.i("XperiaShutter","stop-and-save");s.stop()}
    Phase.PROCESSING,Phase.STOPPING->Unit
    else->{android.util.Log.i("XperiaShutter","start-request");startCapture()}
   }
  })
  val currentHardwareFocus by rememberUpdatedState<()->Unit>({
   if(ready && !switching && !tuningBusy && !s.state.value.busy && route==null && !black)focusRequest++
  })
  DisposableEffect(s) {
   hardwareFocus={currentHardwareFocus()}
   hardwareShutter={currentHardwareShutter()}
   onDispose {hardwareShutter=null;hardwareFocus=null;shutterKey.reset()}
  }
  val main=cameras.filter {it.rear && it.selectable}.maxByOrNull {it.sensorArea}
  val lenses=buildList {
   val selectable=cameras.filter {it.selectable}
   val wide=selectable.filter {it.rear && main!=null && it.equivalentFocal>0 && it.equivalentFocal<main.equivalentFocal*.9f}.minByOrNull {it.equivalentFocal}
   wide?.let {add(LensShortcut(it.id,"0.7x"))}
   main?.let {camera->
    if(camera.apertures.any {kotlin.math.abs(it-2f)<.01f} && camera.apertures.any {kotlin.math.abs(it-4f)<.01f}) {
     add(LensShortcut(camera.id,"F2",2f));add(LensShortcut(camera.id,"F4",4f))
    } else add(LensShortcut(camera.id,"1x"))
   }
   selectable.filter {it.rear && main!=null && it.equivalentFocal>main.equivalentFocal*1.1f}.maxByOrNull {it.equivalentFocal}?.let {add(LensShortcut(it.id,"2.1x"))}
   selectable.firstOrNull {!it.rear}?.let {add(LensShortcut(it.id,"前置"))}
  }
  CameraDashboard(settings,state,s.cameraId,ready&&!tuningBusy&&!switching&&valid&&encodingReady,hint,state.error ?: error ?: authorizationError,
   tuningCapabilities=tuningCaps,canTune=ready&&!switching&&!tuningBusy&&!state.busy,onTuning=::changeTuning,
   lenses=lenses,selectedAperture=s.aperture,changeInterval={if(!state.busy)update(settings.copy(interval=it))},canSwitchLens=(ready||error!=null)&&!switching&&!tuningBusy&&!state.busy,onLens={selectLens(it.id,it.aperture)},
   changeMode={update(settings.copy(mode=it))},onShutter={
    if(state.busy){if(state.phase==Phase.PAUSED)s.resume() else s.pause()}
    else startCapture()
   },onStop=s::stop,onMenu={route="menu"},onProject={try {openGallery()}catch(e:Exception){error="无法打开相册：${e.message}"}},onBlackScreen={route=null;black=true}) {modifier->CameraPreviewScreen(s,state,cameraId,modifier,focusRequest)}
  if(black && state.busy)BlackCaptureScreen(this) {black=false}
  route?.let {page->
   val title=when(page){"capture"->"拍摄设置";"video"->"视频输出";"device"->"镜头与设备能力";"files"->"项目列表";"results"->"拍摄结果";"help"->"使用说明";else->"菜单"}
   val height=LocalConfiguration.current.screenHeightDp.dp*.9f
   Dialog(onDismissRequest={route=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
    Surface(Modifier.widthIn(max=460.dp).fillMaxWidth(.94f).heightIn(max=height),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
     Column {
      Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
       if(page!="menu")TextButton(onClick={route=if(page=="results")"files" else "menu"}) {Text("返回")}
       Text(title,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleLarge)
       TextButton(onClick={route=null}) {Text("完成")}
      }
      HorizontalDivider()
      Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
       if(state.busy && page in listOf("capture","video","device"))Text("拍摄或合成期间设置暂时锁定。",color=MaterialTheme.colorScheme.primary)
       when(page) {
        "menu"->{
         MenuEntry("拍摄设置","间隔、结束方式、倒计时与参数锁定") {route="capture"}
         MenuEntry("视频输出","分辨率、帧率、编码与原片清理") {route="video"}
         MenuEntry("镜头与设备能力","切换镜头、查看相机能力") {route="device"}
         MenuEntry("项目列表","查看原始项目与导出") {route="files"}
         MenuEntry("电池设置","长时间拍摄前检查后台限制") {startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}
         MenuEntry("使用说明","锁屏拍摄、原片清理与操作提示") {route="help"}
        }
        "capture","video"->TimelapseSettingsScreen(settings,!state.busy,::update,page,codecs,lockAuthorized) {pendingStart=false;lockAuthorization.launch(lockManager.authorizationIntent())}
        "device"->CapabilityScreen(cameras,::selectCamera,!state.busy&&!switching)
        "files"->ProjectFilesScreen(com.xperia.timelapse.util.StorageHelper.root(this@MainActivity),state.projectPath) {selectedProject=it.absolutePath;route="results"}
        "results"->{
         val path=selectedProject ?: state.projectPath
         val current=path==state.projectPath
         if(path==null)Text("还没有拍摄项目，点快门开始第一次拍摄。")
         else {
          Text(File(path).name,style=MaterialTheme.typography.titleMedium)
          var detail by remember(path,state.phase){mutableStateOf("")}
          LaunchedEffect(path,state.phase) {detail=withContext(Dispatchers.IO) {runCatching {
           val meta=org.json.JSONObject(File(path,"metadata.json").readText())
           "${meta.optInt("frame_count")} 张 · ${meta.optString("status")}"}.getOrDefault("项目文件")}}
          Text(if(current)"${state.capturedFrames} 张 · ${state.phase.label}" else detail)
          if(current && state.busy)Text(state.processingMessage ?: "拍摄结束后可导出。")
          else {
           val video=File(path,"preview.mp4")
           var info by remember(path,state.phase){mutableStateOf("")}
           var gallerySaved by remember(path,state.phase){mutableStateOf(false)}
           LaunchedEffect(path,state.phase){info=withContext(Dispatchers.IO){runCatching {
            val meta=org.json.JSONObject(File(path,"metadata.json").readText())
            gallerySaved=meta.optBoolean("gallery_saved") || meta.optBoolean("gallery_photos_saved")
            if(meta.optString("video_status")=="verified") "${meta.optString("video_resolution")} · ${meta.optInt("video_fps")}fps\n${meta.optString("video_codec")} · ${meta.optInt("video_bitrate")/1_000_000.0} Mbps" else ""
           }.getOrDefault("")}}
           if(info.isNotEmpty())Text(info)
           if(gallerySaved)Text("已自动保存到相册 · XperiaTimelapse",style=MaterialTheme.typography.bodySmall)
           if(video.isFile)Button(onClick={runCatching{share(video,"video/mp4","分享视频")}.onFailure {error=it.message}}) {Text("分享视频")}
           Button(onClick={scope.launch {
            exporting=true
            try {
             val zip=withContext(Dispatchers.IO) {
              val folder=File(path);val target=File(folder.parentFile,"${folder.name}.zip")
              ZipOutputStream(target.outputStream().buffered()).use {out->folder.listFiles()?.filter {it.isFile && !it.name.contains(".partial")}?.sortedBy {it.name}?.forEach {source->out.putNextEntry(ZipEntry("${folder.name}/${source.name}"));source.inputStream().use {it.copyTo(out)};out.closeEntry()}};target
             };share(zip,"application/zip","导出项目")
            }catch(e:Exception){error="导出失败：${e.message}"}finally{exporting=false}
           }},enabled=!exporting) {Text(if(exporting)"正在打包…" else "导出项目 ZIP")}
          }
         }
         (state.error ?: error)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
        "help"->{
         Text("选择拍照、定时或延时模式，再点圆形快门。拍摄时点快门暂停或继续，方形按钮停止并保存。")
         Text("请先在可见页面开始拍摄，再锁屏。PRO-I 的锁屏与长时稳定性仍需真机验证。")
         Text("视频只有在完整校验通过后才会删除原片。希望保留照片，可在视频输出菜单关闭原片清理。单张拍照始终保留照片。")
         Text("设置会自动保存；旋转屏幕会调整布局，不会重新开始拍摄。拍摄中冻结照片输出方向，避免序列随界面旋转而变化。")
         Text("拍摄中点击黑屏省电，双击恢复，开始 30 秒后自动锁屏，锁屏继续拍摄。自动黑屏、锁屏和指示灯提示在拍摄设置中配置。自动锁屏需要一次系统授权，指示灯亮法由系统控制。")
         Text("版本 0.5.10",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
       }
      }
     }
    }
   }
  }
 }
 private fun openGallery() {
  // Launch the installed gallery without reading unrelated media or requesting library permissions.
  val intents=listOf(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_GALLERY),
   Intent(Intent.ACTION_VIEW).setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*"))
  for(intent in intents)try {startActivity(intent);return}catch(_:ActivityNotFoundException){}
  throw IllegalStateException("未找到可用的相册应用，可从设置中的文件列表查看项目")
 }
 private fun share(file:File,type:String,title:String) {
  val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
  startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),title))
 }
 override fun onDestroy() {if(bound){unbindService(connection);bound=false};super.onDestroy()}
}
@Composable private fun MenuEntry(title:String,subtitle:String,click:()->Unit) {
 Surface(Modifier.fillMaxWidth().clickable(onClick=click),shape=RoundedCornerShape(12.dp),color=Color(0xFF222932)) {
  Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Text("›",Modifier.padding(start=12.dp),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineSmall)
  }
 }
}
