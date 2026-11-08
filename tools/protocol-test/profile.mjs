import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const root = new URL('../../', import.meta.url);
const profile = process.argv[2] ?? 'default';
if (!['default', 'options', 'loopback4', 'loopback6'].includes(profile)) throw new Error('Unknown isolated profile');
const server = new URL('run/server/', root);
let properties = await fs.readFile(new URL('server.properties', server), 'utf8');
if (!properties.includes('RabbitAuth isolated test') || !properties.includes('server-port=25579'))
  throw new Error('Refusing to modify a directory that is not the isolated test server');
let auth = await fs.readFile(new URL('src/main/resources/auth-defaults.properties', root), 'utf8');
const options = profile === 'options' ? {
  'same-ip-auto-login': 'true', 'auto-login-timeout-minutes': '1', 'reenter-cooldown-ticks': '20',
  'max-registrations-per-ip': '1', 'max-connections-per-ip': '2', 'before-login-allow-chat': 'true',
  'blindness-before-login': 'true', 'command-whitelist': '/say(?: .*)?', 'login-timeout-seconds': '10'
} : profile.startsWith('loopback') ? { 'loopback-auto-login': 'true', language: 'en-US' } : {};
for (const [key, value] of Object.entries(options)) auth = auth.replace(new RegExp('^' + key + '=.*$', 'm'), key + '=' + value);
await fs.writeFile(new URL('config/rabbitauth/auth.properties', server), auth);
for (const language of ['en-US', 'zh-CN']) {
  let messages = await fs.readFile(new URL('src/main/resources/languages/' + language + '.properties', root), 'utf8');
  if (profile.startsWith('loopback') && language === 'en-US') messages = messages.replace(/^SUCCESS=.*$/m, 'SUCCESS=CUSTOM_LOGIN_OK');
  await fs.writeFile(new URL('config/rabbitauth/languages/' + language + '.properties', server), messages);
}
properties = properties.replace(/^server-ip=.*$/m, 'server-ip=' + (profile === 'loopback6' ? '::1' : '127.0.0.1'));
await fs.writeFile(new URL('server.properties', server), properties);
console.log('Isolated server profile configured: ' + profile + ' (' + fileURLToPath(server) + ')');
