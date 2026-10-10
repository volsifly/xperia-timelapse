package com.xperia.timelapse.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xperia.timelapse.color.*
import kotlin.math.roundToInt

@Composable fun ColorLookControls(look:ColorLook,enabled:Boolean,importedLuts:List<ImportedLut>,onImport:()->Unit,onDelete:(String)->Unit,change:(ColorLook)->Unit) {
 var open by remember {mutableStateOf(false)}
 TextButton(onClick={open=true},enabled=enabled,modifier=Modifier.height(40.dp),colors=ButtonDefaults.textButtonColors(contentColor=androidx.compose.ui.graphics.Color(0xFFFFC14D))) {Text("色彩 · ${look.label}")}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text("Log 与胶片风格")},text={
  Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
   Text("内置风格",style=MaterialTheme.typography.titleSmall)
   ColorStyle.values().forEach {style->
    TextButton(onClick={change(look.copy(style=style,importedLutId=null,importedLutName=null))},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
     Column(Modifier.weight(1f)) {Text(style.label);Text(style.detail,style=MaterialTheme.typography.bodySmall)}
     if(look.importedLutId==null && style==look.style)Text("✓")
    }
   }
   if(importedLuts.isNotEmpty()) {
    Text("已导入的 3D LUT",style=MaterialTheme.typography.titleSmall)
    importedLuts.forEach {lut->
     Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
      TextButton(onClick={change(look.copy(style=ColorStyle.NATURAL,importedLutId=lut.id,importedLutName=lut.name))},enabled=enabled,modifier=Modifier.weight(1f)) {
       Text(lut.name,Modifier.weight(1f));if(look.importedLutId==lut.id)Text("✓")
      }
      TextButton(onClick={onDelete(lut.id)},enabled=enabled) {Text("删除")}
     }
    }
   }
   OutlinedButton(onClick=onImport,enabled=enabled,modifier=Modifier.fillMaxWidth()) {Text("导入 .cube 文件")}
   if(look.style!=ColorStyle.NATURAL || look.importedLutId!=null) {
    Text("强度 ${(look.strength*100).roundToInt()}%")
    Slider(value=look.strength,onValueChange={change(look.copy(strength=it))},valueRange=0f..1f,enabled=enabled)
   }
   Text("支持 .cube 纯 3D LUT，尺寸 2–65 阶；包含 1D LUT 的文件暂不支持。效果应用于 JPEG 和视频，RAW 原片保持不变。",style=MaterialTheme.typography.bodySmall)
   if(Build.VERSION.SDK_INT<33)Text("当前系统仅保存成片效果，实时预览需要 Android 13 及以上。",style=MaterialTheme.typography.bodySmall)
  }
 },confirmButton={TextButton(onClick={open=false}) {Text("完成")}})
}
