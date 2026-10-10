package com.xperia.timelapse.camera
import android.content.Context
import android.hardware.camera2.*
import androidx.camera.camera2.interop.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.xperia.timelapse.timelapse.TimelapseConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class,ExperimentalZeroShutterLag::class])
class CameraXController(private val context: Context): CameraController {
 private var provider: ProcessCameraProvider?=null
 private var camera: Camera?=null
 private var previewUseCase=Preview.Builder().build()
 private var shot=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
 @Volatile private var latest: TotalCaptureResult?=null
 private var id=""
 private var lastFocusPoint:MeteringPoint?=null
 private var aperture:Float?=null
 private var manualExposure:ApertureExposure?=null
 @Volatile var lastCapturedAperture:Float?=null;private set
 @Volatile var apertureWarning:String?=null;private set
 val measuredAperture get()=latest?.get(CaptureResult.LENS_APERTURE)
 var tuning=CameraTuning();private set
 val tuningCapabilities:CameraTuningCapabilities get() {
  val exposure=camera?.cameraInfo?.exposureState
  val modes=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty()
  return CameraTuningCapabilities(WhiteBalanceMode.values().filter {it.requestValue in modes},exposure?.exposureCompensationRange?.lower ?: 0,exposure?.exposureCompensationRange?.upper ?: 0,exposure?.exposureCompensationStep?.toFloat() ?: 0f)
 }
 var onCaptureStage:((String,Long)->Unit)?=null
 private fun captureStage(stage:String) {
  val time=android.os.SystemClock.elapsedRealtime()
  android.util.Log.i("XperiaShutter","stage=$stage time_ms=$time")
  onCaptureStage?.invoke(stage,time)
 }
 var colorLook=com.xperia.timelapse.color.ColorLook()
 private var outputFormat=ImageCapture.OUTPUT_FORMAT_JPEG
 private var instantCapture=false
 fun supportsRaw(withJpeg:Boolean):Boolean= camera?.cameraInfo?.let {ImageCapture.getImageCaptureCapabilities(it).supportedOutputFormats.contains(if(withJpeg)ImageCapture.OUTPUT_FORMAT_RAW_JPEG else ImageCapture.OUTPUT_FORMAT_RAW)} ?: false
 val rawSupported get()=camera?.cameraInfo?.let {ImageCapture.getImageCaptureCapabilities(it).supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW)} ?: false
 var captureResolution=com.xperia.timelapse.video.VideoResolution.FULL_HD
 private val imageScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var rotation=android.view.Surface.ROTATION_0
 fun setRotation(value:Int,capture:Boolean=true) {rotation=value;previewUseCase.targetRotation=value;if(capture)shot.targetRotation=value}
 private var surface: Preview.SurfaceProvider?=null
 override suspend fun open(owner:LifecycleOwner,cameraId:String) =
  openWithFormat(owner,cameraId,if(id==cameraId)outputFormat else ImageCapture.OUTPUT_FORMAT_JPEG,if(id==cameraId)instantCapture else false)
 private suspend fun openWithFormat(owner: LifecycleOwner,cameraId: String,format:Int,instant:Boolean=false) {
  outputFormat=format;instantCapture=instant
  if(id!=cameraId)lastFocusPoint=null
  id=cameraId;tuning=CameraTuning();aperture=null;manualExposure=null
  val future=ProcessCameraProvider.getInstance(context)
  val p=suspendCoroutine<ProcessCameraProvider> { cont -> future.addListener({ try { cont.resume(future.get()) } catch(e:Exception){cont.resumeWithException(e)} },ContextCompat.getMainExecutor(context)) }
  p.unbindAll(); provider=p; latest=null
  val builder=Preview.Builder()
  Camera2Interop.Extender(builder).setSessionCaptureCallback(object: CameraCaptureSession.CaptureCallback() {
   override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) { latest=result }
  })
  val selector=CameraSelector.Builder().addCameraFilter {list->list.filter {Camera2CameraInfo.from(it).cameraId==cameraId}}.build()
  // Cached JPEG frames avoid waiting for a new still exposure where supported.
  // RAW requires a new sensor frame and stays on the minimum-latency pipeline.
  val variableAperture=(context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id).get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.size ?: 0)>1
  val zsl=instant && !variableAperture && format==ImageCapture.OUTPUT_FORMAT_JPEG && p.getCameraInfo(selector).isZslSupported
  val captureMode=if(zsl)ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
  previewUseCase=builder.setTargetRotation(rotation).build()
  shot=ImageCapture.Builder().setCaptureMode(captureMode).setFlashMode(ImageCapture.FLASH_MODE_OFF).setJpegQuality(95).setOutputFormat(outputFormat).setTargetRotation(rotation).build()
  previewUseCase.setSurfaceProvider(surface)
  camera=p.bindToLifecycle(owner,selector,previewUseCase,shot)
  android.util.Log.i("XperiaShutter","camera=$cameraId format=$format zsl=$zsl")
  // CameraX may reuse its Camera2 control after unbinding. Start fresh metering
  // instead of carrying a previous sequence's manual exposure/focus into reopen.
  Camera2CameraControl.from(checkNotNull(camera).cameraControl).clearCaptureRequestOptions()
 }
 override fun preview(provider: Preview.SurfaceProvider?) { surface=provider; previewUseCase.setSurfaceProvider(provider) }
 val previewResolution get()=previewUseCase.resolutionInfo
 fun photoOutput(resolution:com.xperia.timelapse.video.VideoResolution,fps:Int):com.xperia.timelapse.video.VideoOutput {
  val info=shot.resolutionInfo ?: return com.xperia.timelapse.video.VideoOutput(resolution,fps)
  val size=info.resolution
  val rotated=info.rotationDegrees%180!=0
  val dimensions=com.xperia.timelapse.video.CaptureImageGeometry.dimensions(if(rotated)size.height else size.width,if(rotated)size.width else size.height,resolution.width)
  return com.xperia.timelapse.video.VideoOutput(resolution,fps,dimensions.first,dimensions.second)
 }
 private val focusMutex=kotlinx.coroutines.sync.Mutex()
 suspend fun focus(point:MeteringPoint):Boolean = focusMutex.withLock {
  val cam=checkNotNull(camera) {"相机尚未就绪"}
  val control=cam.cameraControl
  val aeAction=FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AE).disableAutoCancel().build()
  check(cam.cameraInfo.isFocusMeteringSupported(aeAction)) {"该镜头不支持按点击位置测光"}
  val action=FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).disableAutoCancel().build()
  val selected=aperture;val old=manualExposure
  try {
   if(old!=null) {
    manualExposure=null
    awaitControl(Camera2CameraControl.from(control).setCaptureRequestOptions(previewOptions(selected,tuning.whiteBalance).build()))
    val caps=tuningCapabilities
    if(caps.supportsEv)awaitControl(control.setExposureCompensationIndex(caps.indexFor(tuning.ev)))
   }
   val future=control.startFocusAndMetering(action)
   val result=withTimeout(8000) {suspendCancellableCoroutine<FocusMeteringResult> {cont->
    cont.invokeOnCancellation {future.cancel(false)}
    future.addListener({if(cont.isActive)try {cont.resume(future.get())}catch(e:Exception){cont.resumeWithException(e)}},ContextCompat.getMainExecutor(context))
   }}
   // CameraX's focus future confirms the new metering request was applied, but
   // does not guarantee AE convergence. Only freeze a fresh frame for that region.
   val after=latest?.frameNumber ?: -1L
   val metered=withTimeout(5000) {
    var r:TotalCaptureResult
    while(true) {
     val current=latest
     if(current==null) {delay(30);continue}
     r=current
     val regions=r.request.get(CaptureRequest.CONTROL_AE_REGIONS)
     if(r.frameNumber>after && regions?.any {it.meteringWeight>0}==true &&
      r.get(CaptureResult.CONTROL_AE_MODE)!=CaptureRequest.CONTROL_AE_MODE_OFF &&
      r.get(CaptureResult.SENSOR_EXPOSURE_TIME)!=null && r.get(CaptureResult.SENSOR_SENSITIVITY)!=null &&
      r.get(CaptureResult.CONTROL_AE_STATE) in listOf(CaptureResult.CONTROL_AE_STATE_CONVERGED,CaptureResult.CONTROL_AE_STATE_LOCKED,CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED))break
     delay(30)
    }
    r
   }
   if(old!=null && selected!=null)setAperture(selected)
   lastFocusPoint=point
   android.util.Log.i("XperiaFocus","camera=$id focus=${result.isFocusSuccessful} metering_frame=${metered.frameNumber} ae_regions=${metered.request.get(CaptureRequest.CONTROL_AE_REGIONS)?.contentToString()} aperture=$measuredAperture")
   result.isFocusSuccessful
  }catch(e:Exception) {
   if(old!=null) {
    manualExposure=old
    withContext(NonCancellable) {runCatching {awaitControl(Camera2CameraControl.from(control).setCaptureRequestOptions(previewOptions(selected,tuning.whiteBalance).build()))}}
   }
   throw e
  }
 }
 private fun exposurePlan(sample:ApertureExposure,value:Float,ev:Float):ApertureExposure {
  val c=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id)
  check(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in (c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf())) {"此镜头未开放手动光圈控制"}
  val times=checkNotNull(c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE))
  val iso=checkNotNull(c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE))
  val maxFrame=c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: times.upper
  return ApertureExposurePolicy.adjust(sample,value,ev,times.lower,minOf(times.upper,maxFrame),iso.lower,iso.upper)
 }
 suspend fun setAperture(value:Float?) {
  val cam=checkNotNull(camera) {"相机尚未就绪"}
  val c=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id)
  val available=c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: floatArrayOf()
  require(value==null || available.any {kotlin.math.abs(it-value)<.01f}) {"该镜头不支持此光圈"}
  val old=manualExposure;val oldAperture=aperture
  // Only variable-aperture lenses need AE disabled; fixed-aperture lenses keep normal AE.
  val controlled=value!=null && available.size>1
  val next=if(controlled) {
   val sample=old ?: withTimeout(5000) {
    while(latest?.get(CaptureResult.SENSOR_EXPOSURE_TIME)==null || latest?.get(CaptureResult.SENSOR_SENSITIVITY)==null)delay(30)
    val r=checkNotNull(latest)
    ApertureExposure(r.get(CaptureResult.LENS_APERTURE) ?: value!!,checkNotNull(r.get(CaptureResult.SENSOR_EXPOSURE_TIME)),checkNotNull(r.get(CaptureResult.SENSOR_SENSITIVITY)),tuning.ev)
   }
   exposurePlan(sample,value!!,tuning.ev)
  } else null
  manualExposure=next
  try {
   awaitControl(Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(previewOptions(value,tuning.whiteBalance).build()))
   if(controlled)check(withTimeoutOrNull(3000) {
    while(true) {
     val r=latest
     if(r!=null && r.get(CaptureResult.CONTROL_AE_MODE)==CaptureRequest.CONTROL_AE_MODE_OFF &&
      r.get(CaptureResult.LENS_APERTURE)?.let {kotlin.math.abs(it-value!!)<.05f}==true &&
      r.get(CaptureResult.LENS_STATE)!=CaptureResult.LENS_STATE_MOVING)break
     delay(30)
    };true
   }==true) {"光圈未确认切换：请求 F$value，相机回报 F${measuredAperture ?: "未知"}"}
   aperture=value
  }catch(e:Exception) {
   manualExposure=old
   withContext(NonCancellable) {runCatching {awaitControl(Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(previewOptions(oldAperture,tuning.whiteBalance).build()))}}
   throw e
  }
  android.util.Log.i("XperiaAperture","requested=$value measured=$measuredAperture manual=$manualExposure")
 }
 private fun previewOptions(selectedAperture:Float?,whiteBalance:WhiteBalanceMode)=CaptureRequestOptions.Builder().apply {
  selectedAperture?.let {setCaptureRequestOption(CaptureRequest.LENS_APERTURE,it)}
  setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE,whiteBalance.requestValue)
  manualExposure?.let {
   setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
   setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,it.exposureNs)
   setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,it.iso)
   val maxFrame=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: Long.MAX_VALUE
   setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION,maxOf(33_333_333L,it.exposureNs).coerceAtMost(maxFrame))
  }
 }
 private suspend fun <T> awaitControl(future:com.google.common.util.concurrent.ListenableFuture<T>):T=withTimeout(5000) {
  suspendCancellableCoroutine<T> {cont->
   cont.invokeOnCancellation {future.cancel(false)}
   future.addListener({if(cont.isActive)try {cont.resume(future.get())}catch(e:Exception){cont.resumeWithException(e)}},ContextCompat.getMainExecutor(context))
  }
 }
 suspend fun setTuning(requested:CameraTuning):CameraTuning {
  val cam=checkNotNull(camera) {"相机尚未就绪"}
  val caps=tuningCapabilities
  val next=caps.normalize(requested)
  val old=tuning
  val oldExposure=manualExposure
  manualExposure=oldExposure?.let {exposurePlan(it,it.aperture,next.ev)}
  try {
   awaitControl(Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(previewOptions(aperture,next.whiteBalance).build()))
   if(manualExposure==null && caps.supportsEv)awaitControl(cam.cameraControl.setExposureCompensationIndex(caps.indexFor(next.ev)))
   tuning=next
  } catch(e:Exception) {
   manualExposure=oldExposure
   withContext(NonCancellable) {runCatching {
    awaitControl(Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(previewOptions(aperture,old.whiteBalance).build()))
    if(manualExposure==null && caps.supportsEv)awaitControl(cam.cameraControl.setExposureCompensationIndex(caps.indexFor(old.ev)))
   }}
   throw e
  }
  android.util.Log.i("XperiaTuning","camera=$id wb=${next.whiteBalance.requestValue} ev=${next.ev} index=${caps.indexFor(next.ev)} measuredWb=${latest?.get(CaptureResult.CONTROL_AWB_MODE)} measuredEvIndex=${latest?.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)}")
  return next
 }
 suspend fun prepareFormat(owner:LifecycleOwner,config:TimelapseConfig) {
  val format=if(!config.saveRaw)ImageCapture.OUTPUT_FORMAT_JPEG else if(config.autoComposeVideo)ImageCapture.OUTPUT_FORMAT_RAW_JPEG else ImageCapture.OUTPUT_FORMAT_RAW
  val info=checkNotNull(camera).cameraInfo
  check(format in ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats) {"该镜头不支持所选 RAW 拍摄格式，请换镜头或关闭 RAW"}
  val instant=!config.autoComposeVideo
  if(format==outputFormat && instant==instantCapture)return
  val oldTuning=tuning;val oldAperture=aperture;val oldFormat=outputFormat;val oldInstant=instantCapture
  outputFormat=format
  try {openWithFormat(owner,id,format,instant);setAperture(oldAperture);setTuning(oldTuning);lastFocusPoint?.let {focus(it)}}
  catch(e:Exception) {outputFormat=oldFormat;instantCapture=oldInstant;throw e}
 }
 override suspend fun capture(file:File):Pair<Int,Int> = suspendCoroutine {cont->
  val selectedResolution=captureResolution;val selectedLook=colorLook
  val requestedAperture=aperture
  lastCapturedAperture=null;apertureWarning=null
  val raw=File(file.parentFile,file.nameWithoutExtension+".dng")
  val jpegTemp=File(file.parentFile,file.name+".partial")
  val rawTemp=File(file.parentFile,raw.name+".partial")
  val both=outputFormat==ImageCapture.OUTPUT_FORMAT_RAW_JPEG
  val onlyRaw=outputFormat==ImageCapture.OUTPUT_FORMAT_RAW
  val remaining=java.util.concurrent.atomic.AtomicInteger(if(both)2 else 1)
  val settled=java.util.concurrent.atomic.AtomicBoolean(false)
  val callback=object:ImageCapture.OnImageSavedCallback {
   override fun onCaptureStarted() {captureStage("capture_started")}
   override fun onImageSaved(result:ImageCapture.OutputFileResults) {
    if(remaining.decrementAndGet()!=0 || settled.get())return
    captureStage("camera_saved")
    imageScope.launch {
     if(!settled.compareAndSet(false,true))return@launch
     try {
      val size=if(onlyRaw) {
       val exif=androidx.exifinterface.media.ExifInterface(rawTemp)
       val width=exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH,0)
       val height=exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH,0)
       check(width>0 && height>0) {"RAW 图像尺寸无法读取，拍摄未完成"}
       if(exif.rotationDegrees%180!=0)height to width else width to height
      } else CaptureImageWriter.normalize(jpegTemp,selectedResolution,selectedLook)
      val actual=androidx.exifinterface.media.ExifInterface(if(onlyRaw)rawTemp else jpegTemp)
       .getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER,Double.NaN)
      lastCapturedAperture=actual.takeIf {it.isFinite() && it>0}?.toFloat()
      if(requestedAperture!=null && lastCapturedAperture!=null && kotlin.math.abs(lastCapturedAperture!!-requestedAperture)>.05f)
       apertureWarning="照片实际 F${lastCapturedAperture}，未达到所选 F$requestedAperture"
      captureStage("aperture requested=$requestedAperture exif=$lastCapturedAperture")
      if(both || onlyRaw)check(rawTemp.renameTo(raw)) {"RAW 保存失败"}
      if(!onlyRaw)check(jpegTemp.renameTo(file)) {"照片保存失败"}
      captureStage("processed")
      cont.resume(size)
     }catch(e:Exception) {jpegTemp.delete();rawTemp.delete();cont.resumeWithException(e)}
    }
   }
   override fun onError(exception:ImageCaptureException) {
    if(settled.compareAndSet(false,true)) {jpegTemp.delete();rawTemp.delete();cont.resumeWithException(exception)}
   }
  }
  val executor=ContextCompat.getMainExecutor(context)
  captureStage("request")
  if(both)shot.takePicture(ImageCapture.OutputFileOptions.Builder(rawTemp).build(),ImageCapture.OutputFileOptions.Builder(jpegTemp).build(),executor,callback)
  else shot.takePicture(ImageCapture.OutputFileOptions.Builder(if(onlyRaw)rawTemp else jpegTemp).build(),executor,callback)
 }
 override suspend fun lock(config: TimelapseConfig): JSONObject {
  val cam=checkNotNull(camera) { "相机尚未就绪" }
  val c=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id)
  // Control futures can finish before the latest preview callback. Wait for
  // tuning feedback before freezing exposure; Sony preview aperture feedback may
  // remain at F2 even with an F4 request, so it cannot gate capture readiness.
  val tuningCaps=tuningCapabilities
  val expectedEv=tuningCaps.indexFor(tuning.ev)
  if(config.lockExposure || config.lockFocus || config.lockWhiteBalance) {
  check(withTimeoutOrNull(5000) {
   while(true) {
    val result=latest
    if(result!=null && result.get(CaptureResult.CONTROL_AWB_MODE)==tuning.whiteBalance.requestValue &&
     (manualExposure!=null || !tuningCaps.supportsEv || result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)==expectedEv))break
    delay(50)
   }
   true
  }==true) {"白平衡或 EV 尚未生效，请稍后重试"}
  }
  // Preview metering must yield a real capture result before freezing sensor values.
  if(config.lockExposure || config.lockFocus || config.lockWhiteBalance) {
   check(withTimeoutOrNull(5000) { while(latest==null) delay(50);true }==true) { "相机预览未就绪，请重新开始拍摄" }
   withTimeoutOrNull(3000) {
    while (latest?.get(CaptureResult.CONTROL_AE_STATE) == CaptureResult.CONTROL_AE_STATE_SEARCHING ||
     latest?.get(CaptureResult.CONTROL_AWB_STATE) == CaptureResult.CONTROL_AWB_STATE_SEARCHING ||
     latest?.get(CaptureResult.CONTROL_AF_STATE) in listOf(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN)) delay(50)
   }
  }
  val r=latest
  val options=previewOptions(aperture,tuning.whiteBalance)
  val out=JSONObject()
  val caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
  val exposure=r?.get(CaptureResult.SENSOR_EXPOSURE_TIME); val iso=r?.get(CaptureResult.SENSOR_SENSITIVITY)
  var ae=false; var focus=false; var awb=false
  if(config.lockExposure) {
   if(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps && exposure!=null && iso!=null) {
    options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
     .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME,exposure).setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY,iso)
    ae=true
   } else if(c.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)==true) { options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK,true);ae=true }
  }
  val distance=r?.get(CaptureResult.LENS_FOCUS_DISTANCE)
  val lockedDistance=CameraParameterPolicy.focusDistance(distance,c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE))
  if(config.lockFocus && lockedDistance!=null) {
   options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF).setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE,lockedDistance);focus=true
  }
  if(config.lockWhiteBalance && tuning.whiteBalance==WhiteBalanceMode.AUTO && c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)==true) { options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK,true);awb=true }
  // Single shots already use the preview's tuning. Do not resubmit unchanged
  // controls or wait for a new repeating-request acknowledgement on the shutter path.
  if(config.lockExposure || config.lockFocus || config.lockWhiteBalance) {
  val future=Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options.build())
  suspendCancellableCoroutine<Unit> { cont ->
   cont.invokeOnCancellation { future.cancel(false) }
   future.addListener({ if(cont.isActive) { try { future.get();cont.resume(Unit) } catch(e:Exception){cont.resumeWithException(e)} } },ContextCompat.getMainExecutor(context))
  }
  }
  return out.put("requested_white_balance",tuning.whiteBalance.name).put("white_balance_mode",r?.get(CaptureResult.CONTROL_AWB_MODE) ?: JSONObject.NULL)
   .put("exposure_ev",tuning.ev).put("exposure_compensation_index",r?.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: JSONObject.NULL).put("locked_exposure",ae).put("locked_focus",focus).put("locked_white_balance",awb)
   .put("exposure_time_ns",exposure ?: JSONObject.NULL).put("iso",iso ?: JSONObject.NULL).put("focus_diopters",(if(focus)lockedDistance else distance) ?: JSONObject.NULL)
   .put("measured_focus_diopters",distance ?: JSONObject.NULL).put("requested_aperture",aperture ?: JSONObject.NULL).put("measured_aperture",r?.get(CaptureResult.LENS_APERTURE) ?: JSONObject.NULL)
 }
 override fun close() { if(camera==null)return;provider?.unbindAll();camera=null }
}
