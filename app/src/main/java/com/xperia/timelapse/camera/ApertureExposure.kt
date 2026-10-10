package com.xperia.timelapse.camera

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Exposure metered before AE is disabled. EV remains effective in manual aperture mode. */
data class ApertureExposure(val aperture:Float,val exposureNs:Long,val iso:Int,val ev:Float)
object ApertureExposurePolicy {
 fun adjust(sample:ApertureExposure,aperture:Float,ev:Float,minNs:Long,maxNs:Long,minIso:Int,maxIso:Int):ApertureExposure {
  require(sample.aperture>0 && sample.aperture.isFinite() && aperture>0 && aperture.isFinite())
  require(sample.exposureNs>0 && sample.iso>0 && ev.isFinite() && sample.ev.isFinite())
  require(minNs>0 && maxNs>=minNs && minIso>0 && maxIso>=minIso)
  val factor=(aperture/sample.aperture).toDouble().pow(2)*2.0.pow((ev-sample.ev).toDouble())
  val time=(sample.exposureNs*factor).roundToLong().coerceIn(minNs,maxNs)
  val iso=(sample.iso.toDouble()*sample.exposureNs*factor/time).roundToInt().coerceIn(minIso,maxIso)
  return ApertureExposure(aperture,time,iso,ev)
 }
}
