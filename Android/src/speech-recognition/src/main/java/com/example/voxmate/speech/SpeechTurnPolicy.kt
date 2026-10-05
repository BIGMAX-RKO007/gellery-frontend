package com.example.voxmate.speech

/**
 * 完整发言端点配置，不按固定时长提交半句话；由单个检测器持有。
 * @param silenceSeconds 连续静音结束发言的等待秒数，不能识别语义上的思考停顿
 * @param maximumSeconds 最大单轮秒数；超限失败而非自动发送部分内容
 * @param speechThreshold Silero 人声概率门槛，越高越不易触发，不是麦克风音量
 * @param onsetSeconds 持续人声的最短确认时间，单位秒，增加时会延迟插话
 */
data class SpeechTurnPolicy(
  /** 默认容忍 1.4 秒的换气和短暂停顿。 */
  val silenceSeconds: Float = 1.4f,
  /** 限制端侧识别内存和响应时间，默认两分钟。 */
  val maximumSeconds: Int = 120,
  /** 从 0.5 提高到 0.65，适度减少低置信度噪声触发，可按设备注入调整。 */
  val speechThreshold: Float = 0.65f,
  /** 从 160ms 延长到 256ms；原有 500ms 前滚仍用于保护首字。 */
  val onsetSeconds: Float = 0.256f,
) {
  init {
    require(silenceSeconds > 0 && silenceSeconds.isFinite())
    require(maximumSeconds in 1..120 && silenceSeconds < maximumSeconds)
    require(speechThreshold.isFinite() && speechThreshold > 0f && speechThreshold < 1f)
    require(onsetSeconds.isFinite() && onsetSeconds > 0f && onsetSeconds < silenceSeconds)
  }

  /**
   * 顺序校验已采样长度，不记录或提交音频。
   * @param samples 起声后累计样本数，非负
   * @param sampleRate 采样率 Hz，必须为正
   * @throws SpeechTurnTooLongException 超出本轮安全长度
   */
  fun checkLength(samples: Long, sampleRate: Int) {
    require(samples >= 0 && sampleRate > 0)
    if (samples > maximumSeconds * sampleRate.toLong()) throw SpeechTurnTooLongException()
  }
}

/** 发言超过安全上限；调用方应提示重说，不把半段音频交给模型。 */
class SpeechTurnTooLongException : IllegalStateException("Speech turn exceeds safe duration.")
