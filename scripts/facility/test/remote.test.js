'use strict';
// The Lab Dashboard's command channel (lib/remote.js) without a server: what a command may be,
// which browser POSTs the dashboard takes, and the launcher's command port on 127.0.0.1, run
// against a stand-in server. Run with `npm test --prefix scripts/facility` (node --test).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
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
    assert.deepStrictEqual(remote.readEndpoint(dir), { port: 51234, token: 'abc' });
    // Another launcher's token leaves it be; this one's removes it.
    remote.removeEndpoint(dir, 'other');
    assert.deepStrictEqual(remote.readEndpoint(dir), { port: 51234, token: 'abc' });
    remote.removeEndpoint(dir, 'abc');
    assert.strictEqual(fs.existsSync(path.join(dir, remote.ENDPOINT_FILE)), false);
    assert.strictEqual(remote.readEndpoint(dir), null);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
