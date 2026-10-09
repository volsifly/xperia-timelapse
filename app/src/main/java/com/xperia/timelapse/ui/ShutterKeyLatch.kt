package com.xperia.timelapse.ui

/** Fires once on release of a complete press; repeats and canceled presses never fire. */
class ShutterKeyLatch {
 private var pressed=false
 fun event(action:Int,repeat:Int=0,canceled:Boolean=false):Boolean {
  return when(action) {
   0->{if(repeat==0)pressed=true;false}
   1->{val fire=pressed && !canceled;pressed=false;fire}
   else->false
  }
 }
 fun reset() {pressed=false}
}
