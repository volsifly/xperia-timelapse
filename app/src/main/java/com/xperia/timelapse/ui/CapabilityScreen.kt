package com.xperia.timelapse.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xperia.timelapse.camera.CameraCapabilities
@Composable fun CapabilityScreen(items:List<CameraCapabilities>,select:(String)->Unit,enabled:Boolean) {
 Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  items.forEach { camera -> Card { Column(Modifier.padding(12.dp)) { Text(camera.text);TextButton(onClick={select(camera.id)},enabled=enabled) { Text("选择 Camera ${camera.id}") } } } }
 }
}
