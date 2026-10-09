package com.xperia.timelapse.video

import java.nio.ByteBuffer

/** BT.601 limited-range YUV; respect both planar and interleaved codec strides. */
object Yuv420 {
 fun write(argb: IntArray, width: Int, height: Int, planes: List<Plane>) {
  require(width%2==0 && height%2==0 && argb.size==width*height && planes.size==3)
  for(y in 0 until height) for(x in 0 until width) {
   val c=argb[y*width+x]; val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
   planes[0].put(x,y,((66*r+129*g+25*b+128 shr 8)+16).coerceIn(0,255))
   if(x%2==0 && y%2==0) {
    var rr=0;var gg=0;var bb=0
    for(dy in 0..1) for(dx in 0..1) {val p=argb[(y+dy)*width+x+dx];rr+=p shr 16 and 255;gg+=p shr 8 and 255;bb+=p and 255}
    rr/=4;gg/=4;bb/=4
    planes[1].put(x/2,y/2,((-38*rr-74*gg+112*bb+128 shr 8)+128).coerceIn(0,255))
    planes[2].put(x/2,y/2,((112*rr-94*gg-18*bb+128 shr 8)+128).coerceIn(0,255))
   }
  }
 }
 data class Plane(val buffer: ByteBuffer,val rowStride: Int,val pixelStride: Int) {
  private val offset=buffer.position()
  fun put(x:Int,y:Int,value:Int) {buffer.put(offset+y*rowStride+x*pixelStride,value.toByte())}
 }
}
