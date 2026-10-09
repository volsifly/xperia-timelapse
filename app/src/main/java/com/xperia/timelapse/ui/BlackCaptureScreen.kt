package com.xperia.timelapse.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable fun BlackCaptureScreen(activity:Activity,restore:()->Unit) {
 var hint by remember {mutableStateOf(true)}
 LaunchedEffect(Unit) {delay(3000);hint=false}
 DisposableEffect(activity) {
  val window=activity.window
  val brightness=window.attributes.screenBrightness
  val keepScreenOn=window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON!=0
  val controller=WindowCompat.getInsetsController(window,window.decorView)
  window.attributes=window.attributes.apply {screenBrightness=.01f}
  window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  controller.hide(WindowInsetsCompat.Type.systemBars())
  onDispose {
   window.attributes=window.attributes.apply {screenBrightness=brightness}
   if(keepScreenOn)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
   controller.show(WindowInsetsCompat.Type.systemBars())
  }
 }
 BackHandler(onBack=restore)
 Box(Modifier.fillMaxSize().background(Color.Black).semantics {contentDescription="黑屏拍摄，双击恢复"}.pointerInput(Unit) {detectTapGestures(onDoubleTap={restore()})},contentAlignment=Alignment.Center) {
  if(hint)Text("拍摄继续中 · 双击恢复",color=Color.DarkGray)
 }
}
