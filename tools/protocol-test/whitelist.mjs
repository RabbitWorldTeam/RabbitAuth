import { Peer, delay, rcon } from './client.mjs';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import { WebSocketServer } from 'ws';

const phase = process.argv[2] ?? 'main';
const state = JSON.parse(await fs.readFile(new URL('./results-state.json', import.meta.url), 'utf8'));
const {token} = JSON.parse(await fs.readFile(new URL('./results-whitelist-bridge.json', import.meta.url), 'utf8'));
const checks=[], peers=[], pending=new Map();
let socket, ready=false;
const ws = new WebSocketServer({host:'127.0.0.1',port:26670});
const check=(v,s)=>{if(!v)throw new Error('FAILED: '+s);checks.push(s);console.log('PASS '+s);};
function envelope(type,data,id=crypto.randomUUID(),timestamp=Date.now()){return {version:1,type,id,timestamp,server_id:'rabbit-house',data};}
ws.on('connection',(peer,request)=>{
  if(request.headers.authorization!=='Bearer '+token){peer.terminate();return;}
  socket=peer;
  peer.on('message',buffer=>{
    const packet=JSON.parse(buffer.toString());
    if(packet.type==='hello'){peer.send(JSON.stringify(envelope('ready',{reply_to:packet.id})));ready=true;}
    if(packet.data?.reply_to && pending.has(packet.data.reply_to)){
      pending.get(packet.data.reply_to)(packet.data);pending.delete(packet.data.reply_to);
    }
  });
});
async function request(action,name='',extras={},metadata={}){
  const id=metadata.id??crypto.randomUUID();
  const data={source:{id:metadata.group??'qq:test',name:'test group'},sender:{id:metadata.sender??'qq:admin',name:'same display name'},action,name,...extras};
  const type=metadata.text?'chat.external':'admin.whitelist';
  if(metadata.text)data.text=metadata.text;
  return await new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>{pending.delete(id);reject(new Error('Bridge timeout: '+action));},12000);
    pending.set(id,result=>{clearTimeout(timeout);resolve(result)});
    socket.send(JSON.stringify(envelope(type,data,id,metadata.timestamp??Date.now())));
  });
}
async function command(text){await rcon(text);await delay(150);}
async function connect(){const p=new Peer(state.name);peers.push(p);await p.ready();return p;}
async function deny(name=state.name){const p=new Peer(name);peers.push(p);await p.wait(()=>p.ended,'whitelist rejection');check(p.reason.includes('白名单'),'unlisted/paused connection rejected before world entry');}
try{
  for(let n=0;n<120;n++){try{await rcon('list');if(ready)break;}catch{} if(n===119)throw new Error('Fixture did not start');await delay(500);}
  await delay(250);
  if(phase==='main'){
    await command('whitelist off');
    for (const name of [state.name,'WLCase','WLText','WLPersist']) await request('remove',name);
    check((await request('add',state.name,{}, {sender:'qq:other'})).code==='FORBIDDEN','unauthorized account ID denied despite matching display name');
    check((await request('add',state.name,{}, {group:'qq:other'})).code==='FORBIDDEN','administrator denied outside allowed group');
    check((await request('on')).code==='FORBIDDEN','group administrator cannot toggle global whitelist without explicit action grant');
    check((await request('add','WLExpired',{}, {timestamp:Date.now()-31000})).code==='EXPIRED_REQUEST','expired request rejected before mutation');
    const added=await request('add','authtest');
    check(added.ok&&added.entry.name===state.name,'bridge API uses registered canonical name');
    const nonce=crypto.randomUUID();
    check((await request('add','WLCase',{}, {id:nonce})).ok,'authorized group request reaches real RabbitAuth database');
    check((await request('add','WLCase',{}, {id:nonce})).code==='DUPLICATE_REQUEST','same request UUID does not execute twice');
    const text=await request('add','',{}, {text:'白名单 添加 WLText'});
    check(text.ok&&text.entry.name==='WLText','legacy chat envelope can manage whitelist without ordinary chat forwarding');
    check(text.source_id==='qq:test'&&text.sender_id==='qq:admin','result is targeted to original group and sender');
    check((await request('list','',{page:2})).code==='INVALID_PAGE','out-of-range list page reports error');
    await command('whitelist on');
    check((await request('status')).enabled===true,'vanilla whitelist alias enables custom database authority');
    await deny('WLUnknown');
    const player=await connect();
    await player.command('whitelist off','没有执行');
    check((await request('status')).enabled===true,'unauthenticated OP cannot disable whitelist through allowed command pattern');
    await player.command('login '+state.password,'认证成功');
    await player.command('rabbitauth whitelist list','白名单操作');
    check(true,'authenticated administrator can use nested whitelist commands');
    await player.close();
    const automatic=await connect();
    await automatic.wait(()=>automatic.messages.some(m=>m.includes('认证成功')),'same IP auto-login before revocation');
    check(true,'same-IP ticket works before whitelist revocation');
    check((await request('set',state.name,{enabled:false})).ok,'group administrator can pause membership');
    await automatic.wait(()=>automatic.ended,'pause kick');
    check(automatic.reason.includes('白名单'),'pausing membership immediately kicks authenticated player');
    await deny();
    await request('set',state.name,{enabled:true});
    const reenabled=await connect();
    check(reenabled.messages.some(m=>m.includes('请登录'))&&!reenabled.messages.some(m=>m.includes('认证成功')),'pause revokes ticket instead of granting it again on forced logout');
    await reenabled.command('login '+state.password,'认证成功');
    const removed=await request('remove',state.name);
    check(removed.removed===true&&removed.name===state.name&&!removed.entry,'remove result does not misreport enabled membership');
    await reenabled.wait(()=>reenabled.ended,'remove kick');
    check(reenabled.reason.includes('白名单'),'removing membership immediately kicks online player');
    await deny();
    await command('whitelist add '+state.name);
    check((await request('info',state.name)).ok,'console add writes same whitelist used by group API');
    const restored=await connect();
    check(restored.messages.some(m=>m.includes('请登录')),'removal keeps account password and requires fresh authentication');
    await restored.command('login '+state.password,'认证成功');
    await restored.command('whitelist set '+state.name+' enabled false');
    await restored.wait(()=>restored.ended,'in-game pause kick');
    check(restored.reason.includes('白名单'),'in-game state modification shares immediate kick enforcement');
    await request('set',state.name,{enabled:true});
    await request('add','WLPersist');
    await request('set','WLPersist',{enabled:false});
    check((await request('info','WLPersist')).entry.actor==='bridge:qq:test:qq:admin','persistent record tracks exact group and account actor IDs');
    await request('remove','WLText');
    const held=new Peer(state.name,{pauseConfiguration:true});peers.push(held);
    await held.wait(()=>held.pendingPing!==undefined,'configuration-stage player');
    await request('set',state.name,{enabled:false});
    await held.wait(()=>held.ended,'configuration-stage revocation');
    check(held.reason.includes('白名单'),'pause also kicks a connection still in configuration phase');
    await request('set',state.name,{enabled:true});
  }else{
    const status=await request('status');
    check(status.ok&&status.enabled,'global whitelist setting survives real restart');
    const paused=await request('info','WLPersist');
    check(paused.ok&&!paused.entry.enabled,'paused membership survives restart');
    check((await request('info','WLText')).code==='NOT_FOUND','removed membership stays removed after restart');
    await deny('WLPersist');
    const player=await connect();await player.command('login '+state.password,'认证成功');
    check(true,'approved account and password remain usable after restart');
    await player.close();
    await command('whitelist off');
    check((await request('status')).enabled===false,'console can switch custom whitelist off');
    const outsider=new Peer('WLFree');peers.push(outsider);await outsider.ready();
    check(outsider.messages.some(m=>m.includes('请注册')),'global off permits unlisted registration flow');
    await outsider.close();
  }
  for(const p of peers)if(!p.ended)await p.close();
  await fs.writeFile(new URL('./results-whitelist-'+phase+'.json',import.meta.url),JSON.stringify({passed:true,checks},null,2));
  console.log('WHITELIST_TEST_OK '+phase+': '+checks.length+' checks');
  await rcon('stop');
}catch(error){
  console.error(error.stack);
  await fs.writeFile(new URL('./results-whitelist-'+phase+'.json',import.meta.url),JSON.stringify({passed:false,checks,error:error.message},null,2));
  process.exitCode=1;
}finally{
  for(const p of peers)if(!p.ended)p.client.end();
  for(const client of ws.clients)client.terminate();
  await new Promise(resolve=>ws.close(resolve));
}
