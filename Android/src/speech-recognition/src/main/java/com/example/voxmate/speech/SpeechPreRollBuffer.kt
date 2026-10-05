package com.example.voxmate.speech

/**
 * 按原始采样位置保留前滚音频，补充 VAD 片段前缀，避免重复拼接已有回溯。
 *
 * 实例由单个采音线程独占，音频只驻留内存。
 * @param sampleRate 采样率，单位 Hz，必须大于零
 * @param preRollMillis 起声确认前希望保留的总时长，单位毫秒，默认 500
 * @param historySeconds 历史容量，必须覆盖最长语句及句尾等待，默认 18 秒
 */
internal class SpeechPreRollBuffer(
  sampleRate: Int = 16_000,
  preRollMillis: Int = 500,
  historySeconds: Int = 18,
) {
  /** 固定容量环形音频历史，最多约 1.1 MB。 */
  private val history: FloatArray
  /** 起声前目标保留样本数。 */
  private val preRollSamples: Long
  /** 已收到的样本总数，作为与 native VAD 对齐的绝对坐标。 */
  private var received = 0L
  /** 上一完整片段的结束位置，避免把上一轮发言拼入下一轮。 */
  private var previousEnd = 0L

  init {
    require(sampleRate > 0 && preRollMillis >= 0 && historySeconds > 0)
    require(preRollMillis.toLong() < historySeconds * 1000L)
    history = FloatArray(Math.multiplyExact(sampleRate, historySeconds))
    preRollSamples = sampleRate.toLong() * preRollMillis / 1000
  }

  /** 顺序复制一帧；调用后输入数组可复用，不持久化录音。 */
  fun append(samples: FloatArray) {
    samples.forEach { sample ->
      history[(received % history.size).toInt()] = sample
      received++
    }
  }

  /**
   * 只补齐 native 片段尚未包含的起声前缀，保留 native 正文不变。
   * @param samples native 返回的完整片段
   * @param startSample native 片段起点，相对于本次连续录音的样本坐标
   * @param detectedAtSample 起声确认位置；未知时不额外延伸
   * @return 新的独占音频数组；不可用的历史不补零，已有正文不重复
   */
  fun extend(samples: FloatArray, startSample: Long, detectedAtSample: Long?): FloatArray {
    require(startSample >= 0)
    val earliestRetained = maxOf(0L, received - history.size)
    val target = detectedAtSample?.let { maxOf(0L, it - preRollSamples) } ?: startSample
    val prefixStart = maxOf(previousEnd, earliestRetained, minOf(startSample, target))
    val prefixSize = if (startSample <= received) maxOf(0L, startSample - prefixStart).toInt() else 0
    val result = FloatArray(prefixSize + samples.size)
    repeat(prefixSize) { index ->
      result[index] = history[((prefixStart + index) % history.size).toInt()]
    }
    samples.copyInto(result, prefixSize)
    previousEnd = maxOf(previousEnd, startSample + samples.size)
    return result
  }
}
