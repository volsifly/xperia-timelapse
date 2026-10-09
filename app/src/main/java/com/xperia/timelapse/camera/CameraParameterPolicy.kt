package com.xperia.timelapse.camera

object CameraParameterPolicy {
 fun validMeteringPoint(x:Float,y:Float)=x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f
 fun previewPoint(x:Float,y:Float,viewWidth:Int,viewHeight:Int,imageWidth:Int,imageHeight:Int):Pair<Float,Float>? {
  if(viewWidth<=0 || viewHeight<=0 || imageWidth<=0 || imageHeight<=0)return null
  val scale=minOf(viewWidth.toFloat()/imageWidth,viewHeight.toFloat()/imageHeight)
  val width=imageWidth*scale;val height=imageHeight*scale
  val nx=(x-(viewWidth-width)/2)/width;val ny=(y-(viewHeight-height)/2)/height
  return if(validMeteringPoint(nx,ny))nx to ny else null
 }
 // Some HALs report a measured focus position outside their writable range.
 fun focusDistance(measured:Float?,maximum:Float?):Float? {
  if(measured==null || maximum==null || !measured.isFinite() || !maximum.isFinite() || maximum<=0)return null
  return measured.coerceIn(0f,maximum)
 }
}
