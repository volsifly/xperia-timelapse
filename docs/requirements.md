下面是一份可直接粘贴到 Codex / Copilot / 其他 AI 代码生成工具的 **项目需求与结论导出文档**。  
它会告诉 AI：你要做什么、为什么这样做、技术路线是什么、有哪些关键约束。

---

# 项目名称：Sony Xperia PRO-I 专属延时摄影应用

## 一、项目背景

目标设备：**Sony Xperia PRO-I**  
开发目标：为这台手机打造一个 **专属适配的延迟摄影 / 定时拍摄 / 延时摄影应用**。

市面上通用延时摄影应用存在以下问题：

1. Android 相机碎片化严重，不同机型 Camera2 能力差异巨大。
2. 通用应用为了保证兼容性，通常采用保守策略，无法充分发挥 Sony Xperia PRO-I 的影像能力。
3. 索尼手机对第三方相机应用的后台策略、温控策略、相机保活策略较为严格，通用应用容易出现：
   - 息屏后相机被系统中断
   - 长时间拍摄发热降频
   - 自动曝光导致画面闪烁
   - 自动对焦导致焦点漂移
   - 白平衡漂移导致色彩跳变

因此，本项目不做通用型应用，而是针对 Sony Xperia PRO-I 做 **单机深度适配**。

---

## 二、核心结论

### 1. 通用应用难以做好 Sony Xperia PRO-I 的延时摄影

Sony Xperia PRO-I 虽然影像能力很强，但第三方应用并不一定能完整调用索尼官方相机应用的全部能力。  
通用应用通常只能使用标准 Android Camera API，无法处理索尼机型的特殊限制。

主要问题包括：

- 索尼系统对后台相机访问较严格
- 第三方应用难以稳定调用所有专业模式
- 息屏后容易被系统限制
- 长时间拍摄可能触发温控降频
- Camera2 参数在不同模式下可用性不同
- 系统更新可能改变相机驱动行为

---

### 2. 单机深度适配有优势

如果只针对 Sony Xperia PRO-I 开发，可以牺牲通用性，换取更高的专业性和稳定性。

可以做到：

- 专门测试 PRO-I 的 Camera2 能力
- 锁定曝光、快门、ISO、白平衡、对焦
- 优化拍照间隔补偿
- 针对 PRO-I 的存储速度优化写入逻辑
- 针对 PRO-I 的发热表现做长时拍摄策略
- 针对索尼系统做前台服务与后台保活优化
- 做成更贴近 Sony Photography Pro / Video Pro 的专业操作体验

---

## 三、应用定位

本应用不是普通自拍倒计时应用，而是：

> 面向 Sony Xperia PRO-I 的专业延时摄影与定时拍摄工具。

核心使用场景：

1. 定时自拍  
   按下快门后延迟 3 秒、5 秒、10 秒拍摄。

2. 间隔延时摄影  
   每隔固定时间拍一张，例如每 2 秒、5 秒、10 秒拍一张。

3. 专业延时摄影  
   锁定曝光、对焦、白平衡，减少画面闪烁和焦点漂移。

4. 长时间记录拍摄  
   支持长时间连续拍摄，并处理后台、电量、存储问题。

5. 后期合成  
   将照片序列合成为延时视频，或导出给电脑后期处理。

---

## 四、功能规划

### 第一阶段：基础定时拍照

目标：先跑通相机预览、权限、拍照、存储。

功能：

- 打开相机预览
- 申请相机权限
- 显示实时取景画面
- 用户选择延迟时间：
  - 3 秒
  - 5 秒
  - 10 秒
  - 自定义时间
- 点击拍照后进入倒计时
- 倒计时结束后自动拍照
- 照片保存到系统相册或应用目录
- 支持前后摄像头切换，优先使用后置主摄

技术要点：

- 使用 CameraX 或 Camera2
- 处理生命周期
- 处理权限请求
- 使用协程或 Handler 实现倒计时
- 使用 MediaStore 保存图片

---

### 第二阶段：间隔拍摄

目标：实现延时摄影的基础能力。

功能：

