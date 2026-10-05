# 项目分析文档索引

本目录用于沉淀 Google AI Edge Gallery 二次开发过程中的项目分析、设计决策和排障记录，避免重复阅读源码。

## 文档列表

- [VoxMate 首页亮屏与状态提示](./VoxMate首页亮屏与状态提示.md)：隐藏正常扬声器文字、首页前台保持亮屏和固定窗口亮度、退出恢复系统设置。

- [VoxMate 完整发言与播报节奏优化](./VoxMate完整发言与播报节奏优化.md)：取消十五秒自动提交、延长句尾等待、长句保护及自然播报边界。

- [VoxMate 扬声器播放与回声消除调研](./VoxMate扬声器播放与回声消除调研.md)：Android/LiveKit/Agora/WebRTC 方案、已落地的扬声器路由与音量键管理、AEC 风险提示，以及后续软件 AEC 路线。

- [VoxMate 数字人人物商店迁移](./VoxMate数字人人物商店迁移.md)：可选人物选择、Room 持久化、侧栏商店入口、默认人物目录、阿焰 1.2.0 风格改进与情绪标签保留。
- [VoxMate 人物性格生成规范](./VoxMate人物性格生成规范.md)：从 master 迁移的 JSON/ZIP 模板、发布检查及数字人可选人物和减少重复的约束。

- [VoxMate 数字人自动通话迁移](./VoxMate数字人自动通话迁移.md)：从 master 迁移端侧持续采音、自动转写、插话打断和圆形波形入口到数字人首页；包含首字丢失分析、500ms 前滚保护与采音事件解耦修复。

- [项目架构与 KMP 可行性分析](./项目架构与KMP可行性分析.md)：当前源码的技术栈、目录结构、是否使用 Kotlin Multiplatform，以及跨平台改造建议。
- [模型列表加载失败排查记录](./模型列表加载失败排查记录.md)：启动时出现 `Failed to load model list` 的原因、验证方法与修复建议。
- [模型选择与命名说明](./模型选择与命名说明.md)：Gemma 4 E2B/E4B 等模型名称、资源需求、功能差异和选择建议。
- [视频生成能力可行性分析](./视频生成能力可行性分析.md)：视频生成模型能否接入手机端、可选技术路线和推荐架构。
- [首页功能模块说明](./首页功能模块说明.md)：Agent Skills、Ask Image、Audio Scribe、Prompt Lab、Tiny Garden、Mobile Actions 和 Scrapbook 的作用与模型关系。
- [聊天输入输出与语音对话改造分析](./聊天输入输出与语音对话改造分析.md)：文字输入、流式输出的完整调用链，以及改造成语音对话 App 时应复用的接口。
- [免费文字转语音方案分析](./免费文字转语音方案分析.md)：免费 TTS 方案对比、推荐的语音对话状态流程，以及 Android 系统 TTS 的接入方式。
- [Android 内置 Skills 机制与功能说明](./Android内置Skills机制与功能说明.md)：`assets/skills` 的加载和执行机制、每个内置技能的实际作用，以及联网、权限和安全注意事项。
- [Google AI Edge Gallery 费用与许可证说明](./GoogleAIEdgeGallery费用与许可证说明.md)：官方 App 是否收费、包名辨别、近似第三方应用，以及模型许可证和潜在成本说明。
- [无 UI 能力库拆分设计](./无UI能力库拆分设计.md)：将 LiteRT-LM 对话能力与模型清单/下载能力拆成两个独立 Android Library 的边界、API 和迁移步骤。
- [VoxMate 新应用模块说明](./VoxMate新应用模块说明.md)：独立语音聊天 App 的命名、模块位置、依赖关系、包名和后续开发入口。
- [VoxMate 模型管理功能说明](./VoxMate模型管理功能说明.md)：Models 页面的解耦结构、页面行为、下载流程和与原 Gallery 实现的边界。
- [VoxMate 数据库模块说明](./数据库模块说明.md)：从 password_generator 引入的 Room 参考模块、当前使用边界和后续 VoxMate 数据库设计约束。
- [AI 数字人页面与 three-vrm 驱动方案](./AI数字人页面与three-vrm驱动方案.md)：在 VoxMate 中使用 Three.js + three-vrm 驱动 3D 虚拟数字人的架构、Native-Web 桥接与落地计划。
- [APK 打包与自动构建指南](./APK打包与自动构建指南.md)：本地与云端 CI/CD (GitHub Actions / Gitee Go) 自动化打包 VoxMate APK 的完整指引。

## 维护约定

- 分析结论需注明日期，并以当前工作区源码为准。
- 架构或依赖发生明显变化后，应同步更新对应文档。
- 新增重要功能时，优先记录入口、核心调用链、平台依赖和验证方式。
