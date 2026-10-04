#!/usr/bin/env node
'use strict';
// A dashboard for the facility's labs: each lab's live console (streamed from its
// logs/latest.log) with a command box, and its Dynmap, in one browser page. Listens on 127.0.0.1 only.
//
//   node scripts/facility/dashboard.js            http://127.0.0.1:8200
//   node scripts/facility/dashboard.js --port 8300
//
// Labs are the server folders under .local-server/ that run-facility.js made. Commands go to a lab
// over RCON, which the launcher turns on, bound to 127.0.0.1 with a password made for the run, only
// while a hand lab holds (console-channel.json says "ready"); never during a self-test.

const crypto = require('crypto');
const fs = require('fs');
const http = require('http');
const net = require('net');
const path = require('path');

const SERVERS = path.join(__dirname, '..', '..', '.local-server');

/** A lab's log file, or null when it has none (yet). */
function logOf(folder) {
  try { return fs.statSync(path.join(folder, 'logs', 'latest.log')); } catch { return null; }
}

/**
 * Every lab folder that has a logs folder, in name order so the tabs stay put; its Dynmap URL when
 * Dynmap is installed there. The logs folder outlives latest.log's rename at a restart.
 */
function findLabs() {
  let names = [];
  try { names = fs.readdirSync(SERVERS); } catch { return []; }
  return names
    .map((n) => ({ n, m: /^facility-(.+?)(?:-(\d+))?$/.exec(n) }))
    .filter(({ n, m }) => m && fs.existsSync(path.join(SERVERS, n, 'logs')))
    .sort((x, y) => x.n.localeCompare(y.n))
    .map(({ n, m }) => {
      const folder = path.join(SERVERS, n);
      let map = null;
      try {
        const conf = fs.readFileSync(path.join(folder, 'plugins', 'dynmap', 'configuration.txt'), 'utf8');
        const port = /^webserver-port:\s*(\d+)/m.exec(conf);
        if (port) map = `http://localhost:${port[1]}/`;
      } catch { /* no Dynmap here */ }
      return { id: n.replace(/\W/g, '_'), name: `${m[1]} · :${m[2] || 25590}`, folder, map };
    });
}

const BACKLOG = 400;
const WINDOW = 256 * 1024;
const POLL_MS = 500;

const argPort = process.argv.indexOf('--port');
const PORT = argPort > 0 && Number(process.argv[argPort + 1]) > 0 ? Number(process.argv[argPort + 1]) : 8200;

// The command box's POST must carry this page's token and come from this page's origin: another
// site can send a POST here but cannot read the page, and a custom header needs a preflight this server never grants.
const CSRF = crypto.randomBytes(24).toString('hex');
const ORIGINS = new Set([`http://127.0.0.1:${PORT}`, `http://localhost:${PORT}`]);
const MAX_COMMAND_BYTES = 1400;
const RCON_MS = 15000;

/** A lab's RCON port and password from its server.properties, or null unless RCON is on and bound to 127.0.0.1. */
function rconOf(folder) {
  let text;
  try { text = fs.readFileSync(path.join(folder, 'server.properties'), 'utf8'); } catch { return null; }
  const props = new Map();
  for (const line of text.split(/\r?\n/)) {
    const eq = line.indexOf('=');
    if (eq > 0 && !line.startsWith('#') && !props.has(line.slice(0, eq).trim())) props.set(line.slice(0, eq).trim(), line.slice(eq + 1).trim());
  }
  const prop = (key) => (props.has(key) ? props.get(key) : null);
  if (prop('enable-rcon') !== 'true' || !prop('rcon.password') || prop('server-ip') !== '127.0.0.1') return null;
  return { port: Number(prop('rcon.port')), password: prop('rcon.password') };
}

