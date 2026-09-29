# ai-core

无 UI 的端侧大模型会话库，内部使用 LiteRT-LM。它不依赖 Gallery 的 Compose 页面、ViewModel、Firebase 或模型下载实现。

## 能力

- 从本地模型文件初始化 CPU、GPU 或 NPU 推理引擎
- 接收文字、PNG/JPEG 字节和音频字节
- 以 `Flow<AiChatEvent>` 返回流式文字及思考内容
- 保留多轮上下文，并支持停止、重置会话和释放模型

## 最小调用示例

```kotlin
val session = LiteRtAiCore.createSession(
  context,
  AiModelConfig(
    modelPath = downloadedFile.absolutePath,
    backend = AiBackend.GPU,
    maxNumTokens = 4096,
  ),
)

session.send(AiChatRequest(text = "你好"))
  .collect { event ->
    when (event) {
      is AiChatEvent.TextDelta -> ttsBuffer.append(event.text)
      AiChatEvent.Completed -> speak(ttsBuffer.toString())
      AiChatEvent.Cancelled -> Unit
    }
  }
```

语音聊天时，录音转文字（STT）和文字转语音（TTS）放在业务 App 层；本库只负责把文字交给模型并返回文字流。
