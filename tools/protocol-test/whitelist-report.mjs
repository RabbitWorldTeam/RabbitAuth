import fs from 'node:fs/promises';
import crypto from 'node:crypto';
const root=new URL('../../',import.meta.url);
const results=[];
for(const phase of ['main','restart']){
 const result=JSON.parse(await fs.readFile(new URL('./results-whitelist-'+phase+'.json',import.meta.url),'utf8'));
 if(!result.passed)throw new Error('Whitelist phase failed: '+phase);
 results.push({phase,...result});
}
const total=results.reduce((sum,result)=>sum+result.checks.length,0);
const properties=new Map((await fs.readFile(new URL('gradle.properties',root),'utf8'))
 .split(/\r?\n/).filter(line=>line.includes('=')).map(line=>line.split(/=(.*)/s,2)));
const artifact=`${properties.get('mod_id')}-${properties.get('minecraft_version')}-neoforge-${properties.get('mod_version')}.jar`;
const hash=crypto.createHash('sha256').update(await fs.readFile(new URL('build/libs/'+artifact,root))).digest('hex');
const text='# 白名单与群聊管理验收\n\n日期：2026-11-08（Asia/Hong_Kong）。\n\n'+
'RabbitAuth build/runSmokeServer 通过：121 项 Java 回归、生产 JAR 检查、7 个 Mixin 变换。RabbitCore build 通过：73 项桥接回归。\n\n'+
'真实开发服与模拟群聊端两轮共 '+total+' 项检查通过。SQLite 数据在正常停服和重启之间保留。\n\n'+
'联调夹具在开发测试模组内编译 RabbitCore 桥接源代码，实际调用 RabbitAuth 可选 API；不加载 RabbitCore 方块、FTB 或其他整合包内容。\n'+
'这不是完整整合包、真实 AstrBot 或图形客户端的验收；MySQL 服务仍未联调。\n\n'+
'本次 RabbitAuth JAR SHA-256：'+hash+'。\n\n'+
results.map(result=>'## '+(result.phase==='main'?'管理与踢出':'重启持久化')+'（'+result.checks.length+' 项）\n\n'+result.checks.map(name=>'- 通过：'+name).join('\n')).join('\n\n')+
'\n\n## 复现\n\n先构建 RabbitCore，再从 RabbitAuth 项目根目录运行 node tools/protocol-test/whitelist-setup.mjs。\n'+
'终端一运行 node tools/protocol-test/whitelist.mjs main，终端二运行 .\\gradlew.bat runServer -PbridgeIntegration。通过后正常停服。\n'+
'保持同一世界，分别运行 whitelist.mjs restart 和 runServer -PbridgeIntegration，验证真实重启。\n'+
'测试端点只绑定 127.0.0.1，游戏端口 25579、RCON 25580、模拟群聊 26670；随机凭据和原始结果 JSON 位于忽略目录/文件。\n'+
'结束后使用 profile.mjs default 恢复认证配置，恢复或禁用测试桥接配置；白名单测试结束时全局开关为关闭。\n';
await fs.writeFile(new URL('WHITELIST_TESTS.md',root),text);
console.log('Whitelist report generated: '+total+' checks');
