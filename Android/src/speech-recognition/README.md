# speech-recognition

VoxMate 的无 UI 端侧语音识别库。公共 API 不暴露 sherpa-onnx 类型，当前实现使用
SenseVoiceSmall INT8 完成中英等语言转写，并使用 Silero VAD 进行语音起止检测。

调用方负责麦克风权限和 PCM 采集，通过 `SpeechRecognitionEngine` 提交完整语句，通过
`SpeechEndpointDetector` 持续提交 16 kHz 单声道浮点样本。所有实例都必须在不再使用时
调用 `close()`。

模型文件放在 `src/main/assets/speech/sensevoice` 和 `src/main/assets/speech/vad`，随应用离线
交付，不需要 Hugging Face Token 或运行时网络。

其中 `model.int8.onnx` 超过免费 Gitee 的单文件限制，不进入 Git 历史。新开发环境和流水线
必须在打包前将完整模型放到 `src/main/assets/speech/sensevoice/model.int8.onnx`；当前项目记录的
文件大小为 239,233,841 字节，SHA-256 为
`C71F0CE00BEC95B07744E116345E33D8CBBE08CEF896382CF907BF4B51A2CD51`。
