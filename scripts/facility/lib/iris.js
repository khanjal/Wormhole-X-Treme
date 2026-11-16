'use strict';
// The iris as a client is shown it, for G5: the order its cells cross in, worked out the way the
// plugin works it out (IrisSweep: the same centre, keys, spiral winding and cutting), and a
// recorder of the block-change packets a client receives in a gate's opening. Every fact is from
// the plugin's source (IrisSweep, StargateIrisAnimator, ConfigManager, IrisLayering,
// DrawnHorizon, MaterialUtils).
//
//  - Styles: sweep (rings by squared distance from the middle), rows (by height), columns (by
//    the horizontal), spiral (one arm wound in clockwise from the rim as seen from the switch,
//    cut into as many steps as the sweep makes),
//    instant (no animation). The centre is the midpoint of the opening's extremes.
//  - Opening runs innermost first; closing is the same steps reversed.
//  - A step is drawn every 2 ticks (IrisSweepDriver.STEP_TICKS), the first in the tick of the
//    toggle; with more steps than max(2, 20 / 2) = 10 (SWEEP_MAX_TICKS over the step) adjacent
//    steps are merged into that many bands.

const STYLES = ['sweep', 'spiral', 'rows', 'columns', 'instant'];

function centreOf(cells) {
  const axis = (k) => (Math.min(...cells.map((c) => c[k])) + Math.max(...cells.map((c) => c[k]))) / 2;
  return { x: axis('x'), y: axis('y'), z: axis('z') };
}

function keyOf(centre, c, style) {
  const dx = c.x - centre.x;
  const dy = c.y - centre.y;
  const dz = c.z - centre.z;
  if (style === 'rows') return dy * dy;
  if (style === 'columns') return dx * dx + dz * dz;
  return dx * dx + dy * dy + dz * dz;
}

/** Cells grouped by key, nearest first; within a group by y, x, z. */
function rings(cells, style) {
  if (!cells.length) return [];
  const centre = centreOf(cells);
  const by = new Map();
  for (const c of cells) {
    const k = keyOf(centre, c, style);
    if (!by.has(k)) by.set(k, []);
    by.get(k).push(c);
  }
  return [...by.keys()].sort((a, b) => a - b).map((k) => by.get(k).sort((a, b) => a.y - b.y || a.x - b.x || a.z - b.z));
}

/** Cuts a list into `wanted` pieces by position (IrisSweep.cut). */
function cut(items, wanted) {
  const out = [];
  for (let i = 0; i < wanted; i++) out.push(items.slice(Math.floor((i * items.length) / wanted), Math.floor(((i + 1) * items.length) / wanted)));
  return out;
}

/**
 * The opening's {right, up} as a player at the activation switch sees it (IrisSweep.viewOf).
 * `facing` is the switch's facing as {x, z}, or null to guess from the opening.
 */
function viewOf(cells, facing) {
  const spans = (k) => cells.some((c) => c[k] !== cells[0][k]);
  const level = !!facing && Math.abs(facing.x) + Math.abs(facing.z) === 1;
  const flat = !spans('y') && spans('x') && spans('z');
  let up;
  let toViewer;
  if (flat) {
    toViewer = [0, 1, 0];
    up = level ? [-facing.x, 0, -facing.z] : [0, 0, -1];
  } else {
    up = [0, 1, 0];
    toViewer = level ? [facing.x, 0, facing.z] : spans('x') ? [0, 0, 1] : [1, 0, 0];
  }
  const right = [
    up[1] * toViewer[2] - up[2] * toViewer[1],
    up[2] * toViewer[0] - up[0] * toViewer[2],
    up[0] * toViewer[1] - up[1] * toViewer[0],
  ];
  return { right, up };
}

/** One arm wound in clockwise from the rim, the closing order cell by cell (IrisSweep.closingArm). */
function closingArm(cells, facing) {
  const centre = centreOf(cells);
  const { right, up } = viewOf(cells, facing);
  const dot = (c, v) => (c.x - centre.x) * v[0] + (c.y - centre.y) * v[1] + (c.z - centre.z) * v[2];
  const polar = cells.map((c) => {
    const a = dot(c, right);
    const b = dot(c, up);
    let turn = Math.atan2(a, b) / (2 * Math.PI);
    if (turn < 0) turn += 1;
    return { radius: Math.hypot(a, b), turn };
  });
  const outermost = Math.max(...polar.map((p) => p.radius));
  const width = Math.max(1, Math.ceil(outermost / 5));
  const along = polar.map((p) => Math.round((outermost - p.radius) / width - p.turn) + p.turn);
  // Java's sort is stable; so is Array.prototype.sort.
  return cells.map((_, i) => i).sort((a, b) => along[a] - along[b] || polar[b].radius - polar[a].radius).map((i) => cells[i]);
}

