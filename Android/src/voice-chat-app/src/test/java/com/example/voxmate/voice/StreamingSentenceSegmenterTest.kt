package com.example.voxmate.voice

import org.junit.Assert.*
import org.junit.Test

/** 流式播报按边界而非零碎字符切分，实例只在测试线程使用。 */
class StreamingSentenceSegmenterTest {
  /** 小数点跨增量不拆开，英文句末空白才形成句子。 */
  @Test fun decimalAndEnglishSentence() {
    val segmenter = StreamingSentenceSegmenter()
    assertTrue(segmenter.append("It is 3.").isEmpty())
    assertEquals(listOf("It is 3.14."), segmenter.append("14. Next"))
    assertEquals("Next", segmenter.flush())
  }
  /** 无标点长单词不被字符上限截断。 */
  @Test fun noHardWordCut() {
    val segmenter = StreamingSentenceSegmenter()
    val word = "a".repeat(70)
    assertTrue(segmenter.append(word).isEmpty())
    assertEquals(word, segmenter.flush())
    assertNull(segmenter.flush())
  }
  /** 中文完整短句立即播报，不等待整轮生成结束。 */
  @Test fun completeSentenceStreamsImmediately() {
    val segmenter = StreamingSentenceSegmenter()
    assertTrue(segmenter.append("你好").isEmpty())
    assertEquals(listOf("你好。"), segmenter.append("。下一句"))
    assertEquals("下一句", segmenter.flush())
  }
}
