'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const readline = require('node:readline');
const { spawn } = require('node:child_process');
const { validate, resultPath, sha256 } = require('./protocol.cjs');

const REPO = 'IZKGMD/AutoGDBot';
const BRANCH = 'remote/windows-bridge-v1';
const COMMAND = 'remote/windows/command.json';
const STATUS = 'remote/windows/status.json';
const MAX_FILE = 600000;
function atomicJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temporary = `${file}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify(value), { mode: 0o600 });
  fs.renameSync(temporary, file);
}
class GitHub {
  constructor(token, options = {}) {
    this.token = token;
    this.repo = options.repo || REPO;
    this.branch = options.branch || BRANCH;
    this.base = options.base || 'https://api.github.com';
    this.timeout = options.timeout || 10000;
    this.fetch = options.fetch || globalThis.fetch;
    this.etag = null;
  }
  fileUrl(file) {
    const parts = file.split('/');
    if (parts.some(p => !p || p === '.' || p === '..')) throw new Error('Invalid repository path');
    return `${this.base}/repos/${this.repo}/contents/${parts.map(encodeURIComponent).join('/')}`;
  }
  async request(url, method = 'GET', body, extra = {}) {
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), this.timeout);
    try {
      const response = await this.fetch(url, {
        method, redirect: 'error', signal: abort.signal,
        headers: {
          Authorization: `Bearer ${this.token}`, 'User-Agent': 'AutoGDBot-Windows-Bridge/1',
          Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
          ...(body ? { 'Content-Type': 'application/json' } : {}), ...extra,
        }, body: body ? JSON.stringify(body) : undefined,
      });
      if (response.status === 304) return { notModified: true };
      if (response.status === 404) return null;
      if (!response.ok) {
        const retry = response.headers.get('retry-after');
        const reset = response.headers.get('x-ratelimit-reset');
        let retryMs = retry ? Math.max(1000, Number(retry) * 1000) : 3000;
        if (response.status === 403 && reset) retryMs = Math.max(retryMs, Number(reset) * 1000 - Date.now());
        const error = new Error(`GitHub HTTP ${response.status}. Check token permissions, expiry and connection.`);
        error.retryMs = Math.min(3600000, Number.isFinite(retryMs) ? retryMs : 3000);
        throw error;
      }
      const text = await response.text();
      if (Buffer.byteLength(text) > 1500000) throw new Error('GitHub response is too large');
      return { data: JSON.parse(text), etag: response.headers.get('etag') };
    } catch (error) {
      if (error.name === 'AbortError' || error instanceof TypeError) throw new Error('GitHub connection failed or timed out');
      throw error;
    } finally { clearTimeout(timer); }
  }
  async check() {
    const r = await this.request(`${this.base}/repos/${this.repo}`);
    if (!r || r.data.private !== true || String(r.data.full_name).toLowerCase() !== this.repo.toLowerCase()) throw new Error('Expected private AutoGDBot repository is unavailable');
    const command = await this.get(COMMAND);
    if (!command) throw new Error('Remote command branch is unavailable');
  }
  async get(file, conditional = false) {
    const r = await this.request(`${this.fileUrl(file)}?ref=${encodeURIComponent(this.branch)}`, 'GET', undefined,
      conditional && this.etag ? { 'If-None-Match': this.etag } : {});
    if (!r || r.notModified) return r;
    if (r.data.type !== 'file' || r.data.encoding !== 'base64' || r.data.size > MAX_FILE) throw new Error('Unexpected repository file');
    const bytes = Buffer.from(r.data.content, 'base64');
    if (bytes.length > MAX_FILE) throw new Error('Repository file is too large');
    if (conditional) this.etag = r.etag;
    return { sha: r.data.sha, json: JSON.parse(bytes.toString('utf8')) };
  }
  async put(file, value) {
    if (!(file === STATUS || /^remote\/windows\/results\/[a-f0-9]{32}\/\d+\.json$/.test(file))) throw new Error('Write outside remote result directory refused');
    const bytes = Buffer.from(JSON.stringify(value), 'utf8');
    if (bytes.length > MAX_FILE) throw new Error('Result exceeds 600 KB');
    for (let attempt = 0; attempt < 3; attempt++) {
      const existing = await this.get(file);
      try {
        const result = await this.request(this.fileUrl(file), 'PUT', {
          message: `Windows bridge: ${file === STATUS ? 'status' : 'result'}`,
          branch: this.branch, content: bytes.toString('base64'), ...(existing ? { sha: existing.sha } : {}),
        });
        if (!result) throw new Error('GitHub write failed: repository or write permission unavailable');
        return;
      } catch (error) { if (!/HTTP 409/.test(error.message) || attempt === 2) throw error; }
    }
  }
}
class Worker {
  constructor(gameDirectory) {
    this.next = 0; this.pending = new Map(); this.closed = false;
    const env = { ...process.env };
    delete env.AUTOGDBOT_GITHUB_TOKEN;
    this.child = spawn('powershell.exe', ['-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      path.join(__dirname, 'worker.ps1'), '-GameDirectory', gameDirectory], { env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    this.child.stderr.on('data', () => {});
    readline.createInterface({ input: this.child.stdout }).on('line', line => {
      let message; try { message = JSON.parse(line); } catch { return; }
      const entry = this.pending.get(message.id); if (!entry) return;
      this.pending.delete(message.id); clearTimeout(entry.timer);
      message.ok ? entry.resolve(message.data) : entry.reject(new Error(message.error || 'Windows action failed'));
    });
    const failed = () => {
      this.closed = true;
      for (const entry of this.pending.values()) { clearTimeout(entry.timer); entry.reject(new Error('Windows worker exited')); }
      this.pending.clear();
    };
    this.child.on('error', failed); this.child.on('exit', failed);
    this.child.stdin.on('error', failed);
  }
  rpc(op, args = {}) {
    if (this.closed) return Promise.reject(new Error('Windows worker is unavailable'));
    return new Promise((resolve, reject) => {
      const id = ++this.next;
      const timer = setTimeout(() => {
        this.pending.delete(id); this.child.kill();
        reject(new Error('Windows worker timed out; session stopped to avoid uncertain input'));
      }, 35000);
      this.pending.set(id, { resolve, reject, timer });
      this.child.stdin.write(JSON.stringify({ id, op, args }) + '\n');
    });
  }
  close() { if (!this.closed) this.child.stdin.end(); }
}
class Session {
  constructor({ github, worker, directory, session = crypto.randomBytes(16).toString('hex'), now = Date.now }) {
    this.github = github; this.worker = worker; this.directory = directory; this.session = session; this.now = now;
    this.lastSeq = 0; this.stopped = false; this.lastResult = null;
    fs.mkdirSync(path.join(directory, 'outbox'), { recursive: true });
  }
  async announce(state = 'ready') {
    const game = await this.worker.rpc('status');
    if (game.stopped) { this.stopped = true; state = 'stopped'; }
    await this.github.put(STATUS, {
      version: 1, session: this.session, updated_at: new Date(this.now()).toISOString(), state,
      last_seq: this.lastSeq, last_result: this.lastResult, game,
      capabilities: ['status', 'launch', 'close_game', 'install_package', 'focus', 'capture', 'click', 'key', 'collect_logs', 'stop'],
    });
  }
  async flush() {
    const dir = path.join(this.directory, 'outbox');
    const files = fs.readdirSync(dir).filter(x => /^\d+-[a-f0-9]{32}-\d+\.json$/.test(x)).sort();
    for (const file of files) {
      const full = path.join(dir, file), item = JSON.parse(fs.readFileSync(full, 'utf8'));
      await this.github.put(item.path, item.report);
      fs.unlinkSync(full);
    }
  }
  async execute(raw) {
    const command = validate(raw, this.session, this.lastSeq, this.now());
    // Claim BEFORE input: retries of GitHub uploads never repeat a local action.
    this.lastSeq = command.seq;
    atomicJson(path.join(this.directory, 'session.json'), { session: this.session, last_seq: this.lastSeq });
    const report = {
      version: 1, session: this.session, seq: command.seq, op: command.op,
      started_at: new Date(this.now()).toISOString(), ok: false,
    };
    try {
      if (this.stopped) throw new Error('Session has stopped');
      if (command.op === 'stop') {
        this.stopped = true; await this.worker.rpc('stop'); report.data = { stopped: true };
      } else {
        if (command.op === 'install_package') {
          const fetched = await this.github.get('remote/windows/package.json');
          if (!fetched) throw new Error('No bot package queued');
          const packageData = fetched.json;
          if (packageData.id !== 'autogdbot.realtime' || typeof packageData.base64 !== 'string' || !/^[a-f0-9]{64}$/.test(packageData.sha256)) throw new Error('Invalid bot package manifest');
          const bytes = Buffer.from(packageData.base64, 'base64');
          if (bytes.length < 100 || bytes.length > 440000 || sha256(bytes) !== packageData.sha256) throw new Error('Bot package hash or size mismatch');
          // The Windows worker independently checks the ZIP identity and the x64 DLL.
          fs.writeFileSync(path.join(this.directory, 'package.geode'), bytes);
        }
        report.data = await this.worker.rpc(command.op, command.args);
        if (command.args.capture) {
          const delay = command.args.after_ms ?? 400;
          const end = this.now() + delay;
          while (!this.stopped && this.now() < end) await new Promise(r => setTimeout(r, 50));
          if (!this.stopped) {
            try { report.image = await this.worker.rpc('capture'); }
            catch (error) { report.capture_error = error.message; }
          }
        }
      }
      report.ok = true;
    } catch (error) { report.error = error.message; }
    report.finished_at = new Date(this.now()).toISOString();
    const bytes = Buffer.from(JSON.stringify(report));
    if (bytes.length > MAX_FILE) {
      delete report.image;
      if (report.data && report.data.base64) report.data = { error: 'Screenshot result exceeded size limit' };
      report.truncated = true;
    }
    const remote = resultPath(this.session, command.seq);
    const file = path.join(this.directory, 'outbox', `${this.now()}-${this.session}-${command.seq}.json`);
    const pending = fs.readdirSync(path.join(this.directory, 'outbox')).length;
    if (pending >= 20) { this.stopped = true; throw new Error('Too many unsent results; session stopped'); }
    atomicJson(file, { path: remote, report });
    this.lastResult = remote;
    await this.flush();
    await this.announce(this.stopped ? 'stopped' : 'ready');
    return report;
  }
}
async function main() {
  if (process.platform !== 'win32') throw new Error('The agent runs on Windows only');
  if (Number(process.versions.node.split('.')[0]) < 18) throw new Error('Node.js 18 or newer is required');
  const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));
  if (!config.gameDirectory || !path.isAbsolute(config.gameDirectory) || !fs.existsSync(path.join(config.gameDirectory, 'GeometryDash.exe'))) throw new Error('Choose the GeometryDash.exe file again');
  const token = process.env.AUTOGDBOT_GITHUB_TOKEN;
  delete process.env.AUTOGDBOT_GITHUB_TOKEN;
  if (!token || !token.startsWith('github_pat_')) throw new Error('A fine-grained GitHub token is required');
  const github = new GitHub(token);
  await github.check();
  const stateDir = path.join(__dirname, 'local-state');
  fs.mkdirSync(stateDir, { recursive: true });
  const lock = path.join(stateDir, 'agent.lock');
  // A second agent must never send concurrent input. Recover only a dead process's lock.
  if (fs.existsSync(lock)) {
    const oldPid = Number(fs.readFileSync(lock, 'utf8'));
    if (!Number.isSafeInteger(oldPid) || oldPid <= 0) throw new Error('Invalid old lock. Close all bridge consoles, then delete local-state/agent.lock locally.');
    let dead = false;
    try { process.kill(oldPid, 0); } catch (error) { if (error.code === 'ESRCH') dead = true; else throw new Error('Cannot check old bridge process; close it and remove local-state/agent.lock locally.'); }
    if (!dead) throw new Error('A bridge is already running. Close its console first.');
    fs.unlinkSync(lock);
  }
  const fd = fs.openSync(lock, 'wx');
  fs.writeFileSync(fd, String(process.pid)); fs.closeSync(fd);
  const worker = new Worker(path.resolve(config.gameDirectory));
  const session = new Session({ github, worker, directory: stateDir });
  const stopFile = path.join(__dirname, 'STOP.txt');
  let safetyTimer, safetyBusy = false;
  const stop = () => { session.stopped = true; };
  process.once('SIGINT', stop); process.once('SIGTERM', stop);
  const end = Date.now() + 60 * 60 * 1000;
  let heartbeat = 0, failureWait = 3000, lastRejection = null;
  try {
    if (fs.existsSync(stopFile)) throw new Error('Remove STOP.txt locally before starting a new session');
    await session.flush();
    await session.announce();
    if (session.stopped) throw new Error('Stopped locally before connection completed');
    heartbeat = Date.now();
    console.log('CONNECTED. Tell the assistant: bridge is running. F8 stops control.');
    console.log('Session expires in 60 minutes. Keep this console open.');
    safetyTimer = setInterval(async () => {
      if (fs.existsSync(stopFile)) session.stopped = true;
      if (safetyBusy || session.stopped) return;
      safetyBusy = true;
      try { if ((await worker.rpc('safety')).stopped) session.stopped = true; }
      catch { session.stopped = true; }
      finally { safetyBusy = false; }
    }, 500);
    while (!session.stopped && Date.now() < end) {
      try {
        await session.flush();
        const command = await github.get(COMMAND, true);
        if (session.stopped) break;
        if (command && !command.notModified && command.json?.session === session.session) {
          const signature = sha256(Buffer.from(JSON.stringify(command.json)));
          try {
            validate(command.json, session.session, session.lastSeq);
            console.log(`Command ${command.json.seq}: ${command.json.op}`);
            await session.execute(command.json);
            heartbeat = Date.now();
          } catch (error) {
            if (signature !== lastRejection) console.log(`Command processing: ${error.message}. Check its saved result; input is never retried automatically.`);
            lastRejection = signature;
          }
        }
        if (!session.stopped && Date.now() - heartbeat >= 60000) { await session.announce(); heartbeat = Date.now(); }
        failureWait = 3000;
      } catch (error) {
        console.log(error.message);
        failureWait = Math.min(3600000, Math.max(failureWait, error.retryMs || 0));
        failureWait = Math.min(3600000, failureWait * 2);
      }
      const next = Date.now() + failureWait;
      while (!session.stopped && Date.now() < next && Date.now() < end) await new Promise(r => setTimeout(r, 100));
    }
  } finally {
    session.stopped = true;
    clearInterval(safetyTimer);
    try { await worker.rpc('stop'); } catch {}
    try { await session.announce('stopped'); } catch {}
    worker.close();
    try { fs.unlinkSync(lock); } catch {}
    console.log('STOPPED. No further game commands will be executed.');
  }
}
module.exports = { GitHub, Session, atomicJson };
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
