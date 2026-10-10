package com.xperia.timelapse.color

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Packed sRGB cube: red varies fastest. Shared by saved images and preview atlas. */
class Lut3D(val size:Int,val data:ByteArray) {
 init {require(size in 2..65 && data.size==size*size*size*3)}
 fun encode():ByteArray=ByteBuffer.allocate(4+data.size).order(ByteOrder.LITTLE_ENDIAN).putInt(size).put(data).array()
 private val lower=IntArray(256) {(it*(size-1)/255f).toInt()}
 private val fraction=FloatArray(256) {it*(size-1)/255f-lower[it]}
 private val delta=IntArray(256) {if(lower[it]<size-1)1 else 0}
 private val colors=IntArray(size*size*size) {i->
  ((data[i*3].toInt() and 255) shl 16) or ((data[i*3+1].toInt() and 255) shl 8) or (data[i*3+2].toInt() and 255)
 }
 fun pixel(argb:Int,strength:Float):Int {
  require(strength.isFinite() && strength in 0f..1f)
  if(strength==0f)return argb
  val r=argb ushr 16 and 255;val g=argb ushr 8 and 255;val b=argb and 255
  val base=(lower[b]*size+lower[g])*size+lower[r]
  val dr=delta[r];val dg=delta[g]*size;val db=delta[b]*size*size
  val fx=fraction[r];val fy=fraction[g];val fz=fraction[b]
  var rr=0f;var gg=0f;var bb=0f
  // Compute each vertex index and interpolation weight once for all channels.
  for(z in 0..1)for(y in 0..1)for(x in 0..1) {
   val c=colors[base+x*dr+y*dg+z*db]
   val w=(if(x==0)1-fx else fx)*(if(y==0)1-fy else fy)*(if(z==0)1-fz else fz)
   rr+=(c ushr 16 and 255)*w;gg+=(c ushr 8 and 255)*w;bb+=(c and 255)*w
  }
  val outR=(r+(rr-r)*strength).roundToInt().coerceIn(0,255)
  val outG=(g+(gg-g)*strength).roundToInt().coerceIn(0,255)
  val outB=(b+(bb-b)*strength).roundToInt().coerceIn(0,255)
  return (argb and -0x1000000) or (outR shl 16) or (outG shl 8) or outB
 }
 companion object {
  fun decode(bytes:ByteArray):Lut3D {
   require(bytes.size>=4)
   val size=ByteBuffer.wrap(bytes,0,4).order(ByteOrder.LITTLE_ENDIAN).int
   return Lut3D(size,bytes.copyOfRange(4,bytes.size))
  }
 }
}

object LutRepository {
 @Volatile var readAsset:((String)->ByteArray)?=null
 @Volatile var readImported:((String)->ByteArray)?=null
 private val cached=mutableMapOf<String,Lut3D>()
 @Synchronized fun get(id:String):Lut3D=cached.getOrPut(id) {
  val bytes=if(id.startsWith("custom-")) requireNotNull(readImported){"导入的 LUT 尚未初始化"}(id)
   else requireNotNull(readAsset){"LUT 资源尚未初始化"}("luts/$id.lut")
  Lut3D.decode(bytes)
 }
 @Synchronized fun forget(id:String) {cached.remove(id)}
}
