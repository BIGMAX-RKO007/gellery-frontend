# Android 内置 Skills 机制与功能说明

分析日期：2026-09-28

## 1. 核心结论

`Android/src/app/src/main/assets/skills` 里的内容不是新的 AI 模型，也不是 Gradle 功能模块。它们是随 APK 一起打包的“智能体技能包”，主要用于告诉 Gemma：

- 什么情况下应该使用该技能。
- 使用技能时应该遵循哪些步骤。
- 应该调用 `run_js` 还是 `run_intent`。
- 调用工具时应该传递哪些参数。
- 最终结果应该如何展示。

一个技能目录通常由以下内容组成：

```text
skill-name/
├── SKILL.md          技能名称、描述和给模型的操作说明
├── scripts/          可由 run_js 执行的 HTML/JavaScript
└── assets/           WebView 页面、图片等附属资源
```

只有 `SKILL.md` 也可以成为技能。例如 `kitchen-adventure` 只改变模型的对话角色和输出格式，不执行 Android API 或 JavaScript。

## 2. 技能执行流程

```text
App 启动 Agent Skills
  ↓
SkillManager 扫描 assets/skills/*/SKILL.md
  ↓
把已启用技能的名称和简短描述放入系统提示词
  ↓
用户提出请求
  ↓
Gemma 选择匹配的技能并调用 load_skill
  ↓
load_skill 把完整 SKILL.md 指令返回给 Gemma
  ↓
Gemma 按指令执行
  ├── 直接生成文字
  ├── run_js：在隐藏 WebView 中运行 HTML/JavaScript
  └── run_intent：调用 Android 系统能力
  ↓
结果以文字、图片或嵌入式 WebView 显示在聊天中
```

这种设计采用“按需加载”：系统提示词一开始只放技能名称和描述，等模型确定需要某个技能后，才通过 `load_skill` 获取完整说明，避免所有长指令同时占用上下文。

关键代码：

- `skills/SkillManager.kt`：发现、解析、启用、禁用、导入和持久化技能。
- `skills/SkillExtensions.kt`：生成本地脚本 URL，并格式化技能列表。
- `tools/LoadSkillTool.kt`：把完整技能说明交给模型。
- `tools/RunJsTool.kt`：请求页面中的 WebView 执行技能脚本。
- `tools/RunIntentTool.kt`：调用 Android Intent 和系统能力。
- `customtasks/agentchat/AgentChatTaskModule.kt`：规定智能体先选技能、再加载说明、再执行。
- `customtasks/agentchat/AgentChatScreen.kt`：运行 JavaScript并把图片/WebView 结果插入聊天。

## 3. 每个内置技能的作用

| 技能目录 | 作用 | 实现方式 | 联网/权限说明 |
| --- | --- | --- | --- |
| `calculate-hash` | 计算一段文字的哈希值 | `run_js` 调用 Web Crypto，当前算法是 SHA-1 | 计算本身离线；SHA-1 不适合密码存储或安全签名 |
| `create-calendar-event` | 根据自然语言生成日历事件 | 先取得本机日期时间，再通过 `run_intent` 启动系统日历的新增事件页面 | 不会静默写入；用户通常还要在日历 App 中确认保存 |
| `interactive-map` | 在聊天里显示某个地点的互动地图 | JavaScript 返回 Google Maps 嵌入式 WebView | 依赖 Google Maps 网络可达性 |
| `kitchen-adventure` | 进行“拟人化厨房电器世界”的文字冒险 | 纯提示词技能，不执行脚本 | 可离线，主要消耗模型上下文和推理资源 |
| `learn-something-new` | 查询知识主题、总结内容、生成学习卡片，并询问是否设置每日提醒 | 查询 Wikipedia，JavaScript Canvas 生成图片；确认后调用本地通知 Intent | Wikipedia、在线字体和二维码脚本需要网络；默认仅 Gemma-4-E4B-it 开启 |
| `mood-tracker` | 记录每日心情、查询历史、分析趋势、删除或导出数据 | JavaScript 使用 WebView `localStorage` 保存数据，可返回趋势面板 | 基础记录保存在本机；图表页面依赖在线字体和 Chart.js CDN |
| `qr-code` | 把 URL 生成二维码图片 | JavaScript Canvas 生成 Base64 PNG | 二维码库来自 CDN，因此当前实现仍需要网络加载依赖 |
| `query-wikipedia` | 根据主题查询 Wikipedia 并把摘要交给模型 | JavaScript 调用对应语言的 Wikipedia API | 需要网络 |
| `read-calendar-events` | 读取指定日期的系统日历安排 | `run_intent` 查询 Android `CalendarContract.Instances` | 需要运行时 `READ_CALENDAR` 权限 |
| `schedule-notification` | 创建指定时间或每天重复的本地通知 | `run_intent` 把通知计划保存到 App，并安排本地触发 | 核心逻辑本地执行；系统版本较新时还受通知权限和系统调度限制 |
| `send-email` | 根据对话生成收件人、主题和正文 | `run_intent` 打开系统邮件 App，并预填邮件内容 | 不会自动发送，最终发送操作由用户在邮件 App 中确认 |
| `text-spinner` | 打开摄像头识别人脸，并让指定文字显示在头部附近 | WebView + MediaPipe Face Landmarker | 需要摄像头权限；MediaPipe JS、WASM 和人脸模型当前从网络加载 |

