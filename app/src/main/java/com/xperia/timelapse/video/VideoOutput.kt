package com.xperia.timelapse.video

import kotlin.math.abs

enum class VideoResolution(val label:String,val width:Int,val height:Int) {
 HD("720p",1280,720), FULL_HD("1080p",1920,1080), UHD("4K",3840,2160)
}
data class VideoOutput(val resolution:VideoResolution=VideoResolution.FULL_HD,val fps:Int=30) {
 init {require(fps in SUPPORTED_FPS) {"帧率必须为 24、25、30 或 60"}}
 val width get()=resolution.width
 val height get()=resolution.height
 val label get()="${resolution.label} · ${fps}fps"
 fun presentationTimeUs(index:Int):Long {require(index>=0);return index*1_000_000L/fps}
 fun timestampMatches(index:Int,timeUs:Long)=abs(timeUs-presentationTimeUs(index))<=2000
 fun estimatedBytes(frames:Int,bitrate:Int):Long {require(frames>=0 && bitrate>0);return (frames.toLong()*bitrate+8L*fps-1)/(8L*fps)}
 companion object {val SUPPORTED_FPS=listOf(24,25,30,60)}
}
