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
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
class CameraXController(private val context: Context): CameraController {
 private var provider: ProcessCameraProvider?=null
 private var camera: Camera?=null
 private var previewUseCase=Preview.Builder().build()
 private var shot=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(95).build()
 @Volatile private var latest: TotalCaptureResult?=null
 private var id=""
 private var aperture:Float?=null
 var tuning=CameraTuning();private set
 val tuningCapabilities:CameraTuningCapabilities get() {
  val exposure=camera?.cameraInfo?.exposureState
  val modes=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty()
  return CameraTuningCapabilities(WhiteBalanceMode.values().filter {it.requestValue in modes},exposure?.exposureCompensationRange?.lower ?: 0,exposure?.exposureCompensationRange?.upper ?: 0,exposure?.exposureCompensationStep?.toFloat() ?: 0f)
 }
 var captureResolution=com.xperia.timelapse.video.VideoResolution.FULL_HD
 private val imageScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var rotation=android.view.Surface.ROTATION_0
 fun setRotation(value:Int,capture:Boolean=true) {rotation=value;previewUseCase.targetRotation=value;if(capture)shot.targetRotation=value}
 private var surface: Preview.SurfaceProvider?=null
 override suspend fun open(owner: LifecycleOwner,cameraId: String) {
  id=cameraId;tuning=CameraTuning()
  val future=ProcessCameraProvider.getInstance(context)
  val p=suspendCoroutine<ProcessCameraProvider> { cont -> future.addListener({ try { cont.resume(future.get()) } catch(e:Exception){cont.resumeWithException(e)} },ContextCompat.getMainExecutor(context)) }
  p.unbindAll(); provider=p; latest=null
  val builder=Preview.Builder()
  Camera2Interop.Extender(builder).setSessionCaptureCallback(object: CameraCaptureSession.CaptureCallback() {
   override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) { latest=result }
  })
  previewUseCase=builder.setTargetRotation(rotation).build();shot.targetRotation=rotation; previewUseCase.setSurfaceProvider(surface)
  camera=p.bindToLifecycle(owner,CameraSelector.Builder().addCameraFilter { list -> list.filter { Camera2CameraInfo.from(it).cameraId==cameraId } }.build(),previewUseCase,shot)
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
 suspend fun focus(point:MeteringPoint):Boolean {
  val control=checkNotNull(camera) {"相机尚未就绪"}.cameraControl
  val action=FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF).disableAutoCancel().build()
  check(checkNotNull(camera).cameraInfo.isFocusMeteringSupported(action)) {"该镜头不支持点按对焦"}
  val future=control.startFocusAndMetering(action)
  val result=withTimeout(8000) {suspendCancellableCoroutine<FocusMeteringResult> {cont->
   cont.invokeOnCancellation {future.cancel(false)}
   future.addListener({if(cont.isActive)try {cont.resume(future.get())}catch(e:Exception){cont.resumeWithException(e)}},ContextCompat.getMainExecutor(context))
  }}
  android.util.Log.i("XperiaFocus","camera=$id success=${result.isFocusSuccessful}")
  return result.isFocusSuccessful
 }
 suspend fun setAperture(value:Float?) {
  val c=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id)
  require(value==null || c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.any {kotlin.math.abs(it-value)<.01f}==true) {"该镜头不支持此光圈"}
  val options=previewOptions(value,tuning.whiteBalance)
  val future=Camera2CameraControl.from(checkNotNull(camera).cameraControl).setCaptureRequestOptions(options.build())
  withTimeout(5000) {suspendCancellableCoroutine<Unit> {cont->
   cont.invokeOnCancellation {future.cancel(false)}
   future.addListener({if(cont.isActive)try {future.get();cont.resume(Unit)}catch(e:Exception){cont.resumeWithException(e)}},ContextCompat.getMainExecutor(context))
  }}
  aperture=value
 }
 private fun previewOptions(selectedAperture:Float?,whiteBalance:WhiteBalanceMode)=CaptureRequestOptions.Builder().apply {
  selectedAperture?.let {setCaptureRequestOption(CaptureRequest.LENS_APERTURE,it)}
  setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE,whiteBalance.requestValue)
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
  try {
   awaitControl(Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(previewOptions(aperture,next.whiteBalance).build()))
   if(caps.supportsEv)awaitControl(cam.cameraControl.setExposureCompensationIndex(caps.indexFor(next.ev)))
   tuning=next
  } catch(e:Exception) {
   withContext(NonCancellable) {runCatching {
    awaitControl(Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(previewOptions(aperture,old.whiteBalance).build()))
    if(caps.supportsEv)awaitControl(cam.cameraControl.setExposureCompensationIndex(caps.indexFor(old.ev)))
   }}
   throw e
  }
  android.util.Log.i("XperiaTuning","camera=$id wb=${next.whiteBalance.requestValue} ev=${next.ev} index=${caps.indexFor(next.ev)} measuredWb=${latest?.get(CaptureResult.CONTROL_AWB_MODE)} measuredEvIndex=${latest?.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)}")
  return next
 }
 override suspend fun capture(file: File): Pair<Int,Int> = suspendCoroutine { cont ->
  val selectedResolution=captureResolution
  val partial=File(file.parentFile,file.name+".partial")
  shot.takePicture(ImageCapture.OutputFileOptions.Builder(partial).build(),ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageSavedCallback {
   override fun onImageSaved(result: ImageCapture.OutputFileResults) {
    imageScope.launch {
     try {
      val size=CaptureImageWriter.normalize(partial,selectedResolution)
      check(partial.renameTo(file)) {"照片保存失败"}
      cont.resume(size)
     } catch(e:Exception) {partial.delete();cont.resumeWithException(e)}
    }
   }
   override fun onError(exception:ImageCaptureException) { partial.delete(); cont.resumeWithException(exception) }
  })
 }
 override suspend fun lock(config: TimelapseConfig): JSONObject {
  val cam=checkNotNull(camera) { "相机尚未就绪" }
  val c=context.getSystemService(CameraManager::class.java).getCameraCharacteristics(id)
  // Control futures can finish before the latest preview callback. Wait for
  // tuning feedback before freezing exposure; Sony preview aperture feedback may
  // remain at F2 even with an F4 request, so it cannot gate capture readiness.
  val tuningCaps=tuningCapabilities
  val expectedEv=tuningCaps.indexFor(tuning.ev)
  check(withTimeoutOrNull(5000) {
   while(true) {
    val result=latest
    if(result!=null && result.get(CaptureResult.CONTROL_AWB_MODE)==tuning.whiteBalance.requestValue &&
     (!tuningCaps.supportsEv || result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)==expectedEv))break
    delay(50)
   }
   true
  }==true) {"白平衡或 EV 尚未生效，请稍后重试"}
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
  val future=Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options.build())
  suspendCancellableCoroutine<Unit> { cont ->
   cont.invokeOnCancellation { future.cancel(false) }
   future.addListener({ if(cont.isActive) { try { future.get();cont.resume(Unit) } catch(e:Exception){cont.resumeWithException(e)} } },ContextCompat.getMainExecutor(context))
  }
  return out.put("requested_white_balance",tuning.whiteBalance.name).put("white_balance_mode",r?.get(CaptureResult.CONTROL_AWB_MODE) ?: JSONObject.NULL)
   .put("exposure_ev",tuning.ev).put("exposure_compensation_index",r?.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION) ?: JSONObject.NULL).put("locked_exposure",ae).put("locked_focus",focus).put("locked_white_balance",awb)
   .put("exposure_time_ns",exposure ?: JSONObject.NULL).put("iso",iso ?: JSONObject.NULL).put("focus_diopters",(if(focus)lockedDistance else distance) ?: JSONObject.NULL)
   .put("measured_focus_diopters",distance ?: JSONObject.NULL).put("requested_aperture",aperture ?: JSONObject.NULL).put("measured_aperture",r?.get(CaptureResult.LENS_APERTURE) ?: JSONObject.NULL)
 }
 override fun close() { provider?.unbindAll(); camera=null }
}