- 设置拍摄间隔：
  - 1 秒
  - 2 秒
  - 5 秒
  - 10 秒
  - 30 秒
  - 自定义
- 设置拍摄总时长：
  - 1 分钟
  - 5 分钟
  - 10 分钟
  - 30 分钟
  - 1 小时
  - 自定义
- 或者设置总张数：
  - 100 张
  - 300 张
  - 1000 张
  - 自定义
- 实时显示：
  - 已拍摄张数
  - 剩余张数
  - 预计结束时间
  - 预计存储占用
- 支持开始、暂停、停止
- 拍摄失败时自动重试或记录错误日志

技术要点：

- 使用 Foreground Service 保证后台运行
- 使用 WakeLock 防止 CPU 休眠
- 使用 Handler、Coroutine 或 WorkManager 控制间隔
- 拍照耗时需要做动态补偿

间隔补偿逻辑：

```text
目标间隔 = 2000ms
实际拍照耗时 = 350ms
实际保存耗时 = 120ms
下一次延迟 = 2000 - 350 - 120
```

---

### 第三阶段：专业参数锁定

目标：解决延时摄影中的画面闪烁、对焦漂移、白平衡漂移问题。

功能：

- 拍摄前自动测光
- 锁定曝光
- 锁定快门速度
- 锁定 ISO
- 锁定白平衡
- 锁定对焦距离
- 支持手动调整：
  - 快门速度
  - ISO
  - 白平衡
  - 曝光补偿
  - 对焦距离
- 拍摄过程中禁止自动曝光频繁变化
- 拍摄过程中禁止自动对焦反复搜索

技术要点：

优先使用 Camera2，或者 CameraX + Camera2Interop。

可能用到的 Camera2 参数：

```text
CONTROL_AE_MODE
CONTROL_AE_LOCK
SENSOR_EXPOSURE_TIME
SENSOR_SENSITIVITY
CONTROL_AWB_MODE
CONTROL_AWB_LOCK
LENS_FOCUS_DISTANCE
CONTROL_AF_MODE
```

建议策略：

```text
用户点击开始拍摄前：
1. 打开相机预览
2. 获取当前场景测光结果
3. 计算合适的快门、ISO、白平衡、对焦距离
4. 用户确认后锁定参数
5. 进入间隔拍摄流程
```

---

### 第四阶段：Sony Xperia PRO-I 专属适配

目标：针对这台手机做特殊优化。

需要专门测试：

1. Camera2 支持级别  
   - LEGACY / LIMITED / FULL / LEVEL_3

2. 主摄是否支持手动曝光

3. 主摄是否支持手动对焦

4. 是否支持 RAW

5. 支持哪些分辨率

6. 支持哪些帧率范围

7. 拍照模式下哪些参数可锁定

8. 录像模式下哪些参数可锁定

9. 长时间拍摄时的发热表现

10. 锁屏后相机是否会被系统中断

11. 电池优化是否会影响拍摄

12. 存储写入速度是否足以支撑间隔拍摄

应用内需要包含“设备能力检测页”：

显示以下信息：

```text
设备型号
Android 版本
Camera ID
相机朝向
Camera2 支持级别
是否支持手动曝光
最小快门速度
最大快门速度
最小 ISO
最大 ISO
是否支持手动对焦
最小对焦距离
最大对焦距离
是否支持白平衡锁定
支持的预览分辨率
支持的拍照分辨率
支持的 RAW 分辨率
支持的帧率范围
是否支持视频稳定
是否支持高帧率视频
```

---

### 第五阶段：文件管理与视频合成

目标：让拍摄结果可用于后期。

文件目录结构：

```text
/XperiaTimelapse/
    /2026-10-09_18-30-00/
        frame_000001.jpg
        frame_000002.jpg
        frame_000003.jpg
        metadata.json
        preview.mp4
```

metadata.json 示例：

