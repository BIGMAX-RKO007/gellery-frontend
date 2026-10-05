package com.example.voxmate.voice

import kotlin.math.sqrt

/**
 * 在人声检测后增加音量和持续性确认，只过滤事件，不修改 PCM 或完整发言边界。
 * 实例由单个采音线程独占，确认后仍使用底层包含前滚的原音频。
 * @param delegate 独占的底层人声检测器，关闭时一起释放
 * @param minimumRms 归一化 PCM 的最低 RMS，范围 (0,1)，不是设备声压值
 * @param confirmationFrames 连续达标的 20ms 帧数，默认八帧，即额外约 160ms
 */
class NoiseGuardUtteranceDetector(
  /** 原始人声边界与前滚音频的唯一来源。 */
  private val delegate: UtteranceDetector,
  /** 低音量背景噪声的绝对门槛，可在装配处按设备调节。 */
  private val minimumRms: Float = 0.012f,
  /** 默认要求连续八帧，避免一下碰撞立即打断模型。 */
  private val confirmationFrames: Int = 8,
) : UtteranceDetector {
  /** 是否处于底层已检测到的候选发言。 */
  private var candidate = false
  /** 本轮是否已经通知页面真正起声，防止重复打断。 */
  private var confirmed = false
  /** 连续超过音量门槛的帧数，短暂脉冲不会累计到下一次脉冲。 */
  private var consecutive = 0
  /** 空闲低能量帧估计的噪声底，只驻留内存，起声期间不学习用户声音。 */
  private var noiseFloor = 0.002f

  init {
    require(minimumRms.isFinite() && minimumRms > 0f && minimumRms < 1f)
    require(confirmationFrames in 1..25)
  }

  /**
   * 顺序确认候选，未确认的整段音频不交给 ASR，也不触发 AI 打断。
   * @param frame 16kHz 单声道 320 样本帧，不改写
   * @return 确认后的起声及原始完整发言事件
   * @throws IllegalArgumentException 帧格式错误；底层异常原样传播到采音错误状态
   */
  override fun accept(frame: FloatArray): List<UtteranceBoundary> {
    require(frame.size == 320)
    val rms = sqrt(frame.sumOf { it.toDouble() * it } / frame.size).toFloat()
    val boundaries = delegate.accept(frame)
    val result = mutableListOf<UtteranceBoundary>()
    boundaries.forEach { event ->
      when (event) {
        UtteranceBoundary.Started -> {
          candidate = true
          confirmed = false
          consecutive = 0
        }
        is UtteranceBoundary.Finished -> {
          if (confirmed) result += event
          candidate = false
          confirmed = false
          consecutive = 0
        }
      }
    }
    if (candidate && !confirmed) {
      consecutive = if (rms >= maxOf(minimumRms, noiseFloor * 3.5f)) consecutive + 1 else 0
      if (consecutive >= confirmationFrames) {
        confirmed = true
        result += UtteranceBoundary.Started
      }
    } else if (!candidate && rms < minimumRms) {
      noiseFloor = noiseFloor * 0.98f + rms * 0.02f
    }
    return result
  }

  /** 由采音任务 finally 在同一线程释放底层模型，异常原样传播，不另持有录音资源。 */
  override fun close() = delegate.close()
}
