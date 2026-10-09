package com.xperia.timelapse.camera
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import java.io.File
import com.xperia.timelapse.timelapse.TimelapseConfig
import org.json.JSONObject
interface CameraController {
 suspend fun open(owner: LifecycleOwner, cameraId: String)
 fun preview(provider: Preview.SurfaceProvider?)
 suspend fun capture(file: File): Pair<Int,Int>
 suspend fun lock(config: TimelapseConfig): JSONObject
 fun close()
}
