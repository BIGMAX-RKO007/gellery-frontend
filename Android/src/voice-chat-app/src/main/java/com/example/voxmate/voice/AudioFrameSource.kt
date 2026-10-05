package com.example.voxmate.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 可替换的实时 PCM 来源；每个实例仅运行一次，采集结束由实现释放麦克风。 */
interface AudioFrameSource {
  /** 采集 16 kHz 单声道 20ms 浮点帧；异常传播，调用方必须预先取得权限。 */
  suspend fun capture(onReady: suspend (Boolean) -> Unit, onFrame: suspend (FloatArray) -> Unit)
  /** 任意线程立即请求停麦，不保存或上传录音。 */
  fun stop()
}

/** 使用通话音源采集 PCM，尽可能启用设备 AEC；实例由单轮监听任务持有。 */
class AndroidAudioFrameSource : AudioFrameSource {
  /** 跨线程停止位，阻止停止后的异步初始化重新打开麦克风。 */
  private val stopped = AtomicBoolean(false)
  /** 保护跨线程停止与最终释放之间的竞态。 */
  private val lock = Any()
  /** 正在录音的系统实例，只在同步块中发布或清理。 */
  private var recorder: AudioRecord? = null

  /** IO 线程录音并回调完整 320 样本帧；所有音效在 finally 释放。 */
  @SuppressLint("MissingPermission")
  override suspend fun capture(onReady: suspend (Boolean) -> Unit, onFrame: suspend (FloatArray) -> Unit) =
    withContext(Dispatchers.IO) {
      if (stopped.get()) return@withContext
      val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
      check(minimum > 0) { "Invalid microphone buffer" }
      val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16_000,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 640 * 8))
      var echo: AcousticEchoCanceler? = null
      var noise: NoiseSuppressor? = null
      try {
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
        // 厂商可能声明支持但拒绝创建音效，记录失败后继续录音并向 UI 报告 AEC 状态。
        try {
          if (AcousticEchoCanceler.isAvailable()) {
            echo = AcousticEchoCanceler.create(record.audioSessionId)
            echo?.enabled = true
          }
          if (NoiseSuppressor.isAvailable()) {
            noise = NoiseSuppressor.create(record.audioSessionId)
            noise?.enabled = true
          }
        } catch (error: RuntimeException) {
          Log.w("VoiceCapture", "Audio effect unavailable", error)
        }
        synchronized(lock) {
          if (!stopped.get()) { recorder = record; record.startRecording() }
        }
        if (stopped.get()) return@withContext
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone start failed" }
        onReady(echo?.enabled == true)
        val buffer = ShortArray(320)
        var offset = 0
        while (!stopped.get()) {
          currentCoroutineContext().ensureActive()
          val count = record.read(buffer, offset, buffer.size - offset, AudioRecord.READ_BLOCKING)
          if (stopped.get()) break
          check(count > 0) { "Microphone read failed: $count" }
          offset += count
          if (offset == buffer.size) {
            onFrame(FloatArray(buffer.size) { buffer[it] / 32768f })
            offset = 0
          }
        }
      } finally {
        synchronized(lock) {
          recorder = null
          if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
          echo?.release()
          noise?.release()
          record.release()
        }
      }
    }

  /** 尽快解除阻塞读取；停止失败记录诊断，采集任务仍会在 finally 释放。 */
  override fun stop() {
    stopped.set(true)
    synchronized(lock) {
      try {
        if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder?.stop()
      } catch (error: IllegalStateException) {
        Log.w("VoiceCapture", "Microphone already stopped", error)
      }
    }
  }
}