```json
{
  "device": "Sony Xperia PRO-I",
  "project_name": "2026-10-09_18-30-00",
  "interval_ms": 2000,
  "start_time": "2026-10-09T18:30:00",
  "end_time": "2026-10-09T19:30:00",
  "frame_count": 1800,
  "shutter_speed": "1/100",
  "iso": 100,
  "white_balance": 5600,
  "focus_distance": 1.2,
  "resolution": "4000x3000",
  "format": "JPEG",
  "locked_exposure": true,
  "locked_focus": true,
  "locked_white_balance": true
}
```

视频合成功能：

- 将 JPEG 序列合成为视频
- 支持输出：
  - 1080P
  - 4K
- 支持帧率：
  - 24fps
  - 25fps
  - 30fps
  - 60fps
- 可选编码格式：
  - H.264
  - H.265

可选合成方案：

1. Android MediaCodec + MediaMuxer
2. FFmpeg
3. 只导出照片序列，由电脑后期合成

---

## 五、技术栈建议

### 推荐技术栈

- 语言：Kotlin
- 最低支持版本：Android 10 或 Android 11
- 目标设备：Sony Xperia PRO-I
- UI：Jetpack Compose
- 相机方案：
  - 优先：CameraX + Camera2Interop
  - 进阶：纯 Camera2
- 异步：Kotlin Coroutines + Flow
- 后台：Foreground Service
- 存储：
  - 照片保存到 MediaStore
  - 项目文件保存到应用私有目录或公共目录
- 视频合成：
  - 初期可不做
  - 后期使用 MediaCodec / FFmpeg

---

## 六、权限需求

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

如果使用 Android 10+ 分区存储：

- 照片通过 MediaStore 保存
- 项目文件夹可使用应用私有目录
- 如用户需要导出完整文件夹，可使用 SAF 或请求相应存储权限

---

## 七、关键架构

### 1. 相机控制层

负责：

- 打开相机
- 关闭相机
- 预览绑定
- 拍照
- 参数设置
- 参数锁定
- 能力检测

建议接口：

```kotlin
interface CameraController {
    suspend fun openCamera(cameraId: String)
    suspend fun closeCamera()
    suspend fun takePicture(outputPath: File)
    suspend fun lockExposure()
    suspend fun lockFocus()
    suspend fun lockWhiteBalance()
    suspend fun setManualExposure(shutter: Long, iso: Int)
    suspend fun setManualFocus(distance: Float)
    suspend fun getCameraCapabilities(): CameraCapabilities
}
```

---

### 2. 延时拍摄调度器

负责：

- 开始拍摄
- 暂停拍摄
- 停止拍摄
- 计算下一次拍摄时间
- 拍照耗时补偿
- 错误重试
- 状态上报

建议接口：

```kotlin
interface TimelapseScheduler {
    fun start(config: TimelapseConfig)
    fun pause()
    fun resume()
    fun stop()
    fun observeState(): Flow<TimelapseState>
}
```

配置类：

```kotlin
data class TimelapseConfig(
    val intervalMs: Long,
    val totalFrames: Int?,
    val totalDurationMs: Long?,
    val lockExposure: Boolean,
    val lockFocus: Boolean,
    val lockWhiteBalance: Boolean,
    val outputDirectory: File,
    val imageFormat: ImageFormat,
    val resolution: Size
)
```

状态类：

```kotlin
sealed class TimelapseState {
    object Idle : TimelapseState()
    object Running : TimelapseState()
    object Paused : TimelapseState()
    object Stopped : TimelapseState()
    data class Progress(
        val capturedFrames: Int,
        val targetFrames: Int?,
        val nextCaptureInMs: Long
    ) : TimelapseState()
    data class Error(val message: String) : TimelapseState()
}
```

---

### 3. 存储管理层

负责：

- 创建项目目录
- 生成文件命名
- 保存照片
- 写入元数据
- 检测剩余空间
- 记录拍摄日志

文件命名建议：

```text
frame_000001.jpg
frame_000002.jpg
frame_000003.jpg
```

---

### 4. 前台服务层

负责：

- 显示拍摄通知
- 保持应用后台存活
- 防止系统杀死拍摄任务
- 提供停止按钮

通知内容建议：

```text
正在延时拍摄
已拍摄 128 张
下一张 2 秒后
停止拍摄
```

---

## 八、针对 Sony Xperia PRO-I 的特殊策略

