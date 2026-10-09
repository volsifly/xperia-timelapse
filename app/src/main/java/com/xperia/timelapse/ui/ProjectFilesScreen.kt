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

@Composable fun ProjectFilesScreen(root:File,currentPath:String?,open:(File)->Unit) {
 var projects by remember {mutableStateOf<List<Pair<File,String>>?>(null)}
 LaunchedEffect(root,currentPath) {
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
   Surface(Modifier.fillMaxWidth().clickable {open(folder)},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant) {
    Column(Modifier.padding(12.dp)) {Text(folder.name,style=MaterialTheme.typography.titleSmall);Text(description,style=MaterialTheme.typography.bodySmall)}
   }
  }
 }
}
