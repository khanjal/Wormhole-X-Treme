'use strict';
// The Lab Dashboard's POST /cmd (dashboard.js) against a fake lab folder and stand-in launchers:
// which requests it takes, and what it passes back of a lab's port. Run with
// `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const http = require('http');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');
const remote = require('../lib/remote');
const { createDashboard } = require('../dashboard');

const listen = (server) => new Promise((resolve) => { server.listen(0, '127.0.0.1', () => resolve(server.address().port)); });
const shut = (server) => new Promise((resolve) => {
  if (typeof server.closeAllConnections === 'function') server.closeAllConnections();
  server.close(() => resolve());
});

/** A dashboard over a temp .local-server with one lab, facility-test; `post` sends as its own page does. */
async function dashboard(t) {
  const servers = fs.mkdtempSync(path.join(os.tmpdir(), 'wx-dash-'));
  const folder = path.join(servers, 'facility-test');
  fs.mkdirSync(path.join(folder, 'logs'), { recursive: true });
  fs.writeFileSync(path.join(folder, 'logs', 'latest.log'), '[12:00:00] [Server thread/INFO]: Done\n');
  const server = createDashboard({ servers });
  const port = await listen(server);
  t.after(async () => { await shut(server); fs.rmSync(servers, { recursive: true, force: true }); });
  const base = `http://127.0.0.1:${port}`;
  const page = { 'Content-Type': 'application/json', 'X-Wx-Dashboard': '1', Origin: base };
  const post = async (command, { lab = 'facility_test', headers = page, method = 'POST' } = {}) => {
    const r = await fetch(`${base}/cmd?lab=${lab}`, { method, headers, body: method === 'POST' ? JSON.stringify({ command }) : undefined });
    const text = await r.text();
    let body = null;
    try { body = JSON.parse(text); } catch { /* not JSON */ }
    return { status: r.status, body };
  };
  return { folder, post };
}

/** The lab's launcher: a real command port on a stand-in server that records what it runs. */
async function launcher(t, folder, { busy = () => null } = {}) {
  const ran = [];
  const srv = { run: async (c) => { ran.push(c); return { lines: [`ran ${c}`], errors: [] }; } };
  const token = remote.newToken();
  const port = await remote.createCommandPort({ srv, token, busy });
  t.after(() => shut(port));
  remote.writeEndpoint(folder, { port: port.address().port, token });
  return ran;
}

/** Something else on a port the lab's file names: counts requests, answers `status` and `text`. */
async function stranger(t, folder, status, text) {
  const hits = [];
  const server = http.createServer((req, res) => { hits.push(req.headers.authorization); res.writeHead(status); res.end(text); });
  const port = await listen(server);
  t.after(() => shut(server));
  remote.writeEndpoint(folder, { port, token: 'the-token' });
  return hits;
}

const NOT_RUNNING = /not running under a launcher that accepts commands/;

test('a command from the page runs on the lab\'s launcher and comes back with what it printed', async (t) => {
  const { folder, post } = await dashboard(t);
  const ran = await launcher(t, folder);
  assert.deepStrictEqual(await post('/list'), { status: 200, body: { lines: ['ran list'], errors: [] } });
  assert.deepStrictEqual(ran, ['list']);
});

test('a POST without the page\'s Origin is refused with 403, and nothing reaches the launcher', async (t) => {
  const { folder, post } = await dashboard(t);
  const ran = await launcher(t, folder);
  const r = await post('list', { headers: { 'Content-Type': 'application/json', 'X-Wx-Dashboard': '1' } });
  assert.strictEqual(r.status, 403);
  assert.deepStrictEqual(ran, []);
  assert.strictEqual((await post('list')).status, 200); // the same request with it goes through
});

test('a lab the dashboard does not have is a 404, and a GET is a 405', async (t) => {
  const { folder, post } = await dashboard(t);
  const ran = await launcher(t, folder);
  assert.strictEqual((await post('list', { lab: 'facility_nope' })).status, 404);
  assert.strictEqual((await post('list', { method: 'GET' })).status, 405);
  assert.deepStrictEqual(ran, []);
});

test('a command with a line break is refused with 400 before it reaches the launcher', async (t) => {
  const { folder, post } = await dashboard(t);
  const ran = await launcher(t, folder);
  const r = await post('say hi\nstop');
  assert.strictEqual(r.status, 400);
  assert.match(r.body.error, /one line/);
  assert.deepStrictEqual(ran, []);
});

test('the launcher\'s refusal while busy comes back as its 409 and reason', async (t) => {
  const { folder, post } = await dashboard(t);
  const ran = await launcher(t, folder, { busy: () => 'a self-test is running; its cells own the console' });
  assert.deepStrictEqual(await post('list'), { status: 409, body: { error: 'a self-test is running; its cells own the console' } });
  assert.deepStrictEqual(ran, []);
});

test('a lab with no endpoint file is a 503 saying how to start one', async (t) => {
  const { post } = await dashboard(t);
  const r = await post('list');
  assert.strictEqual(r.status, 503);
  assert.match(r.body.error, NOT_RUNNING);
});

test('a file whose launcher has exited is a 503, and its token goes nowhere', async (t) => {
  const { folder, post } = await dashboard(t);
  const hits = await stranger(t, folder, 200, '{"lines":[],"errors":[]}');
  const dead = spawnSync(process.execPath, ['-e', '']).pid;
  remote.writeEndpoint(folder, { ...remote.readEndpoint(folder), pid: dead });
  const r = await post('list');
  assert.strictEqual(r.status, 503);
  assert.match(r.body.error, NOT_RUNNING);
  assert.deepStrictEqual(hits, []);
  // The same port with a live pid is reached: the 503 above was the dead pid's.
  remote.writeEndpoint(folder, { ...remote.readEndpoint(folder), pid: process.pid });
  assert.strictEqual((await post('list')).status, 200);
  assert.deepStrictEqual(hits, ['Bearer the-token']);
});

test('a port that answers 200 but not as a launcher is a 502 naming a stale file', async (t) => {
  const { folder, post } = await dashboard(t);
  const hits = await stranger(t, folder, 200, 'hello from something else');
  assert.deepStrictEqual(await post('list'), { status: 502, body: { error: remote.STALE } });
  assert.strictEqual(hits.length, 1);
});

test('a 404 or 401 from that port is a 502, not passed on as the dashboard\'s own', async (t) => {
  const { folder, post } = await dashboard(t);
  await stranger(t, folder, 404, '{"error":"not found"}');
  assert.deepStrictEqual(await post('list'), { status: 502, body: { error: remote.STALE } });
  // A launcher with another token than the file's: its 401 is a stale file too.
  const ran = await launcher(t, folder);
  remote.writeEndpoint(folder, { ...remote.readEndpoint(folder), token: 'not-its-token' });
  assert.deepStrictEqual(await post('list'), { status: 502, body: { error: remote.STALE } });
  assert.deepStrictEqual(ran, []);
});

test('a file naming a port nobody listens on is a 503', async (t) => {
  const { folder, post } = await dashboard(t);
  const gone = http.createServer();
  const port = await listen(gone);
  await shut(gone);
  remote.writeEndpoint(folder, { port, token: 'abc' });
  const r = await post('list');
  assert.strictEqual(r.status, 503);
  assert.match(r.body.error, NOT_RUNNING);
});
