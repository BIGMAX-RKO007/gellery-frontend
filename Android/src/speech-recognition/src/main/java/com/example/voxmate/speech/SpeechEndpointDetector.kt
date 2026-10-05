package com.example.voxmate.speech

/** 神经网络端点检测产生的起声或完整语句事件。 */
sealed interface SpeechEndpointEvent {
  /** 已确认用户开始说话，可立即用于打断旧回复。 */
  data object Started : SpeechEndpointEvent

  /** 已检测到句尾静音并得到一段完整语音。 */
  data class Finished(val samples: FloatArray) : SpeechEndpointEvent
}

/** 与具体 VAD 框架隔离的持续语音端点检测边界。 */
interface SpeechEndpointDetector {
  /**
   * 顺序接收 16 kHz 单声道浮点 PCM；同一个实例只能由一个采集线程调用。
   *
   * @param samples 当前音频帧，调用结束后实现不得继续持有该数组
   */
  fun accept(samples: FloatArray): List<SpeechEndpointEvent>

  /** 释放 VAD 原生资源，重复调用安全。 */
  fun close()
}
