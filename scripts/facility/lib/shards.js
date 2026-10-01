'use strict';
// --shards N: one version's matrix split across N servers of that version, each with its own
// campus, run at once. Every cell runs exactly as it does alone; only which server runs it is
// decided here. The split balances measured time (a cell's run and its reset, in seconds), not
// the count of cells: the longest cells go first, each to the shard with the least time so far
// (longest processing time first), and the first shard starts with the time of the sections only
// it runs (transit, plates, boards, console). Within a shard the cells keep the matrix's order.
//
// Times come from the last run on this machine (.local-server/cell-times-<version>.json, which
// every self-test updates), else from the times checked in beside this file (a full 1.21.11 run),
// else the mean of those.

const fs = require('fs');
const path = require('path');
const { MATRIX } = require('../matrix');

const DEFAULTS = path.join(__dirname, '..', 'cell-times.json');

function labelOf(id, cell) {
  return cell.name || `${id} ${Object.entries(cell.values).map(([k, x]) => `${k}=${x}`).join(' ')}`;
}

/**
 * What --cells selects: names separated by |, each matched anywhere in a cell's label, or at its
 * start with a leading ^ and its end with a trailing $. A plain matcher, not a regular expression,
 * so nothing typed on the command line becomes one.
 */
function cellMatcher(spec) {
  const parts = String(spec).split('|').map((p) => p.trim()).filter(Boolean).map((p) => {
    const start = p.startsWith('^');
    const end = p.endsWith('$') && p.length > (start ? 1 : 0);
    const text = p.slice(start ? 1 : 0, end ? -1 : undefined);
    return { start, end, text };
  });
  if (parts.length === 0 || parts.some((p) => p.text === '')) throw new Error(`no cell names in "${spec}"`);
  return {
    test(label) {
      return parts.some(({ start, end, text }) => (start && end ? label === text
        : start ? label.startsWith(text)
          : end ? label.endsWith(text)
            : label.includes(text)));
    },
  };
}

/** The labels of the matrix cells a run would take, in matrix order, with --quick and --cells applied. */
function labels({ quick = false, only = null } = {}) {
  const out = [];
  for (const [id, cells] of Object.entries(MATRIX)) {
    for (const cell of cells) {
      const label = labelOf(id, cell);
      if (only && !only.test(label)) continue;
      if (quick && !cell.quick) continue;
      out.push(label);
    }
  }
  return out;
}

function measuredFile(local, version) {
  return path.join(local, `cell-times-${version}.json`);
}

/** { cells: { label: seconds }, once: seconds } for a version: measured here, else the defaults. */
function loadTimes(local, version) {
  const read = (f) => (fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : null);
  const defaults = read(DEFAULTS) || { cells: {}, once: 0 };
  const measured = read(measuredFile(local, version)) || { cells: {} };
  return { cells: { ...defaults.cells, ...measured.cells }, once: measured.once || defaults.once || 0 };
}

/** Folds a run's measured times into the file the next run reads. */
function saveTimes(local, version, cells, once) {
  const f = measuredFile(local, version);
  const had = fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : { cells: {} };
  const out = { cells: { ...had.cells, ...cells }, once: once || had.once };
  fs.mkdirSync(local, { recursive: true });
  fs.writeFileSync(f, `${JSON.stringify(out, null, 1)}\n`);
}

/** Splits `names` into `n` lists by time (see the top of this file); returns [[label, ...], ...]. */
function plan(names, times, n) {
  const known = Object.values(times.cells);
  const mean = known.length ? known.reduce((a, b) => a + b, 0) / known.length : 15;
  const cost = (l) => (times.cells[l] !== undefined ? times.cells[l] : mean);
  const loads = Array.from({ length: n }, (_, i) => (i === 0 ? times.once : 0));
  const assigned = Array.from({ length: n }, () => new Set());
  for (const l of [...names].sort((a, b) => cost(b) - cost(a))) {
    let best = 0;
    for (let i = 1; i < n; i++) if (loads[i] < loads[best]) best = i;
    loads[best] += cost(l);
    assigned[best].add(l);
  }
  return {
    shards: assigned.map((set) => names.filter((l) => set.has(l))),
    loads: loads.map((x) => Math.round(x)),
  };
}

module.exports = { labelOf, labels, cellMatcher, loadTimes, saveTimes, plan, DEFAULTS };
