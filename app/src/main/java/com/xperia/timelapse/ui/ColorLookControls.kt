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

@Composable fun ColorLookControls(look:ColorLook,enabled:Boolean,change:(ColorLook)->Unit) {
 var open by remember {mutableStateOf(false)}
 TextButton(onClick={open=true},enabled=enabled,modifier=Modifier.height(40.dp),colors=ButtonDefaults.textButtonColors(contentColor=androidx.compose.ui.graphics.Color(0xFFFFC14D))) {Text("色彩 · ${look.style.label}")}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text("Log 与胶片风格")},text={
  Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
   var source=""
   ColorStyle.values().forEach {style->
    if(style.source!=source) {source=style.source;Text(source,style=MaterialTheme.typography.titleSmall)}
    TextButton(onClick={change(look.copy(style=style))},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
     Column(Modifier.weight(1f)) {Text(style.label);Text(style.detail,style=MaterialTheme.typography.bodySmall)}
     if(style==look.style)Text("✓")
    }
   }
   if(look.style!=ColorStyle.NATURAL) {
    Text("强度 ${(look.strength*100).roundToInt()}%")
    Slider(value=look.strength,onValueChange={change(look.copy(strength=it))},valueRange=0f..1f,enabled=enabled)
   }
   Text("A：abpy LUT；P：pyrocat101 LUT。均为第三方项目，索尼 JPEG 输入为近似适配，并非富士相机原生色彩。RAW 原片保持不变。",style=MaterialTheme.typography.bodySmall)
   if(Build.VERSION.SDK_INT<33)Text("当前系统仅保存成片效果，实时预览需要 Android 13 及以上。",style=MaterialTheme.typography.bodySmall)
  }
 },confirmButton={TextButton(onClick={open=false}) {Text("完成")}})
}
