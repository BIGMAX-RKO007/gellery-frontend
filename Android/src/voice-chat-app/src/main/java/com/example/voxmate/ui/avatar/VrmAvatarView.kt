package com.example.voxmate.ui.avatar

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.example.voxmate.bridge.VrmBridgeController

/**
 * 承载 3D VRM 数字人的 Compose 组件。
 *
 * 职责：
 * 1. 初始化并管理承载 WebGL 数字人的硬件加速 WebView。
 * 2. 通过 WebViewAssetLoader 安全、高效地加载打包好的离线前端资产。
 * 3. 将 VrmBridgeController 暴露给上层调用者，用于实施唇形同步与表情变化。
 *
 * @param modifier Composable 修饰符。
 * @param onControllerReady 控制器初始化完毕后的回调，供上层控制数字人。
 * @param onAvatarReady 3D 模型完全加载解析并显示后的回调。
 * @param onAvatarClicked 用户触摸点击数字人时的交互回调。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VrmAvatarView(
  modifier: Modifier = Modifier,
  onControllerReady: (VrmBridgeController) -> Unit = {},
  onAvatarReady: () -> Unit = {},
  onAvatarClicked: () -> Unit = {},
) {
  val context = LocalContext.current

  // 配置 Google 官方推荐的 WebViewAssetLoader，支持通过 https 域安全离线加载 assets 资源
  val assetLoader = remember {
    WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", AssetsPathHandler(context))
      .build()
  }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      WebView(ctx).apply {
        // 设置与卡片一致的白色背景，消除 GPU 混合黑屏/白屏问题
        setBackgroundColor(Color.WHITE)

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          mediaPlaybackRequiresUserGesture = false
          cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        clearCache(true)

        val bridgeController = VrmBridgeController(
          webView = this,
          onAvatarReadyCallback = onAvatarReady,
          onAvatarClickedCallback = onAvatarClicked,
        )

        // 注入 JS 桥接对象
        addJavascriptInterface(bridgeController.getJsInterface(), "AndroidBridge")

        WebView.setWebContentsDebuggingEnabled(true)

        webChromeClient = object : android.webkit.WebChromeClient() {
          override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
            consoleMessage?.let {
              android.util.Log.d("VrmAvatar", "[Web] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
            }
            return true
          }
        }

        webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
          ): WebResourceResponse? {
            return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
          }
        }

        // 加载打包在 assets/vrm-web 中的入口静态网页
        loadUrl("https://appassets.androidplatform.net/assets/vrm-web/index.html")

        onControllerReady(bridgeController)
      }
    },
    update = {
      // 可以在此处响应外部状态更新
    },
  )
}
