package com.xperia.timelapse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xperia.timelapse.timelapse.*
import com.xperia.timelapse.camera.*
import com.xperia.timelapse.util.TimeFormatter

data class LensShortcut(val id:String,val label:String,val aperture:Float?=null)
private val Amber=Color(0xFFFFC14D)
@Composable fun CameraDashboard(settings:CaptureSettings,state:TimelapseState,cameraId:String,canStart:Boolean,startHint:String?,error:String?,
 tuningCapabilities:CameraTuningCapabilities,canTune:Boolean,onColorLook:(com.xperia.timelapse.color.ColorLook)->Unit,importedLuts:List<com.xperia.timelapse.color.ImportedLut>,onImportLut:()->Unit,onDeleteLut:(String)->Unit,onTuning:(CameraTuning)->Unit,
 lenses:List<LensShortcut>,selectedAperture:Float?,changeInterval:(String)->Unit,canSwitchLens:Boolean,onLens:(LensShortcut)->Unit,
 changeMode:(CaptureMode)->Unit,onShutter:()->Unit,onStop:()->Unit,onMenu:()->Unit,onProject:()->Unit,onBlackScreen:()->Unit,preview:@Composable (Modifier)->Unit) {
 BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0D10))) {
  val landscape=maxWidth>maxHeight
  val rail=if(landscape)210.dp else 0.dp
  val dock=if(landscape)0.dp else if(maxHeight<580.dp)210.dp else 230.dp
  preview(Modifier.fillMaxSize().padding(end=rail,bottom=dock))
  Column(Modifier.align(Alignment.TopStart).padding(top=12.dp,start=16.dp,end=rail+16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
   Surface(color=Color.Black.copy(alpha=.55f),shape=RoundedCornerShape(8.dp)) {
    Text(if(settings.mode==CaptureMode.TIMELAPSE) "长边 ${settings.resolution.width} · ${settings.fps}fps  /  ${settings.preference.label}" else settings.summary,
     Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
   }
   if(selectedAperture!=null && lenses.count {it.id==cameraId && it.aperture!=null}>1) {
    Text("主摄 F$selectedAperture · 点按对焦测光",color=Amber,style=MaterialTheme.typography.labelSmall)
   }
   if(state.busy || state.capturedFrames>0 || state.processingMessage!=null) Surface(color=Color.Black.copy(alpha=.55f),shape=RoundedCornerShape(8.dp)) {
    Column(Modifier.padding(10.dp)) {
     Text("${state.capturedFrames} / ${state.targetFrames ?: "—"} 张",style=MaterialTheme.typography.titleMedium)
     Text(state.processingMessage ?: if(state.phase==Phase.PAUSED)"已暂停" else if(state.busy && settings.mode!=CaptureMode.TIMELAPSE)"正在拍摄与保存…" else if(state.busy)"下一张 ${(state.nextCaptureInMs+999)/1000}s · 结束 ${TimeFormatter.end(state.estimatedEndMs)}" else "照片已保存",style=MaterialTheme.typography.bodySmall)
     if(state.busy && state.targetFrames!=null && state.targetFrames>0)LinearProgressIndicator(progress=(state.capturedFrames.toFloat()/state.targetFrames).coerceIn(0f,1f),modifier=Modifier.padding(top=8.dp).width(164.dp),color=Amber)
    }
   }
  }
  Column(Modifier.align(Alignment.BottomStart).padding(start=16.dp,end=rail+16.dp,bottom=dock+62.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
   CameraTuningControls(settings.tuning,tuningCapabilities,canTune,onTuning)
   ColorLookControls(settings.colorLook,canTune,importedLuts,onImportLut,onDeleteLut,onColorLook)
  }
  Row(Modifier.align(Alignment.BottomStart).padding(start=16.dp,end=rail+16.dp,bottom=dock+12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   lenses.forEach {lens->FilterChip(selected=lens.id==cameraId && lens.aperture==selectedAperture,onClick={onLens(lens)},enabled=canSwitchLens,
    colors=FilterChipDefaults.filterChipColors(containerColor=Color.Black.copy(alpha=.7f),labelColor=Color.White,selectedContainerColor=Amber,selectedLabelColor=Color(0xFF14181D),disabledContainerColor=Color.Black.copy(alpha=.5f),disabledLabelColor=Color.White.copy(alpha=.45f)),
    label={Text(lens.label)},modifier=Modifier.semantics {contentDescription="切换到${lens.label}镜头"})}
  }
  if(error!=null) Surface(Modifier.align(Alignment.BottomStart).padding(start=16.dp,end=rail+16.dp,bottom=dock+152.dp).fillMaxWidth(),color=Color(0xFF492626),shape=RoundedCornerShape(12.dp)) {
   Text(error,Modifier.padding(12.dp),color=Color(0xFFFFD9D9),style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis)
  }
  if(state.busy)TextButton(onClick=onBlackScreen,modifier=Modifier.align(Alignment.TopEnd).padding(end=rail+16.dp,top=12.dp).background(Color.Black.copy(alpha=.6f),RoundedCornerShape(8.dp))) {Text("黑屏省电",color=Amber)}
  val controls: @Composable ()->Unit = {
   CameraControls(settings,state,canStart,startHint,landscape,changeInterval,changeMode,onShutter,onStop,onProject,onMenu)
  }
  if(landscape) Box(Modifier.align(Alignment.CenterEnd).width(rail).fillMaxHeight().background(Color(0xFF14181D)).padding(12.dp),contentAlignment=Alignment.Center) {controls()}
  else Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(dock).background(Color(0xFF14181D)).padding(horizontal=16.dp,vertical=10.dp)) {controls()}
 }
}
@Composable private fun CameraControls(s:CaptureSettings,state:TimelapseState,canStart:Boolean,hint:String?,landscape:Boolean,interval:(String)->Unit,change:(CaptureMode)->Unit,shutter:()->Unit,stop:()->Unit,project:()->Unit,menu:()->Unit) {
 val pauseAllowed=state.phase in listOf(Phase.RUNNING,Phase.PAUSED,Phase.COUNTDOWN)
 Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly) {
  if(landscape) Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
   CaptureMode.values().forEach {mode->ModeButton(s.mode==mode,mode.label,!state.busy) {change(mode)}}
  } else Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
   CaptureMode.values().forEach {mode->ModeButton(s.mode==mode,mode.label,!state.busy) {change(mode)}}
  }
  if(s.mode==CaptureMode.TIMELAPSE) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
   listOf("1","5","10","30","60").forEach {value->
    TextButton(onClick={interval(value)},enabled=!state.busy,contentPadding=PaddingValues(0.dp),modifier=Modifier.weight(1f).height(36.dp).semantics {selected=s.interval==value;contentDescription="拍摄间隔 ${value} 秒"},colors=ButtonDefaults.textButtonColors(contentColor=if(s.interval==value)Amber else Color(0xFF98A3AE))) {Text("${value}s",style=MaterialTheme.typography.labelMedium)}
   }
  }
  Text(if(state.busy) state.processingMessage ?: state.phase.label else s.summary,style=MaterialTheme.typography.bodySmall,color=Color(0xFFB4BDC6),maxLines=2,overflow=TextOverflow.Ellipsis)
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(landscape)12.dp else 36.dp)) {
   if(!landscape) Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(62.dp)) {Text(if(s.mode==CaptureMode.TIMELAPSE)"${s.interval}s" else "1 张",style=MaterialTheme.typography.titleMedium);Text("${if(s.mode==CaptureMode.TIMELAPSE)"间隔" else "拍摄"}",style=MaterialTheme.typography.labelSmall,color=Color.Gray)}
   val active=if(state.busy)pauseAllowed else canStart
   Box(Modifier.size(if(landscape || androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<580)80.dp else 88.dp).border(2.dp,if(active)Color.White else Color.Gray,CircleShape).padding(6.dp)
    .background(if(active)Amber else Color(0xFF40454C),CircleShape).clickable(enabled=active,role=Role.Button,onClick=shutter)
    .semantics {contentDescription=if(state.busy)if(state.phase==Phase.PAUSED)"继续拍摄" else "暂停拍摄" else "${s.mode.label}快门"},contentAlignment=Alignment.Center) {
    if(state.phase==Phase.PROCESSING || state.phase==Phase.STOPPING)CircularProgressIndicator(Modifier.size(30.dp),color=Color.White,strokeWidth=2.dp)
    else CameraGlyph(if(state.busy)if(state.phase==Phase.PAUSED)"play" else "pause" else "shutter","",Color(0xFF17191C))
   }
   if(!landscape) Box(Modifier.width(62.dp),contentAlignment=Alignment.Center) {
    if(state.busy)IconButton(onClick=stop,enabled=state.phase!=Phase.STOPPING) {CameraGlyph("stop",if(state.phase==Phase.PROCESSING)"取消合成" else "停止拍摄",Color(0xFFFF8D89))}
    else Text("PRO-I",style=MaterialTheme.typography.labelMedium,color=Color.Gray)
   }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
   TextButton(onClick=project,contentPadding=PaddingValues(horizontal=8.dp)) {
    CameraGlyph("folder","拍摄结果",Color.White)
    Spacer(Modifier.width(6.dp));Text("文件",style=MaterialTheme.typography.labelMedium)
   }
   TextButton(onClick=menu,contentPadding=PaddingValues(horizontal=8.dp)) {
    CameraGlyph("menu","打开菜单")
    Spacer(Modifier.width(6.dp));Text("设置",style=MaterialTheme.typography.labelMedium)
   }
  }
  if(landscape && state.busy)TextButton(onClick=stop,enabled=state.phase!=Phase.STOPPING) {Text(if(state.phase==Phase.PROCESSING)"取消合成" else "停止并保存",color=Color(0xFFFF8D89))}
  else if(hint!=null && !state.busy)Text(hint,style=MaterialTheme.typography.labelSmall,color=Amber,maxLines=2)
 }
}
@Composable private fun ModeButton(selected:Boolean,label:String,enabled:Boolean,click:()->Unit) {
 TextButton(onClick=click,enabled=enabled,modifier=Modifier.semantics {this.selected=selected},contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp),colors=ButtonDefaults.textButtonColors(contentColor=if(selected)Amber else Color(0xFF98A3AE))) {Text(label,style=MaterialTheme.typography.labelLarge)}
}
@Composable fun CameraGlyph(kind:String,description:String,color:Color=Color.White) {
 Canvas(Modifier.size(26.dp).semantics {contentDescription=description}) {
  val w=size.width;val h=size.height;val stroke=2.dp.toPx()
  when(kind) {
   "menu"->listOf(.25f,.5f,.75f).forEach {drawLine(color,Offset(w*.15f,h*it),Offset(w*.85f,h*it),stroke)}
   "folder"->{drawRect(color,Offset(w*.12f,h*.32f),Size(w*.76f,h*.5f),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke));drawLine(color,Offset(w*.12f,h*.22f),Offset(w*.45f,h*.22f),stroke)}
   "pause"->{drawRect(color,Offset(w*.2f,h*.15f),Size(w*.2f,h*.7f));drawRect(color,Offset(w*.6f,h*.15f),Size(w*.2f,h*.7f))}
   "stop"->drawRoundRect(color,Offset(w*.2f,h*.2f),Size(w*.6f,h*.6f),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
   "play"->drawPath(Path().apply {moveTo(w*.28f,h*.15f);lineTo(w*.85f,h*.5f);lineTo(w*.28f,h*.85f);close()},color)
   "shutter"->{drawCircle(color,w*.32f,style=androidx.compose.ui.graphics.drawscope.Stroke(stroke));drawCircle(color,w*.1f)}
  }
 }
}
