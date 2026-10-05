# VoxMate 数字人人物商店迁移

记录日期：2026-10-05；从本地 master 人物能力迁移到当前数字人分支。未合并 master 的旧首页或聊天页面，原 Gallery app 不变。

## 用户确认的行为

- 首次启动仅登记内置人物，不自动启用人物性格。未选择时保持当前 VoxMate 系统提示词逐字不变。
- 侧栏“人物商店”紧接“模型下载与管理”，复用同一功能菜单组件、颜色、间距、箭头和点击风格。
- 商店选择真实内置或已安装人物后，应用姓名、性别表达、性格、关系和沟通方式；选择存入本地 Room。
- 下次打开应用先恢复明确选择，再预加载上次模型，防止第一轮使用错误性格。
- 主页标题显示已选人物姓名；未选择时仍显示 VoxMate。此次不替换 VRM 外观或姿态。
- “恢复默认对话”删除选择但保留资源，下次启动也不自动启用阿焰。
- 选择和清除人物开始新会话，避免旧身份历史影响新人物，不重新加载模型权重。
- 保留允许模型输出情绪标签的系统协议和现有 EmotionParser、表情、唇形、TTS 处理。

## 分层与入口

- :persona：来自 master 的无 UI 能力库，独立 Room 数据库、JSON 校验、人物提示词编译、assets/私有目录读取、ZIP 安装和 SHA-256 校验。无 Compose、推理引擎或应用依赖。
- PersonaRepository：loadActive 返回可空选择；listInstalled 只登记资源；readPersona 预检；activate 明确选择；clearActive 清除。
- PersonaDao.registerBundled：事务内登记当前 assets 版本。仅曾明确选择同一内置目录时跟随升级，空选择保持为空；清理已被资源替换的旧版本索引。
- AvatarPersonaPrompt：可选人物 + 固定数字人协议。选中人物不再叠加“你是 VoxMate，一个温暖……”默认身份，避免身份和语气冲突；未选中直接返回原提示词。
- VoxMateAiSessionManager：恢复、加载、选择和 reset 串行执行。选择时 stop 后等待异步生成取消完成；校验/切换失败不提交新选择，持久化失败尽力恢复旧指令。短暂提交段防止协程取消造成一半完成。
- PersonaStoreRoute：连接仓库和会话事件，读取已安装列表、提示失败并支持重试；页面拥有 Loading/Content/Empty/Error 和操作中状态。
- PersonaStoreScreen/Contract：纯 UI，稳定 ID@版本 key，标签可换行，可单独预览，英中资源化。
- VoxMateDrawerContent：新增 personas 功能项和单独导航事件，不访问人物数据库。

## 默认人物位置与改进

master 和当前分支的源目录相同：

    Android/src/persona/src/main/assets/personas/default/

核心文件 persona.json；manifest.json 保存版本和 ID，voice/voice.json 保存未来声音扩展，locales 保存展示文案，LICENSE 保存声明。
打包后位于 assets/personas/default，无需下载。

阿焰升级为 1.2.0：急性子、嘴硬心软、冷幽默，通常一到两句，复杂问题给必要信息。
删除固定口头禅列表，称呼通常是“你”，偶尔损友式称呼必须符合语境且用户接受。
粗口是可选表达，不要求每轮出现；认真、难过、纠错、反感时相应变化。
六组示例覆盖不同情境，提示词明确不连续复用句首、称呼、示例答案。
这属于系统提示词塑形，不改变模型权重，真实自然度仍需用目标手机和模型多轮验证。

## 数据与后续扩展

数据库 voxmate_personas.db，版本 2，独立 persona_packages 与 active_persona 表，不复用 db_lib 密码业务表。
1 → 2 迁移增加 explicitlySelected，历史 master 自动选择置为 false，不再当成用户授权；升级保留人物索引，用户在商店重新明确选择才启用。
遵循 password_generator 的 Database/DAO/Entity 分层；schema 保存在 persona/schemas。
外部 .voxpersona 是 ZIP，manifest 必须在根目录。安装完成不自动启用，需商店选择。
已安装包索引优先展示；阿焰可立即使用，沐沐/林序/夜鸦仍为预览，在线清单和下载未接入。
声音字段仅持久化并保留扩展协议，此次未接入声音克隆、人物音色或语速参数。
生成和发布规则见《VoxMate 人物性格生成规范》。

## 验证与限制

在 Android/src 执行：

    ./gradlew.bat :persona:testDebugUnitTest :persona:assembleDebug :voice-chat-app:testDebugUnitTest :voice-chat-app:assembleDebug --offline

另打包相关 ai-core、model-download、model-manager-ui、speech-recognition 库验证依赖兼容性。
新增测试覆盖：无选择保留原提示词、人物身份与表情协议共存、标签不朗读、登记不自动选择、清除后不自动选择、内置升级和不覆盖外部选择。
内存 DAO 单元测试验证选择策略，不替代真机 Room 持久化验证。
产物：Android/src/persona/build/outputs/aar/persona-debug.aar，以及 voice-chat-app/build/outputs/apk/debug/voice-chat-app-debug.apk。
最终人物与应用单元测试共 17 项，0 失败；人物 AAR 和应用 APK 打包通过，APK 内已核验默认人物 1.2.0 的全部 assets 文件。
构建可验证编译和打包；未连接真机，Room 升级、重启恢复、页面外观和目标模型的语气/表情效果需要实测。

建议真机依次验收：未选择时正常聊天 → 商店选择阿焰 → 主页显示阿焰并语音聊几轮 → 结束进程重启检查恢复 → 恢复默认对话 → 再次重启检查没有自动启用 → 检查开心/难过表情和插话打断。
