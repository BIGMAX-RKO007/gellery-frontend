package com.example.voxmate.voice

import org.junit.Assert.*
import org.junit.Test

/** 使用模拟人声误判验证噪声门控，不连接真实麦克风。 */
class NoiseGuardUtteranceDetectorTest {
  /** 即使底层错误认为低音量噪声是人声，也不打断或提交。 */
  @Test fun lowNoiseDoesNotInterruptOrSubmit() {
    val guard = NoiseGuardUtteranceDetector(Boundaries())
    val result = (1..20).flatMap { guard.accept(FloatArray(320) { 0.003f }) }
    assertTrue(result.isEmpty())
    guard.close()
  }
  /** 一百毫秒脉冲未达到确认时长，不成为用户回合。 */
  @Test fun briefImpulseIsRejected() {
    val guard = NoiseGuardUtteranceDetector(Boundaries())
    val result = (1..20).flatMap { index ->
      guard.accept(FloatArray(320) { if (index <= 5) 0.1f else 0f })
    }
    assertTrue(result.isEmpty())
    guard.close()
  }
  /** 正常发言只发一次起声，完整音频含首字缓冲原样交付。 */
  @Test fun confirmedTurnPreservesOriginalAudio() {
    val source = Boundaries()
    val guard = NoiseGuardUtteranceDetector(source)
    val result = (1..20).flatMap { guard.accept(FloatArray(320) { 0.05f }) }
    assertEquals(1, result.count { it == UtteranceBoundary.Started })
    assertSame(source.audio, result.filterIsInstance<UtteranceBoundary.Finished>().single().samples)
    guard.close()
  }
}

/** 底层从首帧判人声、第二十帧结束，用于验证门控而非 native 声学模型。 */
private class Boundaries : UtteranceDetector {
  /** 合成完整回合，门控不得截断或重新拼接它。 */
  val audio = FloatArray(6400) { 0.05f }
  /** 当前帧编号，单线程测试使用。 */
  private var count = 0
  /** 返回人为边界，参数仅用于统一检测器契约，无异常。 */
  override fun accept(frame: FloatArray): List<UtteranceBoundary> {
    count++
    return when (count) {
      1 -> listOf(UtteranceBoundary.Started)
      20 -> listOf(UtteranceBoundary.Finished(audio))
      else -> emptyList()
    }
  }
}
