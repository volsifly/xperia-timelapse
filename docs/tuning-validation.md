# 0.5.9 白平衡及 EV 验证

日期：2026-10-09。设备：Sony PRO-I XQ-BE72 / Android 13。APK versionCode 14。

## 构建及边界

67 项单元测试全部通过，assembleDebug 与 lintDebug 通过。新增 7 项测试覆盖 EV 到索引换算、上下限限制、设备步长吸附、镜头不支持的白平衡回退、支持预设保留、不支持/无效 EV 步长以及无效历史 EV 值。

EV 使用 CameraX setExposureCompensationIndex，范围和步长来自 ExposureState；白平衡使用 Camera2 CONTROL_AWB_MODE，选项来自 CONTROL_AWB_AVAILABLE_MODES。均为相机实际参数，不是画面后处理滤镜。

## 真机验证

- 系统预览反馈确认白平衡 AUTO=1、DAYLIGHT=5、SHADE=8；日光和阴影切换均成功。
- PRO-I 实测 EV 范围 [-24,24] 个索引，步长 1/12 EV。-2 EV 返回索引 -24、+2 EV 返回 24、测试 +1.0833334 EV 返回 13。
- 镜头 3 切换至主摄 0 后，日光白平衡和 EV 索引 13 保持；重新安装应用后从偏好恢复并重新应用。
- 单张项目 2026-10-09_14-54-28_449480 完成 1 张照片并发布到相册，metadata 中 WB=DAYLIGHT、实测模式 5、EV=1.0833334、实测索引 13。
- 延时项目 2026-10-09_14-54-38_491143 使用上述设置，AE/AF 锁定保持，预设 WB 不额外使用 AWB_LOCK。4 帧正常停止，H.265 1080p/30fps 完整验证后保存相册，清理 4 张原片。
- 横屏实测入口位于镜头按钮上方；竖屏截图和 UI 记录用于检查不与底部拍摄区域重叠。临时旋转配置恢复到测试前状态。
- 测试后恢复自动白平衡、EV 0、延时模式、每 5 秒和原 100 分钟设置。

## 发现的设备限制

Sony 预览结果可能持续报告 F2，即使请求 F4、相机请求记录为 F4。不能把“预览光圈反馈等于请求值”作为开拍前提，否则会错误超时。本次保留原光圈请求路径，并仅对白平衡和 EV 等待最新反馈。metadata 区分请求光圈与预览测量值；没有据此宣称实际成片光圈已独立验证。

证据在本地 tuning-qa/：tuning-log.txt、photo-final.json、timelapse-final.json、portrait.png、portrait-ui.xml 等，不提交手机媒体或原始诊断数据。

API 依据：[CameraX 配置选项](https://developer.android.com/media/camera/camerax/configuration)。
