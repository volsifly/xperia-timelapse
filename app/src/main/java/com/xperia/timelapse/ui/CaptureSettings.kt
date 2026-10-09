package com.xperia.timelapse.ui

import android.content.Context
import com.xperia.timelapse.camera.*
import com.xperia.timelapse.timelapse.TimelapseConfig
import com.xperia.timelapse.video.*

enum class CaptureMode(val label:String) { PHOTO("拍照"), TIMER("定时"), TIMELAPSE("延时") }
data class CaptureSettings(
 val whiteBalanceMode:WhiteBalanceMode=WhiteBalanceMode.AUTO,val exposureEv:Float=0f,
 val autoLock:Boolean=true,val autoBlack:Boolean=true,val indicator:Boolean=true,
 val mode:CaptureMode=CaptureMode.TIMELAPSE, val interval:String="2",val limit:String="100",val duration:Boolean=false,
 val timerSeconds:String="5", val exposure:Boolean=true,val focus:Boolean=true,val whiteBalance:Boolean=true,
 val deletePhotos:Boolean=true,val preference:VideoPreference=VideoPreference.AUTO,
 val quality:VideoQuality=VideoQuality.STANDARD,val resolution:VideoResolution=VideoResolution.FULL_HD,val fps:Int=30
) {
 val tuning get()=CameraTuning(whiteBalanceMode,exposureEv)
 val output get()=VideoOutput(resolution,fps)
 val validInterval get()=interval.toLongOrNull()?.let {it in 1..3600}==true && limit.toLongOrNull()?.let {if(duration)it in 1..1440 else it in 1..100000}==true
 val validTimer get()=timerSeconds.toLongOrNull()?.let {it in 0..86400}==true
 val estimatedFrames get()=if(!validInterval)null else if(duration)(limit.toLong()*60+interval.toLong()-1)/interval.toLong() else limit.toLong()
 val summary get()=when(mode){CaptureMode.PHOTO->"单张照片";CaptureMode.TIMER->"${timerSeconds} 秒倒计时";CaptureMode.TIMELAPSE->"每 ${interval} 秒 · ${limit} ${if(duration) "分钟" else "张"}"}
 fun config():TimelapseConfig = when(mode) {
  CaptureMode.PHOTO,CaptureMode.TIMER -> {require(mode!=CaptureMode.TIMER || validTimer);TimelapseConfig(totalFrames=1,delayMs=if(mode==CaptureMode.TIMER)timerSeconds.toLong()*1000 else 0,lockExposure=false,lockFocus=false,lockWhiteBalance=false,autoComposeVideo=false,deleteOriginalPhotos=false)}
  CaptureMode.TIMELAPSE -> {require(validInterval);TimelapseConfig(intervalMs=interval.toLong()*1000,totalFrames=if(duration)null else limit.toInt(),totalDurationMs=if(duration)limit.toLong()*60000 else null,lockExposure=exposure,lockFocus=focus,lockWhiteBalance=whiteBalance,autoComposeVideo=true,deleteOriginalPhotos=deletePhotos,videoPreference=preference,videoQuality=quality,videoResolution=resolution,videoFrameRate=fps)}
 }
}
class CapturePreferences(context:Context) {
 private val preferences=context.getSharedPreferences("capture-settings",Context.MODE_PRIVATE)
 fun load():CaptureSettings {
  fun <T:Enum<T>> choice(key:String,values:Array<T>,default:T)=values.firstOrNull {it.name==preferences.getString(key,default.name)} ?: default
  return CaptureSettings(whiteBalanceMode=choice("wb-mode",WhiteBalanceMode.values(),WhiteBalanceMode.AUTO),exposureEv=preferences.getFloat("exposure-ev",0f).takeIf {it.isFinite()} ?: 0f,autoLock=preferences.getBoolean("lock-after-30s",true),autoBlack=preferences.getBoolean("black-after-5s",true),indicator=preferences.getBoolean("indicator",true),mode=choice("mode",CaptureMode.values(),CaptureMode.TIMELAPSE),interval=preferences.getString("interval","2")!!,limit=preferences.getString("limit","100")!!,
   duration=preferences.getBoolean("duration",false),timerSeconds=preferences.getString("timer","5")!!,
   exposure=preferences.getBoolean("ae",true),focus=preferences.getBoolean("af",true),whiteBalance=preferences.getBoolean("awb",true),
   deletePhotos=preferences.getBoolean("delete",true),
   preference=choice("codec",VideoPreference.values(),VideoPreference.AUTO),quality=choice("quality",VideoQuality.values(),VideoQuality.STANDARD),
   resolution=choice("resolution",VideoResolution.values(),VideoResolution.FULL_HD),fps=preferences.getInt("fps",30).takeIf {it in VideoOutput.SUPPORTED_FPS} ?: 30)
 }
 fun save(s:CaptureSettings) {preferences.edit().putString("wb-mode",s.whiteBalanceMode.name).putFloat("exposure-ev",s.exposureEv).putBoolean("lock-after-30s",s.autoLock).putBoolean("black-after-5s",s.autoBlack).putBoolean("indicator",s.indicator).putString("mode",s.mode.name).putString("interval",s.interval).putString("limit",s.limit).putBoolean("duration",s.duration).putString("timer",s.timerSeconds)
  .putBoolean("ae",s.exposure).putBoolean("af",s.focus).putBoolean("awb",s.whiteBalance).putBoolean("delete",s.deletePhotos)
  .putString("codec",s.preference.name).putString("quality",s.quality.name).putString("resolution",s.resolution.name).putInt("fps",s.fps).apply()}
}
