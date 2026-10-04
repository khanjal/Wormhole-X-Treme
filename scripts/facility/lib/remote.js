'use strict';
// The Lab Dashboard's command box (dashboard.js) reaches a lab's server through its launcher: the
// launcher listens on 127.0.0.1 only, on a port and with a token it writes into the lab folder
// (.wx-console.json), and runs each command through Server.run, behind the fence protocol.

const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const path = require('path');

const ENDPOINT_FILE = '.wx-console.json';
const MAX_COMMAND = 1000;
const MAX_BODY = 4096;

/** This machine's own names, with a port or without: a page that rebinds a hostname gets nothing. */
function localHost(host) {
  return /^(127\.0\.0\.1|localhost)(:\d+)?$/.test(host || '');
}

/**
 * A console command as typed: one leading slash dropped and trimmed. Throws when it is not one
 * line of printable text up to MAX_COMMAND long, so it can never carry a second command.
 */
function checkCommand(text) {
  if (typeof text !== 'string') throw new Error('the command must be text');
  if (/[\u0000-\u001f\u007f]/.test(text)) throw new Error('the command must be one line, without control characters');
  const cmd = text.trim().replace(/^\//, '').trim();
  if (!cmd) throw new Error('the command is empty');
  if (cmd.length > MAX_COMMAND) throw new Error(`the command is longer than ${MAX_COMMAND} characters`);
  return cmd;
}

/**
 * Whether a browser POST to the dashboard came from the dashboard's own page: a local Host, an
 * Origin of that same host, and a custom header with a JSON body, which no other site can send
 * without a preflight the dashboard never answers.
 */
function sameOrigin(headers) {
  const host = headers.host;
  if (!localHost(host)) return false;
  if (headers.origin !== `http://${host}`) return false;
  if (headers['x-wx-dashboard'] !== '1') return false;
  return /^application\/json(\s*;.*)?$/i.test(headers['content-type'] || '');
}

/** Reads a request body up to `max` bytes; rejects with a status of 413 past that. */
function readBody(req, max = MAX_BODY) {
  return new Promise((resolve, reject) => {
    const parts = [];
    let size = 0;
    let over = false;
    req.on('data', (d) => {
      if (over) return;
      size += d.length;
      if (size > max) {
        over = true;
        reject(Object.assign(new Error(`the request is larger than ${max} bytes`), { status: 413 }));
        return;
      }
      parts.push(d);
    });
    req.on('end', () => { if (!over) resolve(Buffer.concat(parts).toString('utf8')); });
    req.on('error', reject);
  });
}

/** The command in a JSON body { command }, checked; throws with a status of 400. */
function commandOf(body) {
  let parsed;
  try { parsed = JSON.parse(body); } catch { throw Object.assign(new Error('the body is not JSON'), { status: 400 }); }
  try {
    return checkCommand(parsed && parsed.command);
  } catch (e) {
    throw Object.assign(e, { status: 400 });
  }
}

function reply(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

function tokenMatches(header, token) {
  const m = /^Bearer (\S+)$/.exec(header || '');
  if (!m) return false;
  const got = Buffer.from(m[1]);
  const want = Buffer.from(token);
  return got.length === want.length && crypto.timingSafeEqual(got, want);
}

/**
 * The launcher's command port: POST /run { command } with the bearer token runs it on `srv` and
 * answers { lines, errors }. `busy()` gives a reason to refuse (409) or null. Listens on
 * 127.0.0.1, port 0; resolves to the http.Server once it listens.
 */
function createCommandPort({ srv, token, busy = () => null }) {
  const server = http.createServer(async (req, res) => {
    try {
      // The dashboard's own request has no Origin; a browser's always does.
      if (!localHost(req.headers.host) || req.headers.origin !== undefined) { reply(res, 403, { error: 'forbidden' }); return; }
      if (req.method !== 'POST' || req.url !== '/run') { reply(res, 404, { error: 'not found' }); return; }
      if (!tokenMatches(req.headers.authorization, token)) { reply(res, 401, { error: 'bad token' }); return; }
      const command = commandOf(await readBody(req));
      const why = busy();
      if (why) { reply(res, 409, { error: why }); return; }
      const r = await srv.run(command);
      reply(res, 200, { lines: r.lines, errors: r.errors });
    } catch (e) {
      const status = e.status || (/^timed out/.test(e.message) ? 504 : 500);
      if (status === 413) res.setHeader('Connection', 'close');
      reply(res, status, { error: e.message });
    }
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => { server.off('error', reject); resolve(server); });
  });
}

/** A fresh token, new every launch. */
function newToken() {
  return crypto.randomBytes(24).toString('hex');
}

function writeEndpoint(folder, { port, token }) {
  fs.mkdirSync(folder, { recursive: true });
  fs.writeFileSync(path.join(folder, ENDPOINT_FILE), JSON.stringify({ port, token }), { mode: 0o600 });
}

/** The lab's { port, token }, or null when it has none or it is not readable. */
function readEndpoint(folder) {
  try {
    const e = JSON.parse(fs.readFileSync(path.join(folder, ENDPOINT_FILE), 'utf8'));
    if (!Number.isInteger(e.port) || e.port <= 0 || typeof e.token !== 'string' || !e.token) return null;
    return { port: e.port, token: e.token };
  } catch {
    return null;
  }
}

/** Removes the lab's endpoint file; with `token`, only if it is still that launcher's. */
function removeEndpoint(folder, token) {
  if (token !== undefined) {
    const e = readEndpoint(folder);
    if (!e || e.token !== token) return;
  }
  try { fs.rmSync(path.join(folder, ENDPOINT_FILE), { force: true }); } catch { /* already gone */ }
}

module.exports = {
  ENDPOINT_FILE, MAX_COMMAND, MAX_BODY, localHost, checkCommand, sameOrigin, readBody, commandOf,
  createCommandPort, newToken, writeEndpoint, readEndpoint, removeEndpoint,
};
