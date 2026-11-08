import { Peer, delay, rcon, queryPosition } from './client.mjs';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import data from 'minecraft-data';

const phase = process.argv[2] ?? 'base';
const stateFile = new URL('./results-state.json', import.meta.url);
let state;
try { state = JSON.parse(await fs.readFile(stateFile, 'utf8')); }
catch { state = { name: 'AuthTest', password: crypto.randomBytes(16).toString('hex'), watchPassword: crypto.randomBytes(16).toString('hex') }; }
const results = [];
const peers = [];
function check(result, description) {
  if (!result) throw new Error('FAILED: ' + description);
  results.push(description); console.log('PASS ' + description);
}
async function persist() { await fs.writeFile(stateFile, JSON.stringify(state, null, 2)); }
async function connect(name = state.name) { const peer = new Peer(name); peers.push(peer); await peer.ready(); return peer; }
async function auth(peer, password) {
  const registered = peer.messages.some(m => m.includes('请登录'));
  await peer.command(registered ? 'login ' + password : 'register ' + password + ' ' + password, '认证成功');
  await delay(150);
}
async function rejected(name, expected) {
  const peer = new Peer(name); peers.push(peer);
  await peer.wait(() => peer.ended, 'rejected connection');
  check(peer.reason.includes(expected), 'connection rejected: ' + expected);
}
try {
  for (let attempt = 0; attempt < 100; attempt++) {
    try { await rcon("list"); break; } catch (error) { if (attempt === 99) throw error; await delay(500); }
  }
  await delay(300);
  await persist();
  if (phase === 'base') {
    const player = await connect();
    check(player.messages.some(m => m.includes('请注册') || m.includes('请登录')), 'vanilla protocol enters world without RabbitAuth client');
    const inventories = player.packets.filter(p => p.name === 'window_items');
    check(inventories.length > 0 && inventories.every(p => p.data.items.every(i => i.itemCount === 0)), 'initial inventory packets are masked');
    await rcon('op ' + state.name);
    await rcon('clear ' + state.name);
    await rcon('item replace entity ' + state.name + ' hotbar.0 with minecraft:diamond 7');
    await rcon('gamemode creative ' + state.name);
    const position = await queryPosition(state.name);
    player.client.write('position', { x: position[0] + 6, y: position[1], z: position[2], onGround: true });
    await player.command('tp @s 100 -60 100');
    await delay(1200);
    check((await queryPosition(state.name)).every((v, i) => Math.abs(v - position[i]) < 0.01), 'movement and privileged commands blocked before login');
    const inventory = await rcon('data get entity ' + state.name + ' Inventory');
    const stone = data('1.21.1').itemsByName.stone.id;
    player.client.write('set_creative_slot', { slot: 37, item: { itemCount: 64, itemId: stone, addedComponentCount: 0, removedComponentCount: 0, components: [], removeComponents: [] } });
    player.client.write('block_dig', { status: 4, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 1 });
    player.client.write('window_click', { windowId: 0, stateId: 0, slot: 36, mouseButton: 0, mode: 0, changedSlots: [], cursorItem: { itemCount: 0 } });
    await delay(350);
    check((await rcon('data get entity ' + state.name + ' Inventory')) === inventory, 'creative slot, drop and container packets cannot mutate inventory');
    await rcon('setblock 2 -60 0 minecraft:stone');
    player.client.write('block_dig', { status: 2, location: { x: 2, y: -60, z: 0 }, face: 1, sequence: 2 });
    await delay(150);
    check((await rcon('execute if block 2 -60 0 minecraft:stone')).includes('Test passed'), 'block destruction blocked');
    const health = await rcon('data get entity ' + state.name + ' Health');
    await rcon('damage ' + state.name + ' 4 minecraft:generic');
    check((await rcon('data get entity ' + state.name + ' Health')) === health, 'damage prevented before authentication');
    const marker = 'RABBITAUTH_CHAT_' + crypto.randomBytes(6).toString('hex');
    player.client.chat(marker);
    await rcon('summon minecraft:item 0.5 -60 0.5 {Item:{id:"minecraft:gold_ingot",count:3}}');
    await delay(1300);
    check(!(await rcon('data get entity ' + state.name + ' Inventory')).includes('gold_ingot'), 'item pickup blocked before authentication');
    check(!(await fs.readFile(new URL('../../run/server/logs/latest.log', import.meta.url), 'utf8')).includes(marker), 'chat blocked before authentication');
    await rcon('kill @e[type=minecraft:item]');
    await auth(player, state.password);
    check(player.packets.filter(p => p.name === 'window_items').some(p => p.data.items.some(i => i.itemCount === 7)), 'actual inventory resynchronized after registration/login');
    await player.command('tp @s 20.5 -60 20.5');
    await delay(350);
    check((await queryPosition(state.name)).every((v, i) => Math.abs(v - [20.5, -60, 20.5][i]) < 0.01), 'gameplay commands available after authentication');
    await rejected(state.name, '已有连接');
    check(!player.ended, 'duplicate connection leaves original authenticated connection online');
    await rejected('authtest', '大小写');
    const newPassword = crypto.randomBytes(16).toString('hex');
    await delay(1100);
    await player.command('changepassword ' + state.password + ' ' + newPassword, '密码修改成功');
    const oldPassword = state.password; state.password = newPassword; await persist();
    await player.close();
    const second = await connect();
    check(second.packets.filter(p => p.name === 'window_items').every(p => p.data.items.every(i => i.itemCount === 0)), 'saved inventory is hidden again on reconnect');
    await second.command('login ' + oldPassword, '密码错误');
    await delay(1100); await second.command('login ' + state.password, '认证成功');
    check(true, 'old password rejected and new password accepted');
    await rcon('gamemode survival ' + state.name);
    await rcon('gamerule keepInventory true');
    await rcon('kill ' + state.name);
    await delay(400);
    second.client.write('client_command', { actionId: 'perform_respawn' });
    await delay(900);
    await second.command('tp @s 24.5 -60 24.5');
    await delay(250);
    check(Math.abs((await queryPosition(state.name))[0] - 24.5) < .01, 'authentication survives death and respawn');
    const reset = crypto.randomBytes(16).toString('hex');
    await rcon('rabbitauth resetpassword ' + state.name + ' ' + reset);
    await second.wait(() => second.ended, 'administrator reset disconnect');
    state.password = reset; await persist();
    check(second.reason.includes('先登录'), 'administrator reset forces reauthentication');
    const third = await connect();
    await third.command('login ' + newPassword, '密码错误'); await delay(1100);
    await third.command('login ' + state.password, '认证成功');
    check(true, 'reset rejects previous password and accepts replacement');
    await rcon('item replace entity ' + state.name + ' hotbar.0 with minecraft:diamond 7');
    await rcon('tp ' + state.name + ' 32.5 -60 32.5');
    await delay(250);
    state.expectedPosition = await queryPosition(state.name);
    state.expectedInventory = await rcon('data get entity ' + state.name + ' Inventory');
    await persist(); await third.close();
    await rejected('authtest', '大小写');
    await rcon('save-all flush');
  } else if (phase === 'restart') {
    const player = await connect();
    check(player.messages.some(m => m.includes('请登录')), 'account survives full server restart');
    check(player.packets.filter(p => p.name === 'window_items').every(p => p.data.items.every(i => i.itemCount === 0)), 'inventory remains hidden before post-restart login');
    await player.command('login ' + state.password, '认证成功');
    await delay(250);
    check((await queryPosition(state.name)).every((v, i) => Math.abs(v - state.expectedPosition[i]) < .01), 'original position survives full server restart');
    check((await rcon('data get entity ' + state.name + ' Inventory')) === state.expectedInventory, 'inventory survives full server restart');
    await player.close();
    const waiting = await connect();
    await delay(200);
    check(waiting.messages.some(m => m.includes('请登录')), 'authentication not skipped with defaults');
    // Keep this unauthenticated connection active when the server saves and stops.
    await rcon('save-all flush');
  } else if (phase === 'pending') {
    const player = await connect();
    await player.command('login ' + state.password, '认证成功');
    await delay(250);
    check((await queryPosition(state.name)).every((v, i) => Math.abs(v - state.expectedPosition[i]) < .01), 'restart while awaiting authentication preserves original return position');
    check((await rcon('data get entity ' + state.name + ' Inventory')) === state.expectedInventory, 'unauthenticated stop does not overwrite inventory');
    await rcon('execute in minecraft:the_nether run tp ' + state.name + ' 1.5 130 1.5');
    await delay(250);
    const netherPosition = await queryPosition(state.name);
    await player.close();
    const returning = await connect();
    check((await rcon('data get entity ' + state.name + ' Dimension')).includes('overworld'), 'authentication location uses configured dimension');
    await returning.command('login ' + state.password, '认证成功');
    await delay(250);
    check((await rcon('data get entity ' + state.name + ' Dimension')).includes('the_nether'), 'login restores original dimension');
    check((await queryPosition(state.name)).every((v, i) => Math.abs(v - netherPosition[i]) < .01), 'cross-dimension return coordinates restored');
    await rcon('execute in minecraft:overworld run tp ' + state.name + ' ' + state.expectedPosition.join(' '));
    await delay(150); await returning.close();
  } else throw new Error('Unknown test phase');
  const log = await fs.readFile(new URL('../../run/server/logs/latest.log', import.meta.url), 'utf8');
  check(!log.includes(state.password), 'authentication password absent from server logs');
  await fs.writeFile(new URL('./results-' + phase + '.json', import.meta.url), JSON.stringify({ phase, checks: results, passed: true }, null, 2));
  console.log('PROTOCOL_TEST_OK ' + phase + ': ' + results.length + ' checks');
  await rcon('stop');
  if (phase === 'restart') for (const peer of peers) if (!peer.ended) await peer.wait(() => peer.ended, 'server shutdown disconnect', 10000);
} catch (error) {
  console.error(error.stack);
  await fs.writeFile(new URL('./results-' + phase + '.json', import.meta.url), JSON.stringify({ phase, checks: results, passed: false, error: error.message }, null, 2));
  process.exitCode = 1;
} finally {
  for (const peer of peers) if (!peer.ended) peer.client.end();
}
