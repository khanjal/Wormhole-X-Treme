#!/usr/bin/env node
'use strict';
// A read-only dashboard for the facility's labs: each lab's live console (streamed from its
// logs/latest.log) and its Dynmap, in one browser page. Listens on 127.0.0.1 only.
//
//   node scripts/facility/dashboard.js            http://127.0.0.1:8200
//   node scripts/facility/dashboard.js --port 8300
//
// Labs are the server folders under .local-server/ that run-facility.js made; it sends no commands.

const fs = require('fs');
const http = require('http');
const path = require('path');

const SERVERS = path.join(__dirname, '..', '..', '.local-server');

/** A lab's log file, or null when it has none (yet). */
function logOf(folder) {
  try { return fs.statSync(path.join(folder, 'logs', 'latest.log')); } catch { return null; }
}

/** Every lab folder with a log, newest first; its Dynmap URL when Dynmap is installed there. */
function findLabs() {
  let names = [];
  try { names = fs.readdirSync(SERVERS); } catch { return []; }
  return names
    .map((n) => ({ n, m: /^facility-(.+?)(?:-(\d+))?$/.exec(n), log: logOf(path.join(SERVERS, n)) }))
    .filter(({ m, log }) => m && log)
    .sort((x, y) => y.log.mtimeMs - x.log.mtimeMs)
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
const POLL_MS = 500;

const argPort = process.argv.indexOf('--port');
const PORT = argPort > 0 && Number(process.argv[argPort + 1]) > 0 ? Number(process.argv[argPort + 1]) : 8200;

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
  // A reconnect sends the backlog again, so the page clears what it has first.
  res.write('event: reset\ndata: ""\n\n');
  let offset = 0;
  let inode = null;
  let pending = Buffer.alloc(0);
  /** Sends every complete line from offset to `size`, holding back an unfinished last line. */
  const advance = (size, keep, dropFirst = false) => {
    const chunk = readRange(file, offset, size);
    if (!chunk) return;
    offset += chunk.length;
    const all = Buffer.concat([pending, chunk]);
    const cut = all.lastIndexOf(0x0a) + 1;
    pending = all.subarray(cut);
    const lines = all.subarray(0, cut).toString('utf8').split(/\r?\n/);
    if (dropFirst) lines.shift();
    lines.filter((l) => l.length).slice(-keep).forEach(send);
  };
  const first = logOf(lab.folder);
  if (first) {
    inode = first.ino;
    offset = Math.max(0, first.size - 256 * 1024);
    advance(first.size, BACKLOG, offset > 0);
  }
  res.write(`event: status\ndata: ${JSON.stringify(first ? 'live' : 'no log yet')}\n\n`);
  const timer = setInterval(() => {
    const now = logOf(lab.folder);
    if (!now) return;
    // Paper replaces latest.log on a restart: a new file (a new inode), whatever its size.
    if (now.ino !== inode || now.size < offset) {
      if (inode !== null) send('— log restarted (server started again) —');
      inode = now.ino;
      offset = 0;
      pending = Buffer.alloc(0);
    }
    if (now.size > offset) advance(now.size, Infinity);
  }, POLL_MS);
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
.l.w{color:var(--warn)}.l.e{color:var(--err)}.l.x{color:var(--wx)}.l.h{display:none}
iframe{flex:1;border:0;width:100%;background:#fff}
.empty{padding:24px;color:var(--dim)}
</style></head><body>
<header><h1>Lab Dashboard</h1><div id="tabs" style="display:flex;gap:4px;flex-wrap:wrap"></div></header>
<main id="views"></main>
<script>
const LABS = ${labsJson(LABS)};
const tabs = document.getElementById('tabs'), views = document.getElementById('views');
function show(id){for(const b of tabs.children)b.classList.toggle('on',b.dataset.v===id);
  for(const v of views.children)v.classList.toggle('on',v.id===id);
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
    +'<label title="the facility&#39;s fence markers and effect re-applies"><input type="checkbox" class="quiet" checked> hide facility noise</label></div><pre></pre>';
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
  const refilter=()=>{for(const d of pre.children)d.classList.toggle('h',hidden(d.textContent))};
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
  v.innerHTML=lab.map?'<div class="bar"><a style="color:var(--accent)" target="_blank" href="'+lab.map+'">'+lab.map+'</a>'
    +'<span>(blank if the lab is not running)</span></div><iframe loading="lazy" src="'+lab.map+'"></iframe>'
    :'<div class="empty">No Dynmap on this lab. Start it with -With dynmap (no Dynmap build supports 26.x yet).</div>';
  views.appendChild(v);
}
if(!LABS.length)views.innerHTML='<div class="empty">No labs yet. Start one with scripts/facility/lab.ps1; this page picks it up once its server has written a log.</div>';
else{let first='con-'+LABS[0].id;try{first=localStorage.getItem('wxdash.tab')||first}catch{}
show(document.getElementById(first)?first:'con-'+LABS[0].id);}
// Reload when a lab appears or goes, so a lab started after this page still shows up.
const known=LABS.map((l)=>l.id).join();
setInterval(()=>fetch('/labs').then((r)=>r.json()).then((ids)=>{if(ids.join()!==known)location.reload()}).catch(()=>{}),5000);
</script></body></html>`;

/** The labs as JSON that is safe inside an inline script. */
function labsJson(labs) {
  return JSON.stringify(labs.map(({ id, name, map }) => ({ id, name, map })))
    .replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
}

const server = http.createServer((req, res) => {
  // Only this machine's own names: a page elsewhere that rebinds a hostname to 127.0.0.1 gets nothing.
  if (!/^(127\.0\.0\.1|localhost)(:\d+)?$/.test(req.headers.host || '')) { res.writeHead(403); res.end(); return; }
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(page(findLabs()));
  } else if (url.pathname === '/labs') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(findLabs().map((l) => l.id)));
  } else if (url.pathname === '/log') {
    const lab = findLabs().find((l) => l.id === url.searchParams.get('lab'));
    if (!lab) { res.writeHead(404); res.end(); return; }
    stream(lab, res);
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
