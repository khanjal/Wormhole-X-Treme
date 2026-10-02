'use strict';
// Pictures of the facility (--shots): Probe flies to each named vantage point in
// campus.SHOTS, and a headless browser opens the viewer's first-person page and saves a PNG
// once the picture has stopped changing (every chunk in view drawn). The browser is an
// installed Chrome or Edge driven by puppeteer-core, which downloads nothing; WX_BROWSER names
// another (a chrome-headless-shell, say). WebGL is drawn by SwiftShader, in software, so a
// machine without a GPU, or a headless one, draws the same.

const fs = require('fs');
const path = require('path');
const campus = require('./campus');

/** The shots `spec` names: 'all', or a comma-separated list of names; throws on one it does not know. */
function select(spec) {
  if (!spec || spec === 'all') return campus.SHOTS;
  const names = spec.split(',').map((s) => s.trim()).filter(Boolean);
  const bad = names.filter((n) => !campus.SHOTS.some((s) => s.name === n));
  if (bad.length) throw new Error(`no shot named ${bad.join(', ')} (there are: ${campus.SHOTS.map((s) => s.name).join(', ')})`);
  return names.map((n) => campus.SHOTS.find((s) => s.name === n));
}

/** A Chrome or Edge to drive: WX_BROWSER, else the usual install places. Null if none. */
function findBrowser(env = process.env) {
  if (env.WX_BROWSER) {
    if (!fs.existsSync(env.WX_BROWSER)) throw new Error(`WX_BROWSER is ${env.WX_BROWSER}, which is not there`);
    return path.resolve(env.WX_BROWSER);
  }
  const win = [env.PROGRAMFILES, env['PROGRAMFILES(X86)'], env.LOCALAPPDATA].filter(Boolean).flatMap((d) => [
    path.join(d, 'Google', 'Chrome', 'Application', 'chrome.exe'),
    path.join(d, 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
  ]);
  const unix = ['/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/microsoft-edge',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'];
  return (process.platform === 'win32' ? win : unix).find((f) => fs.existsSync(f)) || null;
}

/**
 * The browser's environment: this one without __COMPAT_LAYER. Edge started under a Windows
 * compatibility layer (some launchers set DetectorsAppHealth) starts itself again without it and
 * exits 0, which puppeteer takes for a failed launch, and the second Edge is left running.
 */
function browserEnv(env = process.env) {
  const out = { ...env };
  for (const k of Object.keys(out)) if (k.toUpperCase() === '__COMPAT_LAYER') delete out[k];
  return out;
}

const sleep = (ms) => new Promise((resolve) => { setTimeout(resolve, ms); });

// The page's empty sky: three.js's 'lightblue', what a frame with nothing drawn shows throughout.
const SKY = [173, 216, 230];
// A frame is a picture of the facility only if this share of it is not sky.
const MIN_SCENE = 0.05;

/** The share of an RGBA buffer's pixels that are not the page's sky (within a little rounding). */
function sceneShare(rgba) {
  let scene = 0;
  const n = rgba.length / 4;
  for (let i = 0; i < rgba.length; i += 4) {
    if (Math.abs(rgba[i] - SKY[0]) + Math.abs(rgba[i + 1] - SKY[1]) + Math.abs(rgba[i + 2] - SKY[2]) > 24) scene++;
  }
  return n ? scene / n : 0;
}

/**
 * Whether a shot is a picture: the frame held still, the page was sent chunks and they did not
 * change while it held, enough of it is not empty sky, and the page threw nothing. Returns
 * { ok, why } with every reason it is not.
 */
function judge({ still, chunks, chunksBefore, share, errors = [] }) {
  const why = [];
  if (!still) why.push('still changing');
  if (!chunks) why.push('the page was sent no chunks');
  else if (chunks !== chunksBefore) why.push('chunks still arriving');
  if (!(share >= MIN_SCENE)) why.push(`blank: ${(100 * (share || 0)).toFixed(1)}% of it is not sky`);
  if (errors.length) why.push(`page errors: ${errors.slice(0, 3).join(' / ')}`);
  return { ok: why.length === 0, why };
}

/** A screenshot's share of scene: decoded to pixels in the page (no image library here), judged here. */
async function shareIn(page, png) {
  const b64 = await page.evaluate(async (data) => {
    const img = new Image();
    img.src = `data:image/png;base64,${data}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width;
    c.height = img.height;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    let bin = '';
    for (let i = 0; i < d.length; i += 0x8000) bin += String.fromCharCode(...d.subarray(i, i + 0x8000));
    return btoa(bin);
  }, png.toString('base64'));
  return sceneShare(Buffer.from(b64, 'base64'));
}

/**
 * Waits for the picture to hold: frames `everyMs` apart from `shoot()`, until `pairs` pairs of
 * consecutive frames running are the same with `chunks()` unchanged across them (two pairs, so a
 * pause while SwiftShader is still meshing is not taken for the end), or `settleMs` has passed.
 * Returns { png: the last frame, still, chunksBefore: chunks() at the first frame of the run }.
 */
async function settle({ shoot, chunks, wait = sleep, settleMs = 60000, everyMs = 1000, pairs = 2, now = Date.now }) {
  const t0 = now();
  let last = null;
  let png = null;
  let run = 0;
  let runStart = -1;
  let prevChunks = -1;
  while (now() - t0 < settleMs) {
    const before = chunks();
    png = await shoot();
    if (last && png.equals(last) && before === prevChunks) {
      run++;
      if (run >= pairs) return { png, still: true, chunksBefore: runStart };
    } else {
      run = 0;
      runStart = before;
    }
    last = png;
    prevChunks = before;
    await wait(everyMs);
  }
  return { png, still: false, chunksBefore: runStart };
}

/** Shots as self-test results, section 'shots': one per shot, so a bad one fails the run. */
function asResults(shots) {
  return shots.map((s) => ({ section: 'shots', name: s.name, ok: s.ok, detail: s.file ? `${s.file} (${s.detail})` : s.detail }));
}

/**
 * Takes each shot: { name, file, ok, detail }. `fac` has Probe; `viewer` is lib/viewer.js's
 * running viewer; PNGs go to `outDir`/<name>.png. A shot that is not a picture (see judge) is
 * saved all the same, for a look, and fails. `launch` starts the browser (puppeteer's, unless a
 * test gives its own). WX_SHOTS_SETTLE_MS shortens the wait, to see a failed shot fail a run.
 */
async function takeShots(fac, viewer, shots, outDir, {
  log = console.log, width = 1280, height = 720, settleMs = Number(process.env.WX_SHOTS_SETTLE_MS) || 60000, launch = null,
} = {}) {
  const exe = findBrowser();
  if (!exe) throw new Error('--shots needs Chrome or Edge installed, or WX_BROWSER naming a Chromium-based browser');
  // An ES module: imported, not required (Node 22 warns that requiring one is experimental).
  const start = launch || (await import('puppeteer-core')).default.launch;
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await start({
    executablePath: exe,
    headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-first-run', '--no-default-browser-check', '--mute-audio'],
    env: browserEnv(),
  });
  const out = [];
  const bot = fac.probe.bot;
  try {
    const page = await browser.newPage();
    await page.setViewport({ width, height });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    for (const [i, s] of shots.entries()) {
      const file = path.join(outDir, `${s.name}.png`);
      try {
        // The last shot's page goes first, so it is not sent the chunks of this move.
        await page.goto('about:blank');
        // Flying, so a point in the air holds; again after each move, as a dimension change resets it.
        bot.creative.startFlying();
        await fac.probe.teleport(s, s.dim);
        bot.creative.startFlying();
        await bot.waitForChunksToLoad();
        errors.length = 0;
        // The page names itself, so only its own chunks are counted (another page may be open).
        const token = `s${process.pid}-${i}-${Date.now()}`;
        const chunks = () => viewer.chunks(token);
        await page.goto(`${viewer.url}first/?shot=${token}`, { waitUntil: 'load' });
        await page.waitForSelector('canvas', { timeout: 15000 });
        const t0 = Date.now();
        await sleep(3000);
        const held = await settle({ shoot: () => page.screenshot({ type: 'png' }), chunks, settleMs: Math.max(0, settleMs - 3000) });
        if (!held.png) throw new Error(`no frame within ${settleMs} ms`);
        fs.writeFileSync(file, held.png);
        const share = await shareIn(page, held.png);
        const verdict = judge({ still: held.still, chunks: chunks(), chunksBefore: held.chunksBefore, share, errors });
        const secs = ((Date.now() - t0) / 1000).toFixed(0);
        const detail = `${secs} s, ${chunks()} chunks, ${(100 * share).toFixed(0)}% scene${verdict.ok ? '' : `; ${verdict.why.join('; ')}`}`;
        out.push({ name: s.name, file, ok: verdict.ok, detail });
        log(`  ${verdict.ok ? 'shot' : 'SHOT PROBLEM'} ${s.name}: ${file} (${detail})`);
      } catch (e) {
        out.push({ name: s.name, file: null, ok: false, detail: e.message });
        log(`  SHOT FAILED ${s.name}: ${e.message}`);
      }
    }
  } finally {
    await browser.close().catch(() => {});
    try { bot.creative.stopFlying(); } catch { /* left */ }
    await fac.probe.teleport(campus.TRANSIT.home).catch(() => {});
  }
  return out;
}

module.exports = { select, findBrowser, browserEnv, takeShots, settle, asResults, judge, sceneShare, SKY, MIN_SCENE };
