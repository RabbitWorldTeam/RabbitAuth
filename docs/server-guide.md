# RabbitAuth 服务端指南

安装和玩家命令见 [RabbitAuth 首页](../README.md)。白名单配置和管理员指令见 [白名单指南](whitelist.md)。

## 配置文件

首次正常启动会在服务器目录生成以下文件。修改配置或语言文件后重启服务器。

```text
config/rabbitauth/
  auth.properties
  rabbitauth.db
  languages/
    zh-CN.properties
    en-US.properties
```

完整默认值见 [auth-defaults.properties](../src/main/resources/auth-defaults.properties)。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `login-timeout-seconds` | `120` | 未认证会话超时时间 |
| `max-login-attempts` | `5` | 登录失败限制，也用于 5 分钟 IP 失败窗口 |
| `min-password-length` / `max-password-length` | `8` / `120` | 密码长度，按 UTF-16 代码单元计算 |
| `before-login-allow-chat` | `false` | 允许未认证玩家发言 |
| `hide-inventory` | `true` | 向未认证客户端隐藏背包、装备和副手 |
| `no-damage-before-login` | `true` | 未认证玩家免伤 |
| `blindness-before-login` | `false` | 认证前仅在客户端显示失明 |
| `spawn-enabled` | `true` | 使用认证出生点 |
| `spawn-dimension` | `minecraft:overworld` | 认证出生点所在维度 |
| `spawn-x` / `spawn-y` / `spawn-z` | 留空 | 默认在该维度共享出生点附近寻找安全地面。自定义时三个坐标都要填写，脚下需要实体地面，脚部和头部需要空气。 |
| `reenter-cooldown-ticks` | `0` | 下线后的重入冷却，0 表示关闭 |
| `max-registrations-per-ip` | `3` | 每个注册 IP 的账号上限，0 表示关闭 |
| `max-connections-per-ip` | `3` | 同 IP 连接上限，包含待连接与未认证会话，0 表示关闭 |
| `same-ip-auto-login` | `false` | 已注册账号使用同 IP 票据免登录 |
| `auto-login-timeout-minutes` | `30` | 自上次认证后正常下线起计算免登录有效期 |
| `loopback-auto-login` | `false` | 允许回环地址的已注册账号免登录 |
| `command-whitelist` | 留空 | 认证前额外允许的指令正则 |
| `language` | `zh-CN` | 服务端消息语言 |
| `storage` | `sqlite` | 数据库类型，也可设为 `mysql` |

额外指令规则按大小写完整匹配，包含开头的 `/`，多个规则用 `;;` 分隔。放行仍遵循原有命令权限，`/l`、`/login`、`/r` 和 `/register` 始终可用。

```properties
command-whitelist=/help(?: .*)?;;/rules
```

同 IP 免登录票据保存在内存中，重启、修改密码或管理员重置密码都会使旧票据失效。管理员重置也会断开账号的现有连接。

## 管理员强制登录

服务端控制台和已认证的 4 级 OP 可执行 `/rabbitauth forcelogin <玩家>`，让通过白名单检查的在线玩家直接通过本次连接的认证。新玩家可临时免注册，已有账号可临时免密码。此命令不创建账号、不修改密码，也不签发同 IP 免登录票据。成功操作会在服务端日志记录操作者和目标玩家。

## 数据库与备份

SQLite 默认使用 `config/rabbitauth/rabbitauth.db`，采用 WAL、FULL 同步和 5 秒忙等待。停服后备份整个 RabbitAuth 配置目录与世界玩家存档。

使用 MySQL 时，先创建数据库和拥有建表、读写权限的专用账号，再填入配置。

```properties
storage=mysql
mysql-url=jdbc:mysql://localhost:3306/rabbitauth?connectTimeout=5000&socketTimeout=10000
mysql-user=rabbitauth
mysql-password=YOUR_PASSWORD
```

按部署环境设置远程数据库连接的 TLS。SQLite 与 MySQL 之间需要另行迁移。当前数据库设计用于单个服务端进程，数据库故障期间新认证会被拒绝，恢复后需重启服务器。SQLite 已通过真实重启验证，MySQL 仍待联调。

首次加载白名单时会导入原版 `whitelist.json`。认证账号、名单和审计由 RabbitAuth 的数据库管理，详见[迁移说明](whitelist.md#存储与原版迁移)。

## 认证位置与存档

使用认证出生点时，RabbitAuth 会先保存玩家所在维度、坐标和朝向，再执行传送。通过认证后恢复原位置和背包同步。跨重启恢复仅使用仍标记为待认证的位置，正常进服优先使用原版玩家存档。

## 消息与语言

内置 `zh-CN` 和 `en-US` 会在首次启动时复制到 `languages/`。已有语言文件优先使用。要启用自定义语言，设置 `language=custom` 并在该目录添加 UTF-8 的 `custom.properties`。

消息占位符使用 `{0}`、`{1}` 等。中英文缺少的键回退到对应内置文本，自定义语言缺少的键回退英文。完整键见 [MessageKey](../src/main/java/com/tr4nce/rabbitauth/i18n/MessageKey.java)。消息配色由模组根据用途应用。

## 模组集成

- `RabbitAuthAPI.isAuthenticated(ServerPlayer)` 查询当前连接是否通过认证。
- `PlayerAuthenticatedEvent` 在服务端线程上发布，此时玩家位置与背包已恢复。注册和免登录成功也会触发。
- [RabbitCore](https://github.com/RabbitWorldTeam/RabbitCore) 可通过公开 API 管理白名单，见 [群聊管理](whitelist.md#群聊管理)。

验证范围见 [原版协议测试](../INTEGRATION_TESTS.md) 和 [白名单联调](../WHITELIST_TESTS.md)。
