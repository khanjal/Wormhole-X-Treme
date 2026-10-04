'use strict';
// The Lab Dashboard's command channel (lib/remote.js) without a server: what a command may be,
// which browser POSTs the dashboard takes, and the launcher's command port on 127.0.0.1, run
// against a stand-in server. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const http = require('http');
const os = require('os');
const { spawnSync } = require('child_process');
const path = require('path');
const remote = require('../lib/remote');

test('a command loses one leading slash and its spaces, and keeps the rest', () => {
  assert.strictEqual(remote.checkCommand('/dynmap fullrender world'), 'dynmap fullrender world');
  assert.strictEqual(remote.checkCommand('  op Freya '), 'op Freya');
  assert.strictEqual(remote.checkCommand('//wand'), '/wand');
});

test('a command with a line break, a carriage return or a NUL is refused, so it cannot carry a second one', () => {
  assert.throws(() => remote.checkCommand('say hi\nscoreboard players set #fence1 wxfence 1'), /one line/);
  assert.throws(() => remote.checkCommand('say hi\rstop'), /one line/);
  assert.throws(() => remote.checkCommand('say hi\u0000'), /one line/);
});

test('a command with any other control character or a Unicode line separator is refused too', () => {
  for (const c of ['\u007f', '\t', '\u0085', '\u009f', ' ', ' ']) {
    assert.throws(() => remote.checkCommand(`say a${c}b`), /one line/, `U+${c.codePointAt(0).toString(16).padStart(4, '0')}`);
  }
  // Letters outside ASCII are not control characters.
  assert.strictEqual(remote.checkCommand('say Grüße, Ærø, 東京'), 'say Grüße, Ærø, 東京');
});

test('an empty command, or one past the length cap, is refused', () => {
  assert.throws(() => remote.checkCommand('  /  '), /empty/);
  assert.throws(() => remote.checkCommand(42), /text/);
  assert.strictEqual(remote.checkCommand('x'.repeat(remote.MAX_COMMAND)).length, remote.MAX_COMMAND);
  assert.throws(() => remote.checkCommand('x'.repeat(remote.MAX_COMMAND + 1)), /longer than/);
});

const good = { host: '127.0.0.1:8200', origin: 'http://127.0.0.1:8200', 'x-wx-dashboard': '1', 'content-type': 'application/json' };

test('the dashboard takes a POST from its own page: local Host, that Origin, its header and a JSON body', () => {
  assert.strictEqual(remote.sameOrigin(good), true);
  assert.strictEqual(remote.sameOrigin({ ...good, host: 'localhost:8200', origin: 'http://localhost:8200', 'content-type': 'application/json; charset=utf-8' }), true);
});

test('the dashboard refuses a POST with another Host, a missing or other Origin, no header, or another body type', () => {
  assert.strictEqual(remote.sameOrigin({ ...good, host: 'evil.example:8200', origin: 'http://evil.example:8200' }), false);
  assert.strictEqual(remote.sameOrigin({ ...good, origin: undefined }), false);
  assert.strictEqual(remote.sameOrigin({ ...good, origin: 'http://evil.example' }), false);
  assert.strictEqual(remote.sameOrigin({ ...good, origin: 'http://localhost:8200' }), false);
  assert.strictEqual(remote.sameOrigin({ ...good, 'x-wx-dashboard': undefined }), false);
  assert.strictEqual(remote.sameOrigin({ ...good, 'content-type': 'text/plain' }), false);
});

/** A command port on a stand-in server whose run records each command; `busy` as given. */
async function port(t, busy) {
  const ran = [];
  const srv = { run: async (c) => { ran.push(c); return { lines: ['ok'], errors: [] }; } };
  const token = remote.newToken();
  const server = await remote.createCommandPort({ srv, token, busy });
  t.after(() => new Promise((resolve) => { server.close(resolve); }));
  const url = `http://127.0.0.1:${server.address().port}/run`;
  const post = (body, headers = {}) => fetch(url, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...headers },
    body: typeof body === 'string' ? body : JSON.stringify(body),
  });
  return { ran, server, token, post };
}

