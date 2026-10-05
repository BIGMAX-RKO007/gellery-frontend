package com.example.voxmate.speech

/**
 * 完整发言端点配置，不按固定时长提交半句话；由单个检测器持有。
 * @param silenceSeconds 连续静音结束发言的等待秒数，不能识别语义上的思考停顿
 * @param maximumSeconds 最大单轮秒数；超限失败而非自动发送部分内容
 */
data class SpeechTurnPolicy(
  /** 默认容忍 1.4 秒的换气和短暂停顿。 */
  val silenceSeconds: Float = 1.4f,
  /** 限制端侧识别内存和响应时间，默认两分钟。 */
  val maximumSeconds: Int = 120,
) {
  init {
    require(silenceSeconds > 0 && silenceSeconds.isFinite())
    require(maximumSeconds in 1..120 && silenceSeconds < maximumSeconds)
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
