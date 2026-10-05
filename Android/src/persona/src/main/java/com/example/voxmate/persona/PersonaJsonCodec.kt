package com.example.voxmate.persona

import com.google.gson.Gson
import com.google.gson.JsonParseException

/**
 * 集中解析人物包 JSON，并在数据进入数据库或提示词前执行边界校验。
 *
 * @param gson JSON 编解码器；默认实例不持有 Android Context
 */
class PersonaJsonCodec(private val gson: Gson = Gson()) {
  /**
   * 解析并校验人物清单。
   *
   * @param json UTF-8 清单文本
   * @return 通过结构和字段校验的清单
   * @throws PersonaFormatException JSON 损坏、版本不兼容或字段越界时抛出
   */
  fun decodeManifest(json: String): PersonaManifest =
    decode(json, PersonaManifest::class.java, "manifest.json").also(::validateManifest)

  /**
   * 解析并校验人物行为定义。
   *
   * @param json UTF-8 人物文本
   * @return 通过长度、枚举和数值范围校验的人物定义
   * @throws PersonaFormatException JSON 损坏或字段越界时抛出
   */
  fun decodePersona(json: String): PersonaDocument =
    decode(json, PersonaDocument::class.java, "persona.json").also(::validatePersona)

  /**
   * 解析并校验可选声音配置。
   *
   * @param json UTF-8 声音配置文本
   * @return 通过基础兼容性校验的声音配置
   * @throws PersonaFormatException JSON 损坏或韵律字段越界时抛出
   */
  fun decodeVoice(json: String): PersonaVoiceConfig =
    decode(json, PersonaVoiceConfig::class.java, "voice.json").also(::validateVoice)

  /**
   * 把人物清单编码为 JSON，主要供安装索引和测试使用。
   *
   * @param manifest 待编码人物清单
   * @return JSON 文本
   */
  fun encodeManifest(manifest: PersonaManifest): String = gson.toJson(manifest)

  /**
   * 调用 Gson 解析指定类型，并统一转换底层格式异常。
   *
   * @param json 待解析 JSON
   * @param type 目标 Java 类型
   * @param fileName 用于错误信息的逻辑文件名
   * @return 解析后的非空对象
   * @throws PersonaFormatException 无法解析时抛出
   */
  private fun <T : Any> decode(json: String, type: Class<T>, fileName: String): T =
    try {
      gson.fromJson(json, type) ?: throw PersonaFormatException("$fileName 不能为空。")
    } catch (error: JsonParseException) {
      throw PersonaFormatException("$fileName 不是有效的人物包 JSON。", error)
    } catch (error: RuntimeException) {
      throw PersonaFormatException("$fileName 无法解析。", error)
    }

  /** 校验清单版本、路径和用于磁盘目录的稳定标识。 */
  private fun validateManifest(manifest: PersonaManifest) {
    requireFormat(manifest.schemaVersion == SUPPORTED_SCHEMA_VERSION, "不支持的人物包结构版本。")
    requireFormat(ID_PATTERN.matches(manifest.id), "人物 ID 格式不正确。")
    requireFormat(VERSION_PATTERN.matches(manifest.version), "人物版本格式不正确。")
    requireFormat(manifest.author.length <= MAX_SHORT_TEXT, "人物作者名称过长。")
    requireSafeRelativePath(manifest.personaFile, "人物定义路径")
    manifest.avatarFile?.let { requireSafeRelativePath(it, "头像路径") }
    manifest.voiceConfigFile?.let { requireSafeRelativePath(it, "声音配置路径") }
    requireFormat(manifest.files.size <= MAX_FILE_HASH_COUNT, "人物包文件校验项过多。")
    manifest.files.forEach { (path, hash) ->
      requireSafeRelativePath(path, "校验文件路径")
      requireFormat(SHA_256_PATTERN.matches(hash), "文件 SHA-256 格式不正确。")
    }
  }

  /** 校验人物字段，避免下载包无界占用上下文或制造无效提示词。 */
  private fun validatePersona(persona: PersonaDocument) {
    requireText(persona.identity.name, "人物姓名", MAX_SHORT_TEXT)
    requireText(persona.identity.role, "人物角色", MAX_PARAGRAPH_TEXT)
    requireOptionalText(persona.identity.background, "人物背景", MAX_BACKGROUND_TEXT)
    requireOptionalText(persona.communication.tone, "人物语气", MAX_PARAGRAPH_TEXT)
    requireOptionalText(persona.communication.humorStyle, "幽默方式", MAX_PARAGRAPH_TEXT)
    requireFormat(persona.communication.verbosity in ALLOWED_VERBOSITY, "回答长度配置不正确。")
    requireFormat(persona.personality.traits.size <= MAX_TRAIT_COUNT, "人物性格特征过多。")
    persona.personality.traits.forEach { trait ->
      requireText(trait.name, "性格特征", MAX_SHORT_TEXT)
      requireUnitRange(trait.intensity, "性格特征强度")
    }
    requireUnitRange(persona.personality.initiative, "主动程度")
    requireUnitRange(persona.personality.emotionalExpression, "情绪表达程度")
    requireFormat(persona.personality.profanityLevel in 0..3, "粗口等级必须位于 0 到 3。")
    requireFormat(persona.communication.catchphrases.size <= MAX_CATCHPHRASE_COUNT, "人物口头禅过多。")
    persona.communication.catchphrases.forEach {
      requireText(it, "人物口头禅", MAX_SHORT_TEXT)
    }
    requireFormat(persona.boundaries.neverDo.size <= MAX_BOUNDARY_COUNT, "人物边界条目过多。")
    persona.boundaries.neverDo.forEach { requireText(it, "人物边界", MAX_PARAGRAPH_TEXT) }
    requireFormat(persona.examples.size <= MAX_EXAMPLE_COUNT, "人物示例对话过多。")
    persona.examples.forEach { example ->
      requireText(example.user, "示例用户消息", MAX_EXAMPLE_TEXT)
      requireText(example.assistant, "示例人物回复", MAX_EXAMPLE_TEXT)
    }
  }

