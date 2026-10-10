package com.xperia.timelapse

import android.app.Application
import com.xperia.timelapse.color.LutRepository

class CameraApplication:Application() {
 override fun onCreate() {
  super.onCreate()
  LutRepository.readAsset={path->assets.open(path).use {it.readBytes()}}
 }
}
