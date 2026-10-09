package com.xperia.timelapse.ui
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
@Composable fun CountdownOverlay(ms:Long,modifier:Modifier=Modifier) { Text("${(ms+999)/1000}",modifier,color=Color.White,fontSize=72.sp) }