  /** 校验声音配置标识和韵律范围，当前不检查具体 TTS 引擎是否已经安装。 */
  private fun validateVoice(voice: PersonaVoiceConfig) {
    requireText(voice.engine, "声音引擎", MAX_SHORT_TEXT)
    requireText(voice.engineVersion, "声音引擎版本", MAX_SHORT_TEXT)
    voice.speakerEmbedding?.let { requireSafeRelativePath(it, "说话人嵌入路径") }
    voice.previewAudio?.let { requireSafeRelativePath(it, "声音预览路径") }
    requireFormat(voice.prosody.speed in 0.5..2.0, "声音语速必须位于 0.5 到 2.0。")
    requireFormat(voice.prosody.pitch in 0.5..2.0, "声音音高必须位于 0.5 到 2.0。")
    requireUnitRange(voice.prosody.volume, "声音音量")
  }

  /** 要求字符串非空且不超过指定字符数。 */
  private fun requireText(value: String, name: String, maxLength: Int) {
    requireFormat(value.isNotBlank(), "$name 不能为空。")
    requireFormat(value.length <= maxLength, "$name 过长。")
  }

  /** 只在字符串非空时校验最大长度。 */
  private fun requireOptionalText(value: String, name: String, maxLength: Int) {
    requireFormat(value.length <= maxLength, "$name 过长。")
  }

  /** 要求浮点数处于闭区间 0.0 到 1.0。 */
  private fun requireUnitRange(value: Double, name: String) {
    requireFormat(value in 0.0..1.0, "$name 必须位于 0.0 到 1.0。")
  }

  /** 要求人物包内部路径安全、相对且不包含父目录跳转。 */
  private fun requireSafeRelativePath(path: String, name: String) {
    val normalized = path.replace('\\', '/')
    requireFormat(
      normalized.isNotBlank() &&
        !normalized.startsWith('/') &&
        !normalized.contains(":") &&
        normalized.split('/').none { it == ".." || it.isBlank() },
      "$name 不安全。",
    )
  }

  /** 在条件不成立时抛出稳定的人物格式异常。 */
  private fun requireFormat(condition: Boolean, message: String) {
    if (!condition) throw PersonaFormatException(message)
  }

  /** 集中保存人物包格式和上下文体积限制。 */
  private companion object {
    /** 当前读取器支持的人物包结构版本。 */
    const val SUPPORTED_SCHEMA_VERSION = 1

    /** 短字段允许的最大字符数。 */
    const val MAX_SHORT_TEXT = 80

    /** 普通说明字段允许的最大字符数。 */
    const val MAX_PARAGRAPH_TEXT = 500

    /** 人物背景允许的最大字符数。 */
    const val MAX_BACKGROUND_TEXT = 1_500

    /** 单条示例消息允许的最大字符数。 */
    const val MAX_EXAMPLE_TEXT = 500

    /** 单个人物允许的性格特征数量。 */
    const val MAX_TRAIT_COUNT = 12

    /** 单个人物允许的口头禅数量。 */
    const val MAX_CATCHPHRASE_COUNT = 12

    /** 单个人物允许的边界数量。 */
    const val MAX_BOUNDARY_COUNT = 20

    /** 单个人物允许的示例对话数量。 */
    const val MAX_EXAMPLE_COUNT = 8

    /** 清单允许声明校验值的最大文件数量。 */
    const val MAX_FILE_HASH_COUNT = 64

    /** 允许用于磁盘目录和数据库键的人物 ID 字符。 */
    val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{2,127}")

    /** 允许用于磁盘目录的人物版本字符。 */
    val VERSION_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")

    /** 标准小写或大写 SHA-256 十六进制文本。 */
    val SHA_256_PATTERN = Regex("[A-Fa-f0-9]{64}")

    /** 当前支持的回答长度配置。 */
    val ALLOWED_VERBOSITY = setOf("short", "medium", "long")
  }
}

/**
 * 表示人物包内容不完整、不兼容或超出安全边界。
 *
 * @param message 不包含敏感数据的失败原因
 * @param cause 可选底层 JSON 解析异常
 */
class PersonaFormatException(message: String, cause: Throwable? = null) :
  IllegalArgumentException(message, cause)
