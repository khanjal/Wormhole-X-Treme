'use strict';
// The text the plugin shows players and admins, collected during a self-test and compared with a
// committed snapshot (messages/<version>.snap), so a wording change is seen without walking the
// lab by eye. Report-only: a difference is printed in the summary and never fails the run.
//
// Two taps feed it: every bot's chat and system messages (probe.js join) and every console
// command's reply, which includes a command block's LastOutput (server.js runNow). Only the
// plugin's own lines are kept, those with its ':: ' header, with colour codes stripped and the
// volatile bits (numbers, ids) replaced. A snapshot line is `<cell or section>\t<message>`.

const fs = require('fs');
const path = require('path');

const SNAP_DIR = path.join(__dirname, '..', 'messages');

let source = null;
const ran = new Set();
const seen = new Set();

/** From here on, messages belong to this cell or section. */
function begin(label) {
  source = label;
  ran.add(label);
}

/** One message as the snapshot keeps it, or null if the plugin did not send it. */
function normalise(raw) {
  const s = String(raw).replace(/§./g, '');
  const at = s.indexOf(':: ');
  if (at < 0) return null;
  return s.slice(at).replace(/["']+$/, '').trim()
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi, 'UUID')
    .replace(/\b(?=[0-9a-f]*\d)(?=[0-9a-f]*[a-f])[0-9a-f]{4,}\b/gi, 'ID') // a ring pair's id
    .replace(/(capture: ).*/, '$1STATE') // a capture's file and cache settle a moment after the cell
    .replace(/-?\d+(?:\.\d+)?/g, 'N');
}

function record(raw) {
  const text = source === null ? null : normalise(raw);
  if (text) seen.add(`${source}\t${text}`);
}

/** What this process saw: the cells and sections it ran, and the messages under them. */
function collected() {
  return { ran: [...ran], lines: [...seen] };
}

/** Several processes' collected() as one (the shards of a version). */
function merge(parts) {
  return { ran: [...new Set(parts.flatMap((p) => p.ran))], lines: [...new Set(parts.flatMap((p) => p.lines))] };
}

const sorted = (lines) => [...lines].sort();
const sourceOf = (line) => line.slice(0, line.indexOf('\t'));

/**
 * Writes the run's messages to `outFile`, and prints how they differ from the version's snapshot,
 * within the cells and sections that ran (a --quick or --cells run leaves the rest alone). With
 * `update` the snapshot takes the run's lines for those, and keeps the others'.
 */
function report(version, { ran: ranHere, lines }, { outFile, update = false, log = console.log }) {
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  fs.writeFileSync(outFile, `${sorted(lines).join('\n')}\n`);
  const snapFile = path.join(SNAP_DIR, `${version}.snap`);
  const have = fs.existsSync(snapFile);
  const snap = have ? fs.readFileSync(snapFile, 'utf8').split(/\r?\n/).filter(Boolean) : [];
  const scope = new Set(ranHere);
  const before = new Set(snap.filter((l) => scope.has(sourceOf(l))));
  const now = new Set(lines);
  const added = sorted(lines.filter((l) => !before.has(l)));
  const removed = sorted([...before].filter((l) => !now.has(l)));
  if (update) {
    fs.mkdirSync(SNAP_DIR, { recursive: true });
    fs.writeFileSync(snapFile, `${sorted([...snap.filter((l) => !scope.has(sourceOf(l))), ...lines]).join('\n')}\n`);
    log(`messages on ${version}: snapshot ${have ? 'updated' : 'written'} (${added.length} added, ${removed.length} removed), ${snapFile}`);
    return;
  }
  if (!have) {
    log(`messages on ${version}: ${lines.length} lines, no snapshot yet (--update-messages writes ${path.relative(process.cwd(), snapFile)})`);
    return;
  }
  if (!added.length && !removed.length) {
    log(`messages on ${version}: unchanged (${before.size} lines in the cells run)`);
    return;
  }
  log(`messages on ${version}: CHANGED, ${added.length} added, ${removed.length} removed (report only; --update-messages accepts it; the run is in ${outFile})`);
  for (const l of removed) log(`  - ${l.replace('\t', ' | ')}`);
  for (const l of added) log(`  + ${l.replace('\t', ' | ')}`);
}

module.exports = { begin, record, collected, merge, report, normalise };
