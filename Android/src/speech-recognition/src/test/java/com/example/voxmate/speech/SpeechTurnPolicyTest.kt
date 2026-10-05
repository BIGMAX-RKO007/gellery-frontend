package com.example.voxmate.speech

import org.junit.Assert.assertEquals
import org.junit.Test

/** 完整发言策略边界测试，不需要 native VAD。 */
class SpeechTurnPolicyTest {
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
