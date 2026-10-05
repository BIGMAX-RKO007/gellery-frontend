# VoxMate Persona

`persona` 是 VoxMate 的无 UI 人物能力库，负责：

- 从 `assets/personas` 读取内置人物。
- 解析并校验 `.voxpersona` ZIP 人物包。
- 把已安装人物和当前选择写入独立 Room 数据库。
- 将结构化人物设定编译为 `ai-core` 可使用的系统指令。

模块不依赖 Compose、Navigation、模型下载或 LiteRT-LM。下载层取得人物包输入流后，可调用
`PersonaRepository.install()` 安装；聊天应用只消费 `ActivePersona.systemInstruction`。

## 选择契约

- `loadActive()` 返回可空人物：首次启动只登记资源，不自动启用。
- `listInstalled()` 登记并返回内置/已安装索引；`readPersona(id, version)` 只预检，不改变选择。
- `activate(id, version)` 显式保存选择，`clearActive()` 清除选择但不卸载资源。
- 内置人物位于 `src/main/assets/personas/default/`，修改后同步升级 manifest 版本。
- 当前选中的内置人物跟随资源升级；从未选择或已清除的人物不会被自动启用。
- 声音配置仅作为扩展协议保存；本模块不提供声音克隆，也不驱动 TTS。
- 数据库版本 2 使用 explicitlySelected 区分主动选择；旧 master 版本 1 的自动选择迁移后不会自动启用，资源索引继续保留。
