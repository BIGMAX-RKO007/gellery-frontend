package com.example.voxmate.ui.avatar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voxmate.R

/**
 * 数字人首页的圆形采音指示器，只绘制状态并上报重试事件，不拥有麦克风。
 * @param listening 麦克风已开启
 * @param userSpeaking VAD 正在接收用户发言
 * @param busy 模型准备或推理中
 * @param speaking AI 播报中
 * @param onClick 由页面处理权限申请或重试，不作为开始通话的必要步骤
 */
@Composable
fun CallAudioButton(
  listening: Boolean,
  userSpeaking: Boolean,
  busy: Boolean,
  speaking: Boolean,
  onClick: () -> Unit,
) {
  /** 动效仅表达采音活动，并不冒充真实声压计。 */
  val transition = rememberInfiniteTransition(label = "call-wave")
  val pulse by transition.animateFloat(
    initialValue = 0.25f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
    label = "call-wave-height"
  )
  val description = stringResource(
    if (listening) R.string.call_listening_description else R.string.call_retry
  )
  val active = userSpeaking || speaking
  val color = when {
    busy -> Color(0xFFE59B2F)
    listening -> Color(0xFF2557D6)
    else -> Color(0xFF64748B)
  }
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = color.copy(alpha = 0.12f),
    modifier = Modifier.size(56.dp).semantics { contentDescription = description },
  ) {
    Canvas(Modifier.size(56.dp)) {
      repeat(5) { index ->
        val profile = 1f - kotlin.math.abs(index - 2) * 0.25f
        val halfHeight = size.height * 0.25f * profile *
          (if (active) pulse else if (busy) pulse * 0.65f else 0.3f)
        val x = size.width * (0.3f + index * 0.1f)
        drawLine(
          color, Offset(x, center.y - halfHeight), Offset(x, center.y + halfHeight),
          strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
        )
      }
    }
  }
}
