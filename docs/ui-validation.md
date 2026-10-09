# 0.5.0 界面验证

验证日期为 2026-10-09，环境为 Android 14 x86_64 模拟器，屏幕 1080×2520。实际安装本次 debug APK，通过 Android 界面操作和截图验证；相机画面为模拟相机输出。

## 已执行

- 首次相机授权、通知授权后进入真实 CameraX 预览。
- 竖屏大取景、底部模式与快门；横屏大取景、右侧模式与快门，无主界面配置长表单。
- 打开菜单及拍摄/视频配置，横屏菜单可以滚动访问下方选项，标题和完成按钮保留在顶部。
- 菜单把目标张数从 100 修改为 20，重装和重新启动后设置保留；恢复为 100 后再次重启也保留。
- 关闭自动合成后，执行 2 秒间隔、20 张拍摄，在拍摄中由竖屏转横屏，最终实际得到 20 张可解码 JPEG，metadata 为 completed。
- 新任务暂停后等待当前照片落盘，再检查暂停状态。照片总数在菜单操作期间保持 25 不变，拍摄设置输入框禁用；继续后恢复拍摄，旋转至竖屏仍保留活动任务，点击停止后 metadata 为 stopped。
- 比较每个项目首尾 JPEG 的 EXIF 方向，项目内方向一致；20 张项目方向为 6，两个横屏启动项目方向为 1。这验证模拟器中的输出方向冻结，不代表 PRO-I 实际驱动行为。
- 获取本次应用进程日志，未发现 AndroidRuntime fatal 异常。
- debug APK 构建、44 项单元测试、Lint 通过。Lint 有两条工具/依赖提示，其中部分 Compose 自定义检查因工具兼容性跳过，因此没有把 Lint 作为视觉验证依据。

原始项目元数据、照片数量和方向样本记录见 [ui-evidence.json](ui-evidence.json)。截图直接来自运行中的 Android 应用，未替换取景画面。

## 截图

- [竖屏主界面](screenshots/portrait.png)
- [横屏主界面](screenshots/landscape.png)
- [菜单](screenshots/menu.png)
- [横屏拍摄设置](screenshots/settings-landscape.png)
- [横屏视频菜单滚动后](screenshots/video-menu-landscape.png)
- [横屏拍摄中](screenshots/capture-landscape.png)
- [竖屏拍摄中](screenshots/capture-portrait.png)

## 尚未执行

PRO-I 真机的横竖屏取景方向、连续拍摄与锁屏、物理传感器/系统旋转策略、温控，以及所选编码/分辨率/帧率的真实合成和播放仍需真机验证。本轮间隔任务关闭自动视频合成，不能将本轮界面测试作为编解码功能验证。