test('the command port listens on 127.0.0.1, and nowhere else', async (t) => {
  const { server } = await port(t);
  assert.strictEqual(server.address().address, '127.0.0.1');
});

test('the command port runs a command with the token and answers with what it printed', async (t) => {
  const { ran, post } = await port(t);
  const r = await post({ command: '/op Freya' });
  assert.strictEqual(r.status, 200);
  assert.deepStrictEqual(await r.json(), { lines: ['ok'], errors: [] });
  assert.deepStrictEqual(ran, ['op Freya']);
});

test('the command port refuses a wrong or missing token with 401, and runs nothing', async (t) => {
  const { ran, post, token } = await port(t);
  const wrong = await post({ command: 'op Freya' }, { Authorization: `Bearer ${'0'.repeat(token.length)}` });
  assert.strictEqual(wrong.status, 401);
  const missing = await post({ command: 'op Freya' }, { Authorization: '' });
  assert.strictEqual(missing.status, 401);
  assert.deepStrictEqual(ran, []);
  // The same port then runs a command with the right one: the refusals above were the token's.
  assert.strictEqual((await post({ command: 'list' })).status, 200);
  assert.deepStrictEqual(ran, ['list']);
});

test('the command port refuses any request with an Origin, a browser\'s, with 403, and runs nothing', async (t) => {
  const { ran, post } = await port(t);
  const r = await post({ command: 'op Freya' }, { Origin: 'http://127.0.0.1:8200' });
  assert.strictEqual(r.status, 403);
  assert.deepStrictEqual(ran, []);
});

test('the command port refuses while busy with 409 and the reason, and runs nothing', async (t) => {
  const { ran, post } = await port(t, () => 'a self-test is running; its cells own the console');
  const r = await post({ command: 'op Freya' });
  assert.strictEqual(r.status, 409);
  assert.deepStrictEqual(await r.json(), { error: 'a self-test is running; its cells own the console' });
  assert.deepStrictEqual(ran, []);
});

test('the command port refuses a body over 4 KB, and runs nothing', async (t) => {
  const { ran, post } = await port(t);
  const r = await post({ command: `say ${'x'.repeat(5 * 1024)}` });
  assert.strictEqual(r.status, 413);
  assert.deepStrictEqual(ran, []);
});

test('the body cap is exactly 4096 bytes: a short command padded to 4096 runs, to 4097 is refused', async (t) => {
  const { ran, post } = await port(t);
  const padded = (n) => { const s = '{"command":"list"}'; return s + ' '.repeat(n - s.length); };
  assert.strictEqual(Buffer.byteLength(padded(4096)), remote.MAX_BODY);
  assert.strictEqual((await post(padded(4096))).status, 200);
  assert.strictEqual((await post(padded(4097))).status, 413);
  assert.deepStrictEqual(ran, ['list']);
});

