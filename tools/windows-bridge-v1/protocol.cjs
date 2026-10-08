'use strict';
const crypto = require('node:crypto');
const OPS = new Set(['status', 'launch', 'close_game', 'install_package', 'focus', 'capture', 'click', 'key', 'collect_logs', 'stop']);
const KEYS = new Set(['SPACE', 'ENTER', 'ESC', 'R', 'TAB', 'LEFT', 'RIGHT', 'UP', 'DOWN']);
const SESSION_RE = /^[a-f0-9]{32}$/;
function plain(value) {
  return value && typeof value === 'object' && !Array.isArray(value);
}
function fields(value, allowed) {
  if (!plain(value) || Object.keys(value).some(k => !allowed.includes(k))) throw new Error('Unexpected fields');
}
function integer(value, min, max) {
  return Number.isSafeInteger(value) && value >= min && value <= max;
}
function validate(command, session, lastSeq, now = Date.now()) {
  fields(command, ['version', 'session', 'seq', 'expires_at', 'op', 'args']);
  if (command.version !== 1) throw new Error('Unsupported protocol');
  if (!SESSION_RE.test(session) || command.session !== session) throw new Error('Different session');
  if (!integer(command.seq, 1, Number.MAX_SAFE_INTEGER) || command.seq <= lastSeq) throw new Error('Already seen sequence');
  if (typeof command.expires_at !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{3})?Z$/.test(command.expires_at)) throw new Error('UTC expiry required');
  const expires = Date.parse(command.expires_at);
  if (!Number.isFinite(expires) || expires <= now || expires > now + 300000) throw new Error('Expired command or expiry over five minutes');
  if (!OPS.has(command.op)) throw new Error('Operation is not allowed');
  const a = command.args ?? {};
  const allowed = command.op === 'click' ? ['x', 'y', 'after_ms', 'capture'] :
    command.op === 'key' ? ['key', 'hold_ms', 'after_ms', 'capture'] :
    command.op === 'launch' ? ['capture'] : [];
  fields(a, allowed);
  if ('capture' in a && typeof a.capture !== 'boolean') throw new Error('Invalid capture option');
  if ('after_ms' in a && !integer(a.after_ms, 0, 3000)) throw new Error('Delay must be 0..3000 ms');
  if (command.op === 'click' && (!Number.isFinite(a.x) || !Number.isFinite(a.y) || a.x < 0 || a.x > 1 || a.y < 0 || a.y > 1)) throw new Error('Click must be inside the game client area');
  if (command.op === 'key' && (!KEYS.has(a.key) || ('hold_ms' in a && !integer(a.hold_ms, 10, 1500)))) throw new Error('Invalid game key or duration');
  return { ...command, args: { ...a } };
}
function sha256(buffer) { return crypto.createHash('sha256').update(buffer).digest('hex'); }
function resultPath(session, seq) {
  if (!SESSION_RE.test(session) || !integer(seq, 1, Number.MAX_SAFE_INTEGER)) throw new Error('Invalid result identity');
  return `remote/windows/results/${session}/${seq}.json`;
}
module.exports = { validate, sha256, resultPath };
