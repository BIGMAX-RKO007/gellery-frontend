# VoxMate 模型管理功能说明

记录日期：2026-09-28

## 1. 完成效果

VoxMate 首页的“选择模型并开始配置”按钮现在会打开独立 Models 页面。页面参考 Gallery 的 Global Models Manager，保留以下核心体验：

- 顶部 `Models (数量)` 标题和关闭按钮。
- 模型卡片展开与收起。
- 模型名称、大小、说明、图片/音频能力和最低内存建议。
- 下载、后台断点续传、实时进度、下载速度和取消。
- 下载失败重试。
- 已下载模型删除。
- 下载完成后选择模型并返回 VoxMate 首页。
- 网络不可用时依次使用磁盘缓存和内置 1.0.19 清单。

## 2. 解耦结构

```text
:voice-chat-app
  └─ 只负责页面导航和接收 SelectedModel

:model-manager-ui
  ├─ ModelManagerRoute / ModelManagerScreen
  ├─ ModelManagerController
  └─ ModelManagerRepository

:model-download
  ├─ ModelCatalogRepository
  ├─ ModelDownloadManager
  └─ WorkManager + HTTP Range 下载
```

UI 不直接操作 WorkManager；VoxMate 首页也不直接读取模型清单。这样以后更换页面样式、模型源或下载器时，不需要修改其他层。

## 3. 与 Gallery 原实现的边界

没有直接复制 `GlobalModelManager` 和 `ModelManagerViewModel`，因为它们还耦合了 Gallery Task、Benchmark、Firebase、模型导入、AICore、TOS、Hugging Face OAuth 和大量通用 UI。

VoxMate 当前只迁移语音聊天需要的模型浏览、下载和选择链路。以下 Gallery 专属能力暂未迁入：

- Benchmark 页面。
- 本地模型文件导入和 Hugging Face 搜索。
- Gallery 多任务选择。
- AICore 系统模型。
- Hugging Face OAuth/TOS 页面。

`ModelManagerConfig` 已保留 Hugging Face Token 注入口。受限模型要正常下载，后续应增加独立认证模块或在 VoxMate 设置页安全保存 Token。

## 4. 关键入口

- Feature 页面：`Android/src/model-manager-ui/.../ModelManagerRoute.kt`
- 状态控制：`Android/src/model-manager-ui/.../ModelManagerViewModel.kt`
- 数据适配：`Android/src/model-manager-ui/.../ModelManagerRepository.kt`
- VoxMate 导航：`Android/src/voice-chat-app/.../ui/VoxMateApp.kt`

## 5. 下载前台服务崩溃修复（2026-09-28）

首次真机点击下载时，Android 抛出：

```text
foregroundServiceType 0x00000001 is not a subset of
foregroundServiceType attribute 0x00000000
```

原因是 `ModelDownloadWorker` 使用 `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 启动 WorkManager 前台服务，但下载库 Manifest 只声明了权限，没有为 `androidx.work.impl.foreground.SystemForegroundService` 声明 `android:foregroundServiceType="dataSync"`。

已在 `model-download/src/main/AndroidManifest.xml` 中合并该 service 声明。此问题与模型 URL、网络和 Hugging Face Token 无关。

## 6. 跨页面恢复下载进度（2026-09-29）

此前 Models 页面重新创建时，只检查最终模型文件是否存在。正在执行的 WorkManager
任务虽然仍在后台下载，但新页面会先显示“下载”，直到用户再次点击后才重新订阅进度。

现在下载层提供 `currentState()` 状态快照，页面加载模型清单时会同时查询相同模型 ID 和
版本对应的唯一 WorkManager 任务：

- 已排队任务恢复为 `Queued`。
- 运行中任务立即恢复已下载字节、总字节和速度，并继续观察后续进度。
- 已完成且文件存在的任务恢复为 `Completed`。
- 没有任务或成功记录对应的文件已丢失时显示 `NotDownloaded`。
- 失败和取消状态也会恢复，用户可以直接重试。

因此离开 Models 页面不会停止下载，再次进入也不需要重新点击下载按钮。
