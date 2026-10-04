#!/usr/bin/env node
'use strict';
// A dashboard for the facility's labs: each lab's live console (streamed from its logs/latest.log)
// with a command box, and its Dynmap, in one browser page. Listens on 127.0.0.1 only.
//
//   node scripts/facility/dashboard.js            http://127.0.0.1:8200
//   node scripts/facility/dashboard.js --port 8300
//
// Labs are the server folders under .local-server/ that run-facility.js made. A command goes to
// the lab's launcher, at the port and with the token in its .wx-console.json (lib/remote.js).

const fs = require('fs');
const http = require('http');
const path = require('path');
const remote = require('./lib/remote');

const SERVERS = path.join(__dirname, '..', '..', '.local-server');

/** A lab's log file, or null when it has none (yet). */
function logOf(folder) {
  try { return fs.statSync(path.join(folder, 'logs', 'latest.log')); } catch { return null; }
}

/**
 * Every lab folder that has a logs folder, in name order so the tabs stay put; its Dynmap URL when
 * Dynmap is installed there. The logs folder outlives latest.log's rename at a restart.
 */
function findLabs(servers = SERVERS) {
  let names = [];
  try { names = fs.readdirSync(servers); } catch { return []; }
  return names
    .map((n) => ({ n, m: /^facility-(.+?)(?:-(\d+))?$/.exec(n) }))
    .filter(({ n, m }) => m && fs.existsSync(path.join(servers, n, 'logs')))
    .sort((x, y) => x.n.localeCompare(y.n))
    .map(({ n, m }) => {
      const folder = path.join(servers, n);
      let map = null;
      try {
        const conf = fs.readFileSync(path.join(folder, 'plugins', 'dynmap', 'configuration.txt'), 'utf8');
        const port = /^webserver-port:\s*(\d+)/m.exec(conf);
        // Dynmap binds 127.0.0.1 only; a browser can take localhost to ::1 and be refused.
        if (port) map = `http://127.0.0.1:${port[1]}/`;
      } catch { /* no Dynmap here */ }
      return { id: n.replace(/\W/g, '_'), name: `${m[1]} · :${m[2] || 25590}`, folder, map };
    });
}

const BACKLOG = 400;
const WINDOW = 256 * 1024;
const POLL_MS = 500;

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

const NOT_RUNNING = 'this lab is not running under a launcher that accepts commands (start it with scripts/facility/lab.ps1 or run-facility.js)';
const COMMAND_MS = 20000;
const MAX_ANSWER = 1024 * 1024;
const MAY_RUN = 'it may still run: check the console before sending it again';

function json(res, status, body) {
  const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
  // The rest of an over-size body is not read: the connection goes with the answer.
  if (status === 413) headers.Connection = 'close';
  res.writeHead(status, headers);
  res.end(JSON.stringify(body));
}

