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

/** Every lab folder with a log, newest first; its Dynmap URL when Dynmap is installed there. */
function findLabs() {
  let names = [];
  try { names = fs.readdirSync(SERVERS).filter((n) => /^facility-/.test(n)); } catch { return []; }
  return names
    .map((n) => path.join(SERVERS, n))
    .filter((folder) => fs.existsSync(path.join(folder, 'logs', 'latest.log')))
    .sort((a, b) => fs.statSync(path.join(b, 'logs', 'latest.log')).mtimeMs - fs.statSync(path.join(a, 'logs', 'latest.log')).mtimeMs)
    .map((folder) => {
      const m = /^facility-(.+?)(?:-(\d+))?$/.exec(path.basename(folder));
      let map = null;
      try {
        const conf = fs.readFileSync(path.join(folder, 'plugins', 'dynmap', 'configuration.txt'), 'utf8');
        const port = /^webserver-port:\s*(\d+)/m.exec(conf);
        if (port) map = `http://localhost:${port[1]}/`;
      } catch { /* no Dynmap here */ }
      return { id: path.basename(folder).replace(/\W/g, '_'), name: `${m[1]} · :${m[2] || 25590}`, folder, map };
    });
}

const BACKLOG = 400;
const POLL_MS = 500;

const argPort = process.argv.indexOf('--port');
const PORT = argPort > 0 ? Number(process.argv[argPort + 1]) : 8200;

/** The last `count` lines of a file, read from its end. */
function tail(file, count) {
  try {
    const size = fs.statSync(file).size;
    const start = Math.max(0, size - 256 * 1024);
    const fd = fs.openSync(file, 'r');
    const buf = Buffer.alloc(size - start);
    fs.readSync(fd, buf, 0, buf.length, start);
    fs.closeSync(fd);
    const lines = buf.toString('utf8').split(/\r?\n/);
    if (start > 0) lines.shift();
    return { lines: lines.filter((l) => l.length).slice(-count), offset: size };
  } catch {
    return { lines: [], offset: 0 };
  }
}

/** Streams a lab's log as server-sent events: the backlog, then each new line. */
function stream(lab, res) {
  const file = path.join(lab.folder, 'logs', 'latest.log');
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
  const send = (line) => res.write(`data: ${JSON.stringify(line)}\n\n`);
  const first = tail(file, BACKLOG);
  let offset = first.offset;
  let partial = '';
  first.lines.forEach(send);
  res.write(`event: status\ndata: ${JSON.stringify(fs.existsSync(file) ? 'live' : 'no log yet')}\n\n`);
  const timer = setInterval(() => {
    let size;
    try { size = fs.statSync(file).size; } catch { return; }
    if (size < offset) { offset = 0; partial = ''; send('— log restarted (server started again) —'); }
    if (size === offset) return;
    const fd = fs.openSync(file, 'r');
    const buf = Buffer.alloc(size - offset);
    fs.readSync(fd, buf, 0, buf.length, offset);
    fs.closeSync(fd);
    offset = size;
    const text = partial + buf.toString('utf8');
    const lines = text.split(/\r?\n/);
    partial = lines.pop();
    lines.filter((l) => l.length).forEach(send);
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
const LABS = ${JSON.stringify(LABS.map(({ id, name, map }) => ({ id, name, map })))};
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
if(!LABS.length)views.innerHTML='<div class="empty">No labs yet. Start one with scripts/facility/lab.ps1, then reload.</div>';
else{let first='con-'+LABS[0].id;try{first=localStorage.getItem('wxdash.tab')||first}catch{}
show(document.getElementById(first)?first:'con-'+LABS[0].id);}
</script></body></html>`;

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(page(findLabs()));
  } else if (url.pathname === '/log') {
    const lab = findLabs().find((l) => l.id === url.searchParams.get('lab'));
    if (!lab) { res.writeHead(404); res.end(); return; }
    stream(lab, res);
  } else {
    res.writeHead(404);
    res.end();
  }
}).listen(PORT, '127.0.0.1', () => console.log(`Lab Dashboard: http://127.0.0.1:${PORT}`));
