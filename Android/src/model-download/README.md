# model-download

无 UI 的模型目录与后台下载库。它不依赖 Gallery 的页面、Firebase Analytics、任务类型或 `Model` 大对象。

## 能力

- 解析 Gallery allowlist 风格的模型 JSON，支持网络、缓存和内置 assets 回退
- 构造 Hugging Face 模型地址，支持可选 Bearer Token
- 使用 WorkManager 后台下载大模型
- 使用 HTTP Range 断点续传
- 通过 `Flow<ModelDownloadState>` 返回排队、进度、完成、失败和取消状态
- 返回能直接传给 `ai-core` 的本地文件路径

## 最小调用示例

```kotlin
val artifact = ModelArtifact.fromHuggingFace(
  id = "gemma-4-e2b-it",
  displayName = "Gemma 4 E2B IT",
  repositoryId = "你的仓库 ID",
  fileName = "模型文件名",
  revision = "提交哈希",
  sizeInBytes = 2_600_000_000,
)

val downloads = WorkManagerModelDownloadManager(context)
downloads.enqueue(artifact, huggingFaceToken)
downloads.observe(artifact).collect { state ->
  if (state is ModelDownloadState.Completed) {
    val localModelPath = state.file.absolutePath
  }
}
```

如果使用通知显示后台进度，宿主 App 在 Android 13 及以上需要自行请求通知运行时权限。访问受限 Hugging Face 模型时，Token 会作为 WorkManager 输入暂存到应用自己的数据库中。
