package com.example.voxmate.ui.avatar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 通话期间实体音量键调节通话声音，退出后恢复原绑定，不强行提高系统音量。
 * @param enabled 页面在前台且通信路由已确认；仅在 Compose 主线程使用
 */
@Composable
fun CallAudioVolumeKeys(enabled: Boolean) {
  /** 页面上下文不传给音频服务。 */
  val context = LocalContext.current
  /** 页面生命周期内的宿主，预览可能为 null。 */
  val activity = remember(context) { findActivity(context) }
  DisposableEffect(activity, enabled) {
    val previous = activity?.volumeControlStream
    if (enabled) activity?.volumeControlStream = AudioManager.STREAM_VOICE_CALL
    onDispose {
      if (enabled && previous != null && activity?.volumeControlStream == AudioManager.STREAM_VOICE_CALL) {
        activity.volumeControlStream = previous
      }
    }
  }
}

/**
 * 查找宿主，防止 ContextWrapper 自引用死循环。
 * @param context 页面上下文
 * @return Activity，应用或预览环境返回 null；不修改上下文
 */
private fun findActivity(context: Context): Activity? {
  var current = context
  while (current is ContextWrapper) {
    if (current is Activity) return current
    val next = current.baseContext
    if (next === current) return null
    current = next
  }
  return null
}
