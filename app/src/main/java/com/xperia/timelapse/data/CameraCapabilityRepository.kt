package com.xperia.timelapse.data
import android.content.Context
import com.xperia.timelapse.camera.Camera2CapabilityReader
class CameraCapabilityRepository(context: Context) {
 private val reader=Camera2CapabilityReader(context)
 fun all()=reader.read()
 fun preferredId()=all().filter { it.rear }.sortedWith(compareByDescending<com.xperia.timelapse.camera.CameraCapabilities> { it.sensorArea }.thenByDescending { it.pixels }).firstOrNull()?.id ?: all().first().id
}