/** Why a lab will not take a command just now, from the launcher's console-channel.json; null when it will. */
function refusal(folder) {
  let ch;
  try { ch = JSON.parse(fs.readFileSync(path.join(folder, 'console-channel.json'), 'utf8')); } catch { return 'its launcher is not running'; }
  // A pid of 0 or less would signal a process group, and succeed.
  if (!Number.isInteger(ch.pid) || ch.pid <= 0) return 'its launcher is not running';
  try { process.kill(ch.pid, 0); } catch (e) { if (e.code !== 'EPERM') return 'its launcher is not running'; }
  if (ch.state === 'selftest') return 'a self-test is running there, and a command would interleave with its cells';
  if (ch.state === 'starting') return 'the launcher is still setting the lab up; try again once it says ready';
  if (ch.state === 'no-rcon') return 'RCON is not running there (its port was taken, or the game port is above 55535); see the launcher\'s window';
  if (ch.state !== 'ready') return 'this run takes no commands in its mode';
  return null;
}

/** One RCON packet: length, id, type, a NUL-terminated body and an empty one. */
function rconPacket(id, type, body) {
  const b = Buffer.from(body, 'utf8');
  const p = Buffer.alloc(14 + b.length);
  p.writeInt32LE(10 + b.length, 0);
  p.writeInt32LE(id, 4);
  p.writeInt32LE(type, 8);
  b.copy(p, 12);
  return p;
}

/**
 * Runs one command over RCON and resolves with the server's reply. A long reply comes in several
 * packets, so an invalid packet follows the command: the server answers it after the last one.
 */
function rconRun({ port, password }, command) {
  return new Promise((resolve, reject) => {
    const sock = net.connect({ host: '127.0.0.1', port });
    let buf = Buffer.alloc(0);
    let reply = '';
    let authed = false;
    let marked = false;
    const done = (err, value) => {
      clearTimeout(timer);
      sock.destroy();
      if (err) reject(err); else resolve(value);
    };
    const timer = setTimeout(() => done(new Error(`no reply in ${RCON_MS / 1000} s; the command may still have run`)), RCON_MS);
    sock.on('connect', () => sock.write(rconPacket(1, 3, password)));
    sock.on('error', (e) => done(new Error(e.code === 'ECONNREFUSED' ? 'nothing answers on its RCON port (is the server restarting?)' : e.message)));
    // A reply then a close is a command that stopped the server.
    sock.on('close', () => (marked ? done(null, reply) : done(new Error('RCON closed the connection'))));
    sock.on('data', (d) => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= 4) {
        const len = buf.readInt32LE(0);
        if (len < 10 || len > 1 << 20) { done(new Error('RCON sent a malformed packet')); return; }
        if (buf.length < 4 + len) return;
        const id = buf.readInt32LE(4);
        const type = buf.readInt32LE(8);
        const body = buf.subarray(12, 2 + len).toString('utf8');
        buf = buf.subarray(4 + len);
        if (type === 2 && !authed) {
          if (id === -1) { done(new Error('RCON refused the password')); return; }
          authed = true;
          sock.write(rconPacket(2, 2, command));
        } else if (id === 2) {
          // Not sent with the command: the server reads one packet a read, and drops a connection
          // whose read holds two. It answers this one after the command's last packet.
          if (!marked) { marked = true; sock.write(rconPacket(3, 0, '')); }
          reply += body;
        } else if (id === 3) {
          done(null, reply);
          return;
        }
      }
    });
  });
}

/** A request's body as text, refused past `max` bytes. */
function readBody(req, max) {
  return new Promise((resolve, reject) => {
    const parts = [];
    let size = 0;
    req.on('data', (d) => {
      size += d.length;
      if (size > max) { reject(new Error('too large')); req.destroy(); return; }
      parts.push(d);
    });
    req.on('end', () => resolve(Buffer.concat(parts).toString('utf8')));
    req.on('error', reject);
  });
}

