package com.xperia.timelapse.timelapse

enum class Phase { IDLE, COUNTDOWN, RUNNING, PAUSED, STOPPING, PROCESSING, COMPLETED, STOPPED, ERROR }
data class TimelapseState(
 val phase: Phase = Phase.IDLE, val capturedFrames: Int = 0,
 val targetFrames: Int? = null, val nextCaptureInMs: Long = 0,
 val activeElapsedMs: Long = 0, val error: String? = null,
 val projectPath: String? = null, val estimatedEndMs: Long? = null, val processingMessage: String? = null
) { val busy get() = phase in listOf(Phase.COUNTDOWN, Phase.RUNNING, Phase.PAUSED, Phase.STOPPING, Phase.PROCESSING) }

val Phase.label: String get() = when(this) {
 Phase.IDLE -> "就绪"; Phase.COUNTDOWN -> "准备 / 倒计时"; Phase.RUNNING -> "拍摄中"
 Phase.PAUSED -> "已暂停"; Phase.STOPPING -> "正在保存并停止"; Phase.COMPLETED -> "已完成"
 Phase.PROCESSING -> "正在合成视频"; Phase.STOPPED -> "已停止"; Phase.ERROR -> "拍摄异常"
}
