package com.example.voxmate.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * 使用 sherpa-onnx 与内置 Silero VAD 的语音起止检测器。
 *
 * 该类由单个录音线程独占。Silero 固定接收 512 个样本，因此内部会拼接应用的 20 ms 帧。
 *
 * @param context 用于读取 VAD assets，只保存 Application Context
 */
class SileroSpeechEndpointDetector(context: Context) : SpeechEndpointDetector {
  /** 读取模型所需的 Application Context。 */
  private val applicationContext = context.applicationContext

  /** 延迟到录音后台线程第一次送帧时创建，避免阻塞 Compose 主线程。 */
  private var vad: Vad? = null

  /** 把任意输入帧拼成 Silero 要求的固定窗口。 */
  private val window = FloatArray(WINDOW_SIZE)

  /** 当前窗口已经写入的样本数。 */
  private var windowLength = 0

  /** 是否已经向上层发送本轮起声事件。 */
  private var speaking = false

  /** 释放后拒绝继续接收音频。 */
  private var closed = false

  /** 顺序送入音频并把 sherpa 队列转换成稳定的应用事件。 */
  override fun accept(samples: FloatArray): List<SpeechEndpointEvent> {
    check(!closed) { "Speech endpoint detector is closed." }
    if (samples.isEmpty()) return emptyList()
    val events = mutableListOf<SpeechEndpointEvent>()
    var sourceOffset = 0
    val activeVad = vad ?: createVad().also { vad = it }
    while (sourceOffset < samples.size) {
      val count = minOf(WINDOW_SIZE - windowLength, samples.size - sourceOffset)
      samples.copyInto(window, windowLength, sourceOffset, sourceOffset + count)
      windowLength += count
      sourceOffset += count
      if (windowLength == WINDOW_SIZE) {
        activeVad.acceptWaveform(window)
        windowLength = 0
        if (activeVad.isSpeechDetected() && !speaking) {
          speaking = true
          events += SpeechEndpointEvent.Started
        }
        while (!activeVad.empty()) {
          val segment = activeVad.front()
          if (!speaking) events += SpeechEndpointEvent.Started
          speaking = false
          if (segment.samples.isNotEmpty()) {
            events += SpeechEndpointEvent.Finished(segment.samples.copyOf())
          }
          activeVad.pop()
        }
      }
    }
    return events
  }

  /** 释放 native VAD；重复调用安全。 */
  override fun close() {
    if (closed) return
    closed = true
    vad?.release()
    vad = null
  }

  /** 按电话式对话参数创建 Silero VAD。 */
  private fun createVad(): Vad =
    Vad(
      assetManager = applicationContext.assets,
      config =
        VadModelConfig(
          sileroVadModelConfig =
            SileroVadModelConfig(
              model = VAD_ASSET,
              threshold = 0.5f,
              minSilenceDuration = 0.7f,
              minSpeechDuration = 0.16f,
              windowSize = WINDOW_SIZE,
              maxSpeechDuration = 15f,
            ),
          sampleRate = SAMPLE_RATE,
          numThreads = 1,
          provider = "cpu",
        ),
    )

  /** VAD 固定格式与资源路径。 */
  private companion object {
    /** Silero 16 kHz 输入窗口。 */
    const val WINDOW_SIZE = 512

    /** 固定采样率。 */
    const val SAMPLE_RATE = 16_000

    /** APK 内置 VAD 路径。 */
    const val VAD_ASSET = "speech/vad/silero_vad.onnx"
  }
}
