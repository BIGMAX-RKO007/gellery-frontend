package com.example.voxmate.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android 12+ 通信路由和音频焦点适配器；页面独占，不改变用户设置的音量。
 * @param context 仅用于获取应用级 AudioManager，不保存 Activity
 * 所有公开方法和系统回调在主线程执行，close 后不能再次启用。
 */
class AndroidCallAudioRoute(context: Context) : CallAudioRoute {
  /** 应用级系统服务，不持有页面 Context。 */
  private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
  /** 回调和超时统一串行到主线程。 */
  private val handler = Handler(Looper.getMainLooper())
  /** 可观察路由状态，音频故障不能静默继续播报。 */
  private val mutableState = MutableStateFlow(CallAudioRouteState())
  /** 供页面连接录音、播报和错误显示的状态。 */
  override val state = mutableState.asStateFlow()
  /** 页面仍要求音频，为真时临时丢失焦点后允许恢复。 */
  private var requested = false
  /** 是否已永久释放，避免晚到系统回调重新申请音频。 */
  private var closed = false
  /** 本实例是否获得焦点，失去时不得启动麦克风。 */
  private var focused = false
  /** 本实例已注册系统监听器，保证释放只执行一次。 */
  private var registered = false
  /** 通信监听单独记录，注册中途失败也能释放已成功注册的监听。 */
  private var communicationRegistered = false
  /** 是否持有设备请求，用于清理自己的路由而非覆盖其他应用。 */
  private var deviceRequested = false
  /** 本实例设置通信模式之前的系统模式。 */
  private var previousMode: Int? = null
  /** 当前期望设备 ID，异步确认时不反复提交选择。 */
  private var targetId: Int? = null
  /** 等待实际设备确认最多 5 秒；蓝牙异步连接期间保持暂停。 */
  private val timeout = Runnable {
    if (requested && focused && !mutableState.value.ready) {
      mutableState.value = CallAudioRouteState(issue = CallAudioIssue.ROUTE_UNAVAILABLE)
    }
  }
  /** 音频焦点监听；duck 也暂停，避免 AI 和其他声音竞争。 */
  private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    if (!requested || closed) return@OnAudioFocusChangeListener
    when (change) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        focused = true
        configureRoute()
      }
      else -> {
        focused = false
        handler.removeCallbacks(timeout)
        mutableState.value = CallAudioRouteState(issue = CallAudioIssue.FOCUS_UNAVAILABLE)
        // 焦点已经属于其他应用，不在此时重设全局音频模式。
      }
    }
  }
  /** 页面稳定持有的语音焦点，不按 TTS 的每个短句重复申请。 */
  private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    .setAudioAttributes(AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    .setWillPauseWhenDucked(true)
    .setOnAudioFocusChangeListener(focusListener, handler)
    .build()
  /** 耳机插拔后重新运行隐私优先策略，回调不保存设备名称。 */
  private val devicesCallback = object : AudioDeviceCallback() {
    /** 新输出可用时选择耳机；参数只用于系统通知，实际清单重新读取。 */
    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { configureRoute() }
    /** 输出断开后回退内置扬声器，不丢弃麦克风前滚数据。 */
    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { configureRoute() }
  }
  /** 实际设备变化监听，仅确认系统结果，不循环抢占别的应用的路由。 */
  private val communicationListener = AudioManager.OnCommunicationDeviceChangedListener {
    if (requested && focused && !closed) publishActualRoute()
  }

  /** 主线程按页面前台状态申请或释放；失败通过 state 发布，不抛到 Compose。 */
  override fun setEnabled(enabled: Boolean) {
    check(Looper.myLooper() == Looper.getMainLooper())
    if (closed || requested == enabled) return
    requested = enabled
    if (!enabled) {
      releaseOwnership()
      mutableState.value = CallAudioRouteState()
      return
    }
    try {
      if (manager.mode == AudioManager.MODE_IN_CALL || manager.mode == AudioManager.MODE_RINGTONE) {
        mutableState.value = CallAudioRouteState(issue = CallAudioIssue.FOCUS_UNAVAILABLE)
        return
      }
      if (!registered) {
        manager.registerAudioDeviceCallback(devicesCallback, handler)
        registered = true
        manager.addOnCommunicationDeviceChangedListener({ task -> handler.post(task) }, communicationListener)
        communicationRegistered = true
      }
      focused = manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      if (focused) configureRoute()
      else mutableState.value = CallAudioRouteState(issue = CallAudioIssue.FOCUS_UNAVAILABLE)
    } catch (error: RuntimeException) {
      Log.w(TAG, "申请通话音频失败", error)
      releaseOwnership()
      mutableState.value = CallAudioRouteState(issue = CallAudioIssue.ROUTE_UNAVAILABLE)
    }
  }

  /** 用户主动重试；已正常工作的路由不重复切换。 */
  override fun retry() {
    if (!requested || closed || mutableState.value.ready) return
    setEnabled(false)
    setEnabled(true)
  }

  /** 主线程释放自己的焦点、设备与监听器，已关闭时重复调用无副作用。 */
  override fun close() {
    if (closed) return
    setEnabled(false)
    closed = true
  }

  /** 主线程选择目标设备并等待实际确认；平台异常转为可观察失败状态。 */
  private fun configureRoute() {
    if (!requested || !focused || closed) return
    try {
      if (previousMode == null) previousMode = manager.mode
      manager.mode = AudioManager.MODE_IN_COMMUNICATION
      val available = manager.availableCommunicationDevices
      val target = CallAudioRoutePolicy.select(
        available.map { CallAudioDevice(it.id, classify(it.type)) }, manager.communicationDevice?.id,
      )
      if (target == null) {
        mutableState.value = CallAudioRouteState(issue = CallAudioIssue.ROUTE_UNAVAILABLE)
        return
      }
      if (targetId != target.id || !deviceRequested) {
        targetId = target.id
        mutableState.value = CallAudioRouteState()
        deviceRequested = manager.setCommunicationDevice(available.first { it.id == target.id })
        handler.removeCallbacks(timeout)
        if (!deviceRequested) {
          mutableState.value = CallAudioRouteState(issue = CallAudioIssue.ROUTE_UNAVAILABLE)
          return
        }
        handler.postDelayed(timeout, 5_000L)
      }
      publishActualRoute()
    } catch (error: RuntimeException) {
      Log.w(TAG, "选择通话设备失败", error)
      mutableState.value = CallAudioRouteState(issue = CallAudioIssue.ROUTE_UNAVAILABLE)
    }
  }

  /** 校验实际设备与目标一致，只有确认成功才允许播放和采音；异常通过状态报告。 */
  private fun publishActualRoute() {
    try {
      val device = manager.communicationDevice
      val confirmed = focused && deviceRequested && device?.id == targetId
      if (confirmed) handler.removeCallbacks(timeout)
      else if (!handler.hasCallbacks(timeout)) handler.postDelayed(timeout, 5_000L)
      mutableState.value = CallAudioRouteState(
        ready = confirmed,
        output = device?.let { classify(it.type) },
      )
      Log.d(TAG, "通话路由 confirmed=$confirmed type=${device?.type}")
    } catch (error: RuntimeException) {
      Log.w(TAG, "读取通话设备失败", error)
      mutableState.value = CallAudioRouteState(issue = CallAudioIssue.ROUTE_UNAVAILABLE)
    }
  }

  /** 主线程释放申请，恢复模式仅在仍持有焦点时执行，避免影响抢占音频的其他通话。 */
  private fun releaseOwnership() {
    handler.removeCallbacks(timeout)
    safelyRelease { if (registered) manager.unregisterAudioDeviceCallback(devicesCallback) }
    safelyRelease { if (communicationRegistered) manager.removeOnCommunicationDeviceChangedListener(communicationListener) }
    safelyRelease { if (deviceRequested) manager.clearCommunicationDevice() }
    safelyRelease {
      if (focused && previousMode != null && manager.mode == AudioManager.MODE_IN_COMMUNICATION) {
        manager.mode = requireNotNull(previousMode)
      }
    }
    safelyRelease { manager.abandonAudioFocusRequest(focusRequest) }
    registered = false
    communicationRegistered = false
    deviceRequested = false
    focused = false
    targetId = null
    previousMode = null
  }

  /**
   * 独立清理每项资源，单项平台异常不会跳过剩余释放。
   * @param action 主线程系统释放操作；失败记录日志，不向页面抛异常
   */
  private fun safelyRelease(action: () -> Unit) {
    try {
      action()
    } catch (error: RuntimeException) {
      Log.w(TAG, "释放通话音频失败", error)
    }
  }

  /**
   * 映射系统设备类型，不查询蓝牙名称或声纹。
   * @param type AudioDeviceInfo 类型常量
   * @return UI 和纯策略使用的输出分类，未知类型不主动选用
   */
  private fun classify(type: Int): CallAudioOutput = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallAudioOutput.SPEAKER
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CallAudioOutput.EARPIECE
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID -> CallAudioOutput.HEADSET
    else -> CallAudioOutput.OTHER
  }

  /** 不包含对话内容和设备名称的诊断标签。 */
  private companion object { const val TAG = "CallAudioRoute" }
}
