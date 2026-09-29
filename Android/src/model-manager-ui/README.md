# model-manager-ui

独立的模型管理 Feature Library，用于复刻 Gallery 全局 Models 页面的核心体验，同时避免依赖 Gallery 的 ViewModel、Task、Firebase、Benchmark 和页面导航。

## 模块职责

- 显示 Gallery 1.0.19 模型清单，网络失败时使用缓存或仓库内置清单。
- 展开/收起模型卡片，显示描述、模型大小、输入能力和最低内存建议。
- 下载、断点续传、进度展示、取消、失败重试和删除。
- 下载完成后通过 `SelectedModel` 向宿主返回模型信息和本地文件。
- 通过 `ModelManagerRepository` 隔离数据层，便于测试或替换下载实现。

## 宿主调用

```kotlin
ModelManagerRoute(
  onClose = { navController.navigateUp() },
  onModelSelected = { selectedModel ->
    // selectedModel.file 可直接交给 ai-core。
    navController.navigateUp()
  },
)
```

受限 Hugging Face 模型需要在 `ModelManagerConfig.huggingFaceAccessToken` 中注入有效 Token。认证 UI 不属于本模块，后续可由 VoxMate 设置页或独立 auth 模块提供。
