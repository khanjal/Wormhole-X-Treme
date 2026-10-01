'use strict';
// The plugins-extra drop folder (design 1.5): any plugin jar put there is copied into the test
// server's plugins/ at start, for what --with does not pin (an economy plugin for Vault, say).
// Only a jar this copy wrote is recorded (plugins/.wx-extras.json, with its SHA-256 as written),
// so a later run without it takes it out again, and only while it is still those bytes. A jar
// of the same name already in plugins/ that no run wrote is somebody's own: one with other bytes
// refuses the run, one with the same bytes is left alone and not recorded. A name a --with
// companion installed is refused.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const RECORD = '.wx-extras.json';
const COMPANIONS = '.wx-companions.json';

const sha = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const bare = (f) => typeof f === 'string' && path.basename(f) === f && f.endsWith('.jar');

/** What an earlier run wrote: { file: sha256 }, bare jar names only. */
function readRecord(dir) {
  try {
    const r = JSON.parse(fs.readFileSync(path.join(dir, RECORD), 'utf8'));
    const files = r.files && typeof r.files === 'object' && !Array.isArray(r.files) ? r.files : {};
    return Object.fromEntries(Object.entries(files).filter(([f, h]) => bare(f) && typeof h === 'string'));
  } catch {
    return {};
  }
}

/** The jars a --with run installed here (lib/companions.js's record). */
function companionFiles(dir) {
  try {
    const r = JSON.parse(fs.readFileSync(path.join(dir, COMPANIONS), 'utf8'));
    return Array.isArray(r.files) ? r.files : [];
  } catch {
    return [];
  }
}

/**
 * Copies `from`'s jars into `folder`/plugins and takes out those an earlier run wrote that are
 * gone from it; `from` null or missing copies nothing (and still takes out). Returns
 * { copied, removed, kept } (kept: a jar of ours somebody has since replaced, left alone).
 */
function install(folder, from) {
  const dir = path.join(folder, 'plugins');
  fs.mkdirSync(dir, { recursive: true });
  const had = readRecord(dir);
  const companions = companionFiles(dir);
  const jars = from && fs.existsSync(from) ? fs.readdirSync(from).filter((f) => f.endsWith('.jar')).sort() : [];
  for (const f of jars) {
    if (companions.includes(f)) throw new Error(`${f} in ${from} has the name of a --with companion installed in ${dir}: rename it or run without that companion`);
    const to = path.join(dir, f);
    if (!fs.existsSync(to)) continue;
    const now = sha(to);
    if (f in had) {
      if (now !== had[f] && now !== sha(path.join(from, f))) throw new Error(`${to} was copied by an earlier run but has been replaced since: it is left alone; move it aside to run with ${from}'s`);
    } else if (now !== sha(path.join(from, f))) {
      throw new Error(`${to} is already there, is not the one in ${from}, and no earlier run copied it: it is left alone; move it aside to run with it`);
    }
  }
  const record = {};
  const removed = [];
  const kept = [];
  for (const [f, h] of Object.entries(had)) {
    if (jars.includes(f)) continue;
    const to = path.join(dir, f);
    if (!fs.existsSync(to)) continue;
    if (sha(to) === h) { fs.rmSync(to, { force: true }); removed.push(f); } else kept.push(f);
  }
  const copied = [];
  for (const f of jars) {
    const src = path.join(from, f);
    const to = path.join(dir, f);
    const want = sha(src);
    // Somebody's own copy of the same bytes: theirs, so neither written nor recorded.
    if (!(f in had) && fs.existsSync(to)) continue;
    if (!fs.existsSync(to) || sha(to) !== want) fs.copyFileSync(src, to);
    record[f] = want;
    copied.push(f);
  }
  fs.writeFileSync(path.join(dir, RECORD), `${JSON.stringify({
    note: 'Written by scripts/facility/run-facility.js: the jars it copied here from plugins-extra, each with its SHA-256 as written. '
      + 'A run without one takes it out, if it is still those bytes.',
    files: record,
  }, null, 1)}\n`);
  return { copied, removed, kept };
}

/** The folder to copy from: --plugins-extra, else .local-server/plugins-extra if it is there. */
function folderOf(repo, given) {
  if (given) return path.resolve(given);
  const d = path.join(repo, '.local-server', 'plugins-extra');
  return fs.existsSync(d) ? d : null;
}

module.exports = { install, folderOf, RECORD };
