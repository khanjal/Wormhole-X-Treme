'use strict';
// Reading Wormhole's markers back from BlueMap, squaremap and Pl3xMap, which (unlike Dynmap,
// lib/dynmap.js) have no console command that lists markers: each one's own marker files are
// read instead, from the server folder, where each map writes them for its web page.
//
//   BlueMap    plugins/BlueMap/maps/<id>.conf names each map's dimension; its markers come from
//              its web server, GET /maps/<id>/live/markers.json (the page's own source, kept in
//              the plugin's memory), else the copy written to bluemap/web/maps/<id>/live/ every
//              write-markers-interval: { <set id>: { label, markers: { <id>: { type: poi|html|
//              shape|extrude|line, label, position {x,y,z}, detail, icon, shape [{x,z}], line
//              [{x,y,z}], lineColor {r,g,b,a} } } } }
//   squaremap  plugins/squaremap/web/tiles/<world>/markers.json, <world> the dimension key with
//              ':' as '_' (minecraft_overworld), written at once with flush-json-immediately:
//              [ { id, name, markers: [ { type: icon|polyline|polygon|rectangle|circle|ellipse|
//              multipolygon, point {x,z} | points [{x,z}] | center, tooltip, popup, icon, color } ] } ]
//              Its markers carry no id: a marker is known by its label (tooltip) and place.
//   Pl3xMap    plugins/Pl3xMap/web/tiles/<world>/markers.json, <world> the Bukkit world name:
//              [ { key, label } ], and each layer's markers in markers/<key>.json:
//              [ { type: icon|line|polyline|polygon|rectangle|circle|ellipse, data { key, point |
//              points | polylines | point1, point2 | center }, options { tooltip { content },
//              popup { content }, stroke { color (ARGB int) } } } ]
//
// Each reader gives the same snapshot: { layers: { <layer id>: label }, points: [...], areas:
// [...], lines: [...] }, every marker as { layer, layerLabel, id, label, world, x, y, z, icon,
// color, desc, bounds, points }, its world as the Bukkit world name (world, world_nether,
// world_the_end) and its colour as six hex digits. squaremap and Pl3xMap draw flat: their y is
// null. A layer is told apart by its label, which a person sees and every provider shares
// (lib/dynmap.js LABELS), not by an id each map spells its own way.
//
// What is known from a real run (2026-10-06, the pinned builds on 1.20.4, 1.21.11 and 26.1.2, no
// Wormhole provider yet): squaremap's and Pl3xMap's file layout and their own layers (spawn,
// world border); squaremap's world names; Pl3xMap's Bukkit world names; BlueMap's map configs
// (on 26.x its map ids no longer follow the world: world.conf is the nether). BlueMap's marker
// file is from its API's own serializer and web page, not seen: BlueMap draws nothing until its
// accept-download is switched on, which is the user's to do.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { httpText } = require('./http');

/** Dimension keys to the Bukkit world names the facility's server uses. */
const WORLDS = { 'minecraft:overworld': 'world', 'minecraft:the_nether': 'world_nether', 'minecraft:the_end': 'world_the_end' };
/** How long a change takes to reach each map's marker file, with slack: BlueMap's write interval is 5 s. */
const REDRAW_MS = { bluemap: 15000, squaremap: 12000, pl3xmap: 12000 };

