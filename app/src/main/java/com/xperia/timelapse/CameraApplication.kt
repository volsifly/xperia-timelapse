package com.xperia.timelapse

import android.app.Application
import com.xperia.timelapse.color.LutRepository
import com.xperia.timelapse.color.ImportedLutRepository

class CameraApplication:Application() {
 override fun onCreate() {
  super.onCreate()
  LutRepository.readAsset={path->assets.open(path).use {it.readBytes()}}
  val imported=ImportedLutRepository(this)
  LutRepository.readImported=imported::read
 }
}
