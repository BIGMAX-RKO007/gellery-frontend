package com.example.voxmate.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voxmate.R
import com.example.voxmate.voice.VoiceOption
import com.example.voxmate.voice.VoiceSettings
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 声音与语速自定义底部设置抽屉。
 *
 * 供用户微调文字转语音的播报语速、音调高低，并自主挑选设备已安装的高保真音色，
 * 且支持即时试听与一键直达系统 TTS 设置以扩展语音包。
 *
 * @param settings 当前生效的语音配置状态
 * @param availableVoices 当前系统 TTS 引擎检测到的可用音色列表
 * @param onSettingsChanged 用户调节参数时的回调
 * @param onPreviewSpeech 点击试听按钮时的发音回调，传入示例文本
 * @param onResetDefaults 恢复推荐默认值回调
 * @param onDismiss 关闭底部抽屉回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(
  settings: VoiceSettings,
  availableVoices: List<VoiceOption>,
  onSettingsChanged: (VoiceSettings) -> Unit,
  onPreviewSpeech: (String) -> Unit,
  onResetDefaults: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sampleText = stringResource(R.string.voice_settings_preview_sample)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color(0xFF1E1F20),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(top = 10.dp, bottom = 4.dp)
          .size(width = 40.dp, height = 4.dp)
          .clip(CircleShape)
          .background(Color(0xFF475569)),
      )
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
      // 1. 顶部标题栏
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = stringResource(R.string.voice_settings_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = stringResource(R.string.voice_settings_subtitle),
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
          )
        }
        TextButton(
          onClick = onResetDefaults,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Text(
            text = stringResource(R.string.voice_settings_reset),
            fontSize = 12.sp,
            color = Color(0xFF93C5FD),
          )
        }
      }

      Spacer(Modifier.height(14.dp))
      HorizontalDivider(color = Color(0xFF334155), thickness = 0.8.dp)
      Spacer(Modifier.height(14.dp))

      LazyColumn(
        modifier = Modifier.weight(weight = 1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // 2. 播报语速滑块
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF282A2C)),
            border = BorderStroke(1.dp, Color(0xFF3F4246)),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = stringResource(R.string.voice_settings_speech_rate),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White,
                )
                Text(
                  text = String.format(Locale.getDefault(), "%.2fx", settings.speechRate),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF60A5FA),
                )
              }

              Spacer(Modifier.height(6.dp))

              Slider(
                value = settings.speechRate,
                onValueChange = { raw ->
                  val stepped = (raw * 20).roundToInt() / 20f
                  onSettingsChanged(settings.copy(speechRate = stepped))
                },
                valueRange = VoiceSettings.MIN_SPEECH_RATE..VoiceSettings.MAX_SPEECH_RATE,
                colors = SliderDefaults.colors(
                  thumbColor = Color(0xFF3B82F6),
                  activeTrackColor = Color(0xFF3B82F6),
                  inactiveTrackColor = Color(0xFF4B5563),
                ),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = stringResource(R.string.voice_settings_rate_slow),
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8),
                )
                Text(
                  text = stringResource(R.string.voice_settings_rate_natural),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFF60A5FA),
                )
                Text(
                  text = stringResource(R.string.voice_settings_rate_fast),
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8),
                )
              }
            }
          }
        }

        // 3. 发声音调滑块
        item {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF282A2C)),
            border = BorderStroke(1.dp, Color(0xFF3F4246)),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = stringResource(R.string.voice_settings_pitch),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White,
                )
                Text(
                  text = String.format(Locale.getDefault(), "%.2fx", settings.pitch),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF60A5FA),
                )
              }

              Spacer(Modifier.height(6.dp))

              Slider(
                value = settings.pitch,
                onValueChange = { raw ->
                  val stepped = (raw * 20).roundToInt() / 20f
                  onSettingsChanged(settings.copy(pitch = stepped))
                },
                valueRange = VoiceSettings.MIN_PITCH..VoiceSettings.MAX_PITCH,
                colors = SliderDefaults.colors(
                  thumbColor = Color(0xFF3B82F6),
                  activeTrackColor = Color(0xFF3B82F6),
                  inactiveTrackColor = Color(0xFF4B5563),
                ),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = stringResource(R.string.voice_settings_pitch_deep),
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8),
                )
                Text(
                  text = stringResource(R.string.voice_settings_pitch_natural),
                  fontSize = 11.sp,
                  color = Color(0xFF94A3B8),
                )
                Text(
                  text = stringResource(R.string.voice_settings_pitch_bright),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFF60A5FA),
                )
              }
            }
          }
        }

        // 4. 音色偏好选择列表
        item {
          Text(
            text = stringResource(R.string.voice_settings_voice_selection),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
          )
        }

        // 默认自动优选项
        item {
          VoiceOptionItem(
            title = stringResource(R.string.voice_settings_voice_auto),
            subtitle = stringResource(R.string.voice_settings_voice_auto_desc),
            isSelected = settings.voiceName == null,
            badge = "推荐",
            badgeColor = Color(0xFF2563EB),
            onClick = { onSettingsChanged(settings.copy(voiceName = null)) },
          )
        }

        // 系统识别到的音色列表
        items(availableVoices, key = { it.name }) { voice ->
          val isSelected = settings.voiceName == voice.name
          VoiceOptionItem(
            title = voice.displayName,
            subtitle = voice.name,
            isSelected = isSelected,
            badge = if (voice.isHighQuality) stringResource(R.string.voice_settings_tag_hq) else null,
            badgeColor = if (voice.isHighQuality) Color(0xFF059669) else Color.Gray,
            onClick = { onSettingsChanged(settings.copy(voiceName = voice.name)) },
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      // 5. 试听按钮与系统设置入口
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = { onPreviewSpeech(sampleText) },
          modifier = Modifier.weight(1f).height(46.dp),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2557D6)),
        ) {
          Text("🔊 " + stringResource(R.string.voice_settings_preview), fontSize = 14.sp)
        }

        OutlinedButton(
          onClick = {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
              action = "com.android.settings.TTS_SETTINGS"
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
              context.startActivity(intent)
            } catch (_: Exception) {
              // 备选通用无障碍设置入口
              context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
              })
            }
          },
          modifier = Modifier.height(46.dp),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF475569)),
        ) {
          Text("⚙️ 系统TTS", fontSize = 13.sp, color = Color(0xFFCBD5E1))
        }
      }
    }
  }
}

/**
 * 单个音色偏好单选卡片。
 */
@Composable
private fun VoiceOptionItem(
  title: String,
  subtitle: String,
  isSelected: Boolean,
  badge: String? = null,
  badgeColor: Color = Color(0xFF2563EB),
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = if (isSelected) Color(0xFF2557D6).copy(alpha = 0.15f) else Color(0xFF282A2C),
    border = BorderStroke(
      width = 1.dp,
      color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF3F4246),
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(
        selected = isSelected,
        onClick = onClick,
        colors = RadioButtonDefaults.colors(
          selectedColor = Color(0xFF3B82F6),
          unselectedColor = Color(0xFF64748B),
        ),
      )

      Spacer(Modifier.width(8.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
          )
          if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = badgeColor.copy(alpha = 0.25f),
            ) {
              Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
              )
            }
          }
        }
        Spacer(Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 11.sp,
          color = Color(0xFF94A3B8),
          maxLines = 1,
        )
      }
    }
  }
}