### 1. 相机启动策略

优先选择后置主摄。  
可通过 CameraCharacteristics 判断：

```text
LENS_FACING = BACK
SENSOR_INFO_PHYSICAL_SIZE
SENSOR_INFO_PIXEL_ARRAY_SIZE
```

优先选择分辨率最高、传感器尺寸最大的主摄。

---

### 2. 曝光稳定策略

拍摄前：

```text
1. 打开预览
2. 获取当前测光
3. 读取推荐曝光参数
4. 允许用户微调
5. 锁定 AE
6. 关闭自动曝光
7. 开始间隔拍摄
```

如果无法完全关闭自动曝光，则至少使用：

```text
CONTROL_AE_LOCK = true
```

---

### 3. 对焦稳定策略

拍摄前：

```text
1. 用户点选对焦区域
2. 触发一次自动对焦
3. 读取当前 LENS_FOCUS_DISTANCE
4. 锁定对焦距离
5. 关闭连续自动对焦
```

避免使用：

```text
CONTROL_AF_MODE_CONTINUOUS_PICTURE
CONTROL_AF_MODE_CONTINUOUS_VIDEO
```

建议使用：

```text
CONTROL_AF_MODE_OFF
LENS_FOCUS_DISTANCE = 固定值
```

---

### 4. 白平衡稳定策略

拍摄前：

```text
1. 获取当前白平衡
2. 锁定 AWB
3. 或手动设置色温
```

避免延时视频中出现：

```text
偏暖
偏冷
偏绿
偏蓝
```

---

### 5. 后台保活策略

必须使用：

```text
Foreground Service
```

Android 14+ 注意声明：

```text
foregroundServiceType="camera"
```

并申请：

```text
FOREGROUND_SERVICE_CAMERA
```

同时建议引导用户关闭电池优化：

```text
设置 → 应用 → 本应用 → 电池 → 不限制后台活动
```

---

## 九、风险与限制

### 1. 第三方应用可能无法调用索尼官方相机全部能力

Sony 官方 Photography Pro / Video Pro 可能拥有底层优势。  
第三方应用只能基于标准 Android Camera API。

---

### 2. Camera2 参数支持可能不完整

有些机型虽然支持 Camera2，但可能只支持 LIMITED 级别。  
需要先检测设备能力，再决定功能开放范围。

---

### 3. 高帧率和 RAW 不一定稳定

RAW 和高帧率视频对存储、处理器、温控要求很高。  
如果设备支持，也需要做稳定性测试。

---

### 4. 长时间拍摄会发热

建议应用内加入：

- 温度监控
- 拍摄时长提醒
- 自动降频策略
- 降低分辨率选项
- 降低 JPEG 质量选项
- 暂停冷却提醒

---

### 5. 系统更新可能影响适配

建议加入：

- 日志系统
- 配置开关
- 降级模式
- 兼容模式
- 手动选择相机 ID
- 手动选择拍照策略

---

## 十、MVP 开发任务清单

请按以下顺序生成代码或项目结构：

### Task 1：创建 Android 项目

- 使用 Kotlin
- 使用 Jetpack Compose
- 添加 CameraX / Camera2 依赖
- 添加 Coroutines 依赖
- 添加 Lifecycle 依赖

---

### Task 2：实现相机权限申请

- 检查相机权限
- 请求相机权限
- 权限拒绝时显示说明页面

---

### Task 3：实现相机预览

- 使用 PreviewView
- 绑定 CameraX 或 Camera2 会话
- 默认打开后置主摄

---

### Task 4：实现单张拍照

- 点击按钮拍照
- 保存到 MediaStore 或应用目录
- 显示拍照成功提示

---

### Task 5：实现延迟拍照

- 支持 3 秒、5 秒、10 秒倒计时
- 倒计时 UI 显示
- 倒计时结束后拍照

---

### Task 6：实现间隔拍摄

- 用户设置间隔时间
- 用户设置总张数或总时长
- 自动连续拍照
- 显示拍摄进度

---

### Task 7：实现前台服务

