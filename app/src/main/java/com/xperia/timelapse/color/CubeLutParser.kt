package com.xperia.timelapse.color

import kotlin.math.floor
import kotlin.math.roundToInt

data class ParsedCubeLut(val name:String,val lut:Lut3D)

/** Reads the common .cube 3D LUT text format and converts it to the app's packed sRGB table. */
object CubeLutParser {
 fun parse(text:String,fileName:String="Imported LUT"):ParsedCubeLut {
  require(text.length<=8*1024*1024) {".cube 文件过大"}
  var name=fileName.replace(Regex("(?i)\\.cube$"),"").trim().ifEmpty {"Imported LUT"}
  var size:Int?=null
  var domainMin=floatArrayOf(0f,0f,0f)
  var domainMax=floatArrayOf(1f,1f,1f)
  var sawMin=false;var sawMax=false
  var sawInputRange=false
  val values=ArrayList<Float>()
  for(raw in text.removePrefix("\uFEFF").lineSequence()) {
   val line=raw.substringBefore('#').trim()
   if(line.isEmpty())continue
   val parts=line.split(Regex("\\s+"))
   when(parts[0].uppercase()) {
    "TITLE" -> {
     val title=line.substringAfter(parts[0]).trim().trim('"').trim()
     if(title.isNotEmpty())name=title
    }
    "LUT_3D_SIZE" -> {
     require(size==null && parts.size==2) {"LUT_3D_SIZE 格式错误"}
     size=parts[1].toIntOrNull()?.takeIf {it in 2..65} ?: error("仅支持 2 到 65 阶的 3D LUT")
    }
    "DOMAIN_MIN","DOMAIN_MAX" -> {
     require(!sawInputRange) {"DOMAIN_* 与 LUT_3D_INPUT_RANGE 不能同时使用"}
     require(parts.size==4) {"${parts[0]} 格式错误"}
     val values3=parts.drop(1).map {it.toFloatOrNull()?.takeIf(Float::isFinite) ?: error("输入范围包含无效数值")}.toFloatArray()
     if(parts[0].equals("DOMAIN_MIN",true)) {require(!sawMin);domainMin=values3;sawMin=true}
     else {require(!sawMax);domainMax=values3;sawMax=true}
    }
    "LUT_3D_INPUT_RANGE" -> {
     require(parts.size==3 && !sawInputRange && !sawMin && !sawMax) {"LUT_3D_INPUT_RANGE 格式错误或与 DOMAIN_* 冲突"}
     val min=parts[1].toFloatOrNull()?.takeIf(Float::isFinite) ?: error("输入范围包含无效数值")
     val max=parts[2].toFloatOrNull()?.takeIf(Float::isFinite) ?: error("输入范围包含无效数值")
     require(max>min) {"LUT_3D_INPUT_RANGE 上限必须大于下限"}
     domainMin=floatArrayOf(min,min,min);domainMax=floatArrayOf(max,max,max);sawInputRange=true
    }
    "LUT_1D_SIZE" -> error("暂不支持包含 1D LUT 的 .cube 文件，请导出纯 3D LUT")
    else -> {
     require(parts.size==3) {"无法识别 .cube 行：$line"}
     parts.forEach {values += it.toFloatOrNull()?.takeIf(Float::isFinite) ?: error("LUT 表包含无效数值")}
    }
   }
  }
  val n=size ?: error("缺少 LUT_3D_SIZE")
  require(sawMin==sawMax) {"DOMAIN_MIN 与 DOMAIN_MAX 必须同时提供"}
  require((0..2).all {domainMax[it]>domainMin[it]}) {"DOMAIN_MAX 必须大于 DOMAIN_MIN"}
  require(values.size==n*n*n*3) {"LUT 数据数量不正确：需要 ${n*n*n} 个 RGB 项"}
  val src=values.toFloatArray()
  val out=ByteArray(n*n*n*3)
  fun sample(x0:Int,y0:Int,z0:Int,fx:Float,fy:Float,fz:Float,channel:Int):Float {
   var result=0f
   for(z in 0..1)for(y in 0..1)for(x in 0..1) {
    val ix=(x0+x).coerceAtMost(n-1);val iy=(y0+y).coerceAtMost(n-1);val iz=(z0+z).coerceAtMost(n-1)
    val weight=(if(x==0)1-fx else fx)*(if(y==0)1-fy else fy)*(if(z==0)1-fz else fz)
    result+=src[((iz*n+iy)*n+ix)*3+channel]*weight
   }
   return result
  }
  for(b in 0 until n)for(g in 0 until n)for(r in 0 until n) {
   val normalized=floatArrayOf(r/(n-1f),g/(n-1f),b/(n-1f))
   val p=FloatArray(3) {axis->(((normalized[axis]-domainMin[axis])/(domainMax[axis]-domainMin[axis])).coerceIn(0f,1f)*(n-1))}
   val lo=IntArray(3) {floor(p[it]).toInt()};val fr=FloatArray(3) {p[it]-lo[it]}
   val dest=((b*n+g)*n+r)*3
   for(c in 0..2)out[dest+c]=(sample(lo[0],lo[1],lo[2],fr[0],fr[1],fr[2],c).coerceIn(0f,1f)*255f).roundToInt().toByte()
  }
  return ParsedCubeLut(name,Lut3D(n,out))
 }
}
