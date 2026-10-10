package com.xperia.timelapse.color
import org.junit.Assert.*
import org.junit.Test
class ColorLookTest {
 init {LutRepository.readAsset={path->java.io.File("src/main/assets",path).readBytes()}}
 @Test fun naturalAndZeroStrengthAreBitExact() {for(p in listOf(0xFF123456.toInt(),0x8012AB34.toInt(),-1,0)) {assertEquals(p,ColorTransform(ColorLook()).pixel(p));ColorStyle.values().forEach {assertEquals(p,ColorTransform(ColorLook(it,0f)).pixel(p))}}}
 @Test fun logIsMonotonicAndReservesBlackAndWhiteHeadroom() {var last=-1f;for(i in 0..255) {val v=ColorTransform.curve(i/255f,ColorStyle.LOG);assertTrue(v>=last);last=v};assertTrue(ColorTransform.curve(0f,ColorStyle.LOG)>0f);assertTrue(ColorTransform.curve(1f,ColorStyle.LOG)<1f)}
 @Test fun monochromeOutputsEqualChannelsAndPreservesAlpha() {val p=ColorTransform(ColorLook(ColorStyle.MONO)).pixel(0x8041E390.toInt());assertEquals(0x80,p ushr 24);assertEquals(p ushr 16 and 255,p ushr 8 and 255);assertEquals(p and 255,p ushr 8 and 255)}
 @Test fun allRecipesKeepChannelsBoundedAndAlphaUnchanged() {for(style in ColorStyle.values())for(i in 0..255) {val p=ColorTransform(ColorLook(style)).pixel((0x7F shl 24) or (i shl 16) or ((255-i) shl 8) or i);assertEquals(0x7F,p ushr 24)}}
 @Test fun strengthInterpolatesProcessedColor() {val p=0xFF789ABC.toInt();val full=ColorTransform(ColorLook(ColorStyle.CLASSIC_CHROME)).pixel(p);val half=ColorTransform(ColorLook(ColorStyle.CLASSIC_CHROME,.5f)).pixel(p);for(shift in listOf(0,8,16))assertTrue(kotlin.math.abs(((p ushr shift and 255)+(full ushr shift and 255))/2f-(half ushr shift and 255))<=1f)}
 @Test fun invalidStrengthRejected() {for(v in listOf(-.1f,1.1f,Float.NaN))assertThrows(IllegalArgumentException::class.java) {ColorLook(strength=v)}}
}