- 拍摄时启动 Foreground Service
- 通知栏显示拍摄状态
- 提供停止按钮

---

### Task 8：实现相机能力检测页

- 显示相机 ID
- 显示 Camera2 支持级别
- 显示支持分辨率
- 显示支持的帧率
- 显示是否支持手动曝光
- 显示是否支持手动对焦
- 显示是否支持 RAW

---

### Task 9：实现参数锁定

- 锁定曝光
- 锁定快门
- 锁定 ISO
- 锁定白平衡
- 锁定对焦距离

---

### Task 10：实现项目文件夹保存

- 每次拍摄创建独立项目文件夹
- 照片按序号命名
- 写入 metadata.json

---

## 十一、代码生成要求

请生成一个 Android 项目结构，包含以下模块：

```text
app/
  data/
    CameraCapabilityRepository.kt
    TimelapseProjectRepository.kt
  camera/
    CameraController.kt
    CameraXController.kt
    Camera2CapabilityReader.kt
  timelapse/
    TimelapseConfig.kt
    TimelapseScheduler.kt
    TimelapseState.kt
    TimelapseWorker.kt
  service/
    TimelapseForegroundService.kt
  ui/
    MainActivity.kt
    CameraPreviewScreen.kt
    TimelapseSettingsScreen.kt
    CapabilityScreen.kt
    CountdownOverlay.kt
  util/
    PermissionHelper.kt
    StorageHelper.kt
    TimeFormatter.kt
```

---

## 十二、优先实现目标

当前最优先实现：

```text
Sony Xperia PRO-I 上可以稳定完成：
1. 打开相机预览
2. 延迟 3 / 5 / 10 秒拍照
3. 每隔 2 秒拍一张
4. 连续拍摄 100 张
5. 照片保存到项目文件夹
6. 锁屏后拍摄不中断
```

后续再实现：

```text
1. 手动曝光
2. 锁定快门
3. 锁定 ISO
4. 锁定对焦
5. 锁定白平衡
6. RAW 拍摄
7. 视频合成
```

---

## 十三、最终产品定位

这个应用不是一个泛用型安卓相机应用，而是：

> 一个专为 Sony Xperia PRO-I 优化的延时摄影工具。

它应该具备以下特点：

1. 单机深度适配  
2. 专业相机参数控制  
3. 稳定长时间拍摄  
4. 曝光、对焦、白平衡锁定  
5. 适合城市延时、日落延时、工程记录、静态记录等场景  
6. 可以生成高质量照片序列  
7. 后期可合成专业延时视频

---

## 十四、给 Codex 的简短指令版本

如果你需要更短的版本，可以直接使用下面这段：

```text
请为我生成一个 Android Kotlin 应用，目标设备是 Sony Xperia PRO-I。应用类型为专业延时摄影与延迟拍照工具。

第一阶段实现：
1. 相机权限申请
2. 相机预览
3. 延迟 3/5/10 秒拍照
4. 间隔拍摄，例如每 2 秒拍一张
5. 支持设置总张数或总时长
6. 照片保存到项目文件夹
7. 使用前台服务，保证锁屏后拍摄不中断

技术栈：
- Kotlin
- Jetpack Compose
- CameraX，必要时使用 Camera2Interop
- Kotlin Coroutines
- Foreground Service
- MediaStore 或应用私有目录存储

后续阶段实现：
- Camera2 能力检测
- 手动曝光
- 锁定快门
- 锁定 ISO
- 锁定白平衡
- 锁定对焦
- RAW 拍摄
- 照片序列合成视频

请优先生成项目结构、权限配置、相机控制层、延时调度器、前台服务和基础 UI。
```

---

## 十五、最终结论

市面上确实有人尝试适配 Sony Xperia PRO-I，但通用产品很难真正发挥这台手机的潜力。  
你自己针对这台手机做专属适配，是完全合理且有优势的路线。

建议从 MVP 开始：

```text
延迟拍照
→ 间隔拍摄
→ 前台服务
→ 参数锁定
→ Sony PRO-I 专属优化
→ 视频合成
```

这样可以先让应用可用，再逐步变成专业级延时摄影工具。