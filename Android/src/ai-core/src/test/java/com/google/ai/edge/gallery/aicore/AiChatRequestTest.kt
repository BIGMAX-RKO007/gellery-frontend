package com.google.ai.edge.gallery.aicore

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatRequestTest {
  @Test(expected = IllegalArgumentException::class)
  fun rejectsEmptyRequest() {
    AiChatRequest()
  }

  @Test
  fun acceptsAudioOnlyRequest() {
    val request = AiChatRequest(audioClips = listOf(byteArrayOf(1, 2, 3)))
    assertEquals(1, request.audioClips.size)
  }
}
