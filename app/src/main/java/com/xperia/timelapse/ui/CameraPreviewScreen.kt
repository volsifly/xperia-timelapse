package com.xperia.timelapse.ui
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xperia.timelapse.service.TimelapseForegroundService
import com.xperia.timelapse.timelapse.*
import com.xperia.timelapse.camera.CameraParameterPolicy
import kotlinx.coroutines.*
@Composable fun CameraPreviewScreen(service:TimelapseForegroundService,state:TimelapseState,cameraId:String,modifier:Modifier=Modifier,focusRequest:Int=0,colorLook:com.xperia.timelapse.color.ColorLook=com.xperia.timelapse.color.ColorLook()) {
 val configuration=LocalConfiguration.current
 var previewError by remember {mutableStateOf(false)}
 val previewEffect=remember(colorLook) {colorLook}
 val scope=rememberCoroutineScope()
 var view by remember {mutableStateOf<PreviewView?>(null)}
 var point by remember {mutableStateOf<Offset?>(null)}
 var message by remember {mutableStateOf("")}
 var success by remember {mutableStateOf<Boolean?>(null)}
 var job by remember {mutableStateOf<Job?>(null)}
 LaunchedEffect(cameraId,state.busy,configuration.orientation) {job?.cancel();point=null;message=""}
 DisposableEffect(service) { onDispose {job?.cancel();service.camera.preview(null)} }
 fun focusAt(position:Offset) {
    job?.cancel()
    job=scope.launch {
     if(state.busy) {message="拍摄中对焦已锁定";delay(1800);message="";return@launch}
     val preview=view ?: return@launch
     if(preview.previewStreamState.value!=PreviewView.StreamState.STREAMING)return@launch
     val info=service.camera.previewResolution ?: return@launch
     val rotated=info.rotationDegrees%180!=0
     val normalized=CameraParameterPolicy.previewPoint(position.x,position.y,preview.width,preview.height,
      if(rotated)info.resolution.height else info.resolution.width,if(rotated)info.resolution.width else info.resolution.height)
     if(normalized==null) {point=null;message="请点击取景画面，对黑边无法对焦";delay(1800);message="";return@launch}
     android.util.Log.i("XperiaFocus","tap=$position frame=$normalized camera=$cameraId")
     point=position;success=null;message="正在对焦并测光"
     try {
      success=service.focus(preview.meteringPointFactory.createPoint(position.x,position.y))
      message=if(success==true)"已对焦并测光" else "已测光，未合焦，请换个位置"
     }catch(e:CancellationException){throw e}catch(e:Exception){success=false;message=e.message ?: "对焦失败"}
     delay(1800);point=null;message=""
    }
 }
 LaunchedEffect(focusRequest) {
  if(focusRequest>0 && !state.busy)view?.let {focusAt(Offset(it.width/2f,it.height/2f))}
 }
 Box(modifier.background(Color.Black)) {
  AndroidView(factory={context->PreviewView(context).apply {
   implementationMode=PreviewView.ImplementationMode.COMPATIBLE
   scaleType=PreviewView.ScaleType.FIT_CENTER
   service.camera.preview(surfaceProvider);view=this
  }},update={preview->
   configuration.orientation
   val resolution=service.camera.previewResolution
   val rotated=resolution?.rotationDegrees?.rem(180)!=0
   previewError=runCatching {com.xperia.timelapse.color.ColorLookRenderer.preview(preview,previewEffect,
    if(rotated)resolution?.resolution?.height ?: 0 else resolution?.resolution?.width ?: 0,
    if(rotated)resolution?.resolution?.width ?: 0 else resolution?.resolution?.height ?: 0)}.isFailure
   preview.post {service.camera.setRotation(preview.display?.rotation ?: Surface.ROTATION_0,capture=!state.busy)}
  },modifier=Modifier.fillMaxSize())
  Box(Modifier.matchParentSize().semantics {contentDescription="取景画面，点击对焦并测光"}.pointerInput(service,cameraId,state.busy) {
   detectTapGestures {position->
    focusAt(position)
   }
  }) {
   point?.let {position->Canvas(Modifier.matchParentSize()) {
    val color=if(success==false)Color(0xFFFF8D89) else if(success==true)Color(0xFF8EDFA1) else Color(0xFFFFC14D)
    val radius=24.dp.toPx()
    drawCircle(color,radius,position,style=Stroke(2.dp.toPx()))
    drawLine(color,position-Offset(radius+8.dp.toPx(),0f),position-Offset(radius-4.dp.toPx(),0f),2.dp.toPx())
    drawLine(color,position+Offset(radius-4.dp.toPx(),0f),position+Offset(radius+8.dp.toPx(),0f),2.dp.toPx())
   }}
   if(message.isNotEmpty())Text(message,Modifier.align(Alignment.BottomCenter).padding(bottom=72.dp).background(Color.Black.copy(alpha=.65f)).padding(8.dp),color=Color.White,style=MaterialTheme.typography.labelMedium)
  }
  if(previewError)Text("色彩预览不可用；拍摄仍应用所选效果",Modifier.align(Alignment.BottomCenter).padding(8.dp),color=Color.White)
  if(state.phase==Phase.COUNTDOWN && state.nextCaptureInMs>0)CountdownOverlay(state.nextCaptureInMs,Modifier.align(Alignment.Center))
 }
}
