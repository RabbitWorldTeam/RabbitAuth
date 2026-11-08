import { Peer, delay, rcon } from './client.mjs';
import fs from 'node:fs/promises';
const state = JSON.parse(await fs.readFile(new URL('./results-state.json', import.meta.url), 'utf8'));
const checks = [], peers = [];
const check = (value, label) => { if (!value) throw new Error('FAILED: ' + label); checks.push(label); console.log('PASS ' + label); };
for (let i = 0; i < 100; i++) { try { await rcon('list'); break; } catch (error) { if (i === 99) throw error; await delay(500); } }
await delay(300);
try {
  const held = new Peer(state.name, { pauseConfiguration: true }); peers.push(held);
  await held.wait(() => held.pendingPing !== undefined, 'configuration-stage hold');
  const duplicate = new Peer(state.name); peers.push(duplicate);
  await duplicate.wait(() => duplicate.ended, 'pending duplicate rejection');
  check(duplicate.reason.includes('已有连接'), 'duplicate is rejected while original connection is still configuring');
  check(!held.ended, 'pending original connection remains intact');
  held.client.write('pong', { id: held.pendingPing });
  await held.ready(); await held.close();
  const original = new Peer(state.name); peers.push(original); await original.ready();
  original.client.write('chat_command', { command: 'login ' + state.password });
  original.client.end();
  await original.wait(() => original.ended, 'disconnect during password verification');
  const replacement = new Peer(state.name); peers.push(replacement); await replacement.ready();
  await delay(800);
  check(replacement.messages.some(m => m.includes('请登录')) && !replacement.messages.some(m => m.includes('认证成功')),
    'old asynchronous password result cannot authenticate a replacement connection');
  await replacement.command('login IncorrectReplacementPassword', '密码错误');
  check(!replacement.messages.some(m => m.includes('认证成功')), 'replacement still needs its own valid password');
  await delay(1100);
  await replacement.command('login ' + state.password, '认证成功');
  check(true, 'replacement authenticates only after its own valid request');
  await replacement.close();
  await fs.writeFile(new URL('./results-races.json', import.meta.url), JSON.stringify({ passed: true, checks }, null, 2));
  console.log('PROTOCOL_TEST_OK races: ' + checks.length + ' checks');
  await rcon('stop');
} catch (error) {
  console.error(error.stack);
  await fs.writeFile(new URL('./results-races.json', import.meta.url), JSON.stringify({ passed: false, checks, error: error.message }, null, 2));
  process.exitCode = 1;
} finally { for (const peer of peers) if (!peer.ended) peer.client.end(); }
