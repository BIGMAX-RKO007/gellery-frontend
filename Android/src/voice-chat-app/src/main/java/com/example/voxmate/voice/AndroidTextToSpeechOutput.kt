package com.example.voxmate.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 使用 Android 系统文字转语音服务实现 [SpeechOutput]。
 *
 * 实例只保存 Application Context。初始化完成前收到的文本会暂存在内存队列中；调用
 * [release] 后队列、系统 TTS 和监听器都会被释放，实例不可再次使用。
 *
 * @param context 用于连接系统 TTS 服务的 Android Context
 * @param locale 默认播报语言，通常跟随系统语言
 * @param initialSettings 初始播报配置，包含自定义语速、音调与可选音色
 */
class AndroidTextToSpeechOutput(
  context: Context,
  private val locale: Locale = Locale.getDefault(),
  initialSettings: VoiceSettings = VoiceSettings(),
) : SpeechOutput {
  /** 不会持有 Activity 的 Application Context。 */
  private val appContext = context.applicationContext

  /** 把 TTS 生命周期调用集中到 Android 主线程。 */
  private val mainHandler = Handler(Looper.getMainLooper())

  /** 语音事件的私有可变流，允许 TTS Binder 回调非阻塞上报。 */
  private val mutableEvents = MutableSharedFlow<SpeechOutputEvent>(extraBufferCapacity = 16)

  /** 供页面协调器收集的只读播报事件流。 */
  override val events: Flow<SpeechOutputEvent> = mutableEvents.asSharedFlow()

  /** 当前系统检测到的可用音色列表流。 */
  private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())

  /** 供设置页面观察的可用系统音色列表。 */
  override val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

  /** 当前生效的语音配置参数。 */
  private var currentSettings: VoiceSettings = initialSettings

  /** 为每个系统播报请求生成唯一标识，便于判断整个队列是否结束。 */
  private val utteranceIds = AtomicLong(0L)

  /** 防止释放后的异步初始化回调重新启用实例。 */
  private val released = AtomicBoolean(false)

  /** 初始化完成前暂存的播报请求，仅在主线程访问。 */
  private val pendingRequests = mutableListOf<PendingSpeechRequest>()

  /** 已提交给系统 TTS 且尚未完成的播报标识，仅在同步块内访问。 */
  private val activeUtterances = mutableSetOf<String>()

  /** 系统 TTS 实例；初始化完成前已经创建但尚不可播报。 */
  private var textToSpeech: TextToSpeech? = null

  /** 标记系统 TTS 是否已经通过初始化和语言检查。 */
  private var ready = false
  /** 目标播报语言，可由语音页面显式选择；默认跟随系统。 */
  private var selectedLocale = locale
  /** 系统服务是否已初始化，区别于目标语言包是否可用。 */
  private var initialized = false
  /** 初始化或语言选择失败时拒绝继续排队，避免积累无法播放的文字。 */
  private var initializationError: String? = null

  init {
    textToSpeech =
      TextToSpeech(appContext) { status ->
        mainHandler.post { completeInitialization(status) }
      }
  }

  /**
   * 把非空文字提交给系统 TTS。
   *
   * 调用可来自任意线程，实际队列操作会切换到主线程。初始化尚未完成时请求会进入内存
   * 队列；初始化失败或实例已释放时不会继续播报。
   *
   * @param text 需要播报的文字
   * @param queue `true` 时追加，`false` 时停止旧内容并从该文本重新开始
   */
  override fun speak(text: String, queue: Boolean) {
    val normalized = text.trim()
    if (normalized.isEmpty() || released.get()) return
    mainHandler.post {
      if (released.get()) return@post
      initializationError?.let {
        mutableEvents.tryEmit(SpeechOutputEvent.Error(it))
        return@post
      }
      val request = PendingSpeechRequest(text = normalized, queue = queue)
      if (!ready) {
        if (!queue) pendingRequests.clear()
        pendingRequests += request
      } else {
        submit(request)
      }
    }
  }

  /** 主线程设置后续播报语言，不支持的语言通过事件提示，避免静默换成错误音色。 */
  override fun setLanguage(language: String) {
    mainHandler.post {
      selectedLocale = if (language == "auto") locale else Locale.forLanguageTag(language)
      if (initialized && !released.get()) {
        val result = textToSpeech?.setLanguage(selectedLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          ready = false
          initializationError = ERROR_LANGUAGE_UNAVAILABLE
          pendingRequests.clear()
          mutableEvents.tryEmit(SpeechOutputEvent.Error(ERROR_LANGUAGE_UNAVAILABLE))
        } else {
          ready = true
          initializationError = null
        }
      }
    }
  }

  /** 停止当前声音并清空初始化队列和系统播放队列。 */
  override fun stop() {
    mainHandler.post {
      pendingRequests.clear()
      synchronized(activeUtterances) { activeUtterances.clear() }
      textToSpeech?.stop()
      mutableEvents.tryEmit(SpeechOutputEvent.Completed)
    }
  }

  /** 永久停止并释放系统 TTS；重复调用安全。 */
  override fun release() {
    if (!released.compareAndSet(false, true)) return
    mainHandler.post {
      ready = false
      pendingRequests.clear()
      synchronized(activeUtterances) { activeUtterances.clear() }
      textToSpeech?.stop()
      textToSpeech?.shutdown()
      textToSpeech = null
    }
  }

  /**
   * 完成系统 TTS 初始化、语言选择和进度监听器安装。
   *
   * @param status Android TTS 返回的初始化状态码
   */
  private fun completeInitialization(status: Int) {
    if (released.get()) return
    val engine = textToSpeech
    if (status != TextToSpeech.SUCCESS || engine == null) {
      initializationError = ERROR_INITIALIZATION
      pendingRequests.clear()
      mutableEvents.tryEmit(SpeechOutputEvent.Error(ERROR_INITIALIZATION))
      return
    }
    engine.setOnUtteranceProgressListener(createProgressListener())
    initialized = true
    engine.setAudioAttributes(AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    val languageResult = engine.setLanguage(selectedLocale)
    if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
      languageResult == TextToSpeech.LANG_NOT_SUPPORTED
    ) {
      initializationError = ERROR_LANGUAGE_UNAVAILABLE
      pendingRequests.clear()
      mutableEvents.tryEmit(SpeechOutputEvent.Error(ERROR_LANGUAGE_UNAVAILABLE))
      return
    }
    ready = true
    initializationError = null
    refreshAvailableVoices(engine)
    applySettingsInternal(engine, currentSettings)
    val queued = pendingRequests.toList()
    pendingRequests.clear()
    queued.forEach(::submit)
  }

  /**
   * 动态应用用户设置的语速、音调及音色。
   *
   * 可在任意线程调用，内部统一调度至主线程执行以保证底层 TTS 调用安全。
   *
   * @param settings 用户调整后的语音配置
   */
  override fun applySettings(settings: VoiceSettings) {
    mainHandler.post {
      currentSettings = settings
      val engine = textToSpeech
      if (initialized && !released.get() && engine != null) {
        applySettingsInternal(engine, settings)
      }
    }
  }

  /**
   * 在主线程直接更新系统 TTS 引擎的语速、音调与音色。
   */
  private fun applySettingsInternal(engine: TextToSpeech, settings: VoiceSettings) {
    try {
      engine.setSpeechRate(settings.speechRate)
      engine.setPitch(settings.pitch)

      val allVoices = engine.voices ?: emptySet()
      val targetVoice = if (!settings.voiceName.isNullOrBlank()) {
        allVoices.firstOrNull { it.name == settings.voiceName }
      } else {
        findBestVoice(allVoices)
      }

      if (targetVoice != null) {
        engine.voice = targetVoice
      }
    } catch (_: Exception) {
      // 容错处理：部分机型厂商特定引擎不支持或抛出异常时不崩溃
    }
  }

  /**
   * 提取当前系统引擎支持的且匹配当前语言的音色列表。
   */
  private fun refreshAvailableVoices(engine: TextToSpeech) {
    try {
      val rawVoices = engine.voices ?: emptySet()
      val targetLang = selectedLocale.language
      val matched = rawVoices.filter { voice ->
        voice.locale.language.equals(targetLang, ignoreCase = true)
      }
      val options = matched.map { voice ->
        val isHigh = voice.quality == Voice.QUALITY_VERY_HIGH || voice.quality == Voice.QUALITY_HIGH
        val displayName = buildVoiceDisplayName(voice)
        VoiceOption(
          name = voice.name,
          displayName = displayName,
          isHighQuality = isHigh,
          requiresNetwork = voice.isNetworkConnectionRequired,
          locale = voice.locale.toLanguageTag(),
        )
      }.sortedWith(compareByDescending<VoiceOption> { it.isHighQuality }.thenBy { it.requiresNetwork })

      _availableVoices.value = options
    } catch (_: Exception) {
      _availableVoices.value = emptyList()
    }
  }

  /**
   * 遍历音色集合，按高保真、本地离线与名称特征自动选择推荐音色。
   */
  private fun findBestVoice(voices: Set<Voice>): Voice? {
    val targetLang = selectedLocale.language
    val candidates = voices.filter { it.locale.language.equals(targetLang, ignoreCase = true) }
    if (candidates.isEmpty()) return null

    return candidates.maxByOrNull { voice ->
      var score = 0
      if (!voice.isNetworkConnectionRequired) score += 50
      if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 40
      else if (voice.quality == Voice.QUALITY_HIGH) score += 20
      if (voice.name.contains("neural", ignoreCase = true)) score += 30
      if (voice.name.contains("female", ignoreCase = true) || voice.name.contains("cmn", ignoreCase = true)) score += 15
      score
    }
  }

  /**
   * 构造对用户友好的音色名称。
   */
  private fun buildVoiceDisplayName(voice: Voice): String {
    val rawName = voice.name
    val country = voice.locale.country
    val tag = if (country.isNotBlank()) " ($country)" else ""
    return when {
      rawName.contains("female", ignoreCase = true) -> "女性音色$tag"
      rawName.contains("male", ignoreCase = true) -> "男性音色$tag"
      rawName.contains("neural", ignoreCase = true) -> "神经网络自然音$tag"
      rawName.contains("local", ignoreCase = true) -> "离线标准音$tag"
      else -> rawName.substringAfterLast("#").substringAfterLast("_")
    }
  }

  /**
   * 创建系统播报进度监听器，并把 Binder 回调转换成应用层事件。
   *
   * @return 供当前 TTS 实例独占使用的监听器
   */
  private fun createProgressListener(): UtteranceProgressListener =
    object : UtteranceProgressListener() {
      /** 当系统开始播放某个语句时通知页面。 */
      override fun onStart(utteranceId: String?) {
        if (synchronized(activeUtterances) { utteranceId in activeUtterances }) {
          mutableEvents.tryEmit(SpeechOutputEvent.Started)
        }
      }

      /** 当一个语句结束且队列已经清空时通知页面。 */
      override fun onDone(utteranceId: String?) {
        val queueCompleted =
          synchronized(activeUtterances) {
            val removed = activeUtterances.remove(utteranceId)
            removed && activeUtterances.isEmpty()
          }
        if (queueCompleted) mutableEvents.tryEmit(SpeechOutputEvent.Completed)
      }

      /** 当系统无法合成某个语句时清理标识并上报错误。 */
      @Suppress("OVERRIDE_DEPRECATION")
      override fun onError(utteranceId: String?) {
        handleSpeechError(utteranceId)
      }

      /** 当系统返回带错误码的失败时清理标识并上报错误。 */
      override fun onError(utteranceId: String?, errorCode: Int) {
        handleSpeechError(utteranceId)
      }
    }

  /**
   * 提交一个已经通过初始化检查的系统播报请求。
   *
   * @param request 包含文字和队列策略的内部请求
   */
  private fun submit(request: PendingSpeechRequest) {
    val engine = textToSpeech ?: return
    val utteranceId = "voxmate-tts-${utteranceIds.incrementAndGet()}"
    if (!request.queue) {
      synchronized(activeUtterances) { activeUtterances.clear() }
    }
    synchronized(activeUtterances) { activeUtterances += utteranceId }
    val result =
      engine.speak(
        request.text,
        if (request.queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
        Bundle.EMPTY,
        utteranceId,
      )
    if (result == TextToSpeech.ERROR) handleSpeechError(utteranceId)
  }

  /**
   * 统一处理系统 TTS 的异步或同步失败。
   *
   * @param utteranceId 失败语句的标识；为空时仍会上报通用错误
   */
  private fun handleSpeechError(utteranceId: String?) {
    synchronized(activeUtterances) {
      if (utteranceId != null) activeUtterances.remove(utteranceId)
    }
    mutableEvents.tryEmit(SpeechOutputEvent.Error(ERROR_SYNTHESIS))
  }

  /** Android TTS 适配器内部使用的稳定错误代码。 */
  private companion object {
    /** 系统 TTS 服务无法初始化。 */
    const val ERROR_INITIALIZATION = "tts_initialization_failed"

    /** 当前系统 TTS 引擎没有目标语言数据。 */
    const val ERROR_LANGUAGE_UNAVAILABLE = "tts_language_unavailable"

    /** 某个语句在合成或播放前失败。 */
    const val ERROR_SYNTHESIS = "tts_synthesis_failed"
  }
}

/**
 * 初始化阶段暂存的系统播报请求。
 *
 * @property text 已去除首尾空白的播报文字
 * @property queue 是否追加到现有播放队列
 */
private data class PendingSpeechRequest(
  val text: String,
  val queue: Boolean,
)
