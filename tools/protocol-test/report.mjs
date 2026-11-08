import fs from 'node:fs/promises';
import crypto from 'node:crypto';

const phases = [
  ['base', '基础认证与保护'], ['restart', '正常世界重启'], ['pending', '待认证停服与跨维度返回'],
  ['options', '可选配置与实际过期'], ['loopback4', 'IPv4 回环与国际化'],
  ['loopback6', 'IPv6 回环与国际化'], ['races', '真实连接竞态']
];
let total = 0;
const sections = [], rows = [];
for (const [name, title] of phases) {
  const result = JSON.parse(await fs.readFile(new URL('./results-' + name + '.json', import.meta.url), 'utf8'));
  if (!result.passed || !Array.isArray(result.checks)) throw new Error('Incomplete protocol phase: ' + name);
  total += result.checks.length;
  rows.push('| ' + title + ' | ' + result.checks.length + ' | 通过 |');
  sections.push('## ' + title + '\n\n' + result.checks.map(check => '- 通过：' + check).join('\n'));
}
const root = new URL('../../', import.meta.url);
const properties = await fs.readFile(new URL('gradle.properties', root), 'utf8');
const version = properties.match(/^mod_version=(.+)$/m)[1].trim();
const artifact = 'rabbitauth-' + version + '.jar';
const hash = crypto.createHash('sha256').update(await fs.readFile(new URL('build/libs/' + artifact, root))).digest('hex');
const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Hong_Kong', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const text = '# RabbitAuth 真实协议联调验收\n\n' +
  '日期：' + date + '（Asia/Hong_Kong）。Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / Node ' + process.version + '。\n\n' +
  '7 轮、共 ' + total + ' 项真实连接检查全部通过。本轮未改动认证实现，新增测试脚本和验收记录。\n\n' +
  '| 测试轮次 | 检查数 | 结果 |\n| --- | --- | --- |\n' + rows.join('\n') + '\n\n' +
  '## 环境与证据\n\n' +
  '- 用户同意 Minecraft EULA 后，在 run/server/ 隔离世界测试。仅绑定本机回环，游戏端口 25579、RCON 25580。\n' +
  '- 客户端使用 Minecraft 1.21.1 原版协议，不安装 RabbitAuth；通过实际数据包与 RCON 查询的服务端状态断言。\n' +
  '- 多次使用 stop 正常保存、完全退出并重新启动，验证 SQLite、玩家存档和返回位置。结束后已恢复默认认证配置并停服。\n' +
  '- 另有此前已通过的 97 项 Java 回归、生产 JAR 内容检查和 NeoForge Mixin/驱动启动检查。\n' +
  '- 构建产物：' + artifact + '；SHA-256：' + hash + '。协议测试运行对应的开发类，生产 JAR 装载仍应作为发布前单独验收。\n\n' +
  '## 范围限制\n\n' +
  '- 无图形协议测试不等于 Minecraft 客户端渲染或目标整合包人工验收。额外背包、任务和其他模组协议仍需联调。\n' +
  '- 工具将 declare_recipes 原始接收，未用原版配方解码器验证 NeoForge 配方扩展；认证、背包、位置和效果等断言均读取实际协议数据。\n' +
  '- 未提供真实 MySQL 服务，只有其驱动加载和代码构建验证。\n' +
  '- 正常保存/停服恢复已测，非正常断电、进程崩溃和维度删除等异常恢复尚未验收。\n' +
  '- RabbitCore 尚未改为消费认证 API。\n\n' +
  sections.join('\n\n') + '\n\n复现步骤见 tools/protocol-test/README.md。随机测试凭据和原始结果 JSON 已忽略，报告不包含密码。\n';
await fs.writeFile(new URL('INTEGRATION_TESTS.md', root), text);
console.log('Protocol report generated: ' + total + ' passed checks across ' + phases.length + ' phases');
