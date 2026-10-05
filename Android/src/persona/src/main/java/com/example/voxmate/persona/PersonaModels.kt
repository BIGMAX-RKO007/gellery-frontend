package com.example.voxmate.persona

/**
 * 描述人物包的安装清单，负责版本兼容、资源定位和完整性校验。
 *
 * @property schemaVersion 人物包结构版本；当前仅支持版本 1
 * @property id 全局稳定人物 ID，只允许字母、数字、点、下划线和连字符
 * @property version 人物包版本，用于并存升级和对话快照
 * @property author 人物包作者或发布者名称
 * @property defaultLocale 人物包展示信息的默认语言标签
 * @property minimumAppVersion 可读取此包的最低 VoxMate 版本
 * @property personaFile 相对人物包根目录的人物 JSON 路径
 * @property avatarFile 可选头像资源的相对路径
 * @property voiceConfigFile 可选声音配置的相对路径；大体积 TTS 模型不得直接重复放入人物包
 * @property license 人物及声音资源采用的许可证标识
 * @property files 资源相对路径到小写 SHA-256 的映射；内置开发包允许为空
 */
data class PersonaManifest(
  val schemaVersion: Int = 1,
  val id: String = "",
  val version: String = "",
  val author: String = "",
  val defaultLocale: String = "zh-CN",
  val minimumAppVersion: String = "1.0.0",
  val personaFile: String = "persona.json",
  val avatarFile: String? = null,
  val voiceConfigFile: String? = null,
  val license: String = "",
  val files: Map<String, String> = emptyMap(),
)

/**
 * 可下载人物的完整行为定义，不包含应用安全规则或模型运行参数。
 *
 * @property identity 人物姓名、自我认知和背景身份
 * @property personality 相对稳定的性格维度
 * @property communication 对话语言和表达习惯
 * @property relationship 人物与用户的关系定位
 * @property boundaries 人物自身边界；只能增加限制，不能覆盖应用安全规则
 * @property examples 用于小模型学习表达风格的少量示例对话
 */
data class PersonaDocument(
  val identity: PersonaIdentity = PersonaIdentity(),
  val personality: PersonaPersonality = PersonaPersonality(),
  val communication: PersonaCommunication = PersonaCommunication(),
  val relationship: PersonaRelationship = PersonaRelationship(),
  val boundaries: PersonaBoundaries = PersonaBoundaries(),
  val examples: List<PersonaConversationExample> = emptyList(),
)

/**
 * 描述人物身份；身份信息与性格强弱分开，避免用性别推断行为。
 *
 * @property name 人物在对话中使用的姓名
 * @property genderPresentation 人物自我呈现的性别文本，仅用于身份和自称
 * @property selfReference 人物在回答中使用的第一人称称呼
 * @property userAddress 人物默认如何称呼用户
 * @property role 人物承担的关系或任务角色
 * @property background 用于保持身份一致的简短背景，不应包含真实用户隐私
 */
data class PersonaIdentity(
  val name: String = "",
  val genderPresentation: String = "unspecified",
  val selfReference: String = "我",
  val userAddress: String = "你",
  val role: String = "",
  val background: String = "",
)

/**
 * 描述一个具有强度的性格特征。
 *
 * @property name 可读的性格特征名称
 * @property intensity 特征强度，必须位于 0.0 到 1.0
 */
data class PersonaTrait(val name: String = "", val intensity: Double = 0.5)

/**
 * 描述人物稳定性格及情绪表达程度。
 *
 * @property traits 带强度的人物核心特征
 * @property initiative 主动提问和推进话题的程度，范围为 0.0 到 1.0
 * @property emotionalExpression 情绪表达强度，范围为 0.0 到 1.0
 * @property profanityLevel 粗口等级，范围为 0 到 3；应用安全边界始终优先
 */
data class PersonaPersonality(
  val traits: List<PersonaTrait> = emptyList(),
  val initiative: Double = 0.5,
  val emotionalExpression: Double = 0.5,
  val profanityLevel: Int = 0,
)