/** Passes a command from the page to the lab's launcher, and its answer back; the token stays here. */
async function runOn(lab, req, res) {
  let command;
  try {
    command = remote.commandOf(await remote.readBody(req));
  } catch (e) {
    json(res, e.status || 400, { error: e.message });
    return;
  }
  const end = remote.readEndpoint(lab.folder);
  // A file whose launcher has gone (killed outright) is never trusted with the token.
  if (!end || !remote.alive(end.pid)) { json(res, 503, { error: NOT_RUNNING }); return; }
  const body = JSON.stringify({ command });
  let done = false;
  const answer = (status, error) => {
    if (done) return;
    done = true;
    json(res, status, { error });
  };
  const out = http.request({
    host: '127.0.0.1', port: end.port, path: '/run', method: 'POST', timeout: COMMAND_MS,
    headers: { Authorization: `Bearer ${end.token}`, 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
  }, (r) => {
    const parts = [];
    let size = 0;
    r.on('data', (d) => {
      size += d.length;
      if (size > MAX_ANSWER) { answer(502, remote.STALE); out.destroy(); return; }
      parts.push(d);
    });
    r.on('end', () => {
      if (done) return;
      done = true;
      const a = remote.launcherAnswer(r.statusCode, Buffer.concat(parts).toString('utf8'));
      json(res, a.status, a.body);
    });
    r.on('error', (e) => answer(502, `the launcher's answer broke off (${e.message}); ${MAY_RUN}`));
  });
  out.on('timeout', () => {
    answer(504, `no answer from the launcher in ${COMMAND_MS / 1000} s; ${MAY_RUN}`);
    out.destroy();
  });
  // Nothing listening there: the launcher went without removing its file.
  out.on('error', (e) => answer(e.code === 'ECONNREFUSED' ? 503 : 502, e.code === 'ECONNREFUSED' ? NOT_RUNNING : `${e.message}; ${MAY_RUN}`));
  out.end(body);
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
.bar button{background:var(--panel);border:1px solid var(--line);color:var(--text);padding:2px 8px;border-radius:6px;cursor:pointer;font:inherit}
.dot{width:8px;height:8px;border-radius:50%;background:var(--dim);display:inline-block}.dot.live{background:var(--wx)}
pre{flex:1;margin:0;overflow:auto;padding:8px 12px;font:12.5px/1.45 ui-monospace,Consolas,monospace;white-space:pre-wrap;word-break:break-word}
.l.w{color:var(--warn)}.l.e{color:var(--err)}.l.x{color:var(--wx)}.l.h{display:none}.l.me{color:var(--dim)}.l.me.e{color:var(--err)}
.cmd{display:flex;gap:8px;align-items:center;padding:6px 12px;border-top:1px solid var(--line);color:var(--dim)}
.cmd input{flex:1;min-width:0;background:var(--panel);border:1px solid var(--line);color:var(--text);padding:5px 8px;border-radius:6px;font:12.5px ui-monospace,Consolas,monospace}
.cmd button{background:var(--panel);border:1px solid var(--line);color:var(--text);padding:4px 12px;border-radius:6px;cursor:pointer;font:inherit}
.cmdst{max-width:45%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.cmdst.e{color:var(--err)}
iframe{flex:1;border:0;width:100%;background:#fff}
.empty{padding:24px;color:var(--dim)}
</style></head><body>
<header><h1>Lab Dashboard</h1><div id="tabs" style="display:flex;gap:4px;flex-wrap:wrap"></div></header>
<main id="views"></main>
<script>
const LABS = ${labsJson(LABS)};
const tabs = document.getElementById('tabs'), views = document.getElementById('views');
function show(id){const was=views.querySelector('.view.on');
  for(const b of tabs.children)b.classList.toggle('on',b.dataset.v===id);
  for(const v of views.children)v.classList.toggle('on',v.id===id);
  const now=document.getElementById(id);
  // A map loaded before its lab served stays blank: reload it on coming from another tab, not when already on it.
  const f=now.querySelector('iframe');if(f&&was&&was.id!==id)f.src=f.src;
  // A hidden console does not scroll as lines arrive: bring a following one to its newest line.
  const p=now.querySelector('pre'),fo=now.querySelector('.follow');if(p&&fo&&fo.checked)p.scrollTop=p.scrollHeight;
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
    +'<label title="the facility&#39;s fence markers and effect re-applies"><input type="checkbox" class="quiet" checked> hide facility noise</label></div><pre></pre>'
    +'<form class="cmd" autocomplete="off"><span>&gt;</span><input type="text" spellcheck="false" maxlength="1000"'
    +' placeholder="console command, e.g. op Name or dynmap fullrender world (Enter runs it; Up and Down for earlier ones)">'
    +'<button>Run</button><span class="cmdst"></span></form>';
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
    '^Removed \\\\d+ item','Showing new title','^Set the difficulty',
  ].join('|'));
  const hidden=(t)=>(q.value&&!t.toLowerCase().includes(q.value.toLowerCase()))||(wxonly.checked&&!/WormholeXTreme/.test(t))
    ||(quiet.checked&&noise.test(t.replace(/^\\[[^\\]]*\\] \\[[^\\]]*\\]: /,'')));
  const refilter=()=>{for(const d of pre.children)if(!d.classList.contains('me'))d.classList.toggle('h',hidden(d.textContent))};
  // The command box: the reply comes through the log like any other line; a refusal shows here.
  const cmd=v.querySelector('.cmd'),cin=cmd.querySelector('input'),cst=v.querySelector('.cmdst'),hkey='wxdash.hist.'+lab.id;
  let hist=[];try{hist=JSON.parse(localStorage.getItem(hkey))||[]}catch{}
  if(!Array.isArray(hist))hist=[];hist=hist.filter((h)=>typeof h==='string').slice(-100);
  let at=hist.length;
  const mine=(t,bad)=>{const d=document.createElement('div');d.className='l me'+(bad?' e':'');d.textContent=t;pre.appendChild(d);pre.scrollTop=pre.scrollHeight};
  const state=(t,bad)=>{cst.textContent=t;cst.title=t;cst.className='cmdst'+(bad?' e':'')};
  cmd.onsubmit=async(ev)=>{ev.preventDefault();const c=cin.value.trim();if(!c)return;
    if(hist[hist.length-1]!==c){hist.push(c);if(hist.length>100)hist=hist.slice(-100);try{localStorage.setItem(hkey,JSON.stringify(hist))}catch{}}
    at=hist.length;cin.value='';mine('> '+c);state('running…');
    try{const r=await fetch('/cmd?lab='+encodeURIComponent(lab.id),{method:'POST',
        headers:{'Content-Type':'application/json','X-Wx-Dashboard':'1'},body:JSON.stringify({command:c})});
      const j=await r.json().catch(()=>({}));
      // 500, 502 and 504 leave it unknown whether the command ran: the launcher's queue may still run it.
      if(!r.ok){const why=j.error||('HTTP '+r.status);state(why,true);mine((r.status>=500&&r.status!==503?'  no result: ':'  not run: ')+why,true);return}
      const errs=j.errors||[],lines=j.lines||[];
      state(errs.length?errs[0]:lines.length?lines[lines.length-1]:'done, no output',errs.length>0);
    }catch(e){state('the dashboard did not answer: '+e.message,true);mine('  no result: the dashboard did not answer; check the console before sending it again',true)}};
  cin.onkeydown=(e)=>{
    if(e.key==='ArrowUp'){e.preventDefault();if(at>0)cin.value=hist[--at]}
    else if(e.key==='ArrowDown'){e.preventDefault();if(at<hist.length){at++;cin.value=at<hist.length?hist[at]:''}}};
  q.oninput=refilter;wxonly.onchange=refilter;quiet.onchange=refilter;
  const es=new EventSource('/log?lab='+lab.id);
  es.onmessage=(m)=>{const t=JSON.parse(m.data),d=document.createElement('div');d.className='l '+kind(t);d.textContent=t;
    if(hidden(t))d.classList.add('h');pre.appendChild(d);
    while(pre.children.length>5000)pre.firstChild.remove();
    if(follow.checked)pre.scrollTop=pre.scrollHeight};
  es.addEventListener('reset',()=>{pre.textContent=''});
  es.addEventListener('status',(m)=>{const s=JSON.parse(m.data);st.textContent=s;dot.classList.toggle('live',s==='live')});
  es.onerror=()=>{st.textContent='reconnecting…';dot.classList.remove('live')};
}
for(const lab of LABS){
  const id='map-'+lab.id;addTab(id,'Dynmap '+lab.name);
  const v=document.createElement('section');v.className='view';v.id=id;
  v.innerHTML=lab.map?'<div class="bar"><button class="reload" title="reload the map">↻</button>'
    +'<a style="color:var(--accent)" target="_blank" href="'+lab.map+'">'+lab.map+'</a>'
    +'<span>(blank if the lab is not running)</span></div><iframe loading="lazy" src="'+lab.map+'"></iframe>'
    :'<div class="empty">No Dynmap on this lab. Start it with -With dynmap (no Dynmap build supports 26.x yet).</div>';
  views.appendChild(v);
  const f=v.querySelector('iframe');if(f)v.querySelector('.reload').onclick=()=>{f.src=f.src};
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

/** The dashboard's http.Server, not yet listening, over the labs in `servers`. */
function createDashboard({ servers = SERVERS } = {}) {
  const labs = () => findLabs(servers);
  return http.createServer((req, res) => {
    // Only this machine's own names: a page elsewhere that rebinds a hostname to 127.0.0.1 gets nothing.
    if (!remote.localHost(req.headers.host)) { res.writeHead(403); res.end(); return; }
    let url;
    try { url = new URL(req.url, 'http://127.0.0.1'); } catch { res.writeHead(400); res.end(); return; }
    if (url.pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(page(labs()));
    } else if (url.pathname === '/labs') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(labs().map((l) => l.id)));
    } else if (url.pathname === '/cmd') {
      if (req.method !== 'POST') { json(res, 405, { error: 'POST only' }); return; }
      // Its own page only: a page elsewhere cannot send this header and body without a preflight.
      if (!remote.sameOrigin(req.headers)) { json(res, 403, { error: 'forbidden' }); return; }
      const lab = labs().find((l) => l.id === url.searchParams.get('lab'));
      if (!lab) { json(res, 404, { error: 'no such lab' }); return; }
      runOn(lab, req, res).catch((e) => { if (!res.headersSent) json(res, 500, { error: e.message }); });
    } else if (url.pathname === '/log') {
      const lab = labs().find((l) => l.id === url.searchParams.get('lab'));
      if (!lab) { res.writeHead(404); res.end(); return; }
      stream(lab, res);
    } else {
      res.writeHead(404);
      res.end();
    }
  });
}

if (require.main === module) {
  const argPort = process.argv.indexOf('--port');
  const PORT = argPort > 0 && Number(process.argv[argPort + 1]) > 0 ? Number(process.argv[argPort + 1]) : 8200;
  const server = createDashboard();
  server.on('error', (e) => {
    console.error(e.code === 'EADDRINUSE' ? `Port ${PORT} is in use; is the dashboard already running? Try --port.` : e.message);
    process.exit(1);
  });
  server.listen(PORT, '127.0.0.1', () => console.log(`Lab Dashboard: http://127.0.0.1:${PORT}`));
}

module.exports = { createDashboard, findLabs };
