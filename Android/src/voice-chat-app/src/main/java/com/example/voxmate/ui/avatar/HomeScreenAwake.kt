package com.example.voxmate.ui.avatar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * 首页前台保持亮屏并固定窗口亮度，退出后恢复；不修改全局设置，不阻止用户手动锁屏。
 * @param enabled 首页处于前台时为真，必须在 Compose 主线程调用
 */
@Composable
fun HomeScreenAwake(enabled: Boolean) {
  /** 当前宿主视图，保活标记仅由本页面临时持有。 */
  val view = LocalView.current
  /** 当前上下文，仅在页面生命周期内使用。 */
  val context = LocalContext.current
  /** 宿主窗口，预览环境可能没有 Activity。 */
  val activity = remember(context) { homeActivity(context) }
  DisposableEffect(view, activity, enabled) {
    /** 原视图状态，离开时还原，避免影响其他页面。 */
    val previousAwake = view.keepScreenOn
    /** 原窗口亮度；负数代表跟随系统。 */
    val previousBrightness = activity?.window?.attributes?.screenBrightness
    if (enabled) {
      view.keepScreenOn = true
      activity?.window?.let { window ->
        val attributes = window.attributes
        // 跟随系统时读取当前配置作为本页固定值，不把音视频页面强制提到最高亮度。
        attributes.screenBrightness = if (previousBrightness != null && previousBrightness >= 0f) {
          previousBrightness
        } else {
          (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
            .coerceIn(0.01f, 1f)
        }
        window.attributes = attributes
      }
    }
    onDispose {
      if (enabled) {
        view.keepScreenOn = previousAwake
        activity?.window?.let { window ->
          if (previousBrightness != null) {
            val attributes = window.attributes
            attributes.screenBrightness = previousBrightness
            window.attributes = attributes
          }
        }
      }
    }
  }
}

/**
 * 从包装上下文查找页面宿主，不持有全局 Activity 引用。
 * @param context Compose 的页面上下文
 * @return 宿主 Activity，预览和应用上下文返回 null，无系统副作用
 */
private fun homeActivity(context: Context): Activity? {
  var current = context
  while (current is ContextWrapper) {
    if (current is Activity) return current
    val base = current.baseContext
    if (base === current) return null
    current = base
  }
  return null
}
