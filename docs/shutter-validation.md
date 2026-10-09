# 0.5.10 实体快门验证

日期：2026-10-09。Sony PRO-I XQ-BE72 / Android 13，versionCode 15。

## 行为

- 半按 KEYCODE_FOCUS：空闲且相机就绪时，使用 PreviewView 的 meteringPointFactory 对画面中心触发现有 CameraX AF 路径，显示正在对焦、已对焦或未合焦状态；一次按住仅触发一次。
- 全按 KEYCODE_CAMERA：完整按下并释放触发一次。空闲启动，RUNNING/PAUSED/COUNTDOWN 停止并保存；PROCESSING/STOPPING 忽略。
- 上层 UI 回调使用最新状态，复用屏幕快门的配置校验和自动锁屏授权流程。焦点丢失或按键取消时取消未完成全按。
- 真正系统锁屏时 Activity 不具备全局接收按键能力，需先解锁。应用黑屏仍可接收快门。

## 单元及构建

73 项测试通过，新增 6 项按键状态测试覆盖完整按压、长按多次重复、取消、没有初始按下的重复/释放、窗口焦点丢失以及取消后的下一次按压。assembleDebug、lintDebug 均通过。

## 真机证据

1. 开发版本实际收到用户的 gpio-keys 事件：半按 key=80 scan=528，全按 key=27 scan=766。15:00:36 的全按重复 1～10 次仅在释放时触发 start-request；15:00:38 的下一次全按触发 stop-and-save。这验证了真实实体键映射和一次按压一次动作。
2. 最终版使用系统输入注入 key=80，触发中心坐标 Offset(1004.5,485.0)，传感器归一化坐标 (0.5,0.5)。AF 请求返回 success=false，并走“未合焦”提示；验证了 AF 调用链，不宣称该近距离测试场景已成功合焦。最终版的实体半按成功合焦仍需用户对准清晰目标验证。
3. 注入长按 key=27 后开始延时项目 2026-10-09_15-02-50_302570，在应用黑屏期间再次注入 key=27 停止，6 帧 H.265 自动保存相册，完整解码验证后清理原片。
4. 项目 2026-10-09_15-03-56_026764 再次黑屏停止成功，6 帧视频状态 verified、gallery_saved=true。black-ui.xml 保存当时黑屏 UI。实际停止前未手动恢复黑屏。
5. 用户每 5 秒、100 分钟的设置保留；本次短任务通过实体快门事件停止，不改结束设置。

本地证据在 shutter-qa/，不提交手机媒体和诊断数据。

实现依据：[Android KeyEvent](https://developer.android.com/reference/android/view/KeyEvent)。