/** A colour as six lower-case hex digits: from '#37b0d8', an ARGB int, or { r, g, b }. */
function hex(c) {
  if (c === null || c === undefined) return null;
  if (typeof c === 'number') return (c >>> 0).toString(16).padStart(8, '0').slice(2);
  if (typeof c === 'string') return c.replace(/^#/, '').toLowerCase().slice(-6);
  if (typeof c === 'object' && 'r' in c) return [c.r, c.g, c.b].map((v) => Number(v).toString(16).padStart(2, '0')).join('');
  return null;
}

/** An icon's name: the last part of a path or URL, without its extension. */
function iconName(icon) {
  if (!icon || typeof icon !== 'string') return null;
  return icon.split(/[\\/]/).pop().replace(/\?.*$/, '').replace(/\.[a-z0-9]+$/i, '');
}

/** The box around a list of {x, z} points, or null for none. */
function boundsOf(pts) {
  const p = (pts || []).filter((q) => q && Number.isFinite(q.x) && Number.isFinite(q.z));
  if (!p.length) return null;
  return {
    minX: Math.min(...p.map((q) => q.x)), maxX: Math.max(...p.map((q) => q.x)),
    minZ: Math.min(...p.map((q) => q.z)), maxZ: Math.max(...p.map((q) => q.z)),
  };
}

/** A point list from any of the shapes these files use: [{x,z}], {points: [...]}, [[{x,z}]]. */
function flatPoints(v) {
  if (!v) return [];
  if (Array.isArray(v)) return v.flatMap((x) => (Array.isArray(x) || (x && x.points) ? flatPoints(x) : [x]));
  if (Array.isArray(v.points)) return flatPoints(v.points);
  if (Array.isArray(v.polylines)) return flatPoints(v.polylines);
  if (Array.isArray(v.polygons)) return flatPoints(v.polygons);
  return [];
}

const empty = () => ({ layers: {}, points: [], areas: [], lines: [] });

/** Adds a marker to a snapshot under its kind. */
function add(snap, kind, m) {
  snap[kind].push(m);
}

// ---- squaremap ---------------------------------------------------------------------------------

/** A squaremap world folder name (minecraft_the_nether) as the Bukkit world name. */
function squaremapWorld(dir) {
  const key = dir.replace('_', ':');
  return WORLDS[key] || dir;
}

/** One world's squaremap markers.json (parsed) into `snap`. */
function parseSquaremap(json, world, snap = empty()) {
  for (const layer of Array.isArray(json) ? json : []) {
    const lid = String(layer.id);
    snap.layers[lid] = layer.name;
    for (const m of layer.markers || []) {
      const label = m.tooltip !== undefined ? String(m.tooltip).replace(/<[^>]*>/g, '') : null;
      const base = { layer: lid, layerLabel: layer.name, id: null, label, world, desc: m.popup || m.tooltip || null, color: hex(m.color) };
      if (m.type === 'icon') {
        add(snap, 'points', { ...base, x: m.point.x, y: null, z: m.point.z, icon: iconName(m.icon) });
      } else if (m.type === 'polyline') {
        const pts = flatPoints(m.points);
        add(snap, 'lines', { ...base, points: pts, bounds: boundsOf(pts) });
      } else if (m.type === 'rectangle') {
        const pts = flatPoints(m.points);
        add(snap, 'areas', { ...base, bounds: boundsOf(pts) });
      } else if (m.type === 'circle' || m.type === 'ellipse') {
        const r = Number(m.radius || (m.radiusX !== undefined ? Math.max(m.radiusX, m.radiusZ) : 0));
        add(snap, 'areas', { ...base, bounds: { minX: m.center.x - r, maxX: m.center.x + r, minZ: m.center.z - r, maxZ: m.center.z + r } });
      } else {
        // polygon, multipolygon
        add(snap, 'areas', { ...base, bounds: boundsOf(flatPoints(m.points)) });
      }
    }
  }
  return snap;
}

// ---- Pl3xMap -----------------------------------------------------------------------------------

/** One Pl3xMap marker ({ type, data, options }) into `snap`, in layer `layer`. */
function addPl3x(snap, layer, label, world, m) {
  const d = m.data || {};
  const o = m.options || {};
  const text = (x) => (x && x.content !== undefined ? String(x.content) : null);
  const tip = text(o.tooltip);
  const base = {
    layer, layerLabel: label, id: d.key || null, label: tip === null ? null : tip.replace(/<[^>]*>/g, ''), world,
    desc: text(o.popup) || tip, color: hex(o.stroke && o.stroke.color !== undefined ? o.stroke.color : (o.fill && o.fill.color)),
  };
  switch (m.type) {
    case 'icon':
      add(snap, 'points', { ...base, x: d.point.x, y: null, z: d.point.z, icon: iconName(d.image) });
      break;
    case 'line':
    case 'polyline':
    case 'multiline': {
      const pts = flatPoints(d.points || d.lines || d.polylines);
      add(snap, 'lines', { ...base, points: pts, bounds: boundsOf(pts) });
      break;
    }
    case 'rectangle':
      add(snap, 'areas', { ...base, bounds: boundsOf([d.point1, d.point2]) });
      break;
    case 'circle':
    case 'ellipse': {
      const r = Number(d.radius || (d.radius && d.radius.x) || 0);
      add(snap, 'areas', { ...base, bounds: { minX: d.center.x - r, maxX: d.center.x + r, minZ: d.center.z - r, maxZ: d.center.z + r } });
      break;
    }
    default:
      add(snap, 'areas', { ...base, bounds: boundsOf(flatPoints(d.polylines || d.polygons || d.points)) });
  }
}

/** One Pl3xMap world: its layer index (parsed) and a reader of each layer's marker list. */
function parsePl3xmap(index, layerMarkers, world, snap = empty()) {
  for (const layer of Array.isArray(index) ? index : []) {
    const key = String(layer.key);
    snap.layers[key] = layer.label;
    for (const m of layerMarkers(key) || []) addPl3x(snap, key, layer.label, world, m);
  }
  return snap;
}

// ---- BlueMap -----------------------------------------------------------------------------------

/** One BlueMap map's live markers.json (parsed) into `snap`, for the world that map draws. */
function parseBluemap(json, world, snap = empty()) {
  for (const [sid, set] of Object.entries(json || {})) {
    snap.layers[sid] = set.label;
    for (const [id, m] of Object.entries(set.markers || {})) {
      const pos = m.position || {};
      const base = { layer: sid, layerLabel: set.label, id, label: m.label === undefined ? null : String(m.label), world, desc: m.detail || m.label || null };
      if (m.type === 'poi' || m.type === 'html') {
        add(snap, 'points', { ...base, x: pos.x, y: pos.y, z: pos.z, icon: iconName(m.icon), color: null });
      } else if (m.type === 'line') {
        const pts = flatPoints(m.line);
        add(snap, 'lines', { ...base, points: pts, bounds: boundsOf(pts), color: hex(m.lineColor) });
      } else {
        // shape, extrude
        add(snap, 'areas', { ...base, bounds: boundsOf(flatPoints(m.shape)), color: hex(m.lineColor) });
      }
    }
  }
  return snap;
}

/** BlueMap's maps in a server folder: [{ id, world }], from plugins/BlueMap/maps/<id>.conf. */
function bluemapMaps(folder) {
  const dir = path.join(folder, 'plugins', 'BlueMap', 'maps');
  let names = [];
  try { names = fs.readdirSync(dir).filter((f) => f.endsWith('.conf')); } catch { return []; }
  return names.map((f) => {
    const body = fs.readFileSync(path.join(dir, f), 'utf8');
    const dim = /^dimension\s*[:=]\s*"?([^"\s]+)"?/m.exec(body);
    return { id: f.slice(0, -5), world: dim ? WORLDS[dim[1]] || dim[1] : null };
  });
}

