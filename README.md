# RabbitAuth

独立的仅服务端认证模组，面向离线直连服务器。
Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21。

客户端不需要安装 RabbitAuth。不依赖 RabbitCore、Architectury、FTB Quests 或 ProtocolLib。
Argon2id、SQLite 和 MySQL JDBC 依赖随 JAR 内置。

## 当前状态

五个阶段的功能代码已实现，处于集成验证阶段。自动回归和 NeoForge Mixin/驱动启动检查见 [IMPLEMENTATION.md](IMPLEMENTATION.md)。
已通过 64 项原版协议客户端联调，覆盖真实进服、正常世界重启及跨维度返回，见 [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md)。图形客户端、真实 MySQL 服务和目标整合包兼容性仍待验证。

## 功能与指令

| 指令 | 作用 |
| --- | --- |
| /register <密码> <确认密码> | 注册并认证 |
| /login <密码> | 验证密码 |
| /changepassword <旧密码> <新密码> | 已认证玩家修改密码 |
| /rabbitauth resetpassword <名称> <新密码> | 控制台或已认证的权限等级 4 管理员重置密码 |

密码参数不能包含空格。默认最短 8、最长 120 个 UTF-16 代码单元；上限保证双密码指令不会超过原版指令长度限制。
密码以随机盐 Argon2id 哈希保存，参数为 64 MiB、3 次迭代、并行度 1。不支持 CatseedLogin 旧账号迁移。
玩家发出的认证指令在普通指令分发前处理，避免将密码带进通用命令日志或错误回显；模组自身不记录明文密码。

- 名称按 Locale.ROOT 规范化建立唯一索引，保留注册大小写，进入世界前拒绝错误大小写。
- 在原版踢出旧连接前检查并占用账号名，同时覆盖尚在配置阶段的连接，防止同时进入。
- 登录前冻结玩家 tick，默认拒绝非白名单游戏数据包；禁止移动、交互、攻击、拾取、丢弃、容器操作和游戏指令。
- 默认阻止未认证玩家受伤。聊天可放行，额外指令可使用正则白名单。
- 主背包、装备、副手和容器同步对未认证客户端显示为空，不清空真实物品；成功后重新同步。
- 登录前传送认证出生点，成功后恢复原维度、坐标和朝向。未认证玩家不写回原版玩家存档。
- 重入 tick 冷却、同 IP 注册上限、同时连接上限；认证失败还有跨重连的 5 分钟 IP 限流窗口。
- 可选客户端失明，成功后移除并恢复原有失明显示，不修改真实药水效果。
- 同 IP 限时免登录、可选回环免登录；仅已注册账号可用，默认都关闭。
- SQLite 默认、MySQL 可选；账号及返回位置持久化，数据库故障时拒绝认证。
- MessageKey 统一消息键，内置简体中文与英文，支持 UTF-8 自定义语言覆盖。

## 构建与检查

使用 Java 21：

```powershell
.\gradlew.bat clean build
.\gradlew.bat authTest
.\gradlew.bat runSmokeServer
.\gradlew.bat runServer
```

Unix 使用 ./gradlew。产物位于 build/libs/。
build 包含 authTest 和 verifyArtifact；runSmokeServer 额外验证全部七个 Mixin 目标及运行时依赖后以 --initSettings 退出，不接受 EULA、不启动对外游戏服务。
src/smoke 中的开发检查不进入正式 JAR。

Windows Wrapper 将 TEMP/TMP 临时设为项目的 .gradle/java-tmp，解决沙箱重定向目录导致的 Java 本地 socket 失败。只作用于本次构建及子进程，不修改系统环境变量。

## 安装与配置

将 JAR 放进专用 NeoForge 服务器的 mods 目录，服务器使用 online-mode=false。
开发服务器目录为 run/server/；首次运行需由管理员阅读并接受 Minecraft EULA。

首次正常启动生成：

```text
config/rabbitauth/
  auth.properties
  rabbitauth.db
  languages/
    zh-CN.properties
    en-US.properties
```

