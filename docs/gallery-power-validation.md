# 0.5.6 相册与黑屏真机验收

2026-10-09，Sony PRO-I XQ-BE72 / Android 13。versionCode 11，已更新 APK。

## 相册与照片

主界面文件入口使用 APP_GALLERY Intent；真机实际打开 Google 相册离线版，页面能看到 XperiaTimelapse 的历史视频。照片和视频由相册自身浏览，不读取其他应用的媒体库，不新增读取媒体权限。项目 ZIP 导出保留在设置中的项目列表。

实际拍一张主摄 JPEG，保存为 content://media/external_primary/images/media/1000001479，relative_path=Pictures/XperiaTimelapse/，is_pending=0。原图与相册条目都是 2803882 bytes，下载后 SHA-256 完全一致。具体核对值见 [photo.json](gallery-power-qa/photo.json)。没有发布延时原片副本，既有成片发布及清理策略保留。

## 黑屏、熄屏与灯

原配置每 10 秒、100 分钟保留。主摄拍摄中点黑屏省电，界面成为全黑且窗口亮度降低，照片持续增加。黑屏后不再持有屏幕常亮，但手机系统设置 stay_on_while_plugged_in=7，当前正在充电，导致不会按超时自动熄屏；未修改这个系统设置。

使用电源键熄屏后，PowerManager 显示 Dozing、mHoldingDisplaySuspendBlocker=false，屏幕灯 id=0 的 color=ff000000。照片从熄屏前 9 张增加到熄屏后 14 张，拍摄状态仍为 running。双击恢复了拍摄界面；锁屏后的系统通知面板需先收起、解锁，再恢复应用。黑屏纯色图及恢复截图见 [black.png](gallery-power-qa/black.png)、[restored.png](gallery-power-qa/restored.png)。

通知通道 mLights=true，拍摄时灯驱动 id=4 的 color=ff00ff00；合成时为 ff0080ff；完成后回到 00000000。系统报告 mHasLight=true，notification_light_pulse=1。此为灯驱动状态证据，没有外部摄像头录像证明肉眼闪烁频率。暂停黄色已接入，未进行真机灯驱动验证。亮灯由 Android 和 Sony 决定，尤其受通知权限、系统通知亮灯设置、勿扰及充电灯影响。官方 API 见 [NotificationChannel](https://developer.android.com/reference/android/app/NotificationChannel#enableLights(boolean))。

## 停止与交付

实际任务最终拍摄 22 张，停止后自动生成 1080p/30fps H.265 视频，完整验证、相册发布与 22 张原片清理完成。完成后指示灯请求取消。保留用户原有间隔和时长配置；系统亮度、屏幕超时、充电常亮与通知设置未改变。

构建、56 项单元测试和 lint 通过（0 errors / 2 项已有 warnings）。相册发布复用已测试的完整复制、读回校验、发布失败清理和保留原片机制。本轮没有功耗仪实测、长时间温控验收或其他手机兼容性结论；自动黑屏设置的触发路径已检查，本轮实际拍摄使用手动黑屏。