test('the command port refuses a Host other than this machine\'s with 403, and runs nothing', async (t) => {
  const { ran, server, token } = await port(t);
  // fetch cannot set Host; http.request can.
  const status = await new Promise((resolve, reject) => {
    const body = '{"command":"list"}';
    const req = http.request({
      host: '127.0.0.1', port: server.address().port, path: '/run', method: 'POST',
      headers: { Host: 'evil.example', Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Content-Length': body.length },
    }, (res) => { res.resume(); resolve(res.statusCode); });
    req.on('error', reject);
    req.end(body);
  });
  assert.strictEqual(status, 403);
  assert.deepStrictEqual(ran, []);
});

test('a body cut off before its end is refused with 400, not left waiting', async (t) => {
  const got = [];
  const server = http.createServer((req) => {
    remote.readBody(req).then((b) => got.push(`resolved ${b}`), (e) => got.push(e.status));
  });
  await new Promise((resolve) => { server.listen(0, '127.0.0.1', resolve); });
  t.after(() => new Promise((resolve) => { server.close(resolve); }));
  const req = http.request({ host: '127.0.0.1', port: server.address().port, method: 'POST', headers: { 'Content-Length': 100 } });
  req.on('error', () => {});
  req.write('{"command":');
  await new Promise((resolve) => { setTimeout(resolve, 100); });
  req.destroy();
  const t0 = Date.now();
  while (!got.length && Date.now() - t0 < 2000) await new Promise((resolve) => { setTimeout(resolve, 20); });
  assert.deepStrictEqual(got, [400]);
});

test('busyReason gives each reason, and null when the console is free', () => {
  assert.strictEqual(remote.busyReason({ selftest: true }), 'a self-test is running; its cells own the console');
  assert.strictEqual(remote.busyReason({ shots: true }), 'screenshots are being taken');
  assert.strictEqual(remote.busyReason({ stopping: true }), 'the server is stopping');
  assert.strictEqual(remote.busyReason({ restarting: true }), 'the server is restarting');
  assert.strictEqual(remote.busyReason({ selftest: false, shots: false, stopping: false, restarting: false }), null);
});

test('the launcher\'s own answers pass through; anything else from that port is a 502', () => {
  assert.deepStrictEqual(remote.launcherAnswer(200, '{"lines":["ok"],"errors":[]}'), { status: 200, body: { lines: ['ok'], errors: [] } });
  for (const s of [400, 409, 413, 500, 504]) {
    assert.deepStrictEqual(remote.launcherAnswer(s, '{"error":"why"}'), { status: s, body: { error: 'why' } });
  }
  const stale = { status: 502, body: { error: remote.STALE } };
  assert.deepStrictEqual(remote.launcherAnswer(200, 'hello'), stale);
  assert.deepStrictEqual(remote.launcherAnswer(200, '{"lines":["ok"]}'), stale);
  assert.deepStrictEqual(remote.launcherAnswer(200, '{"lines":"ok","errors":[]}'), stale);
  assert.deepStrictEqual(remote.launcherAnswer(200, '{"lines":["ok"],"errors":[1]}'), stale);
  for (const s of [401, 403, 404, 418]) assert.deepStrictEqual(remote.launcherAnswer(s, '{"error":"why"}'), stale);
  assert.deepStrictEqual(remote.launcherAnswer(409, '{"message":"why"}'), stale);
});

test('the command port refuses a command with a line break with 400, and runs nothing', async (t) => {
  const { ran, post } = await port(t);
  const r = await post({ command: 'say hi\nstop' });
  assert.strictEqual(r.status, 400);
  assert.match((await r.json()).error, /one line/);
  assert.deepStrictEqual(ran, []);
});

test('the endpoint file round-trips, and removeEndpoint takes it away', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-remote-'));
  try {
    remote.writeEndpoint(dir, { port: 51234, token: 'abc' });
    assert.ok(fs.existsSync(path.join(dir, remote.ENDPOINT_FILE)));
    const mine = { port: 51234, token: 'abc', pid: process.pid };
    assert.deepStrictEqual(remote.readEndpoint(dir), mine);
    // Another launcher's token leaves it be; this one's removes it.
    remote.removeEndpoint(dir, 'other');
    assert.deepStrictEqual(remote.readEndpoint(dir), mine);
    remote.removeEndpoint(dir, 'abc');
    assert.strictEqual(fs.existsSync(path.join(dir, remote.ENDPOINT_FILE)), false);
    assert.strictEqual(remote.readEndpoint(dir), null);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('a launcher starting clears an endpoint file left by one killed outright, whoever wrote it', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-remote-'));
  try {
    remote.writeEndpoint(dir, { port: 51234, token: 'left-behind', pid: 999999 });
    assert.ok(remote.readEndpoint(dir));
    remote.clearStale(dir);
    assert.strictEqual(fs.existsSync(path.join(dir, remote.ENDPOINT_FILE)), false);
    remote.clearStale(dir); // nothing there: no throw
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('alive tells a running process from one that has exited', () => {
  assert.strictEqual(remote.alive(process.pid), true);
  const gone = spawnSync(process.execPath, ['-e', '']).pid;
  assert.ok(gone > 0);
  assert.strictEqual(remote.alive(gone), false);
});
