package com.example.voxmate.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** 验证首字前滚恢复使用原始样本坐标，不重复 native 回溯或上一轮语音。 */
class SpeechPreRollBufferTest {
  /** native 只保留后半部分时，补齐起声前目标窗口。 */
  @Test fun recoversMissingPrefixWithoutDuplicatingSegment() {
    val buffer = SpeechPreRollBuffer(sampleRate = 10, preRollMillis = 500, historySeconds = 2)
    buffer.append(FloatArray(15) { it.toFloat() })
    val result = buffer.extend(floatArrayOf(8f, 9f, 10f), 8, 10)
    assertArrayEquals(floatArrayOf(5f, 6f, 7f, 8f, 9f, 10f), result, 0f)
  }

  /** native 已包含足够回溯时不额外拼接。 */
  @Test fun keepsExistingNativePreRoll() {
    val buffer = SpeechPreRollBuffer(sampleRate = 10, preRollMillis = 500, historySeconds = 2)
    buffer.append(FloatArray(15) { it.toFloat() })
    val samples = floatArrayOf(3f, 4f, 5f, 6f)
    assertArrayEquals(samples, buffer.extend(samples, 3, 10), 0f)
  }

  /** 连续短句不会把上一回合尾部重复交给 ASR。 */
  @Test fun excludesPreviousTurn() {
    val buffer = SpeechPreRollBuffer(sampleRate = 10, preRollMillis = 500, historySeconds = 2)
    buffer.append(FloatArray(15) { it.toFloat() })
    buffer.extend(floatArrayOf(5f, 6f, 7f), 5, 6)
    assertArrayEquals(
      floatArrayOf(8f, 9f, 10f, 11f),
      buffer.extend(floatArrayOf(10f, 11f), 10, 12), 0f
    )
  }

  /** 历史覆盖后只补齐仍然存在的前缀，不补零、不重放旧环形内容。 */
  @Test fun handlesRingWrapAndUnavailableHistory() {
    val buffer = SpeechPreRollBuffer(sampleRate = 10, preRollMillis = 500, historySeconds = 1)
    buffer.append(FloatArray(20) { it.toFloat() })
    assertArrayEquals(
      floatArrayOf(10f, 11f, 12f, 13f),
      buffer.extend(floatArrayOf(12f, 13f), 12, 14), 0f
    )
  }
}
