package com.google.ai.edge.gallery.aicore

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Factory for the standalone LiteRT-LM chat capability. */
object LiteRtAiCore {
  suspend fun createSession(context: Context, config: AiModelConfig): AiChatSession =
    withContext(Dispatchers.IO) {
      require(config.modelPath.isNotBlank()) { "modelPath must not be blank." }
      val appContext = context.applicationContext
      val backend = config.backend.toLiteRtBackend(appContext)
      val engine =
        Engine(
          EngineConfig(
            modelPath = config.modelPath,
            backend = backend,
            visionBackend = if (config.enableVision) Backend.GPU() else null,
            audioBackend = if (config.enableAudio) Backend.CPU() else null,
            maxNumTokens = config.maxNumTokens,
            cacheDir = config.cacheDir,
          )
        )
      try {
        engine.initialize()
        LiteRtAiChatSession(engine, backend, config)
      } catch (error: Throwable) {
        runCatching { engine.close() }
        throw error
      }
    }
}

private class LiteRtAiChatSession(
  private val engine: Engine,
  private val backend: Backend,
  private val config: AiModelConfig,
) : AiChatSession {
  private val generating = AtomicBoolean(false)
  private val closed = AtomicBoolean(false)
  private val conversationLock = Any()

  @Volatile private var conversation: Conversation = createConversation(config.systemInstruction)

  override val isGenerating: Boolean
    get() = generating.get()

  override fun send(request: AiChatRequest): Flow<AiChatEvent> = callbackFlow {
    check(!closed.get()) { "The AI session is closed." }
    check(generating.compareAndSet(false, true)) { "A response is already being generated." }

    val contents = buildList {
      request.images.forEach { add(Content.ImageBytes(it)) }
      request.audioClips.forEach { add(Content.AudioBytes(it)) }
      if (request.text.isNotBlank()) add(Content.Text(request.text))
    }
    val runtimeContext: Map<String, Any> =
      request.extraContext + ("enable_thinking" to request.enableThinking)

    try {
      currentConversation().sendMessageAsync(
        Contents.of(contents),
        object : MessageCallback {
          override fun onMessage(message: Message) {
            trySend(AiChatEvent.TextDelta(message.toString(), message.channels[THOUGHT_CHANNEL]))
          }

          override fun onDone() {
            generating.set(false)
            trySend(AiChatEvent.Completed)
            close()
          }

          override fun onError(throwable: Throwable) {
            generating.set(false)
            if (throwable is CancellationException) {
              trySend(AiChatEvent.Cancelled)
              close()
            } else {
              close(throwable)
            }
          }
        },
        runtimeContext,
      )
    } catch (error: Throwable) {
      generating.set(false)
      close(error)
    }

    awaitClose {
      // Flow collection ending does not implicitly cancel model generation. Call stop() explicitly.
    }
  }

  override fun stop() {
    if (!generating.get() || closed.get()) return
    runCatching { currentConversation().cancelProcess() }
  }

  override suspend fun reset(systemInstruction: String?) =
    withContext(Dispatchers.IO) {
      check(!closed.get()) { "The AI session is closed." }
      check(!generating.get()) { "Stop the current response before resetting the conversation." }
      synchronized(conversationLock) {
        conversation.close()
        conversation = createConversation(systemInstruction ?: config.systemInstruction)
      }
    }

  override suspend fun close() =
    withContext(Dispatchers.IO) {
      if (!closed.compareAndSet(false, true)) return@withContext
      if (generating.get()) runCatching { currentConversation().cancelProcess() }
      synchronized(conversationLock) { runCatching { conversation.close() } }
      runCatching { engine.close() }
      generating.set(false)
    }

  private fun currentConversation(): Conversation = synchronized(conversationLock) { conversation }

  private fun createConversation(systemInstruction: String?): Conversation =
    engine.createConversation(
      ConversationConfig(
        samplerConfig =
          if (backend is Backend.NPU) null
          else
            SamplerConfig(
              topK = config.topK,
              topP = config.topP,
              temperature = config.temperature,
            ),
        systemInstruction = systemInstruction?.let { Contents.of(it) },
      )
    )

  private companion object {
    const val THOUGHT_CHANNEL = "thought"
  }
}

private fun AiBackend.toLiteRtBackend(context: Context): Backend =
  when (this) {
    AiBackend.CPU -> Backend.CPU()
    AiBackend.GPU -> Backend.GPU()
    AiBackend.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
  }
