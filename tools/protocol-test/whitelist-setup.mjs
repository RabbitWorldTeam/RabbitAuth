import fs from 'node:fs/promises';
import crypto from 'node:crypto';
const root = new URL('../../', import.meta.url), server = new URL('run/server/', root);
let config = await fs.readFile(new URL('src/main/resources/auth-defaults.properties', root), 'utf8');
config = config.replace(/^same-ip-auto-login=.*$/m, 'same-ip-auto-login=true')
  .replace(/^command-whitelist=.*$/m, 'command-whitelist=/whitelist(?: .*)?;;/rabbitauth(?: .*)?');
await fs.writeFile(new URL('config/rabbitauth/auth.properties', server), config);
const token = crypto.randomBytes(24).toString('hex');
const path = new URL('world/serverconfig/rabbitcore-bridge-server.toml', server);
await fs.mkdir(new URL('world/serverconfig/', server), { recursive: true });
try { await fs.copyFile(path, new URL('world/serverconfig/rabbitcore-bridge-server.toml.before-whitelist', server), fs.constants.COPYFILE_EXCL); } catch (error) { if (!['ENOENT','EEXIST'].includes(error.code)) throw error; }
await fs.writeFile(path, '[bridge]\nenabled=true\nurl="ws://127.0.0.1:26670/rabbitcore"\ntoken="'+token+'"\nserver_id="rabbit-house"\nexternal_to_game=false\ngame_to_external=false\n[bridge.presence]\nenabled=false\n[bridge.admin.whitelist]\nenabled=true\nsource_ids=["qq:test"]\nsender_ids=["qq:admin"]\n');
await fs.writeFile(new URL('results-whitelist-bridge.json', import.meta.url), JSON.stringify({token}));
console.log('Isolated whitelist/bridge fixture configured');
