package com.xperia.timelapse.color

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Lut3DTest {
 private fun identity():Lut3D {
  val bytes=ByteArray(2*2*2*3)
  for(b in 0..1)for(g in 0..1)for(r in 0..1) {val i=((b*2+g)*2+r)*3;bytes[i]=(r*255).toByte();bytes[i+1]=(g*255).toByte();bytes[i+2]=(b*255).toByte()}
  return Lut3D(2,bytes)
 }
 @Test fun interpolationAndChannelOrderPreserveIdentity() {
  val lut=identity()
  for(r in 0..255 step 17)for(g in 0..255 step 17)for(b in 0..255 step 17) {
   val p=(0x80 shl 24) or (r shl 16) or (g shl 8) or b
   assertEquals(p,lut.pixel(p,1f))
  }
 }
 @Test fun strengthBlendsAndKeepsAlpha() {
  val lut=Lut3D(2,ByteArray(24){255.toByte()});val p=0x80406080.toInt()
  assertEquals(p,lut.pixel(p,0f));assertEquals(0x80FFFFFF.toInt(),lut.pixel(p,1f));assertEquals(0x80A0B0C0.toInt(),lut.pixel(p,.5f))
 }
 @Test fun corruptAssetsAreRejected() {
  assertThrows(IllegalArgumentException::class.java){Lut3D.decode(byteArrayOf(0,0,0,0))}
  assertThrows(IllegalArgumentException::class.java){Lut3D(2,ByteArray(23))}
 }
 @Test fun optimizedSamplingMatchesIndependentTrilinearReference() {
  val random=java.util.Random(42)
  val bytes=ByteArray(5*5*5*3);random.nextBytes(bytes);val lut=Lut3D(5,bytes)
  repeat(2000) {
   val p=random.nextInt();val strength=random.nextFloat();val result=lut.pixel(p,strength)
   val rgb=intArrayOf(p ushr 16 and 255,p ushr 8 and 255,p and 255)
   val pos=rgb.map {it*4/255.0};val lo=pos.map {kotlin.math.floor(it).toInt()};val f=pos.indices.map {pos[it]-lo[it]}
   for(c in 0..2) {
    var value=0.0
    for(z in 0..1)for(y in 0..1)for(x in 0..1) {
     val index=(((lo[2]+z).coerceAtMost(4)*5+(lo[1]+y).coerceAtMost(4))*5+(lo[0]+x).coerceAtMost(4))*3+c
     value+=(bytes[index].toInt() and 255)*(if(x==0)1-f[0] else f[0])*(if(y==0)1-f[1] else f[1])*(if(z==0)1-f[2] else f[2])
    }
    val expected=rgb[c]+(value-rgb[c])*strength
    assertTrue(kotlin.math.abs((result ushr (16-c*8) and 255)-expected)<=.501)
   }
   assertEquals(p ushr 24,result ushr 24)
  }
 }
 @Test fun allImportedStylesHaveValidDistinctAssets() {
  val styles=ColorStyle.values().filter {it.lutId!=null};assertEquals(21,styles.size)
  assertEquals(21,styles.map {it.lutId}.toSet().size)
  for(s in styles) {
   val lut=Lut3D.decode(File("src/main/assets/luts/${s.lutId}.lut").readBytes());assertEquals(33,lut.size)
   val outputs=listOf(0xFF3C7080.toInt(),0xFF806C50.toInt(),0xFFB0B0B0.toInt()).map {lut.pixel(it,1f)}
   assertTrue(outputs.toSet().size>1)
  }
 }
}
