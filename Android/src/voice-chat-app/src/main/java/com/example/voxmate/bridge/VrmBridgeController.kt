package com.example.voxmate.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.lang.ref.WeakReference

/**
 * VRM 数字人 Native 与 Web 端通信控制器。
 *
 * 职责：
 * 1. 向内嵌 WebView 执行 JavaScript 指令（如实时嘴型开合、切换情绪表情等）。
 * 2. 接收来自 Web 端的事件回调（如模型加载就绪、用户触摸交互等）。
 *
 * 生命周期由持有它的 Composable 或 Activity 管理。
 */
class VrmBridgeController(
  webView: WebView,
  private val onAvatarReadyCallback: (() -> Unit)? = null,
  private val onAvatarClickedCallback: (() -> Unit)? = null,
) {
  /**
   * 弱引用持有 WebView，防止内存泄露。
   */
  private val webViewRef: WeakReference<WebView> = WeakReference(webView)

  /**
   * 主线程 Handler，确保 evaluateJavascript 在主线程执行。
   */
  private val mainHandler: Handler = Handler(Looper.getMainLooper())

  /**
   * 触发数字人张嘴说话（唇形同步）。
   *
   * @param volume 嘴型张开幅度，有效范围为 0.0f（完全闭合）到 1.0f（最大张开）。
   */
  fun speak(volume: Float) {
    val clamped = volume.coerceIn(0.0f, 1.0f)
    runJs("window.avatarController && window.avatarController.speak($clamped);")
  }

  /**
   * 设置数字人面部表情。
   *
   * @param expression 表情标识，如 "happy", "angry", "sad", "relaxed", "surprised", "neutral"。
   */
  fun setExpression(expression: String) {
    runJs("window.avatarController && window.avatarController.setExpression('$expression');")
  }

  /**
   * 重置表情至自然待机状态。
   */
  fun resetExpression() {
    runJs("window.avatarController && window.avatarController.resetExpression();")
  }

  /**
   * 安全执行 JavaScript 代码块。
   *
   * @param script 欲在 WebView 中执行的 JS 脚本字符串。
   */
  private fun runJs(script: String) {
    mainHandler.post {
      webViewRef.get()?.evaluateJavascript(script, null)
    }
  }

  /**
   * 注入到 Web 端 window.AndroidBridge 的接口宿主。
   */
  inner class JsInterface {
    /**
     * Web 端 3D 模型加载并解析就绪后回调。
     */
    @JavascriptInterface
    fun onAvatarReady() {
      mainHandler.post {
        onAvatarReadyCallback?.invoke()
      }
    }

    /**
     * 用户点击/触摸 3D 数字人画布后回调。
     */
    @JavascriptInterface
    fun onAvatarClicked() {
      mainHandler.post {
        onAvatarClickedCallback?.invoke()
      }
    }
  }

  /**
   * 获取供 WebView.addJavascriptInterface 使用的接口实例。
   */
  fun getJsInterface(): JsInterface = JsInterface()
}
