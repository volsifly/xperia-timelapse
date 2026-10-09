package com.xperia.timelapse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xperia.timelapse.camera.*
import kotlin.math.roundToInt

@Composable fun CameraTuningControls(tuning:CameraTuning,caps:CameraTuningCapabilities,enabled:Boolean,change:(CameraTuning)->Unit) {
 var panel by remember {mutableStateOf<String?>(null)}
 val amber=Color(0xFFFFC14D)
 Row(Modifier.background(Color.Black.copy(alpha=.65f),RoundedCornerShape(12.dp)),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
  TextButton(onClick={panel="wb"},enabled=enabled && caps.whiteBalances.size>1,modifier=Modifier.height(40.dp).semantics {contentDescription="设置白平衡"}) {
   Text("WB · ${tuning.whiteBalance.label}",color=if(enabled)amber else Color.Gray,style=MaterialTheme.typography.labelMedium)
  }
  TextButton(onClick={panel="ev"},enabled=enabled && caps.supportsEv,modifier=Modifier.height(40.dp).semantics {contentDescription="设置曝光补偿 EV"}) {
   Text("EV · ${evLabel(tuning.ev)}",color=if(enabled)amber else Color.Gray,style=MaterialTheme.typography.labelMedium)
  }
 }
 if(panel=="wb")AlertDialog(onDismissRequest={panel=null},title={Text("白平衡")},text={
  Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) {
   caps.whiteBalances.forEach {mode->
    TextButton(onClick={change(tuning.copy(whiteBalance=mode));panel=null},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
     Text(mode.label,Modifier.weight(1f),color=if(mode==tuning.whiteBalance)amber else MaterialTheme.colorScheme.onSurface)
     if(mode==tuning.whiteBalance)Text("✓",color=amber)
    }
   }
  }
 },confirmButton={TextButton(onClick={panel=null}) {Text("关闭")}})
 if(panel=="ev" && caps.supportsEv) {
  var index by remember(tuning.ev) {mutableFloatStateOf(caps.indexFor(tuning.ev).toFloat())}
  LaunchedEffect(enabled,tuning.ev) {if(enabled)index=caps.indexFor(tuning.ev).toFloat()}
  fun apply(value:Int) {index=value.coerceIn(caps.minIndex,caps.maxIndex).toFloat();change(tuning.copy(ev=index*caps.evStep))}
  AlertDialog(onDismissRequest={panel=null},title={Text("曝光补偿 EV")},text={
   Column {
    Text("${evLabel(index*caps.evStep)} EV",style=MaterialTheme.typography.headlineMedium,color=amber)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
     TextButton(onClick={apply(index.roundToInt()-1)},enabled=enabled && index>caps.minIndex,modifier=Modifier.semantics {contentDescription="降低 EV"}) {Text("−")}
     TextButton(onClick={apply(0)},enabled=enabled) {Text("重置为 0")}
     TextButton(onClick={apply(index.roundToInt()+1)},enabled=enabled && index<caps.maxIndex,modifier=Modifier.semantics {contentDescription="增加 EV"}) {Text("+")}
    }
    Slider(value=index,onValueChange={index=it},onValueChangeFinished={apply(index.roundToInt())},enabled=enabled,
     valueRange=caps.minIndex.toFloat()..caps.maxIndex.toFloat(),steps=(caps.maxIndex-caps.minIndex-1).coerceAtLeast(0),modifier=Modifier.semantics {contentDescription="EV 调节滑块"})
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
     Text("${evLabel(caps.minIndex*caps.evStep)} EV");Text("${evLabel(caps.maxIndex*caps.evStep)} EV")
    }
   }
  },confirmButton={TextButton(onClick={panel=null}) {Text("完成")}})
 }
}
