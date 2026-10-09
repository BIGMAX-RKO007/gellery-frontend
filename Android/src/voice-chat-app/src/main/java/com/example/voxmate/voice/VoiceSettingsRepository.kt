package com.example.voxmate.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户语音偏好设置的数据持久化仓库。
 *
 * 职责：
 * 1. 使用 Android 本地私有 SharedPreferences 持久化用户的语速、音调和音色偏好；
 * 2. 对外暴露可观察的 [StateFlow]，使页面与底层 TTS 引擎能响应式获取最新配置。
 *
 * 线程约束：
 * 读取在实例初始化时同步完成，写入采用异步 apply，可在任意线程安全调用。
 *
 * @param context 用于读取私有偏好存储的 Android 上下文，内部自动转换为 Application Context。
 */
class VoiceSettingsRepository(context: Context) {
  private val appContext = context.applicationContext
  private val preferences =
    appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(loadSettings())

  /** 当前生效的语音配置状态流。 */
  val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

  /**
   * 更新并持久化语音配置。
   *
   * @param newSettings 欲保存的最新语音配置
   */
  fun updateSettings(newSettings: VoiceSettings) {
    preferences.edit()
      .putFloat(KEY_SPEECH_RATE, newSettings.speechRate)
      .putFloat(KEY_PITCH, newSettings.pitch)
      .putString(KEY_VOICE_NAME, newSettings.voiceName)
      .apply()
    _settings.value = newSettings
  }

  /**
   * 重置语音配置为出厂推荐默认值。
   */
  fun resetToDefault() {
    updateSettings(VoiceSettings())
  }

  /**
   * 从持久化存储中读取已保存的配置，不存在时回退到推荐默认值。
   *
   * @return 读取到的 [VoiceSettings] 实例
   */
  private fun loadSettings(): VoiceSettings {
    val rate = preferences.getFloat(KEY_SPEECH_RATE, VoiceSettings.DEFAULT_SPEECH_RATE)
    val pitch = preferences.getFloat(KEY_PITCH, VoiceSettings.DEFAULT_PITCH)
    val voiceName = preferences.getString(KEY_VOICE_NAME, null)
    return VoiceSettings(
      speechRate = rate,
      pitch = pitch,
      voiceName = voiceName,
    )
  }

  private companion object {
    private const val PREFERENCES_NAME = "voxmate_voice_settings"
    private const val KEY_SPEECH_RATE = "speech_rate"
    private const val KEY_PITCH = "pitch"
    private const val KEY_VOICE_NAME = "voice_name"
  }
}
