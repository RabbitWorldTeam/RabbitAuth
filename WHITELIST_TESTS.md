# 白名单与群聊管理验收

日期：2026-11-08（Asia/Hong_Kong）。

RabbitAuth build/runSmokeServer 通过：121 项 Java 回归、生产 JAR 检查、7 个 Mixin 变换。RabbitCore build 通过：73 项桥接回归。

真实开发服与模拟群聊端两轮共 34 项检查通过。SQLite 数据在正常停服和重启之间保留。

联调夹具在开发测试模组内编译 RabbitCore 桥接源代码，实际调用 RabbitAuth 可选 API；不加载 RabbitCore 方块、FTB 或其他整合包内容。
这不是完整整合包、真实 AstrBot 或图形客户端的验收；MySQL 服务仍未联调。

本次 RabbitAuth JAR SHA-256：dcb0ad96c3ebfb60dd9c5a0fd5a81482bb606361b015fc12ab14a07aaa155ccb。

## 管理与踢出（27 项）

- 通过：unauthorized account ID denied despite matching display name
- 通过：administrator denied outside allowed group
- 通过：group administrator cannot toggle global whitelist without explicit action grant
- 通过：expired request rejected before mutation
- 通过：bridge API uses registered canonical name
- 通过：authorized group request reaches real RabbitAuth database
- 通过：same request UUID does not execute twice
- 通过：legacy chat envelope can manage whitelist without ordinary chat forwarding
- 通过：result is targeted to original group and sender
- 通过：out-of-range list page reports error
- 通过：vanilla whitelist alias enables custom database authority
- 通过：unlisted/paused connection rejected before world entry
- 通过：unauthenticated OP cannot disable whitelist through allowed command pattern
- 通过：authenticated administrator can use nested whitelist commands
- 通过：same-IP ticket works before whitelist revocation
- 通过：group administrator can pause membership
- 通过：pausing membership immediately kicks authenticated player
- 通过：unlisted/paused connection rejected before world entry
- 通过：pause revokes ticket instead of granting it again on forced logout
- 通过：remove result does not misreport enabled membership
- 通过：removing membership immediately kicks online player
- 通过：unlisted/paused connection rejected before world entry
- 通过：console add writes same whitelist used by group API
- 通过：removal keeps account password and requires fresh authentication
- 通过：in-game state modification shares immediate kick enforcement
- 通过：persistent record tracks exact group and account actor IDs
- 通过：pause also kicks a connection still in configuration phase

## 重启持久化（7 项）

- 通过：global whitelist setting survives real restart
- 通过：paused membership survives restart
- 通过：removed membership stays removed after restart
- 通过：unlisted/paused connection rejected before world entry
- 通过：approved account and password remain usable after restart
- 通过：console can switch custom whitelist off
- 通过：global off permits unlisted registration flow

## 复现

先构建 RabbitCore，再从 RabbitAuth 项目根目录运行 node tools/protocol-test/whitelist-setup.mjs。
终端一运行 node tools/protocol-test/whitelist.mjs main，终端二运行 .\gradlew.bat runServer -PbridgeIntegration。通过后正常停服。
保持同一世界，分别运行 whitelist.mjs restart 和 runServer -PbridgeIntegration，验证真实重启。
测试端点只绑定 127.0.0.1，游戏端口 25579、RCON 25580、模拟群聊 26670；随机凭据和原始结果 JSON 位于忽略目录/文件。
结束后使用 profile.mjs default 恢复认证配置，恢复或禁用测试桥接配置；白名单测试结束时全局开关为关闭。
