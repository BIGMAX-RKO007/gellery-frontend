# VoxMate 首页亮屏与状态提示

日期：2026-10-05。仅修改 voice-chat-app，原 Gallery 不变。

- CallAudioNotice 不再渲染正常状态的“扬声器通话”文字，不影响扬声器路由。耳机、连接中、音频故障、回声风险与重试提示保留。
- HomeScreenAwake 独立封装首页窗口行为，前台使用 view.keepScreenOn 防止无操作超时息屏及息屏前变暗，不需 WAKE_LOCK 权限。
- 亮度在进入首页时固定：优先保留已有窗口显式亮度，否则读取系统 SCREEN_BRIGHTNESS 配置（读取缺失默认 128/255）。这是系统配置值，不是显示面板实时测量亮度，自动亮度设备进入时可能有亮度变化。仅修改当前窗口，不写全局设置，也不强制最高亮度。
- 切后台、离开首页或销毁时恢复原保活状态和窗口亮度；模型尚未加载及文字模式下首页也保持亮屏。
- 不阻止电源键锁屏，不绕过系统过热保护、厂商强制调光或省电策略；持续亮屏会增加耗电。

验证：Android/src 执行 gradlew.bat :voice-chat-app:testDebugUnitTest :voice-chat-app:assembleDebug --offline。
真机验收：等待超过系统息屏时长、自动亮度环境变化、进入模型管理页、切后台、返回首页及手动锁屏。
APK：Android/src/voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk。
