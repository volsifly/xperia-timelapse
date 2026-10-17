package com.xperia.timelapse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable fun ProjectFilesScreen(root:File,currentPath:String?,refreshKey:String,busy:Boolean,regeneratingPath:String?,open:(File)->Unit,regenerate:(File)->Unit) {
 var projects by remember {mutableStateOf<List<Pair<File,String>>?>(null)}
 LaunchedEffect(root,currentPath,refreshKey) {
  projects=withContext(Dispatchers.IO) {
   root.listFiles()?.filter {it.isDirectory && File(it,"metadata.json").isFile}?.sortedByDescending {it.name}?.map {folder->
    val description=runCatching {val meta=JSONObject(File(folder,"metadata.json").readText())
     "${meta.optInt("frame_count")} 张 · ${if(File(folder,"preview.mp4").isFile)"视频" else "照片"}${if(folder.absolutePath==currentPath)" · 本次" else ""}"
    }.getOrDefault("项目文件")
    folder to description
   }.orEmpty()
  }
 }
 when {
  projects==null->CircularProgressIndicator()
  projects!!.isEmpty()->Text("还没有拍摄文件。拍摄完成后，照片和视频会显示在这里。")
  else->projects!!.forEach {(folder,description)->
   val canGenerate=!File(folder,"preview.mp4").isFile && (folder.listFiles()?.count {it.name.matches(Regex("frame_\\d{6}\\.jpg")) && it.isFile && it.length()>0} ?: 0)>=2
   val isGenerating=busy && regeneratingPath==folder.absolutePath
   Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant) {
    Column(Modifier.fillMaxWidth().clickable {open(folder)}.padding(12.dp)) {
     Text(folder.name,style=MaterialTheme.typography.titleSmall);Text(description,style=MaterialTheme.typography.bodySmall)
     if(canGenerate || isGenerating) {
      Spacer(Modifier.height(8.dp))
      OutlinedButton(onClick={regenerate(folder)},enabled=!busy && canGenerate) {Text(if(isGenerating)"正在生成视频…" else "生成视频")}
     }
    }
   }
  }
 }
}
