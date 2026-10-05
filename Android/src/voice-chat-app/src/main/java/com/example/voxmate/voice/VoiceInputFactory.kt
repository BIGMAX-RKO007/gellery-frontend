package com.example.voxmate.voice

import android.content.Context
import com.example.voxmate.speech.SileroSpeechEndpointDetector

/** 应用唯一语音输入装配入口；页面和 AI 协调器不依赖具体录音或 VAD 实现。 */
object VoiceInputFactory {
  /** 创建页面独占的持续音频输入；麦克风和 VAD 都在后台采集线程延迟初始化。 */
  fun create(context: Context): VoiceAudioInput =
    ContinuousVoiceAudioInput(
      detectorFactory = {
        NeuralUtteranceDetector(SileroSpeechEndpointDetector(context.applicationContext))
      }
    )
}
