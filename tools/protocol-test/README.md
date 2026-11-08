# RabbitAuth 协议联调

这些开发脚本通过真实 Minecraft 1.21.1 TCP 连接测试认证与保护，再用本机 RCON 读取服务端权威状态。
不需要在协议客户端安装 RabbitAuth，不进入正式模组 JAR。

## 准备

使用支持此依赖的 Node.js（本次运行环境为本机 Node），在项目根目录执行：

```powershell
npm.cmd ci --prefix tools/protocol-test --ignore-scripts --no-audit --no-fund
node tools/protocol-test/prepare.mjs
```

prepare.mjs 只准备 run/server/ 隔离实例，不会自动同意 Minecraft EULA。
阅读并同意协议后，手动将该目录 eula.txt 设为 eula=true；已有的同意状态会保留。

测试服仅监听本机回环，游戏端口 25579，RCON 端口 25580；RCON 密码随机生成，不输出。
脚本拒绝修改缺少隔离实例标记的 server.properties。不要将这些脚本用于生产世界。
测试修改会影响隔离世界的测试玩家、物品、方块、游戏规则和权限。

## 顺序

每次测试前在第一个终端从项目根目录运行 .\\gradlew.bat runServer。
第二个终端运行下表中的客户端脚本。客户端会等服务端启动，成功后通过 stop 正常停服。
必须等上一次服务端进程完全退出，再切换配置或重新启动。

| 配置（停服后执行） | 客户端脚本 | 检查 |
| --- | --- | --- |
| node tools/protocol-test/profile.mjs default | node tools/protocol-test/integration.mjs base | 基础认证及保护 |
| 保持 default，重启 | node tools/protocol-test/integration.mjs restart | 正常重启持久化，并在待登录状态下保存停服 |
| 保持 default，再次重启 | node tools/protocol-test/integration.mjs pending | 待登录停服恢复和跨维度返回 |
| node tools/protocol-test/profile.mjs options | node tools/protocol-test/options.mjs | 可选配置、IP 上限、实际等待一分钟票据过期 |
| node tools/protocol-test/profile.mjs loopback4 | node tools/protocol-test/loopback.mjs | IPv4 回环、英文、自定义消息 |
| node tools/protocol-test/profile.mjs loopback6 | 先设 $env:RABBITAUTH_TEST_HOST='::1'，再运行 node tools/protocol-test/loopback.mjs | IPv6 回环 |
| node tools/protocol-test/profile.mjs default | 清除上述环境变量后运行 node tools/protocol-test/races.mjs | 配置阶段及异步认证竞态 |

IPv6 测试后使用 Remove-Item Env:RABBITAUTH_TEST_HOST 清除当前终端覆盖。
完成后停服并执行 node tools/protocol-test/profile.mjs default，恢复安全默认认证配置。

results-state.json 保存随机测试密码和持久化预期，用于跨进程验证；它与各阶段结果 JSON、node_modules、运行目录均已忽略。
不要提交这些测试凭据。公开验收报告位于项目根目录 INTEGRATION_TESTS.md，只包含检查名称和结果。

## 范围

这是无图形协议客户端验证，不代表真实游戏渲染、配方界面或所有模组网络行为已验证。
测试工具将 declare_recipes 包作为原始数据接收，避免用其原版配方解码器解释 NeoForge 配方扩展；认证、位置、背包和效果等断言仍解析实际数据包。
MySQL 服务联调、目标整合包及图形客户端人工验收另行执行。