修改配置或语言文件后重启服务器。完整默认值见 src/main/resources/auth-defaults.properties。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| login-timeout-seconds | 120 | 未认证超时 |
| max-login-attempts | 5 | 单会话失败上限，及 5 分钟 IP 失败窗口上限 |
| before-login-allow-chat | false | 允许未认证玩家发言 |
| hide-inventory | true | 隐藏发送给未认证客户端的背包数据 |
| no-damage-before-login | true | 未认证玩家免伤 |
| blindness-before-login | false | 仅客户端显示失明 |
| spawn-enabled | true | 使用认证出生点 |
| spawn-dimension | minecraft:overworld | 认证维度 |
| spawn-x / spawn-y / spawn-z | 空 | 全空时从该维度共享出生点附近寻找安全地面；自定义时须同时设置，脚下需有实体地面且脚部和头部为空气 |
| reenter-cooldown-ticks | 0 | 下线后冷却，0 关闭 |
| max-registrations-per-ip | 3 | 每个注册 IP 的账号上限，0 关闭 |
| max-connections-per-ip | 3 | 含待连接及未认证会话的同 IP 连接上限，0 关闭 |
| same-ip-auto-login | false | 同 IP 凭票据免登录 |
| auto-login-timeout-minutes | 30 | 从最后一次已认证下线计时，免登录后再次正常下线刷新 |
| loopback-auto-login | false | 对回环地址连接的已注册账号直接免登录 |
| command-whitelist | 空 | 正则之间用 ;; 分隔；匹配含 / 的完整指令 |
| language | zh-CN | 服务端消息语言文件名 |
| storage | sqlite | sqlite 或 mysql |

例如允许 /help 和 /rules：

```properties
command-whitelist=/help(?: .*)?;;/rules
```

匹配区分大小写，使用完整匹配；login/register 始终可用。Java properties 中的正则反斜杠需要写成双反斜杠。
放行指令仍遵守原本权限。默认拒绝其他模组在游戏阶段的自定义交互数据包，配置阶段的 NeoForge 握手不受此过滤影响。

同 IP 免登录票据只保存在内存，重启后失效；改密和管理员重置会使旧票据失效。
管理员重置后断开该账号现有连接，并禁止该次下线重新生成免登录票据。
回环免登录开启后，来自本机的已注册账号连接不再需要密码；普通 IP 数量限制仍然生效。

## 数据存储

SQLite 默认文件位于 config/rabbitauth/rabbitauth.db，采用 WAL、FULL 同步和 5 秒忙等待。
停服后备份该目录及世界玩家存档；不要只复制运行中的主 db 文件。

MySQL 需要预先创建专用数据库，并赋予该库建表和读写权限：

```properties
storage=mysql
mysql-url=jdbc:mysql://localhost:3306/rabbitauth?connectTimeout=5000&socketTimeout=10000
mysql-user=rabbitauth
mysql-password=YOUR_PASSWORD
```

远程数据库连接的 TLS 选项请按部署环境设置。SQLite 与 MySQL 不会自动互相迁移。
当前是单服务端进程持有数据库的设计，不支持多个服务器同时共用账号库或在线修改数据库后自动刷新缓存。
数据库连接故障会拒绝新认证，恢复连接后需重启服务端。
账号缓存查询不做主线程 I/O；密码和运行期间的 SQL 在两个工作线程与 32 项有界队列内执行，SQL 写入串行化。
初始化和停服收尾允许等待数据库；启动校验失败会阻止服务器以无认证保护状态运行。

返回位置包含维度、坐标、朝向和待认证标记：先持久化再传送，认证成功后清除标记。
只有仍待认证的位置记录用于跨重启恢复；正常进服优先使用原版玩家存档，避免覆盖崩服前较新的保存。

## 国际化

默认文件会复制到 config/rabbitauth/languages/，可直接编辑；不会覆盖已有文件。
language=custom 时读取 languages/custom.properties，缺少的键回退英文；zh-CN/en-US 缺少自定义键则回退各自内置文本。
消息占位符为 {0}、{1} 等，所有键见 MessageKey 枚举。

## 与 RabbitCore 等模组集成

RabbitAuthAPI.isAuthenticated(ServerPlayer) 查询当前连接的认证状态。
PlayerAuthenticatedEvent 在服务端线程上、认证位置及背包恢复后发布，注册成功和免登录成功同样会触发。
死亡重生时只向同一连接的新玩家实体继承认证状态。

RabbitCore 尚未修改为消费此 API。其他模组的登录事件、额外背包能力或直接修改玩家数据的行为需单独适配和联调。
ProtocolLib 属于 Bukkit 生态，本模组不使用它。原版背包隐藏通过 NeoForge 服务端数据包处理实现。
仅对 plugins/ 中名称包含 CatSeedLoginBlinding 的文件提示冲突；重命名插件和混合服务端不在支持范围。

## 自定义白名单

已提供添加、移除、查询、分页列表、启用/暂停和全局开关；/whitelist 与 /rabbitauth whitelist 操作同一份数据库。
移除或暂停立即踢出在线/待配置连接并撤销免登录，不删除账号或背包。OP 不能绕过。
配置、原版迁移与可选群聊接口见 [白名单说明](docs/whitelist.md)。AstrBot 插件本体尚待接入。

白名单新增验收见 [WHITELIST_TESTS.md](WHITELIST_TESTS.md)：121 项认证回归、73 项 RabbitCore 桥接回归及 34 项白名单真实联调通过。
