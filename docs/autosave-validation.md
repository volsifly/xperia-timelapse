# 0.5.3 停止后自动保存视频

2026-10-09，Sony XQ-BE72 / Android 13，最终包 0.5.3 / versionCode 8 已覆盖安装。

## 行为

延时模式固定自动合成。正常完成或手动停止、且至少拍摄两张时，先完整解码验证项目 MP4，再自动保存到 MediaStore 的 Movies/XperiaTimelapse。待发布条目保持 IS_PENDING=1，复制完成后重新读取，核对长度和 SHA-256，成功才发布到相册。发布成功并记录 metadata 后才按用户清理设置删除本项目 JPEG。保存失败、复制校验失败或取消时清除未完成条目并保留源文件。无需选择位置或应用；结果页的“分享视频”仅是可选分享。

停止后主界面显示“视频已保存到相册”，结果页显示相册保存状态。单张或定时拍照继续保留 JPEG；不足两张的延时序列也保留照片。取景页面保持亮屏，仍可手动锁屏。

## 实测

从默认 100 张的延时任务开始拍摄，点击“停止并保存”，累计 5 张后结束。没有操作任何导出按钮、位置选择器或分享目标。

- 视频为 H.265、1920x1080、30fps，完整解码 5 帧。
- 系统媒体库条目 MIME video/mp4、IS_PENDING=0，路径为 /storage/emulated/0/Movies/XperiaTimelapse/2026-10-09_12-51-48_378684.mp4。
- 公共文件与项目 MP4 拉取后逐字节一致。
- metadata：gallery_saved=true，deleted_original_count=5，original_photos_deleted=true；清理仅在相册保存成功后进行。
- 主界面和结果页均显示已保存到相册；结果页提供“分享视频”。
- 构建和 lint 通过，54 项单元测试通过。新增回归覆盖未验证文件不能创建条目、损坏复制不能发布、复制取消及发布失败清理条目并保留源文件，以及延时配置固定自动合成。

证据：[metadata](autosave-qa/metadata.json)、[媒体库查询](autosave-qa/media-store.txt)、[独立解码结果](autosave-qa/ffprobe.json)、[停止后界面](autosave-qa/completed.png)、[相册实际文件副本](autosave-qa/gallery-video.mp4)。

已证明系统媒体库发布与公共文件保存，未通过独立相册应用逐项验收其缩略图刷新行为。原有项目未自动迁入相册。本次没有验证 4K/60fps 或长时任务。
