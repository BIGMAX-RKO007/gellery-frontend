package com.example.voxmate.ui.avatar

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.voxmate.R
import com.example.voxmate.voice.CallAudioIssue
import com.example.voxmate.voice.CallAudioOutput
import com.example.voxmate.voice.CallAudioRouteState

/**
 * 纯 UI 音频设备和回声风险提示，不直接操作系统音频。
 * @param state 已确认路由或故障状态
 * @param echoEnabled AEC 启用状态，null 时不显示风险
 * @param onRetry 主线程用户重试事件，平台失败由状态展示
 */
@Composable
fun CallAudioNotice(state: CallAudioRouteState, echoEnabled: Boolean?, onRetry: () -> Unit) {
  Column {
    val notice = when {
      state.issue == CallAudioIssue.FOCUS_UNAVAILABLE -> R.string.call_audio_focus_unavailable
      state.issue != null -> R.string.call_audio_route_unavailable
      !state.ready -> R.string.call_audio_connecting
      state.output == CallAudioOutput.HEADSET -> R.string.call_audio_headset
      else -> null
    }
    notice?.let { Text(stringResource(it)) }
    if (state.ready && echoEnabled == false) Text(stringResource(R.string.call_audio_echo_warning))
    if (state.issue != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.call_audio_retry)) }
  }
}
