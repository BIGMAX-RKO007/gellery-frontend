package com.example.voxmate.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/** 完整发言策略边界测试，不需要 native VAD。 */
class SpeechTurnPolicyTest {
  /** 起声采用适度保守配置，不改变长句结束等待。 */
  @Test fun conservativeOnsetKeepsEndpointWait() {
    val policy = SpeechTurnPolicy()
    assertEquals(0.65f, policy.speechThreshold, 0.001f)
    assertEquals(0.256f, policy.onsetSeconds, 0.001f)
    assertEquals(1.4f, policy.silenceSeconds, 0.001f)
  }
  /** 非法概率配置显式失败，避免 native 收到不可用参数。 */
  @Test(expected = IllegalArgumentException::class)
  fun invalidThresholdRejected() {
    SpeechTurnPolicy(speechThreshold = Float.NaN)
  }
  /** 长句超过原来的十五秒限制不会被强制结束。 */
  @Test fun longTurnRemainsAllowed() {
    SpeechTurnPolicy().checkLength(60L * 16000, 16000)
  }
  /** 两分钟边界可录入，超过时明确失败而非分段。 */
  @Test(expected = SpeechTurnTooLongException::class)
  fun overflowFailsExplicitly() {
    SpeechTurnPolicy().checkLength(120L * 16000 + 1, 16000)
  }
  /** 默认句尾等待容忍较短思考停顿。 */
  @Test fun pauseToleranceIsExplicit() {
    assertEquals(1.4f, SpeechTurnPolicy().silenceSeconds, 0.001f)
  }
}
