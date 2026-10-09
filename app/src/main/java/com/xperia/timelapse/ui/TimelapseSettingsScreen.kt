package com.xperia.timelapse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xperia.timelapse.video.*

@Composable fun TimelapseSettingsScreen(s:CaptureSettings,enabled:Boolean,change:(CaptureSettings)->Unit,section:String,codecs:Set<VideoEncoding>?,lockAuthorized:Boolean=false,authorizeLock:()->Unit={}) {
 Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
  if(section=="capture") {
   Text("拍摄图像大小",style=MaterialTheme.typography.titleMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {VideoResolution.values().forEach { option -> FilterChip(s.resolution==option,{change(s.copy(resolution=option))},label={Text("${option.width}")},enabled=enabled)}}
   Text("按所选长边缩放照片，保留画面比例和横竖方向；视频与照片宽高一致。",style=MaterialTheme.typography.bodySmall)
   HorizontalDivider()
   Text("间隔拍摄",style=MaterialTheme.typography.titleMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    NumberField("间隔 / 秒",s.interval,{change(s.copy(interval=it))},enabled,Modifier.weight(1f))
    NumberField(if(s.duration)"总时长 / 分钟" else "总张数",s.limit,{change(s.copy(limit=it))},enabled,Modifier.weight(1f))
   }
   SettingSwitch("按时长结束","关闭时按总张数结束",s.duration,enabled) {change(s.copy(duration=it))}
   if(!s.validInterval) Text("间隔 1～3600 秒；张数 1～100000 或时长 1～1440 分钟。",color=MaterialTheme.colorScheme.error)
   HorizontalDivider()
   Text("定时拍照",style=MaterialTheme.typography.titleMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(3,5,10).forEach { value -> FilterChip(s.timerSeconds==value.toString(),{change(s.copy(timerSeconds=value.toString()))},label={Text("${value}s")},enabled=enabled)}}
   NumberField("自定义倒计时 / 秒",s.timerSeconds,{change(s.copy(timerSeconds=it))},enabled)
   if(!s.validTimer)Text("倒计时为 0～86400 秒。",color=MaterialTheme.colorScheme.error)
   HorizontalDivider()
   Text("省电与状态提示",style=MaterialTheme.typography.titleMedium)
   SettingSwitch("开始 5 秒后自动黑屏","双击恢复界面；开始后 30 秒可自动锁屏",s.autoBlack,enabled) {change(s.copy(autoBlack=it))}
   SettingSwitch("开始 30 秒后自动锁屏","锁屏后继续拍摄",s.autoLock,enabled) {change(s.copy(autoLock=it))}
   if(s.autoLock && !lockAuthorized)TextButton(onClick=authorizeLock,enabled=enabled) {Text("授权自动锁屏")}
   if(s.autoLock && lockAuthorized)Text("自动锁屏已授权",style=MaterialTheme.typography.bodySmall)
   SettingSwitch("指示灯提示","拍摄橙灯、暂停黄灯、合成蓝灯；常亮/闪烁由系统控制",s.indicator,enabled) {change(s.copy(indicator=it))}
   Text("前 30 秒保持屏幕唤醒，授权后自动锁屏；CPU 和拍摄服务继续运行。指示灯在真正熄屏后由系统控制，无法保证常亮。",style=MaterialTheme.typography.bodySmall)
   HorizontalDivider()
   Text("画面稳定",style=MaterialTheme.typography.titleMedium)
   SettingSwitch("锁定曝光","支持时固定快门与 ISO",s.exposure,enabled) {change(s.copy(exposure=it))}
   SettingSwitch("锁定对焦","保持当前对焦距离",s.focus,enabled) {change(s.copy(focus=it))}
   SettingSwitch("锁定白平衡","减少连续照片的色彩变化",s.whiteBalance,enabled) {change(s.copy(whiteBalance=it))}
  } else {
   Text("延时拍摄结束后自动合成视频并保存到相册。",style=MaterialTheme.typography.bodySmall)
   SettingSwitch("合成后删除原片","完整校验通过后永久删除 JPEG",s.deletePhotos,enabled) {change(s.copy(deletePhotos=it))}
   HorizontalDivider()
   Text("输出帧率",style=MaterialTheme.typography.titleMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {VideoOutput.SUPPORTED_FPS.forEach { option -> FilterChip(s.fps==option,{change(s.copy(fps=option))},label={Text("$option")},enabled=enabled)}}
   Text("帧率决定成片播放速度，拍摄间隔单独设置。",style=MaterialTheme.typography.bodySmall)
   Text("视频编码",style=MaterialTheme.typography.titleMedium)
   VideoPreference.values().forEach { option ->
    val supported=option!=VideoPreference.AV1 || codecs?.contains(VideoEncoding.AV1)==true
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=s.preference==option,enabled=enabled&&supported,role=Role.RadioButton,onClick={change(s.copy(preference=option))}),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
     RadioButton(s.preference==option,null,enabled=enabled&&supported)
     Text(option.label+if(!supported)" · 当前组合不可用" else "")
    }
   }
   Text("输出画质",style=MaterialTheme.typography.titleMedium)
   Column {VideoQuality.values().forEach {option -> Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=s.quality==option,enabled=enabled,role=Role.RadioButton,onClick={change(s.copy(quality=option))}),verticalAlignment=Alignment.CenterVertically) {RadioButton(s.quality==option,null,enabled=enabled);Text(option.label)}}}
   val predicted=codecs?.let {VideoEncodingPolicy.candidates(s.preference,it).firstOrNull()}
   Text("可用编码：${codecs?.joinToString {it.label} ?: "检测中…"}",style=MaterialTheme.typography.bodySmall)
   if(codecs!=null && predicted==null)Text("当前输出组合不受支持，请调整设置。",color=MaterialTheme.colorScheme.error)
   if(predicted!=null)Text("照片长边 ${s.resolution.width} · ${s.fps}fps · 实际编码能力以照片尺寸为准",style=MaterialTheme.typography.bodySmall)
   Text("编码失败会尝试兼容格式；全部失败则保留照片。",style=MaterialTheme.typography.bodySmall)
  }
 }
}
@Composable private fun NumberField(label:String,value:String,change:(String)->Unit,enabled:Boolean,modifier:Modifier=Modifier) {
 OutlinedTextField(value,change,label={Text(label)},singleLine=true,enabled=enabled,modifier=modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
}
@Composable private fun SettingSwitch(title:String,detail:String,checked:Boolean,enabled:Boolean,change:(Boolean)->Unit) {
 Row(Modifier.fillMaxWidth().toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=change),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
  Column(Modifier.weight(1f)) {Text(title);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  Switch(checked,null,enabled=enabled)
 }
}
