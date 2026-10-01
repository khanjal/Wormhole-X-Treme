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
    return env.WX_BROWSER;
  }
  const win = [env.PROGRAMFILES, env['PROGRAMFILES(X86)'], env.LOCALAPPDATA].filter(Boolean).flatMap((d) => [
    path.join(d, 'Google', 'Chrome', 'Application', 'chrome.exe'),
    path.join(d, 'Microsoft', 'Edge', 'Application', 'msedge.exe'),
  ]);
  const unix = ['/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser', '/usr/bin/microsoft-edge',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'];
  return (process.platform === 'win32' ? win : unix).find((f) => fs.existsSync(f)) || null;
}

const sleep = (ms) => new Promise((resolve) => { setTimeout(resolve, ms); });

/**
 * Takes each shot: { name, file, ok, detail }. `fac` has Probe; `viewer` is lib/viewer.js's
 * running viewer; PNGs go to `outDir`/<name>.png.
 */
async function takeShots(fac, viewer, shots, outDir, { log = console.log, width = 1280, height = 720, settleMs = 60000 } = {}) {
  const exe = findBrowser();
  if (!exe) throw new Error('--shots needs Chrome or Edge installed, or WX_BROWSER naming a Chromium-based browser');
  // An ES module: imported, not required (Node 22 warns that requiring one is experimental).
  const { default: puppeteer } = await import('puppeteer-core');
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await puppeteer.launch({
    executablePath: exe,
    headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--no-first-run', '--no-default-browser-check', '--mute-audio'],
  });
  const out = [];
  const bot = fac.probe.bot;
  try {
    const page = await browser.newPage();
    await page.setViewport({ width, height });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    for (const s of shots) {
      const file = path.join(outDir, `${s.name}.png`);
      try {
        // Flying, so a point in the air holds; again after each move, as a dimension change resets it.
        bot.creative.startFlying();
        await fac.probe.teleport(s, s.dim);
        bot.creative.startFlying();
        await bot.waitForChunksToLoad();
        errors.length = 0;
        await page.goto(`${viewer.url}first/`, { waitUntil: 'load' });
        await page.waitForSelector('canvas', { timeout: 15000 });
        // Drawn once two frames a second apart are the same, after three seconds at least.
        const t0 = Date.now();
        let last = null;
        let png = null;
        let still = false;
        await sleep(3000);
        while (Date.now() - t0 < settleMs) {
          png = await page.screenshot({ type: 'png' });
          if (last && png.equals(last)) { still = true; break; }
          last = png;
          await sleep(1000);
        }
        fs.writeFileSync(file, png);
        const secs = ((Date.now() - t0) / 1000).toFixed(0);
        const ok = still && !errors.length;
        const detail = `${secs} s${still ? '' : `, still changing after ${settleMs / 1000} s`}${errors.length ? `; page errors: ${errors.slice(0, 3).join(' / ')}` : ''}`;
        out.push({ name: s.name, file, ok, detail });
        log(`  ${ok ? 'shot' : 'SHOT PROBLEM'} ${s.name}: ${file} (${detail})`);
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

module.exports = { select, findBrowser, takeShots };
