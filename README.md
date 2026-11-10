# RabbitAuth

为离线直连的 Minecraft 专用服务器提供进服认证和白名单。

**Minecraft 1.21.1 · NeoForge 21.1.265+（21.1.x） · Java 21 · 仅服务端安装**

[发布页](https://github.com/RabbitWorldTeam/RabbitAuth/releases) · [Wiki 指南](https://github.com/RabbitWorldTeam/RabbitAuth/wiki) · [问题反馈](https://github.com/RabbitWorldTeam/RabbitAuth/issues)

## 主要功能

- 登录、注册、改密与管理员密码重置，使用 Argon2id 存储密码。
- 认证前限制交互，认证后恢复原位置和背包同步。
- 独立白名单、SQLite 或 MySQL 存储、中英文彩色提示。

## 安装

将 `rabbitauth-1.21.1-neoforge-1.0.0.jar` 放入专用服务器的 `mods/`，并设置 `online-mode=false`。客户端无需安装 RabbitAuth。

## 常用指令

| 指令 | 用途 |
| --- | --- |
| `/r <密码> <确认密码>` | 注册，完整指令为 `/register` |
| `/l <密码>` | 登录，完整指令为 `/login` |
| `/changepassword <旧密码> <新密码>` | 修改密码 |
| `/rabbitauth whitelist` | 白名单管理入口 |

[服务端配置](docs/server-guide.md) · [白名单指南](docs/whitelist.md) · [协议测试](INTEGRATION_TESTS.md)

## 从源码构建

`./gradlew build`，Windows 使用 `.\gradlew.bat build`。JAR 输出到 `build/libs/`。

[MIT](LICENSE) · [第三方声明](THIRD_PARTY_NOTICES.md)
