# RabbitAuth 真实协议联调验收

日期：2026-11-07（Asia/Hong_Kong）。Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / Node v22.17.0。

7 轮、共 64 项真实连接检查全部通过。本轮未改动认证实现，新增测试脚本和验收记录。

| 测试轮次 | 检查数 | 结果 |
| --- | --- | --- |
| 基础认证与保护 | 20 | 通过 |
| 正常世界重启 | 6 | 通过 |
| 待认证停服与跨维度返回 | 6 | 通过 |
| 可选配置与实际过期 | 13 | 通过 |
| IPv4 回环与国际化 | 7 | 通过 |
| IPv6 回环与国际化 | 7 | 通过 |
| 真实连接竞态 | 5 | 通过 |

## 环境与证据

- 用户同意 Minecraft EULA 后，在 run/server/ 隔离世界测试。仅绑定本机回环，游戏端口 25579、RCON 25580。
- 客户端使用 Minecraft 1.21.1 原版协议，不安装 RabbitAuth；通过实际数据包与 RCON 查询的服务端状态断言。
- 多次使用 stop 正常保存、完全退出并重新启动，验证 SQLite、玩家存档和返回位置。结束后已恢复默认认证配置并停服。
- 另有此前已通过的 97 项 Java 回归、生产 JAR 内容检查和 NeoForge Mixin/驱动启动检查。
- 构建产物：rabbitauth-0.1.0-SNAPSHOT.jar；SHA-256：74e142902861fd35cddfb4f48c92c2ad252f947085af74be512bc12aed78b547。协议测试运行对应的开发类，生产 JAR 装载仍应作为发布前单独验收。

## 范围限制

- 无图形协议测试不等于 Minecraft 客户端渲染或目标整合包人工验收。额外背包、任务和其他模组协议仍需联调。
- 工具将 declare_recipes 原始接收，未用原版配方解码器验证 NeoForge 配方扩展；认证、背包、位置和效果等断言均读取实际协议数据。
- 未提供真实 MySQL 服务，只有其驱动加载和代码构建验证。
- 正常保存/停服恢复已测，非正常断电、进程崩溃和维度删除等异常恢复尚未验收。
- RabbitCore 尚未改为消费认证 API。

## 基础认证与保护

- 通过：vanilla protocol enters world without RabbitAuth client
- 通过：initial inventory packets are masked
- 通过：movement and privileged commands blocked before login
- 通过：creative slot, drop and container packets cannot mutate inventory
- 通过：block destruction blocked
- 通过：damage prevented before authentication
- 通过：item pickup blocked before authentication
- 通过：chat blocked before authentication
- 通过：actual inventory resynchronized after registration/login
- 通过：gameplay commands available after authentication
- 通过：connection rejected: 已有连接
- 通过：duplicate connection leaves original authenticated connection online
- 通过：connection rejected: 大小写
- 通过：saved inventory is hidden again on reconnect
- 通过：old password rejected and new password accepted
- 通过：authentication survives death and respawn
- 通过：administrator reset forces reauthentication
- 通过：reset rejects previous password and accepts replacement
- 通过：connection rejected: 大小写
- 通过：authentication password absent from server logs

## 正常世界重启

- 通过：account survives full server restart
- 通过：inventory remains hidden before post-restart login
- 通过：original position survives full server restart
- 通过：inventory survives full server restart
- 通过：authentication not skipped with defaults
- 通过：authentication password absent from server logs

## 待认证停服与跨维度返回

- 通过：restart while awaiting authentication preserves original return position
- 通过：unauthenticated stop does not overwrite inventory
- 通过：authentication location uses configured dimension
- 通过：login restores original dimension
- 通过：cross-dimension return coordinates restored
- 通过：authentication password absent from server logs

## 可选配置与实际过期

- 通过：restart revokes process-local auto-login tickets
- 通过：configured pre-login blindness is sent
- 通过：IP registration limit enforced against real database account
- 通过：IP connection limit includes unauthenticated sessions
- 通过：regex-whitelisted command executes before login
- 通过：before-login-allow-chat permits actual chat
- 通过：attack packet cannot damage nearby mob before login
- 通过：block placement packet blocked before login
- 通过：pre-login blindness removed after login
- 通过：configured tick reentry cooldown rejects immediate reconnect
- 通过：same-IP authenticated logout grants password-free reentry
- 通过：configured login timeout disconnects unauthenticated client
- 通过：expired same-IP ticket requires password

## IPv4 回环与国际化

- 通过：IPv4 127.0.0.1 registered account bypasses password with explicit loopback option
- 通过：loopback bypass does not require password input
- 通过：unregistered loopback connection still requires registration
- 通过：loopback does not silently authenticate an unregistered account
- 通过：English fallback messages work alongside custom success override
- 通过：case protection remains active with loopback bypass
- 通过：duplicate protection remains active with loopback bypass

## IPv6 回环与国际化

- 通过：IPv6 ::1 registered account bypasses password with explicit loopback option
- 通过：loopback bypass does not require password input
- 通过：unregistered loopback connection still requires registration
- 通过：loopback does not silently authenticate an unregistered account
- 通过：English fallback messages work alongside custom success override
- 通过：case protection remains active with loopback bypass
- 通过：duplicate protection remains active with loopback bypass

## 真实连接竞态

- 通过：duplicate is rejected while original connection is still configuring
- 通过：pending original connection remains intact
- 通过：old asynchronous password result cannot authenticate a replacement connection
- 通过：replacement still needs its own valid password
- 通过：replacement authenticates only after its own valid request

复现步骤见 tools/protocol-test/README.md。随机测试凭据和原始结果 JSON 已忽略，报告不包含密码。