// ---- readers -----------------------------------------------------------------------------------

/**
 * A marker file, parsed (gzipped or not), or null when there is none, it is older than `since`
 * (a file the map wrote before the server's last start, which a restart's checks must not take
 * for what the map drew since), or it is being written just now.
 */
function readJson(file, since = 0) {
  for (const f of [file, `${file}.gz`]) {
    if (!fs.existsSync(f)) continue;
    try {
      if (fs.statSync(f).mtimeMs < since) return null;
      const raw = fs.readFileSync(f);
      return JSON.parse((f.endsWith('.gz') ? zlib.gunzipSync(raw) : raw).toString('utf8'));
    } catch {
      return null;
    }
  }
  return null;
}

/**
 * A reader for one map in one server folder. `snapshot()` reads every world's markers; `until`
 * polls like lib/dynmap.js MapReader's. `port` is the map's web port (BlueMap's markers are read
 * from it first). `since` (a time, or a function giving one: the server's last start) leaves out
 * a marker file written before it.
 */
class JsonMapReader {
  constructor(name, folder, { port = null, since = 0 } = {}) {
    if (!REDRAW_MS[name]) throw new Error(`no marker reader for ${name}`);
    Object.assign(this, { name, folder, port, since, redrawMs: REDRAW_MS[name] });
  }

