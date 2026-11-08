import fs from 'node:fs/promises';
import crypto from 'node:crypto';

const root = new URL('../../', import.meta.url);
const server = new URL('run/server/', root);
await fs.mkdir(new URL('config/rabbitauth/languages/', server), { recursive: true });
const propertiesFile = new URL('server.properties', server);
let properties;
try {
  properties = await fs.readFile(propertiesFile, 'utf8');
  if (!properties.includes('RabbitAuth isolated test') || !properties.includes('server-port=25579'))
    throw new Error('Refusing to reuse a server that is not the isolated RabbitAuth test instance');
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
  properties = [
    'online-mode=false', 'server-ip=127.0.0.1', 'server-port=25579', 'enforce-secure-profile=false',
    'view-distance=2', 'simulation-distance=2', 'spawn-protection=0', 'level-type=minecraft:flat',
    'generate-structures=false', 'max-players=10', 'motd=RabbitAuth isolated test',
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","lakes":false,"features":false}'
  ].join('\n') + '\n';
}
if (!/^rcon.password=.+$/m.test(properties)) properties += 'rcon.password=' + crypto.randomBytes(24).toString('hex') + '\n';
properties = properties.replace(/^enable-rcon=.*$/m, '').replace(/^rcon.port=.*$/m, '');
properties += '\nenable-rcon=true\nrcon.port=25580\nbroadcast-rcon-to-ops=false\n';
await fs.writeFile(propertiesFile, properties);
try { await fs.access(new URL('eula.txt', server)); }
catch { await fs.writeFile(new URL('eula.txt', server), 'eula=false\n'); }
for (const language of ['zh-CN', 'en-US']) {
  await fs.copyFile(new URL('src/main/resources/languages/' + language + '.properties', root),
    new URL('config/rabbitauth/languages/' + language + '.properties', server));
}
await fs.copyFile(new URL('src/main/resources/auth-defaults.properties', root),
  new URL('config/rabbitauth/auth.properties', server));
console.log('Prepared isolated local test directory; existing EULA agreement preserved. Read and accept Minecraft EULA before starting a new test instance.');
