package com.xperia.timelapse.camera
import android.content.Context
import android.hardware.camera2.*
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.os.Build

data class CameraCapabilities(val id: String, val rear: Boolean, val sensorArea: Float, val pixels: Int, val text: String,val equivalentFocal:Float=0f,val selectable:Boolean=true,val apertures:List<Float> = emptyList())
class Camera2CapabilityReader(context: Context) {
 val manager=context.getSystemService(CameraManager::class.java)
 fun read(): List<CameraCapabilities> = manager.cameraIdList.map { id ->
  val c=manager.getCameraCharacteristics(id)
  val modes=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
  val map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
  val size=c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
  val pixels=c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
  val rear=c.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK
  val level=when(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) { 0->"LIMITED";1->"FULL";2->"LEGACY";3->"LEVEL_3";4->"EXTERNAL";else->"未知" }
  CameraCapabilities(id,rear,(size?.width ?: 0f)*(size?.height ?: 0f),(pixels?.width ?: 0)*(pixels?.height ?: 0),
   "设备：${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}\n"+
   "Camera ID：$id / ${if(rear) "后置" else "前置或外接"} / $level\n"+
   "传感器：$size / 像素：$pixels\n"+
   "手动曝光：${1 in modes} / 快门(ns)：${c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}\n"+
   "ISO：${c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}\n"+
   "手动对焦：${(c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f)>0} / 0～${c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)} 屈光度\n"+
   "AE锁：${c.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)} / AWB锁：${c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)}\n"+
   "预览：${map?.getOutputSizes(SurfaceTexture::class.java)?.joinToString()}\n"+
   "JPEG：${map?.getOutputSizes(ImageFormat.JPEG)?.joinToString()}\n"+
   "RAW：${3 in modes} / ${map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.joinToString()}\n"+
   "FPS：${c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.joinToString()}\n"+
   "视频防抖：${c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.joinToString()}\n"+
   "高速视频：${map?.highSpeedVideoSizes?.joinToString()}",
   if(size!=null && size.width>0)(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f)*36f/size.width else 0f,
   map?.getOutputSizes(ImageFormat.JPEG)?.isNotEmpty()==true,c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty())
 }
}