/** POST /command {lab, command}: runs one line on a lab's server console over RCON. */
async function command(req, res) {
  const answer = (code, body) => {
    res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(body));
  };
  if (req.method !== 'POST') return answer(405, { error: 'POST only' });
  if (!ORIGINS.has(req.headers.origin)) return answer(403, { error: 'refused: not from this dashboard' });
  const token = Buffer.from(String(req.headers['x-wx-csrf'] || ''));
  if (token.length !== CSRF.length || !crypto.timingSafeEqual(token, Buffer.from(CSRF))) return answer(403, { error: 'refused: bad token (reload the page)' });
  if (!/^application\/json\b/.test(req.headers['content-type'] || '')) return answer(415, { error: 'JSON only' });
  let body;
  try { body = JSON.parse(await readBody(req, 8192)); } catch { return answer(400, { error: 'bad request' }); }
  if (!body || typeof body.lab !== 'string' || typeof body.command !== 'string') return answer(400, { error: 'bad request' });
  const lab = findLabs().find((l) => l.id === body.lab);
  if (!lab) return answer(404, { error: 'no such lab' });
  const line = body.command.trim().replace(/^\//, '');
  // The server drops a packet past 1460 bytes without a reply, so the cap is in bytes.
  // eslint-disable-next-line no-control-regex
  if (!line || Buffer.byteLength(line) > MAX_COMMAND_BYTES || /[\x00-\x1f\x7f]/.test(line)) {
    return answer(400, { error: `one line of up to ${MAX_COMMAND_BYTES} bytes, without control characters` });
  }
  const why = refusal(lab.folder);
  if (why) return answer(409, { error: `refused: ${why}` });
  const rcon = rconOf(lab.folder);
  if (!rcon) return answer(409, { error: 'refused: RCON is off on this lab; start it again with lab.ps1 to turn it on' });
  console.log(`${lab.name}: ${line}`);
  try {
    return answer(200, { reply: (await rconRun(rcon, line)).replace(/§./g, '') });
  } catch (e) {
    return answer(502, { error: e.message });
  }
}

/** Bytes [from, to) of a file, or null if it cannot be read just now (rotating, locked). */
function readRange(file, from, to) {
  let fd;
  try {
    fd = fs.openSync(file, 'r');
    const buf = Buffer.alloc(to - from);
    const got = fs.readSync(fd, buf, 0, buf.length, from);
    return buf.subarray(0, got);
  } catch {
    return null;
  } finally {
    if (fd !== undefined) try { fs.closeSync(fd); } catch { /* already gone */ }
  }
}

/** Streams a lab's log as server-sent events: a reset, the backlog, then each new line. */
function stream(lab, res) {
  const file = path.join(lab.folder, 'logs', 'latest.log');
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
  const send = (line) => res.write(`data: ${JSON.stringify(line)}\n\n`);
  const status = (s) => res.write(`event: status\ndata: ${JSON.stringify(s)}\n\n`);
  // A reconnect sends the backlog again, so the page clears what it has first.
  res.write('event: reset\ndata: ""\n\n');
  let inode = null;
  let offset = 0;
  let pending = Buffer.alloc(0);
  let cutFirst = false; // reading began mid-line: drop everything up to the first newline
  let keep = BACKLOG; // lines to send from the next read: the backlog once, then all of them
  let live = false;
  /** Starts on a (new) log file from its last WINDOW bytes, with a fresh backlog. */
  const begin = (stat) => {
    inode = stat.ino;
    offset = Math.max(0, stat.size - WINDOW);
    pending = Buffer.alloc(0);
    cutFirst = offset > 0;
    keep = BACKLOG;
  };
  /** Sends every complete line up to `size`, holding back an unfinished last line. */
  const advance = (size) => {
    const chunk = readRange(file, offset, size);
    if (!chunk) return;
    offset += chunk.length;
    const all = Buffer.concat([pending, chunk]);
    const cut = all.lastIndexOf(0x0a) + 1;
    pending = all.subarray(cut);
    if (cut === 0) return;
    const lines = all.subarray(0, cut).toString('utf8').split(/\r?\n/);
    if (cutFirst) { lines.shift(); cutFirst = false; }
    lines.filter((l) => l.length).slice(-keep).forEach(send);
    keep = Infinity;
    if (!live) { live = true; status('live'); }
  };
  const tick = () => {
    const now = logOf(lab.folder);
    if (!now) return;
    if (inode === null) begin(now);
    // Paper replaces latest.log on a restart: a new file (a new inode), whatever its size.
    else if (now.ino !== inode || now.size < offset) {
      send('— log restarted (server started again) —');
      begin(now);
    }
    if (now.size > offset) advance(now.size);
  };
  tick();
  if (!live) status(logOf(lab.folder) ? 'live' : 'no log yet');
  const timer = setInterval(tick, POLL_MS);
  res.on('close', () => clearInterval(timer));
}