  async snapshot() {
    const snap = empty();
    const since = (typeof this.since === 'function' ? this.since() : this.since) || 0;
    const web = path.join(this.folder, 'plugins', this.name === 'pl3xmap' ? 'Pl3xMap' : 'squaremap', 'web', 'tiles');
    if (this.name === 'squaremap' || this.name === 'pl3xmap') {
      let worlds = [];
      try { worlds = fs.readdirSync(web, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name); } catch { return snap; }
      for (const w of worlds) {
        const index = readJson(path.join(web, w, 'markers.json'), since);
        if (!index) continue;
        if (this.name === 'squaremap') parseSquaremap(index, squaremapWorld(w), snap);
        else parsePl3xmap(index, (key) => readJson(path.join(web, w, 'markers', `${key}.json`), since), w, snap);
      }
      return snap;
    }
    for (const map of bluemapMaps(this.folder)) {
      let json = null;
      if (this.port) {
        try { json = JSON.parse(await httpText(`http://127.0.0.1:${this.port}/maps/${encodeURIComponent(map.id)}/live/markers.json`, 5000)); } catch { json = null; }
      }
      if (!json) json = readJson(path.join(this.folder, 'bluemap', 'web', 'maps', map.id, 'live', 'markers.json'), since);
      if (json) parseBluemap(json, map.world, snap);
    }
    return snap;
  }

  /** Polls every half second until `test(snapshot)`, for at most `ms`: { ok, snap, ms }. */
  async until(test, ms = this.redrawMs) {
    const t0 = Date.now();
    let snap;
    for (;;) {
      snap = await this.snapshot();
      if (test(snap)) return { ok: true, snap, ms: Date.now() - t0 };
      if (Date.now() - t0 > ms) return { ok: false, snap, ms: Date.now() - t0 };
      await new Promise((resolve) => { setTimeout(resolve, 500); });
    }
  }
}

/**
 * The markers in a snapshot's Wormhole layer `label` (lib/dynmap.js LABELS: 'Stargates', ...),
 * of `kind` (points, areas, lines), whose label is `name` (any when null).
 */
function inLayer(snap, label, kind, name = null) {
  return ((snap && snap[kind]) || []).filter((m) => m.layerLabel === label && (name === null || m.label === name));
}

/** True if a marker is at `at` ({x, y, z}): within `within` on x and z, and on y where the map has one. */
function at(m, p, within = 0.75) {
  if (!m || !Number.isFinite(m.x)) return false;
  return Math.abs(m.x - p.x) <= within && Math.abs(m.z - p.z) <= within && (m.y === null || m.y === undefined || Math.abs(m.y - p.y) <= within);
}

/** True if an area's box covers {x, z}, with `slack`. */
function covers(m, p, slack = 0.5) {
  const b = m && m.bounds;
  return Boolean(b) && p.x >= b.minX - slack && p.x <= b.maxX + slack && p.z >= b.minZ - slack && p.z <= b.maxZ + slack;
}

module.exports = {
  JsonMapReader, parseSquaremap, parsePl3xmap, parseBluemap, bluemapMaps, inLayer, at, covers, hex, iconName, WORLDS, REDRAW_MS,
};
