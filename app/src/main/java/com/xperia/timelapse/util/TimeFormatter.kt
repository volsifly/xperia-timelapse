package com.xperia.timelapse.util
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object TimeFormatter { fun end(time:Long?)=time?.let { SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date(it)) } ?: "—" }
