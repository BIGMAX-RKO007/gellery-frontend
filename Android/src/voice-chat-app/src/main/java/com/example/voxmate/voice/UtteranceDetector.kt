package com.example.voxmate.voice

import com.example.voxmate.speech.SpeechEndpointDetector
import com.example.voxmate.speech.SpeechEndpointEvent
import kotlin.math.sqrt

/** 可替换的语音起止检测器，单个录音协程独占；后续可接神经网络 VAD。 */
interface UtteranceDetector {
  /** 接收 16 kHz、20ms 音频帧，返回起声或语句完成事件；调用方不能复用数组。 */
  fun accept(frame: FloatArray): List<UtteranceBoundary>

  /** 释放可选的神经网络 VAD 资源；纯 Kotlin 实现无需处理。 */
  fun close() = Unit
}

/** 分句事件，音频只存在内存；开始事件用于不等待识别完成的插话打断。 */
sealed interface UtteranceBoundary {
  /** 已检测到持续声音活动，但不保证能区分回声和真正人声。 */
  data object Started : UtteranceBoundary
  /** 完成当前分段，过短噪声返回空数组，调用方不得送入模型。 */
  data class Finished(val samples: FloatArray) : UtteranceBoundary
}

/**
 * 轻量自适应能量分句：160ms 起声、700ms 静音结束、最长 15 秒、200ms 前滚。
 * 不是声纹识别或声学回声消除，嘈杂环境可能误触发；接口可替换为模型 VAD。
 */
class EnergyUtteranceDetector : UtteranceDetector {
  /** 最近十帧的前滚缓冲，保留说话开头。 */
  private val preRoll = ArrayDeque<FloatArray>()
  /** 当前语句帧，最多 750 帧，避免无限录音占用内存。 */
  private val frames = mutableListOf<FloatArray>()
  /** 非说话时自适应更新的归一化噪声底。 */
  private var noiseFloor = 0.002f
  /** 连续高于噪声底的帧数。 */
  private var onset = 0
  /** 语句中连续静音的帧数。 */
  private var silence = 0
  /** 已进入说话状态时为真。 */
  private var speaking = false

  /** 顺序处理一帧，音量阈值为噪声底三倍且不低于 0.012；参数非法抛出异常。 */
  override fun accept(frame: FloatArray): List<UtteranceBoundary> {
    require(frame.size == 320)
    val rms = sqrt(frame.sumOf { (it * it).toDouble() } / frame.size).toFloat()
    val voiced = rms > maxOf(0.012f, noiseFloor * 3f)
    if (!speaking) {
      preRoll.addLast(frame)
      if (preRoll.size > 10) preRoll.removeFirst()
      onset = if (voiced) onset + 1 else 0
      if (!voiced) noiseFloor = noiseFloor * 0.98f + rms * 0.02f
      if (onset < 8) return emptyList()
      speaking = true
      frames.addAll(preRoll)
      preRoll.clear()
      return listOf(UtteranceBoundary.Started)
    }
    frames += frame
    silence = if (voiced) 0 else silence + 1
    if (silence < 35 && frames.size < 750) return emptyList()
    val audio = FloatArray(frames.size * 320)
    frames.forEachIndexed { index, samples -> samples.copyInto(audio, index * 320) }
    frames.clear()
    speaking = false
    onset = 0
    silence = 0
    return listOf(UtteranceBoundary.Finished(audio))
  }
}

/** 把 speech-recognition 库的神经网络端点事件适配到现有录音管线。 */
class NeuralUtteranceDetector(
  private val detector: SpeechEndpointDetector,
) : UtteranceDetector {
  /** 将库事件转换为应用层事件。 */
  override fun accept(frame: FloatArray): List<UtteranceBoundary> =
    detector.accept(frame).map { event ->
      when (event) {
        SpeechEndpointEvent.Started -> UtteranceBoundary.Started
        is SpeechEndpointEvent.Finished -> UtteranceBoundary.Finished(event.samples)
      }
    }

  /** 释放 sherpa-onnx VAD 原生资源。 */
  override fun close() = detector.close()
}