const page = (LABS) => `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lab Dashboard</title>
<style>
:root{--bg:#0f1115;--panel:#161a21;--line:#262c36;--text:#d7dde6;--dim:#8a94a3;--accent:#4fb3ff;
--warn:#e6b450;--err:#ff6b6b;--wx:#7ee0a1;}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px/1.4 system-ui,sans-serif;
height:100vh;display:flex;flex-direction:column}
header{display:flex;gap:4px;align-items:center;padding:8px 12px;border-bottom:1px solid var(--line);flex-wrap:wrap}
header h1{font-size:15px;margin:0 12px 0 0;font-weight:600}
.tab{background:none;border:1px solid transparent;color:var(--dim);padding:6px 12px;border-radius:6px;cursor:pointer;font:inherit}
.tab:hover{color:var(--text)}.tab.on{color:var(--text);background:var(--panel);border-color:var(--line)}
main{flex:1;min-height:0;display:flex;flex-direction:column}
.view{display:none;flex:1;min-height:0;flex-direction:column}.view.on{display:flex}
.bar{display:flex;gap:8px;align-items:center;padding:8px 12px;border-bottom:1px solid var(--line);color:var(--dim);flex-wrap:wrap}
.bar input[type=search]{background:var(--panel);border:1px solid var(--line);color:var(--text);padding:5px 8px;border-radius:6px;min-width:220px;font:inherit}
.bar label{display:flex;gap:4px;align-items:center;cursor:pointer}
.dot{width:8px;height:8px;border-radius:50%;background:var(--dim);display:inline-block}.dot.live{background:var(--wx)}
pre{flex:1;margin:0;overflow:auto;padding:8px 12px;font:12.5px/1.45 ui-monospace,Consolas,monospace;white-space:pre-wrap;word-break:break-word}
.l.w{color:var(--warn)}.l.e{color:var(--err)}.l.x{color:var(--wx)}.l.h{display:none}.l.r{color:var(--accent)}
.cmd{display:flex;gap:8px;padding:8px 12px;border-top:1px solid var(--line)}
.cmd input{flex:1;background:var(--panel);border:1px solid var(--line);color:var(--text);padding:5px 8px;border-radius:6px;font:12.5px ui-monospace,Consolas,monospace}
.cmd button{background:var(--panel);border:1px solid var(--line);color:var(--text);padding:4px 12px;border-radius:6px;cursor:pointer;font:inherit}
iframe{flex:1;border:0;width:100%;background:#fff}
.empty{padding:24px;color:var(--dim)}
</style></head><body>
<header><h1>Lab Dashboard</h1><div id="tabs" style="display:flex;gap:4px;flex-wrap:wrap"></div></header>
<main id="views"></main>
<script>
const LABS = ${labsJson(LABS)}, CSRF = '${CSRF}';
const tabs = document.getElementById('tabs'), views = document.getElementById('views');
function show(id){for(const b of tabs.children)b.classList.toggle('on',b.dataset.v===id);
  for(const v of views.children)v.classList.toggle('on',v.id===id);
  const shown=document.getElementById(id);if(shown.onShow)shown.onShow();
  try{localStorage.setItem('wxdash.tab',id)}catch{}}
function addTab(id,label){const b=document.createElement('button');b.className='tab';b.dataset.v=id;b.textContent=label;
  b.onclick=()=>show(id);tabs.appendChild(b)}
function kind(t){return /\\bERROR\\b|Exception|^\\s+at /.test(t)?'e':/\\bWARN/.test(t)?'w':/WormholeXTreme/.test(t)?'x':''}
for(const lab of LABS){
  const id='con-'+lab.id; addTab(id,'Console '+lab.name);
  const v=document.createElement('section');v.className='view';v.id=id;
  v.innerHTML='<div class="bar"><span class="dot"></span><span class="st">connecting…</span>'
    +'<input type="search" placeholder="filter (e.g. Wormhole, WARN)">'
    +'<label><input type="checkbox" class="follow" checked> follow</label>'
    +'<label><input type="checkbox" class="wxonly"> Wormhole only</label>'
    +'<label title="the facility&#39;s fence markers, effect re-applies and the command box&#39;s RCON connections"><input type="checkbox" class="quiet" checked> hide facility noise</label></div><pre></pre>'
    +'<form class="cmd"><input class="line" autocomplete="off" spellcheck="false" maxlength="1000"'
    +' placeholder="run on this lab&#39;s console, e.g. op Steve (Up and Down for earlier commands)"><button>Run</button></form>';
  views.appendChild(v);
  const pre=v.querySelector('pre'),q=v.querySelector('input[type=search]'),follow=v.querySelector('.follow'),
    wxonly=v.querySelector('.wxonly'),quiet=v.querySelector('.quiet'),st=v.querySelector('.st'),dot=v.querySelector('.dot');
  // The facility's own command replies: its checks, fences, block and entity edits, and the
  // players' shield. Wormhole's output, joins, chat, warnings and errors are never in this list.
  const noise=new RegExp([
    '\\\\[wxfence\\\\]','#fence\\\\d','^Test (passed|failed)','Unable to apply this effect','Applied effect .* to',
    'Gamerule .* is now set to','Nothing changed\\\\.','Modified entity data of','Changed the block at','Could not set the block',
    'Successfully filled','No blocks were filled','No entity was found','No chunks were marked for force loading',
    'Marked chunk','chunks? (?:is|are) (?:now )?(?:marked|force loaded)','Teleported \\\\S+ to','Summoned new',
    'An objective already exists','^Killed ',
    '^Gave \\\\d+','^Set the time to','^Set the weather to','^Changed the weather','game mode to',
    'Created custom bossbar','Custom bossbar','^Removed custom bossbar','^Played sound','^Displaying particle',
    '^Set \\\\[wx','^Enabled trigger','^Reset .* for','^Set .* for .* to \\\\d+','Made \\\\S+ a server operator',
    '^Removed \\\\d+ item','Showing new title','^Set the difficulty','Thread RCON Client',
  ].join('|'));
  const hidden=(t)=>(q.value&&!t.toLowerCase().includes(q.value.toLowerCase()))||(wxonly.checked&&!/WormholeXTreme/.test(t))
    ||(quiet.checked&&noise.test(t.replace(/^\\[[^\\]]*\\] \\[[^\\]]*\\]: /,'')));
  // Command replies (class r) come over RCON, not from the log, and no filter hides them.
  const refilter=()=>{for(const d of pre.children)if(!d.classList.contains('r'))d.classList.toggle('h',hidden(d.textContent))};
  q.oninput=refilter;wxonly.onchange=refilter;quiet.onchange=refilter;
  const add=(t,cls)=>{const d=document.createElement('div');d.className='l '+cls;d.textContent=t;
    if(!cls.startsWith('r')&&hidden(t))d.classList.add('h');pre.appendChild(d);
    while(pre.children.length>5000)pre.firstChild.remove();
    if(follow.checked)pre.scrollTop=pre.scrollHeight};
  // A hidden pre does not scroll: start at the newest line when first shown, and on coming back while following.
  let seen=false;
  v.onShow=()=>{if(!seen||follow.checked)pre.scrollTop=pre.scrollHeight;seen=true};
  const form=v.querySelector('.cmd'),line=v.querySelector('.line'),key='wxdash.history.'+lab.id;
  let history=[];try{const h=JSON.parse(localStorage.getItem(key));if(Array.isArray(h))history=h.filter((c)=>typeof c==='string')}catch{}
  let at=history.length,draft='';
  line.onkeydown=(e)=>{
    if(e.key!=='ArrowUp'&&e.key!=='ArrowDown')return;
    e.preventDefault();
    if(at===history.length)draft=line.value;
    at=Math.max(0,Math.min(history.length,at+(e.key==='ArrowUp'?-1:1)));
    line.value=at===history.length?draft:history[at]};
  form.onsubmit=async(e)=>{
    e.preventDefault();
    const c=line.value.trim();if(!c)return;
    if(history[history.length-1]!==c){history.push(c);history=history.slice(-100)}
    try{localStorage.setItem(key,JSON.stringify(history))}catch{}
    at=history.length;draft='';line.value='';
    add('» '+c,'r');pre.scrollTop=pre.scrollHeight;
    try{
      const r=await fetch('/command',{method:'POST',headers:{'Content-Type':'application/json','X-WX-CSRF':CSRF},
        body:JSON.stringify({lab:lab.id,command:c})});
      const j=await r.json();
      if(j.error)add('  '+j.error,'r e');
      else for(const t of (j.reply||'(no reply)').split('\\n'))if(t)add('  '+t,'r');
    }catch(err){add('  the dashboard did not answer: '+err.message,'r e')}
    pre.scrollTop=pre.scrollHeight};
  const es=new EventSource('/log?lab='+lab.id);
  es.onmessage=(m)=>{const t=JSON.parse(m.data);add(t,kind(t))};
  es.addEventListener('reset',()=>{pre.textContent=''});
  es.addEventListener('status',(m)=>{const s=JSON.parse(m.data);st.textContent=s;dot.classList.toggle('live',s==='live')});
  es.onerror=()=>{st.textContent='reconnecting…';dot.classList.remove('live')};
}
for(const lab of LABS){
  const id='map-'+lab.id;addTab(id,'Dynmap '+lab.name);
  const v=document.createElement('section');v.className='view';v.id=id;
  v.innerHTML=lab.map?'<div class="bar"><a style="color:var(--accent)" target="_blank" href="'+lab.map+'">'+lab.map+'</a>'
    +'<span>(blank if the lab is not running)</span></div><iframe loading="lazy" src="'+lab.map+'"></iframe>'
    :'<div class="empty">No Dynmap on this lab. Start it with -With dynmap (no Dynmap build supports 26.x yet).</div>';
  views.appendChild(v);
}
if(!LABS.length)views.innerHTML='<div class="empty">No labs yet. Start one with scripts/facility/lab.ps1; this page picks it up once its server has written a log.</div>';
else{let first='con-'+LABS[0].id;try{first=localStorage.getItem('wxdash.tab')||first}catch{}
show(document.getElementById(first)?first:'con-'+LABS[0].id);}
// Reload when a lab appears or goes, so a lab started after this page still shows up.
const known=LABS.map((l)=>l.id).sort().join();
setInterval(()=>fetch('/labs').then((r)=>r.json()).then((ids)=>{if(ids.sort().join()!==known)location.reload()}).catch(()=>{}),5000);
</script></body></html>`;

