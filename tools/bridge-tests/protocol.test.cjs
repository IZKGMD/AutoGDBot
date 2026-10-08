'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');
const { GitHub, Session } = require('../windows-bridge-v1/agent.cjs');
const { validate } = require('../windows-bridge-v1/protocol.cjs');
const SESSION = 'a'.repeat(32);
const NOW = Date.now();
const command = (extra = {}) => ({ version: 1, session: SESSION, seq: 1,
  expires_at: new Date(NOW + 60000).toISOString(), op: 'status', args: {}, ...extra });

test('fresh session accepts game commands; stale sessions, replays and expirations fail', () => {
  assert.equal(validate(command(), SESSION, 0, NOW).op, 'status');
  for (const change of [ {session:'b'.repeat(32)}, {seq:0}, {seq:1.5}, {seq:Number.MAX_SAFE_INTEGER+1},
    {expires_at:new Date(NOW-1).toISOString()}, {expires_at:new Date(NOW+300001).toISOString()},
    {expires_at:'tomorrow'}, {version:2}, {op:'shell'}, {op:'download'}, {program:'cmd.exe'} ]) {
    assert.throws(() => validate(command(change), SESSION, 0, NOW));
  }
  assert.throws(() => validate(command(), SESSION, 1, NOW));
});
test('input coordinates, keys, delays and arguments are bounded', () => {
  assert.doesNotThrow(() => validate(command({op:'click',args:{x:0.25,y:0.8,capture:true,after_ms:200}}),SESSION,0,NOW));
  assert.doesNotThrow(() => validate(command({op:'key',args:{key:'SPACE',hold_ms:1500}}),SESSION,0,NOW));
  for (const args of [{x:-1,y:0}, {x:1.001,y:0}, {x:0,y:NaN}, {x:0}, {x:0,y:0,capture:'true'},
    {x:0,y:0,after_ms:3001}, {x:0,y:0,window:'other app'}]) {
    assert.throws(() => validate(command({op:'click',args}),SESSION,0,NOW));
  }
  for (const args of [{key:'WIN'}, {key:'CTRL'}, {key:'SPACE',hold_ms:1501}, {key:'SPACE',hold_ms:0},
    {key:'SPACE',hold_ms:'80'}, {key:'SPACE',script:'alert(1)'}]) {
    assert.throws(() => validate(command({op:'key',args}),SESSION,0,NOW));
  }
  assert.throws(() => validate(command({op:'collect_logs',args:{path:'C:/Users'}}),SESSION,0,NOW));
});
function fixture(github) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'gdbridge-'));
  const calls = [];
  const worker = { async rpc(op,args) { calls.push({op,args}); return op === 'capture' ? {mime:'image/jpeg',base64:'YQ=='} : {running:true}; } };
  const session = new Session({github,worker,directory:dir,session:SESSION,now:()=>NOW});
  return {dir,calls,session};
}
test('action is claimed before network failure; upload retry cannot repeat a key press', async () => {
  let failed = true; const writes=[];
  const f = fixture({async put(file,data) { if(failed) throw new Error('offline'); writes.push({file,data}); }});
  try {
    await assert.rejects(f.session.execute(command({op:'key',args:{key:'ENTER'}})),/offline/);
    assert.equal(f.calls.filter(x=>x.op==='key').length,1);
    assert.equal(fs.readdirSync(path.join(f.dir,'outbox')).length,1);
    assert.equal(JSON.parse(fs.readFileSync(path.join(f.dir,'session.json'))).last_seq,1);
    failed = false;
    await f.session.flush();
    await assert.rejects(f.session.execute(command({op:'key',args:{key:'ENTER'}})),/Already seen/);
    assert.equal(f.calls.filter(x=>x.op==='key').length,1);
    assert.equal(writes.length,1);
    assert.equal(writes[0].data.ok,true);
  } finally { fs.rmSync(f.dir,{recursive:true,force:true}); }
});
test('F8/local stop forbids input and reports stopped state', async () => {
  const f = fixture({async put() {}});
  try {
    f.session.stopped = true;
    const result = await f.session.execute(command({op:'click',args:{x:.5,y:.5}}));
    assert.equal(result.ok,false);
    assert.equal(f.calls.filter(x=>x.op==='click').length,0);
  } finally { fs.rmSync(f.dir,{recursive:true,force:true}); }
});
test('capture failure preserves the actual action result', async () => {
  const f=fixture({async put() {}});
  f.session.worker.rpc=async op=> {f.calls.push({op}); if(op==='capture')throw new Error('Game lost foreground');return {};};
  f.session.now=Date.now;
  try {
    const result=await f.session.execute(command({op:'click',args:{x:.5,y:.5,capture:true,after_ms:0}}));
    assert.equal(result.ok,true);
    assert.match(result.capture_error,/foreground/);
    assert.equal(f.calls.filter(x=>x.op==='click').length,1);
  } finally { fs.rmSync(f.dir,{recursive:true,force:true}); }
});
test('fresh session cannot execute commands left over from an old run', async () => {
  const f=fixture({async put() {}});
  try {
    f.session.session='b'.repeat(32);
    await assert.rejects(f.session.execute(command({op:'launch'})),/Different session/);
    assert.equal(f.calls.length,0);
  } finally { fs.rmSync(f.dir,{recursive:true,force:true}); }
});
test('package installation accepts only the fixed repository package with a matching hash', async () => {
  const bytes=Buffer.alloc(256,42);
  const packageData={id:'autogdbot.realtime',sha256:crypto.createHash('sha256').update(bytes).digest('hex'),base64:bytes.toString('base64')};
  const f=fixture({async get(file) {assert.equal(file,'remote/windows/package.json');return {json:packageData};},async put(){}});
  try {
    let report=await f.session.execute(command({op:'install_package'}));
    assert.equal(report.ok,true);
    assert.deepEqual(fs.readFileSync(path.join(f.dir,'package.geode')),bytes);
    assert.equal(f.calls.filter(x=>x.op==='install_package').length,1);
    packageData.sha256='0'.repeat(64);
    report=await f.session.execute(command({op:'install_package',seq:2}));
    assert.equal(report.ok,false);assert.match(report.error,/hash/);
    assert.equal(f.calls.filter(x=>x.op==='install_package').length,1);
    packageData.id='other.mod';
    report=await f.session.execute(command({op:'install_package',seq:3}));
    assert.equal(report.ok,false);assert.match(report.error,/manifest/);
  } finally {fs.rmSync(f.dir,{recursive:true,force:true});}
});
test('real HTTP exchange checks private repository, branch, conditional GET and sequential result writes', async () => {
  const files=new Map([['remote/windows/command.json',{sha:'initial',json:command()}]]);
  const requests=[]; let conflict=true;
  const server=http.createServer(async(req,res)=> {
    const url=new URL(req.url,'http://localhost');
    requests.push({method:req.method,url,authorization:req.headers.authorization});
    if(url.pathname==='/repos/IZKGMD/AutoGDBot') {
      res.setHeader('Content-Type','application/json');res.end(JSON.stringify({private:true,full_name:'IZKGMD/AutoGDBot'}));return;
    }
    const prefix='/repos/IZKGMD/AutoGDBot/contents/';
    if(!url.pathname.startsWith(prefix)){res.writeHead(404);res.end('{}');return;}
    const file=decodeURIComponent(url.pathname.slice(prefix.length));
    if(req.method==='GET') {
      assert.equal(url.searchParams.get('ref'),'remote/windows-bridge-v1');
      if(req.headers['if-none-match']==='"tag"'){res.writeHead(304);res.end();return;}
      const entry=files.get(file);
      if(!entry){res.writeHead(404);res.end('{}');return;}
      const bytes=Buffer.from(JSON.stringify(entry.json));
      res.setHeader('ETag','"tag"');res.end(JSON.stringify({type:'file',encoding:'base64',sha:entry.sha,size:bytes.length,content:bytes.toString('base64')}));return;
    }
    const chunks=[];for await(const chunk of req)chunks.push(chunk);
    const body=JSON.parse(Buffer.concat(chunks).toString());
    assert.equal(body.branch,'remote/windows-bridge-v1');
    if(conflict && file==='remote/windows/status.json'){conflict=false;res.writeHead(409);res.end('{}');return;}
    const existing=files.get(file);
    if(existing)assert.equal(body.sha,existing.sha);
    files.set(file,{sha:'sha-'+requests.length,json:JSON.parse(Buffer.from(body.content,'base64').toString())});
    res.writeHead(existing?200:201);res.end(JSON.stringify({commit:{sha:'commit'}}));
  });
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const api=new GitHub('test-local-token',{base:`http://127.0.0.1:${server.address().port}`});
  const f=fixture(api);
  try {
    await api.check();
    const c=await api.get('remote/windows/command.json',true);
    assert.equal(c.json.session,SESSION);
    assert.equal((await api.get('remote/windows/command.json',true)).notModified,true);
    await f.session.announce();
    const report=await f.session.execute(command());
    assert.equal(report.ok,true);
    const stored=files.get(`remote/windows/results/${SESSION}/1.json`).json;
    assert.equal(stored.seq,1);assert.equal(files.get('remote/windows/status.json').json.last_seq,1);
    assert.equal(requests.every(x=>x.authorization==='Bearer test-local-token'),true);
    await assert.rejects(api.put('README.md',{}),/outside/);
    assert.throws(()=>api.fileUrl('../secret'),/Invalid/);
  } finally {fs.rmSync(f.dir,{recursive:true,force:true});await new Promise(r=>server.close(r));}
});
test('public repository and failed writes are never treated as a connected private bridge', async () => {
  const api=new GitHub('dummy',{fetch:async()=>new Response(JSON.stringify({private:false,full_name:'IZKGMD/AutoGDBot'}),{status:200})});
  await assert.rejects(api.check(),/private/);
  const absent=new GitHub('dummy',{fetch:async()=>new Response('{}',{status:404})});
  await assert.rejects(absent.put('remote/windows/status.json',{}),/write failed/);
});
