package com.example.voxmate.voice

import org.junit.Assert.*
import org.junit.Test

/** 检测器回归测试，输入合成帧，不采集或存储真实声音。 */
class WholeSpeechTurnTest {
  /** 三十秒发言和中途一秒停顿只产生一个最终回合。 */
  @Test fun longSpeechAndShortPauseStayInOneTurn() {
    val detector = EnergyUtteranceDetector()
    val events = mutableListOf<UtteranceBoundary>()
    repeat(800) { events += detector.accept(FloatArray(320) { 0.1f }) }
    repeat(50) { events += detector.accept(FloatArray(320)) }
    repeat(700) { events += detector.accept(FloatArray(320) { 0.1f }) }
    assertEquals(1, events.count { it == UtteranceBoundary.Started })
    assertTrue(events.filterIsInstance<UtteranceBoundary.Finished>().isEmpty())
    repeat(71) { events += detector.accept(FloatArray(320)) }
    val turns = events.filterIsInstance<UtteranceBoundary.Finished>()
    assertEquals(1, turns.size)
    assertTrue(turns.single().samples.size > 30 * 16000)
    detector.close()
  }
}
