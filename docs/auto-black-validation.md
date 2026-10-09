# 0.5.7 自动黑屏、防待机与橙色灯验收

2026-10-09，PRO-I XQ-BE72 / Android 13，versionCode 12，已安装。构建、56 项单元测试及 lint 通过，0 errors / 2 项已有 warnings。

默认在 Phase.RUNNING 开始 5 秒后进入黑屏；使用取消式协程延迟，停止/结束/暂停会取消尚未执行的延迟，恢复运行后重新计时。双击恢复后不会在同一运行阶段反复自动盖住界面。新的设置键默认 true，不继承旧版默认 false；间隔等用户配置保留。

真机未点击黑屏按钮，只点快门。开始后约 3.2 秒仍显示取景和操作区，约 7.4 秒已进入黑屏，符合 5 秒延迟代码路径。证据截图为 [开始前段](auto-black-qa/after-2s.png)、[自动进入黑屏](auto-black-qa/after-7s.png)、[双击恢复](auto-black-qa/restored.png)。文件名是计划采样时间，实际包含截屏耗时，不当作精确 2 秒或 7 秒计时证据。

黑屏窗口 mAttrs 显示 sbrt=0.01、KEEP_SCREEN_ON。为验证本功能独立于充电常亮，临时将 stay_on_while_plugged_in 从 7 改为 0；97.376 秒没有触屏，超出屏幕超时 60 秒后仍为 Awake，mStayOn=false、mWakeLockSummary=0x23、mHoldingDisplaySuspendBlocker=true。测试后恢复原值 7。摘要见 [awake.json](auto-black-qa/awake.json)。

当前用户配置是每 5 秒、100 分钟，未重置为此前的 10 秒。任务 2026-10-09_14-20-00_849592 实际拍摄 20 张，停止后完整合成、发布到相册。测试不证明任意长期温控条件下都不会被系统终止。

## 硬件 LED 的实际边界

创建新的 indicator_running_orange_v2 通道，颜色 0xFFFF8000；请求 onMs=1、offMs=0 常亮。设备计算后的 mLight 仍是 color=-32768、onMs=100、offMs=6000，证明节奏被系统覆盖。直接 LightsManager.getLights 返回 SecurityException；不能把通知栏图标或充电灯的橙色当作拍摄硬件灯常亮证明。屏幕保持唤醒时系统可能不点亮通知 LED。

Android 13 的 [NotificationRecord.calculateLights 源码](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-13.0.0_r1/services/core/java/com/android/server/notification/NotificationRecord.java)对通知通道使用系统默认亮灭时长；只有旧版通知兼容路径读取应用的 ledOnMS/ledOffMS。因此本版本实现橙色请求，**没有实现或宣称普通权限下的硬件常亮**。

## 用户报告未亮灯后的现场检查

当前仍在拍摄，未重启或安装应用，也未操作电源键。PowerManager 为 Awake，mWakeLockSummary=0x23；橙色通知请求有效（mIntercept=false、mSuppressedVisualEffects=0、mHasLight=true、mNotificationPulseEnabled=true），但实际灯驱动 id=4 的 color=00000000。确认不是仅凭通知请求推断实际亮灯。

Android 13 NotificationManagerService.updateLightsLocked 在 mScreenOn 为 true 时直接 turnOff。保持屏幕唤醒的全黑模式与硬件通知灯显示冲突。在保留当前“不自动待机”要求时不能擅自改回熄屏，已向用户询问优先使用真正熄屏加 CPU 唤醒，还是保留屏幕唤醒。源码：https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-13.0.0_r1/services/core/java/com/android/server/notification/NotificationManagerService.java 。
