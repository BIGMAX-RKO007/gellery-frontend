package com.google.ai.edge.gallery.aicore

/** Hardware backend used by LiteRT-LM. */
enum class AiBackend { CPU, GPU, NPU }

/** Configuration needed to load a local LiteRT-LM model. */
data class AiModelConfig(
  val modelPath: String,
  val backend: AiBackend = AiBackend.GPU,
  val maxNumTokens: Int = 4096,
  val enableVision: Boolean = false,
  val enableAudio: Boolean = false,
  val cacheDir: String? = null,
  val topK: Int = 40,
  val topP: Double = 0.95,
  val temperature: Double = 0.8,
  val systemInstruction: String? = null,
)

/** One user turn. Attachments are encoded bytes accepted by the model runtime. */
data class AiChatRequest(
  val text: String = "",
  val images: List<ByteArray> = emptyList(),
  val audioClips: List<ByteArray> = emptyList(),
  val enableThinking: Boolean = false,
  val extraContext: Map<String, String> = emptyMap(),
) {
  init {
    require(text.isNotBlank() || images.isNotEmpty() || audioClips.isNotEmpty()) {
      "A request must contain text, an image, or an audio clip."
    }
  }
}

/** Streaming output from a chat turn. */
sealed interface AiChatEvent {
  data class TextDelta(val text: String, val thinking: String? = null) : AiChatEvent

  data object Completed : AiChatEvent

  data object Cancelled : AiChatEvent
}