function spiral(cells, facing = null) {
  if (!cells.length) return [];
  return cut(closingArm(cells, facing).reverse(), Math.min(cells.length, Math.max(1, rings(cells, 'sweep').length)));
}

/** At most `maxSteps` steps, adjacent ones merged (0: as many as there are). */
function atMost(steps, maxSteps) {
  if (maxSteps <= 0 || steps.length <= maxSteps) return steps;
  return cut(steps, maxSteps).map((band) => band.flat());
}

/** The steps an opening iris uncovers the cells in, first first. `instant` is one step. `facing` is the switch's, as {x, z}. */
function openingOrder(cells, style, maxSteps = 0, facing = null) {
  if (style === 'instant') return [cells.slice()];
  return atMost(style === 'spiral' ? spiral(cells, facing) : rings(cells, style), maxSteps);
}

function closingOrder(cells, style, maxSteps = 0, facing = null) {
  return openingOrder(cells, style, maxSteps, facing).slice().reverse();
}

/** The plugin's step pace and band limit, fixed in IrisSweepDriver since 1.11.0; worked out as maxSteps does. */
function pace(stepTicks = 2, maxTicks = 20) {
  const step = Math.min(20, Math.max(1, stepTicks));
  const max = Math.min(200, Math.max(0, maxTicks));
  return { stepTicks: step, maxSteps: max <= 0 ? 0 : Math.max(2, Math.floor(max / step)) };
}

/**
 * Records every block change a client is sent in the given cells (block_change and
 * multi_block_change packets, as they arrive), from now until `stop()`, which returns
 * [{ t, key, name }] in arrival order.
 */
function recordCells(bot, cells) {
  const keys = new Set(cells.map((c) => `${c.x},${c.y},${c.z}`));
  const events = [];
  const nameOf = (stateId) => {
    const b = bot.registry.blocksByStateId[stateId];
    return b ? b.name : `state ${stateId}`;
  };
  const one = (x, y, z, stateId) => {
    const key = `${x},${y},${z}`;
    if (keys.has(key)) events.push({ t: Date.now(), key, name: nameOf(stateId) });
  };
  const onBlock = (p) => one(p.location.x, p.location.y, p.location.z, p.type);
  const onMulti = (p) => {
    // chunkCoordinates (x, y, z of the section) and records as packed longs (1.20+: stateId << 12 | x << 8 | z << 4 | y).
    const sx = p.chunkCoordinates.x * 16;
    const sy = p.chunkCoordinates.y * 16;
    const sz = p.chunkCoordinates.z * 16;
    for (const r of p.records) {
      const v = typeof r === 'bigint' ? r : BigInt(Array.isArray(r) ? (BigInt(r[0]) << 32n) | BigInt(r[1] >>> 0) : r);
      const stateId = Number(v >> 12n);
      const x = Number((v >> 8n) & 15n);
      const z = Number((v >> 4n) & 15n);
      const y = Number(v & 15n);
      one(sx + x, sy + y, sz + z, stateId);
    }
  };
  bot._client.on('block_change', onBlock);
  bot._client.on('multi_block_change', onMulti);
  return {
    stop() {
      bot._client.off('block_change', onBlock);
      bot._client.off('multi_block_change', onMulti);
      return events;
    },
  };
}

/**
 * The steps a recording shows, for a crossing that ends with every cell `to` (closing: the iris
 * block; opening: air). Each cell counts at its last change to `to` (closing: a sweep first hides
 * every cell and draws the outer band, then each band in turn) or its first (opening: every cell
 * is drawn back to air once more when the sweep ends). Cells changed together (within `gapMs`)
 * are one step. Returns [{ t, keys: Set }] in order.
 */
function stepsOf(events, { to, first = false, gapMs = 30 }) {
  const at = new Map();
  const prev = new Map();
  for (const e of events) {
    const was = prev.get(e.key);
    prev.set(e.key, e.name);
    if (e.name !== to || was === to) continue;
    if (first && at.has(e.key)) continue;
    at.set(e.key, e.t);
  }
  const sorted = [...at.entries()].sort((a, b) => a[1] - b[1]);
  const steps = [];
  for (const [key, t] of sorted) {
    const last = steps[steps.length - 1];
    if (last && t - last.last <= gapMs) { last.keys.add(key); last.last = t; } else steps.push({ t, last: t, keys: new Set([key]) });
  }
  return steps;
}

/** Whether observed steps are the expected ones, cell for cell and in order. */
function sameSteps(observed, expected) {
  if (observed.length !== expected.length) return false;
  return expected.every((cells, i) => cells.length === observed[i].keys.size && cells.every((c) => observed[i].keys.has(`${c.x},${c.y},${c.z}`)));
}

module.exports = { STYLES, centreOf, rings, spiral, openingOrder, closingOrder, pace, recordCells, stepsOf, sameSteps };
