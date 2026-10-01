'use strict';
// The plugins-extra drop folder (design 1.5): any plugin jar put there is copied into the test
// server's plugins/ at start, for what --with does not pin (an economy plugin for Vault, say).
// What it copied is recorded in plugins/.wx-extras.json, so a later run without a jar takes it
// out again, and never one it did not put there: a jar of the same name already in plugins/ that
// no run copied is somebody's own, and the run is refused rather than overwrite it.

const fs = require('fs');
const path = require('path');

const RECORD = '.wx-extras.json';

function readRecord(dir) {
  try {
    const r = JSON.parse(fs.readFileSync(path.join(dir, RECORD), 'utf8'));
    return Array.isArray(r.files) ? r.files.filter((f) => path.basename(f) === f && f.endsWith('.jar')) : [];
  } catch {
    return [];
  }
}

const same = (a, b) => fs.existsSync(a) && fs.existsSync(b) && fs.readFileSync(a).equals(fs.readFileSync(b));

/**
 * Copies `from`'s jars into `folder`/plugins and takes out those an earlier run copied that are
 * gone from it; `from` null or missing copies nothing (and still takes out). Returns
 * { copied, removed }.
 */
function install(folder, from) {
  const dir = path.join(folder, 'plugins');
  fs.mkdirSync(dir, { recursive: true });
  const had = readRecord(dir);
  const jars = from && fs.existsSync(from) ? fs.readdirSync(from).filter((f) => f.endsWith('.jar')).sort() : [];
  for (const f of jars) {
    const to = path.join(dir, f);
    if (fs.existsSync(to) && !had.includes(f) && !same(path.join(from, f), to)) {
      throw new Error(`${to} is already there, is not the one in ${from}, and no earlier run copied it: it is left alone; move it aside to run with it`);
    }
  }
  const removed = had.filter((f) => !jars.includes(f) && fs.existsSync(path.join(dir, f)));
  for (const f of removed) fs.rmSync(path.join(dir, f), { force: true });
  const copied = [];
  for (const f of jars) {
    const to = path.join(dir, f);
    if (!same(path.join(from, f), to)) fs.copyFileSync(path.join(from, f), to);
    copied.push(f);
  }
  fs.writeFileSync(path.join(dir, RECORD), `${JSON.stringify({
    note: 'Written by scripts/facility/run-facility.js: the jars it copied here from plugins-extra. A run without one takes it out.',
    files: copied,
  }, null, 1)}\n`);
  return { copied, removed };
}

/** The folder to copy from: --plugins-extra, else .local-server/plugins-extra if it is there. */
function folderOf(repo, given) {
  if (given) return path.resolve(given);
  const d = path.join(repo, '.local-server', 'plugins-extra');
  return fs.existsSync(d) ? d : null;
}

module.exports = { install, folderOf, RECORD };
