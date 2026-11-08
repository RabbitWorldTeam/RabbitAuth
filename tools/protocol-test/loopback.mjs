import { Peer, delay, rcon } from './client.mjs';
import fs from 'node:fs/promises';
const ipv6 = process.env.RABBITAUTH_TEST_HOST === '::1';
const checks = [], peers = [];
function check(result, description) { if (!result) throw new Error('FAILED: ' + description); checks.push(description); console.log('PASS ' + description); }
for (let i = 0; i < 100; i++) { try { await rcon('list'); break; } catch (error) { if (i === 99) throw error; await delay(500); } }
await delay(300);
try {
  const player = new Peer('AuthTest'); peers.push(player); await player.ready();
  check(player.messages.some(m => m.includes('CUSTOM_LOGIN_OK')), (ipv6 ? 'IPv6 ::1' : 'IPv4 127.0.0.1') + ' registered account bypasses password with explicit loopback option');
  check(!player.messages.some(m => m.includes('Login:')), 'loopback bypass does not require password input');
  const newcomer = new Peer(ipv6 ? 'AuthLoop6' : 'AuthLoop4'); peers.push(newcomer); await newcomer.ready();
  check(newcomer.messages.some(m => m.includes('Register:')), 'unregistered loopback connection still requires registration');
  check(!newcomer.messages.some(m => m.includes('CUSTOM_LOGIN_OK')), 'loopback does not silently authenticate an unregistered account');
  await newcomer.command('register a b', 'Passwords do not match');
  check(true, 'English fallback messages work alongside custom success override');
  const wrongCase = new Peer('authtest'); peers.push(wrongCase); await wrongCase.wait(() => wrongCase.ended, 'case rejection');
  check(wrongCase.reason.includes('Use registered name'), 'case protection remains active with loopback bypass');
  const duplicate = new Peer('AuthTest'); peers.push(duplicate); await duplicate.wait(() => duplicate.ended, 'duplicate rejection');
  check(duplicate.reason.includes('active connection') && !player.ended, 'duplicate protection remains active with loopback bypass');
  for (const peer of peers) if (!peer.ended) await peer.close();
  await fs.writeFile(new URL('./results-loopback' + (ipv6 ? '6' : '4') + '.json', import.meta.url), JSON.stringify({ passed: true, checks }, null, 2));
  console.log('PROTOCOL_TEST_OK loopback' + (ipv6 ? '6' : '4') + ': ' + checks.length + ' checks');
  await rcon('stop');
} catch (error) {
  console.error(error.stack);
  await fs.writeFile(new URL('./results-loopback' + (ipv6 ? '6' : '4') + '.json', import.meta.url), JSON.stringify({ passed: false, checks, error: error.message }, null, 2));
  process.exitCode = 1;
} finally { for (const peer of peers) if (!peer.ended) peer.client.end(); }
