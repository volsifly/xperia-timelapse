package com.xperia.timelapse.color

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ColorStyle(val label:String,val detail:String,val contrast:Float=0f,val saturation:Float=1f,val lift:Float=0f,val ceiling:Float=1f,val warm:Float=0f,val cool:Float=0f,val lutId:String?=null,val source:String="自研") {
 NATURAL("自然","保留相机原本色彩"),
 LOG("软件 Log","平坦曲线，适合后期调色；非传感器原生 Log"),
 CLASSIC_NEG("经典负片","自研 · 暖高光、青色阴影",.32f,.82f,.025f,.97f,.025f,.035f),
 CLASSIC_CHROME("经典正片","自研 · 低饱和、冷阴影",.18f,.66f,.015f,.96f,.01f,.035f),
 VIVID("鲜艳反转片","自研 · 鲜艳色彩、较强反差",.26f,1.28f,0f,1f,.008f,.006f),
 CINEMA("影院柔色","自研 · 柔和反差、低饱和",-.22f,.72f,.045f,.93f,.012f,.015f),
 MONO("细腻黑白","自研 · 黑白层次",.2f,0f,.012f,.985f),
 ABPY_ASTIA("A · ASTIA","abpy · 富士相机匹配 LUT",lutId="abpy_astia",source="abpy"),
 ABPY_BLEACH_BYPASS("A · Bleach Bypass","abpy · 富士相机匹配 LUT",lutId="abpy_bleach_bypass",source="abpy"),
 ABPY_CLASSIC_CHROME("A · Classic Chrome","abpy · 富士相机匹配 LUT",lutId="abpy_classic_chrome",source="abpy"),
 ABPY_CLASSIC_NEG("A · Classic Neg","abpy · 富士相机匹配 LUT",lutId="abpy_classic_neg",source="abpy"),
 ABPY_ETERNA("A · ETERNA","abpy · 富士相机匹配 LUT",lutId="abpy_eterna",source="abpy"),
 ABPY_NOSTALGIC_NEG("A · Nostalgic Neg","abpy · 富士相机匹配 LUT",lutId="abpy_nostalgic_neg",source="abpy"),
 ABPY_PRO_NEG_HI("A · PRO Neg Hi","abpy · 富士相机匹配 LUT",lutId="abpy_pro_neg_hi",source="abpy"),
 ABPY_PRO_NEG_STD("A · PRO Neg Std","abpy · 富士相机匹配 LUT",lutId="abpy_pro_neg_std",source="abpy"),
 ABPY_PROVIA("A · PROVIA","abpy · 富士相机匹配 LUT",lutId="abpy_provia",source="abpy"),
 ABPY_REALA_ACE("A · REALA ACE","abpy · 富士相机匹配 LUT",lutId="abpy_reala_ace",source="abpy"),
 ABPY_VELVIA("A · Velvia","abpy · 富士相机匹配 LUT",lutId="abpy_velvia",source="abpy"),
 PYRO_ACROS("P · ACROS","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_acros",source="pyrocat101"),
 PYRO_ASTIA("P · ASTIA","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_astia",source="pyrocat101"),
 PYRO_CLASSIC_CHROME("P · Classic Chrome","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_classic_chrome",source="pyrocat101"),
 PYRO_CLASSIC_NEG("P · Classic Neg","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_classic_neg",source="pyrocat101"),
 PYRO_ETERNA_BB("P · ETERNA BB","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_eterna_bb",source="pyrocat101"),
 PYRO_ETERNA("P · ETERNA","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_eterna",source="pyrocat101"),
 PYRO_PRO_NEGSTD("P · PRO Neg Std","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_pro_negstd",source="pyrocat101"),
 PYRO_PROVIA("P · PROVIA","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_provia",source="pyrocat101"),
 PYRO_REALA_ACE("P · REALA ACE","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_reala_ace",source="pyrocat101"),
 PYRO_VELVIA("P · Velvia","pyrocat101 · F-Log2C 衍生 LUT",lutId="pyro_velvia",source="pyrocat101")
}
data class ColorLook(val style:ColorStyle=ColorStyle.NATURAL,val strength:Float=1f) {
 init {require(strength.isFinite() && strength in 0f..1f)}
 val active get()=style!=ColorStyle.NATURAL && strength>0f
}

/** Legacy recipes plus source-labelled imported LUTs; RAW is never modified. */
class ColorTransform(val look:ColorLook) {
 private val lut=if(look.active)look.style.lutId?.let {LutRepository.get(it)} else null
 private val curve=FloatArray(256) {curve(it/255f,look.style)}
 fun pixel(argb:Int):Int {
  if(!look.active)return argb
  lut?.let {return it.pixel(argb,look.strength)}
  val sr=(argb ushr 16 and 255)/255f;val sg=(argb ushr 8 and 255)/255f;val sb=(argb and 255)/255f
  val r=curve[argb ushr 16 and 255];val g=curve[argb ushr 8 and 255];val b=curve[argb and 255]
  val y=.2126f*r+.7152f*g+.0722f*b
  val s=look.style
  val rr=y+(r-y)*s.saturation+s.warm*y-s.cool*(1-y)
  val gg=y+(g-y)*s.saturation+s.cool*(1-y)*.35f
  val bb=y+(b-y)*s.saturation-s.warm*y+s.cool*(1-y)
  fun channel(original:Float,graded:Float)=((original+(graded.coerceIn(0f,1f)-original)*look.strength)*255).roundToInt().coerceIn(0,255)
  return (argb and -0x1000000) or (channel(sr,rr) shl 16) or (channel(sg,gg) shl 8) or channel(sb,bb)
 }
 companion object {
  fun curve(x:Float,style:ColorStyle):Float {
   if(style==ColorStyle.LOG) {
    val linear=if(x<=.04045f)x/12.92f else ((x+.055f)/1.055f).pow(2.4f)
    return .095f+.81f*ln(1f+100f*linear)/ln(101f)
   }
   return (style.lift+(style.ceiling-style.lift)*(x+style.contrast*(x-.5f)*4f*x*(1f-x))).coerceIn(0f,1f)
  }
 }
}
