package com.example.voxmate.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 通信设备策略测试，不依赖真实 AudioManager 或手机。 */
class CallAudioRoutePolicyTest {
  /** 无耳机时选择扬声器，不选默认听筒。 */
  @Test fun speakerOverridesEarpiece() {
    assertEquals(2, CallAudioRoutePolicy.select(listOf(
      CallAudioDevice(1, CallAudioOutput.EARPIECE), CallAudioDevice(2, CallAudioOutput.SPEAKER)
    ), 1)?.id)
  }
  /** 插入耳机后优先保护私密通话。 */
  @Test fun headsetOverridesSpeaker() {
    assertEquals(3, CallAudioRoutePolicy.select(listOf(
      CallAudioDevice(2, CallAudioOutput.SPEAKER), CallAudioDevice(3, CallAudioOutput.HEADSET)
    ), 2)?.id)
  }
  /** 多耳机环境保持当前设备，避免无必要切换。 */
  @Test fun currentHeadsetIsPreserved() {
    assertEquals(4, CallAudioRoutePolicy.select(listOf(
      CallAudioDevice(3, CallAudioOutput.HEADSET), CallAudioDevice(4, CallAudioOutput.HEADSET)
    ), 4)?.id)
  }
  /** 拔出耳机后回到扬声器。 */
  @Test fun unplugFallsBackToSpeaker() {
    assertEquals(2, CallAudioRoutePolicy.select(listOf(CallAudioDevice(2, CallAudioOutput.SPEAKER)), 3)?.id)
  }
  /** 不支持目标设备时报告失败，不静默使用听筒。 */
  @Test fun unsupportedDevicesReturnNull() {
    assertNull(CallAudioRoutePolicy.select(listOf(CallAudioDevice(1, CallAudioOutput.EARPIECE)), 1))
  }
}