/** The labs as JSON that is safe inside an inline script. */
function labsJson(labs) {
  return JSON.stringify(labs.map(({ id, name, map }) => ({ id, name, map })))
    .replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
}

const server = http.createServer((req, res) => {
  // Only this machine's own names: a page elsewhere that rebinds a hostname to 127.0.0.1 gets nothing.
  if (!/^(127\.0\.0\.1|localhost)(:\d+)?$/.test(req.headers.host || '')) { res.writeHead(403); res.end(); return; }
  let url;
  try { url = new URL(req.url, 'http://127.0.0.1'); } catch { res.writeHead(400); res.end(); return; }
  if (url.pathname === '/') {
    // Not framed by another page, which could steer a focused command box with keystrokes.
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'X-Frame-Options': 'DENY', 'Content-Security-Policy': "frame-ancestors 'none'" });
    res.end(page(findLabs()));
  } else if (url.pathname === '/labs') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(findLabs().map((l) => l.id)));
  } else if (url.pathname === '/log') {
    const lab = findLabs().find((l) => l.id === url.searchParams.get('lab'));
    if (!lab) { res.writeHead(404); res.end(); return; }
    stream(lab, res);
  } else if (url.pathname === '/command') {
    command(req, res).catch((e) => { console.error(e); if (!res.headersSent) { res.writeHead(500); res.end(); } });
  } else {
    res.writeHead(404);
    res.end();
  }
});
server.on('error', (e) => {
  console.error(e.code === 'EADDRINUSE' ? `Port ${PORT} is in use; is the dashboard already running? Try --port.` : e.message);
  process.exit(1);
});
server.listen(PORT, '127.0.0.1', () => console.log(`Lab Dashboard: http://127.0.0.1:${PORT}`));
