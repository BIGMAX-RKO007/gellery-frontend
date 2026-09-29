# db_lib

该模块从 `password_generator` 项目复制而来，保留 Room 的 `Database / DAO / Entity`
分层，作为 VoxMate 后续数据库设计的参考实现。构建配置已适配当前项目的 AGP 9、
版本目录和 Android SDK。

当前实体和表仍属于密码管理业务，`:voice-chat-app` **没有依赖本模块**。新增 VoxMate
会话、模型记录或设置表时，应先按 VoxMate 领域重新定义实体与迁移方案，不能直接复用
密码表，也不能在数据库中明文保存 Token 或其他敏感凭证。

验证命令：

```powershell
./gradlew.bat :db_lib:assembleDebug
```
