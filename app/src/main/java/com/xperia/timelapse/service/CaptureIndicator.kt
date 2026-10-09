package com.xperia.timelapse.service

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.xperia.timelapse.timelapse.Phase
import com.xperia.timelapse.timelapse.label
import com.xperia.timelapse.ui.MainActivity

/** Notification LEDs are owned by Android; never use the photographic flash as an indicator. */
class CaptureIndicator(private val context:Context) {
 private val manager=context.getSystemService(NotificationManager::class.java)
 private var last:Phase?=null
 private val colors=mapOf("running" to 0xFFFF8000.toInt(),"paused" to 0xFFFFBF00.toInt(),"processing" to 0xFF0080FF.toInt())
 private fun channel(key:String)=if(key=="running")"indicator_running_orange_v2" else "indicator_$key"
 init {
  if(android.os.Build.VERSION.SDK_INT>=31)android.util.Log.i("XperiaIndicator","directLights="+runCatching {context.getSystemService(android.hardware.lights.LightsManager::class.java).lights.map {it.id}}.getOrElse {listOf(it.javaClass.simpleName)})
  colors.forEach {(key,color)->manager.createNotificationChannel(NotificationChannel(channel(key),"拍摄指示灯 · ${when(key){"running"->"拍摄";"paused"->"暂停";else->"合成"}}",NotificationManager.IMPORTANCE_DEFAULT).apply {
   enableLights(true);lightColor=color;enableVibration(false);setSound(null,null);setShowBadge(false)
  })}
 }
 fun update(phase:Phase,enabled:Boolean) {
  if(!enabled) {clear();return}
  if(last==phase)return
  val key=when(phase){Phase.COUNTDOWN,Phase.RUNNING->"running";Phase.PAUSED->"paused";Phase.PROCESSING->"processing";else->null}
  if(key==null) {clear();return}
  last=phase
  val open=PendingIntent.getActivity(context,2,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(context,channel(key)).setSmallIcon(android.R.drawable.ic_menu_camera)
   .setContentTitle("${phase.label} · 指示灯提示").setContentText("点此恢复拍摄界面")
   .setContentIntent(open).setOnlyAlertOnce(true).setLights(colors.getValue(key),if(key=="running")1 else 500,if(key=="running")0 else 1500).build()
  runCatching {manager.notify(11,notification)}
 }
 fun clear() {last=null;manager.cancel(11)}
}