## 4. 三种技能类型

### 4.1 纯提示词技能

例如 `kitchen-adventure`。`SKILL.md` 只规定角色、行为和输出格式，最终还是由 Gemma 直接生成文字。

优点是实现简单；缺点是执行可靠性依赖模型能否严格遵守说明。

### 4.2 JavaScript/WebView 技能

例如：

- `calculate-hash`
- `interactive-map`
- `learn-something-new`
- `mood-tracker`
- `qr-code`
- `query-wikipedia`
- `text-spinner`

Gemma 调用 `run_js(skillName, scriptName, data)` 后，App 会：

1. 找到对应的 HTML 文件。
2. 用 WebView 加载页面。
3. 调用页面提供的 `ai_edge_gallery_get_result(data, secret)` 函数。
4. 接收 JSON 返回值。
5. 根据返回内容显示文字、Base64 图片或新的 WebView。

标准结果大致为：

```json
{
  "result": "返回给模型的文字或 JSON",
  "image": { "base64": "data:image/..." },
  "webview": { "url": "...", "iframe": false },
  "error": null
}
```

### 4.3 Android Intent 技能

例如：

- `create-calendar-event`
- `read-calendar-events`
- `schedule-notification`
- `send-email`

这类技能的 `SKILL.md` 负责指导模型整理参数，真正的 Android 操作由 `RunIntentTool` 和 `IntentHandler` 完成。它们不是在 Markdown 中直接执行系统操作。

## 5. 技能启用状态

技能管理页面可以启用或禁用技能，状态会保存到 DataStore。只有已启用技能才会出现在模型可用列表中，也只有已启用技能能被 `load_skill` 和 `run_js` 找到。

源码默认禁用：

```text
calculate-hash
kitchen-adventure
text-spinner
send-email
```

`learn-something-new` 默认只对 `Gemma-4-E4B-it` 开启，其他模型默认禁用。原因不是其他模型完全不能运行，而是该技能步骤较多，对模型的工具调用和指令遵循能力要求更高。

## 6. 本地、远程和用户导入技能

`SkillManager` 支持三类来源：

1. APK 自带：当前 `assets/skills` 下的技能。
2. 本地导入：用户选取包含 `SKILL.md` 的文件夹，App 会复制到内部 `filesDir/skills`。
3. URL 导入：输入技能目录 URL，App 下载其中的 `SKILL.md`，脚本仍从该远程目录加载。

因此 Skills 系统本质上也是一个可扩展的插件机制，但它的执行主体仍然是 Gemma、WebView JavaScript 和项目中已经注册的 Android Intent。

## 7. 安全与二次开发注意事项

- `SKILL.md` 是直接提供给模型的指令，内容可以显著改变模型行为。不要默认信任从陌生 URL 或文件夹导入的技能。
- JavaScript 技能运行在启用了 JavaScript、DOM Storage 和网络访问的 WebView 中。传给脚本的对话数据或密钥可能被脚本发送到外部服务器。
- `RunJsTool` 和 `RunIntentTool` 当前标记为 `alwaysAllow = true`，产品化时建议对敏感操作增加明确确认，而不仅依赖模型指令。
- 对发送邮件、建立日历事件、清除心情记录等有副作用的操作，建议在真正执行前展示结构化确认页。
- 如果需要完全离线，应把 CDN JavaScript、字体、WASM 和模型资源下载到 `assets`，并替换 Wikipedia、Google Maps 等在线服务。
- `calculate-hash` 当前使用 SHA-1，只适合演示普通摘要计算；安全用途应增加 SHA-256/512，并且密码存储应使用专门的慢哈希算法。
- 内置 `learn-something-new/SKILL.md` 含有很强的“覆盖提示词”表达。它属于项目信任的内置内容，但也说明导入技能存在提示注入风险，生产版本应建立审核和权限边界。

## 8. 对语音助手二次开发的建议

如果目标是“用户说话 -> Gemma 回答 -> 语音播报”，Skills 并不是基础依赖。普通语音聊天可以先不启用任何技能：

```text
语音识别 -> Gemma 对话 -> TTS
```

只有需要手机操作时才启用相应技能，例如：

- “提醒我晚上八点喝药” -> `schedule-notification`
- “明天下午三点添加会议” -> `create-calendar-event`
- “我今天有哪些安排” -> `read-calendar-events`
- “帮我查一下量子纠缠” -> `query-wikipedia`

建议第一版只开放少量、明确、安全的技能。启用太多技能会增加模型选择错误、参数错误和误触发系统操作的概率。

## 9. 新增自定义技能的最小结构

一个只改变回复方式的技能只需要：

```text
assets/skills/my-skill/SKILL.md
```

需要确定性计算或可视化时再增加：

```text
assets/skills/my-skill/scripts/index.html
assets/skills/my-skill/assets/...
```

`SKILL.md` 至少包含：

```markdown
---
name: my-skill
description: 简短说明何时应该使用这个技能。
---

# Instructions

说明模型应该如何处理请求，以及需要调用的工具和参数。
```

新增涉及 Android 原生能力的技能时，仅添加 `SKILL.md` 不够，还必须在 `IntentAction` 和 `IntentHandler` 中注册并实现新的原生操作。