/**
 * 描述人物可观察的沟通风格。
 *
 * @property tone 总体语气描述
 * @property verbosity 回答长度偏好，只接受 short、medium 或 long
 * @property humorStyle 幽默表达方式
 * @property catchphrases 少量可选口头禅；不要求每次回答都使用
 * @property replyInUserLanguage 是否跟随用户当前使用的语言回答
 */
data class PersonaCommunication(
  val tone: String = "",
  val verbosity: String = "medium",
  val humorStyle: String = "",
  val catchphrases: List<String> = emptyList(),
  val replyInUserLanguage: Boolean = true,
)

/**
 * 描述人物和用户之间的关系定位。
 *
 * @property type 稳定的机器可读关系类型
 * @property description 提供给模型的自然语言关系说明
 */
data class PersonaRelationship(val type: String = "assistant", val description: String = "")

/**
 * 描述人物不得做出的行为，只能用于收紧行为范围。
 *
 * @property neverDo 人物必须避免的行为列表
 */
data class PersonaBoundaries(val neverDo: List<String> = emptyList())

/**
 * 为语言模型提供一轮人物风格示例，不代表真实发生的对话。
 *
 * @property user 示例用户消息
 * @property assistant 符合人物风格的示例回复
 */
data class PersonaConversationExample(val user: String = "", val assistant: String = "")

/**
 * 描述人物未来可使用的声音配置；当前版本只持久化，不直接控制具体 TTS 引擎。
 *
 * @property engine 声音引擎的稳定 ID，例如 android-system-tts
 * @property engineVersion 配置兼容的引擎协议版本
 * @property voiceId 引擎内部音色 ID；为 null 时由引擎选择默认音色
 * @property supportedLanguages 支持的 BCP-47 语言标签
 * @property speakerEmbedding 可选说话人嵌入文件相对路径，必须具有合法授权
 * @property previewAudio 可选声音预览文件相对路径
 * @property prosody 语速、音高、音量和情绪参数
 */
data class PersonaVoiceConfig(
  val engine: String = "android-system-tts",
  val engineVersion: String = "1",
  val voiceId: String? = null,
  val supportedLanguages: List<String> = emptyList(),
  val speakerEmbedding: String? = null,
  val previewAudio: String? = null,
  val prosody: PersonaVoiceProsody = PersonaVoiceProsody(),
)

/**
 * 保存人物声音的引擎无关韵律参数。
 *
 * @property speed 相对语速，建议范围为 0.5 到 2.0
 * @property pitch 相对音高，建议范围为 0.5 到 2.0
 * @property volume 相对音量，建议范围为 0.0 到 1.0
 * @property emotion 人物默认声音情绪标签
 */
data class PersonaVoiceProsody(
  val speed: Double = 1.0,
  val pitch: Double = 1.0,
  val volume: Double = 1.0,
  val emotion: String = "neutral",
)

/**
 * 已完成解析和校验、可以交给应用使用的人物包。
 *
 * @property manifest 人物安装清单
 * @property persona 人物行为定义
 * @property voice 可选声音配置
 * @property source 人物包的实际存储来源
 */
data class PersonaBundle(
  val manifest: PersonaManifest,
  val persona: PersonaDocument,
  val voice: PersonaVoiceConfig?,
  val source: PersonaSource,
)

/** 人物包的物理来源，用于从 assets 或应用私有目录读取资源。 */
enum class PersonaSource {
  /** 随 APK 发布且不可在运行时修改的 assets 人物。 */
  ASSET,

  /** 从 `.voxpersona` 安装到应用私有目录的人物。 */
  INSTALLED,
}

/**
 * 应用当前启用的人物及其已经编译的系统指令。
 *
 * @property bundle 当前人物包快照
 * @property systemInstruction 创建和重置 AI 会话时必须注入的系统指令
 */
data class ActivePersona(val bundle: PersonaBundle, val systemInstruction: String) {
  /** 用于 Compose 和会话生命周期比较的稳定版本键。 */
  val key: String = "${bundle.manifest.id}@${bundle.manifest.version}"
}
