import mc from 'minecraft-protocol';
import mcData from 'minecraft-data';
import nbt from 'prismarine-nbt';
import net from 'node:net';
import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const host = process.env.RABBITAUTH_TEST_HOST ?? '127.0.0.1';
if (!['127.0.0.1', '::1'].includes(host)) throw new Error('Only local isolated test hosts are allowed');
const serverDirectory = fileURLToPath(new URL('../../run/server/', import.meta.url));
export const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
export function plain(component) {
  const value = component?.type ? nbt.simplify(component) : component;
  if (typeof value === 'string') return value;
  if (value?.text != null) return value.text + (value.extra ?? []).map(plain).join('');
  return JSON.stringify(value);
}
export async function rcon(command) {
  const config = await fs.readFile(serverDirectory + '/server.properties', 'utf8');
  const secret = config.match(/^rcon.password=(.*)$/m)?.[1].trim();
  if (!secret) throw new Error('Isolated server RCON is not configured');
  return await new Promise((resolve, reject) => {
    const socket = net.connect(25580, host);
    let data = Buffer.alloc(0), authenticated = false;
    const timeout = setTimeout(() => done(new Error('RCON timed out')), 10000);
    function done(error, output) {
      clearTimeout(timeout); socket.destroy();
      if (error) reject(error); else resolve(output);
    }
    function send(type, text) {
      const body = Buffer.from(text);
      const packet = Buffer.alloc(body.length + 14);
      packet.writeInt32LE(body.length + 10, 0); packet.writeInt32LE(42, 4);
      packet.writeInt32LE(type, 8); body.copy(packet, 12); socket.write(packet);
    }
    socket.on('connect', () => send(3, secret));
    socket.on('error', error => done(error));
    socket.on('data', chunk => {
      data = Buffer.concat([data, chunk]);
      while (data.length >= 4 && data.length >= data.readInt32LE(0) + 4) {
        const length = data.readInt32LE(0), packet = data.subarray(0, length + 4);
        data = data.subarray(length + 4);
        const id = packet.readInt32LE(4), type = packet.readInt32LE(8);
        if (id === -1) return done(new Error('RCON authentication failed'));
        if (!authenticated && type === 2) { authenticated = true; send(2, command); }
        else if (authenticated && type === 0) return done(null, packet.subarray(12, packet.length - 2).toString());
      }
    });
  });
}
export class Peer {
  constructor(name, options = {}) {
    this.name = name; this.messages = []; this.packets = []; this.ended = false; this.reason = '';
    const data = mcData('1.21.1');
    // Recipe data isn't part of these authentication checks. NeoForge recipe extensions
    // are not decoded by minecraft-data's vanilla recipe schema.
    const customPackets = { [data.version.majorVersion]: { play: { toClient: { types: { packet_declare_recipes: 'restBuffer' } } } } };
    this.client = mc.createClient({ host, port: 25579, username: name, version: '1.21.1', auth: 'offline', customPackets });
    this.client.on('packet', (packet, meta) => {
      if (meta.name === 'ping') {
        if (options.pauseConfiguration) this.pendingPing = packet.id;
        else this.client.write('pong', { id: packet.id });
      }
      if (meta.name === 'position') {
        this.position = packet; this.client.write('teleport_confirm', { teleportId: packet.teleportId });
      }
      if (meta.name === 'system_chat') this.messages.push(plain(packet.content));
      if (meta.name === 'profileless_chat') this.messages.push(plain(packet.message));
      if (meta.name === 'kick_disconnect' || meta.name === 'disconnect') this.reason = plain(packet.reason);
      this.packets.push({ name: meta.name, data: packet });
    });
    this.client.on('end', () => { this.ended = true; });
    this.client.on('error', error => { this.error = error.message; });
  }
  async wait(predicate, description, timeout = 12000) {
    const start = Date.now();
    while (Date.now() - start < timeout) {
      if (predicate()) return;
      if (this.error) throw new Error(this.name + ': ' + this.error);
      await delay(30);
    }
    throw new Error(this.name + ': timeout: ' + description + '; reason=' + this.reason + '; messages=' + this.messages.join('|'));
  }
  async ready() { await this.wait(() => this.position && this.messages.some(m => m.includes('[RabbitAuth]')), 'world and authentication prompt'); }
  async command(command, expected) {
    const start = this.messages.length;
    this.client.write('chat_command', { command });
    if (expected) await this.wait(() => this.messages.slice(start).some(m => m.includes(expected)), 'expected sanitized response');
  }
  async close() { this.client.end(); await this.wait(() => this.ended, 'disconnect', 5000); await delay(250); }
}
export async function queryPosition(name) {
  const result = await rcon('data get entity ' + name + ' Pos');
  const numbers = result.match(/\[([^\]]+)\]/)?.[1].split(',').map(n => parseFloat(n.trim()));
  if (!numbers || numbers.length !== 3) throw new Error('Cannot decode position: ' + result);
  return numbers;
}
