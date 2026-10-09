package com.xperia.timelapse.util
import android.content.Context
import android.os.Environment
import java.io.File
object StorageHelper {
 fun root(context: Context): File = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "XperiaTimelapse").apply { mkdirs() }
 // Reserve real free space; do not assume other apps cached bytes can be reclaimed.
 @android.annotation.SuppressLint("UsableSpace")
 fun requireSpace(root: File) { check(root.usableSpace > 100L*1024*1024) { "存储不足 100 MB，已停止拍摄" } }
}
