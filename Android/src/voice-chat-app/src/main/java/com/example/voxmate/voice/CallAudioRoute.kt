package com.example.voxmate.voice

import kotlinx.coroutines.flow.StateFlow

/** 电话式对话的输出分类，不暴露 Android 设备对象，供路由策略和 UI 使用。 */
enum class CallAudioOutput {
  /** 手机内置扬声器，未接耳机时的默认选择。 */
  SPEAKER,
  /** 有线、USB 或蓝牙通信耳机，优先保护对话隐私。 */
  HEADSET,
  /** 听筒，不能作为默认免提输出。 */
  EARPIECE,
  /** 未分类的通信设备，不主动选用。 */
  OTHER,
}

/** 路由失败分类，实际文案由 UI 资源提供，不记录设备名称或音频内容。 */
enum class CallAudioIssue {
  /** 音频焦点被其他播放或电话占用。 */
  FOCUS_UNAVAILABLE,
  /** 无目标设备、系统拒绝选择或异步路由超时。 */
  ROUTE_UNAVAILABLE,
}

/**
 * 通话音频状态快照；路由确认前不得启动录音和播报。
 * @property ready 焦点已取得且实际输出匹配目标设备
 * @property output 实际输出分类，尚未确认时为空
 * @property issue 可重试错误；正常或等待路由确认时为空
 */
data class CallAudioRouteState(
  val ready: Boolean = false,
  val output: CallAudioOutput? = null,
  val issue: CallAudioIssue? = null,
)

/** 页面独占的音频路由所有者；主线程使用，离开页面必须 close，允许未来替换实现。 */
interface CallAudioRoute {
  /** 只读路由与焦点状态，不持有 Activity 或录音资源。 */
  val state: StateFlow<CallAudioRouteState>
  /** 按前台通话生命周期申请或释放路由；重复相同值不重复申请。 */
  fun setEnabled(enabled: Boolean)
  /** 用户主动重试失败的音频申请，不修改系统音量。 */
  fun retry()
  /** 永久释放设备选择、音频焦点和监听器，重复调用安全。 */
  fun close()
}

/**
 * 可独立测试的免提选择策略，不依赖 Android 路由对象。
 * @property id 系统设备 ID，只在本轮路由中比较，不持久化
 * @property output 设备输出分类
 */
data class CallAudioDevice(val id: Int, val output: CallAudioOutput)

/** 确定性设备选择策略；当前耳机优先，再其他可用耳机，最后内置扬声器。 */
object CallAudioRoutePolicy {
  /**
   * 选择免提或耳机，不静默降级为小音量听筒。
   * @param devices 系统当前可用通信输出，顺序来自平台
   * @param currentId 当前实际设备 ID
   * @return 目标设备；只有听筒或不支持的设备时为空
   */
  fun select(devices: List<CallAudioDevice>, currentId: Int?): CallAudioDevice? =
    devices.firstOrNull { it.id == currentId && it.output == CallAudioOutput.HEADSET }
      ?: devices.firstOrNull { it.output == CallAudioOutput.HEADSET }
      ?: devices.firstOrNull { it.output == CallAudioOutput.SPEAKER }
}
