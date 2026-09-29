package com.google.ai.edge.gallery.aicore

import kotlinx.coroutines.flow.Flow

/** UI-independent conversation API. One session keeps the model's conversation context. */
interface AiChatSession {
  val isGenerating: Boolean

  /** Emits text chunks as they are generated and ends after [AiChatEvent.Completed]. */
  fun send(request: AiChatRequest): Flow<AiChatEvent>

  /** Requests cancellation of the active generation. */
  fun stop()

  /** Clears conversation history while keeping the loaded engine. */
  suspend fun reset(systemInstruction: String? = null)

  /** Releases the conversation and native model engine. */
  suspend fun close()
}
