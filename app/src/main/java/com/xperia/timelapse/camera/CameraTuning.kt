package com.xperia.timelapse.camera

import kotlin.math.roundToInt
import java.util.Locale

/** Camera2 AWB presets; OFF is intentionally omitted because it requires manual gains. */
enum class WhiteBalanceMode(val requestValue:Int,val label:String) {
 AUTO(1,"自动"),DAYLIGHT(5,"日光"),CLOUDY(6,"阴天"),SHADE(8,"阴影"),
 INCANDESCENT(2,"白炽灯"),FLUORESCENT(3,"荧光灯"),WARM_FLUORESCENT(4,"暖荧光灯"),TWILIGHT(7,"黄昏")
}
data class CameraTuning(val whiteBalance:WhiteBalanceMode=WhiteBalanceMode.AUTO,val ev:Float=0f)
data class CameraTuningCapabilities(
 val whiteBalances:List<WhiteBalanceMode> = emptyList(),val minIndex:Int=0,val maxIndex:Int=0,val evStep:Float=0f
) {
 val supportsEv get()=evStep.isFinite() && evStep>0 && minIndex<maxIndex
 fun indexFor(ev:Float):Int=if(supportsEv && ev.isFinite())(ev/evStep).coerceIn(minIndex.toFloat(),maxIndex.toFloat()).roundToInt() else 0
 fun normalize(tuning:CameraTuning)=CameraTuning(whiteBalances.firstOrNull {it==tuning.whiteBalance} ?: WhiteBalanceMode.AUTO,if(supportsEv)indexFor(tuning.ev)*evStep else 0f)
}
fun evLabel(ev:Float)=String.format(Locale.US,"%+.1f",if(ev==0f)0f else ev)
