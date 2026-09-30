'use strict';
// Reading Dynmap's markers back, for the Map Desk (#236). Dynmap's own console commands answer
// on the main thread, inside run()'s fence:
//
//   dmarker listsets                 "wormhole.gates: label:"Stargates", hide:false, prio:10, ..."
//   dmarker list set:<set>           "<id>: label:"<label>", set:<set>, world:<w>, x:<x>, y:<y>, z:<z>, icon:<icon>, markup:false"
//   dmarker listareas set:<set>      "<id>: label:"<label>", set:<set>, world:<w>, weight:3, color:37b0d8, ..."
//   dmarker listlines set:<set>      "<id>: label:"<label>", set:<set>, world:<w>, corners:{ {x,y,z} {x,y,z} }, weight: 3, color:37b0d8, ..."
//   dmarker getdesc id:<id> set:<set> [type:area|line]   the popup's HTML
//
// A set Dynmap does not have is "Error: invalid set - <set>". Wormhole's sets (DynmapMapProvider)
// are wormhole.gates, wormhole.rings, wormhole.beams and wormhole.mirrors; a gate's point and area
// are its name in lower case, a dialled pair's line "<a>|<b>" (in order), a ring end
// "<pair>:a" / ":b" and its line the pair's id, a beam or mirror its name in lower case. They are
// made non-persistent, so a restart rebuilds them rather than reloading them.

const { ticks } = require('./probe');

const SETS = { gates: 'wormhole.gates', rings: 'wormhole.rings', beams: 'wormhole.beams', mirrors: 'wormhole.mirrors' };
const LABELS = { 'wormhole.gates': 'Stargates', 'wormhole.rings': 'Transport rings', 'wormhole.beams': 'Beam destinations', 'wormhole.mirrors': 'Quantum mirrors' };
/** How long Wormhole takes to redraw (a 100-tick poll), with slack for a busy server. */
const REDRAW_MS = 8000;

const POINT = /^(\S+): label:"(.*)", set:(\S+), world:(\S+), x:(-?[\d.]+), y:(-?[\d.]+), z:(-?[\d.]+), icon:([^,\s]+)/;
const AREA = /^(\S+): label:"(.*)", set:(\S+), world:(\S+), weight:\s*\d+, color:([0-9a-f]+)/;
const LINE = /^(\S+): label:"(.*)", set:(\S+), world:(\S+), corners:\{ (.*) \}, weight:\s*\d+, color:([0-9a-f]+)/;
const SET = /^(\S+): label:"(.*)", hide:/;

class MapReader {
  constructor(srv) {
    this.srv = srv;
  }

  async lines(command) {
    return (await this.srv.run(command)).lines;
  }

  /** The marker sets: { id: label }. */
  async sets() {
    const out = {};
    for (const l of await this.lines('dmarker listsets')) {
      const m = SET.exec(l);
      if (m) out[m[1]] = m[2];
    }
    return out;
  }

  /** A set's point markers, { id: { label, world, x, y, z, icon } }, or null if Dynmap has no such set. */
  async points(set) {
    return this.parse(`dmarker list set:${set}`, POINT, (m) => ({ label: m[2], world: m[4], x: Number(m[5]), y: Number(m[6]), z: Number(m[7]), icon: m[8] }));
  }

  /** A set's area markers, { id: { label, world, color } }, or null. */
  async areas(set) {
    return this.parse(`dmarker listareas set:${set}`, AREA, (m) => ({ label: m[2], world: m[4], color: m[5] }));
  }

  /** A set's lines, { id: { label, world, corners, color } }, or null. */
  async polylines(set) {
    return this.parse(`dmarker listlines set:${set}`, LINE, (m) => ({ label: m[2], world: m[4], corners: m[5], color: m[6] }));
  }

  async parse(command, re, make) {
    const lines = await this.lines(command);
    if (lines.some((l) => /invalid set/.test(l))) return null;
    const out = {};
    for (const l of lines) {
      const m = re.exec(l);
      if (m) out[m[1]] = make(m);
    }
    return out;
  }

  /** A marker's popup HTML, as Dynmap holds it (type: icon, area or line). */
  async desc(set, id, type = null) {
    const lines = await this.lines(`dmarker getdesc id:${id} set:${set}${type ? ` type:${type}` : ''}`);
    return lines.join('\n');
  }

  /** Everything Wormhole has drawn: { sets, gates, gateAreas, gateLines, rings, ringLines, beams, mirrors, mirrorLines }. */
  async snapshot() {
    return {
      sets: await this.sets(),
      gates: await this.points(SETS.gates),
      gateAreas: await this.areas(SETS.gates),
      gateLines: await this.polylines(SETS.gates),
      rings: await this.points(SETS.rings),
      ringLines: await this.polylines(SETS.rings),
      beams: await this.points(SETS.beams),
      mirrors: await this.points(SETS.mirrors),
      mirrorLines: await this.polylines(SETS.mirrors),
    };
  }

  /**
   * Polls the markers every half second until `test(snapshot)` is true, for at most `ms`;
   * returns { ok, snap, ms } with the last snapshot read either way.
   */
  async until(test, ms = REDRAW_MS) {
    const t0 = Date.now();
    let snap;
    for (;;) {
      snap = await this.snapshot();
      if (test(snap)) return { ok: true, snap, ms: Date.now() - t0 };
      if (Date.now() - t0 > ms) return { ok: false, snap, ms: Date.now() - t0 };
      await ticks(10);
    }
  }
}

module.exports = { MapReader, SETS, LABELS, REDRAW_MS };
