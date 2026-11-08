# RabbitAuth 白名单

RabbitAuth 负责白名单唯一数据源、进服校验和持久化；RabbitCore 仅提供可选群聊管理入口。未实现备注或玩家改名。

## 指令

以下两种前缀操作同一份名单：

```text
/whitelist add Steve
/whitelist remove Steve
/whitelist list [页码]
/whitelist info Steve
/whitelist set Steve enabled true
/whitelist set Steve enabled false
/whitelist on
/whitelist off
/whitelist status
/whitelist import
```

也可以使用 /rabbitauth whitelist 后接相同子命令。
只允许控制台或已经认证的权限等级 4 管理员操作，OP 在登录前也不能修改。
修改先提交数据库及审计，再在服务端线程执行踢人和通知；运行中的 SQL 不阻塞主线程。控制台和游戏玩家收到异步结果，RCON 可能只得到空的即时返回，不能把它当成操作失败或成功。

列表每页 20 条；info/list 显示启用或暂停状态。添加尚未注册的名字后，该玩家可以进服注册。
管理员输入已注册名字时使用账号的注册大小写；玩家进服必须使用预留/注册大小写，避免离线 UUID 分裂。
预留名字的大小写约束在全局开关关闭时也保留。

## 生效规则

- 全局白名单开启时，仅已添加且处于启用状态的玩家可以进入。OP、同 IP 免登录和回环免登录都不能绕过。
- 移除或暂停后，立即断开该名字的在线玩家和待配置连接，同时撤销免登录票据；强制退出不会重新发放票据。
- 移除不删除认证账号、密码、背包或世界数据。重新添加后仍使用原账号密码。
- 全局关闭表示不限制成员资格。移除/暂停仍会按指令要求踢出当前连接，但全局关闭期间可以重新进入；重新开启后恢复名单检查。
- 开启全局检查会断开不符合名单条件的现有连接。建议控制台先添加管理员本人，再执行 on。

## 存储与原版迁移

同 RabbitAuth 配置使用 SQLite 或 MySQL，新增 whitelist_settings、access_whitelist、whitelist_audit 三张表。
首次初始化一次性导入原版 whitelist.json，并继承当时的原版白名单开关。若已存在注册账号，以注册名大小写规范化导入。
初次导入失败会阻止无保护启动，原文件保留。

此后原版 /whitelist 已替换，server.properties 的 white-list 和原版 JSON 不再是白名单权威来源，也不会持续覆盖数据库。
import 用于手动合并原版文件，只添加缺少的名字，不重新启用已暂停的条目、不删除现有条目。
不支持原版 reload 子命令；运行中的修改提交后自动生效。

成员记录保留创建/更新时间和最近操作者；审计记录包含操作、名字、时间、操作者，并与名单修改处于同一事务。
SQLite 已完成真实重启验证；MySQL 仍待真实服务联调。部署前按正常数据库流程备份账号库及世界玩家数据。
出现数据库写入错误时白名单拒绝放行，恢复后需要重启。

## 可选 API

RabbitAuthAPI.manageWhitelist(Map<String,String>) 返回 CompletableFuture<Map<String,Object>>，用于可信的同进程模组。
调用方必须自行验证外部操作者权限；该 API 本身不赋予群聊账号权限。
请求字段为 action、name、enabled、page、actor，可选 deadline 为执行截止时间的 Unix 毫秒数。
action 包含 add/remove/set/info/list/status/on/off/import；外部桥接不会开放 import。
结果包含 ok、code、message；成功时包含全局 enabled，成员结果位于 entry，分页结果位于 entries/page/pages/total。
remove 返回 removed=true 和 name，不返回仍然有效的 entry。

RabbitAuth 不依赖 RabbitCore；未装 RabbitAuth 或 API 不匹配时，RabbitCore 返回 AUTH_UNAVAILABLE。

## 群聊管理

授权只由服务器配置中的明确群 ID 和账号 ID 决定，群主/群管理员身份、昵称不会自动获得权限。
谁可以授权：能够编辑服务器桥接配置的服务器管理者；游戏内 OP 管理名单并不等于能修改群聊授权名单。
配置及 AstrBot 协议详见 RabbitCore 的 docs/whitelist-admin.md。

AstrBot 桥接尚未完成。本次已实现模组端接口并通过模拟端点和真实服务端联调，实际群聊收发仍待插件接入。
