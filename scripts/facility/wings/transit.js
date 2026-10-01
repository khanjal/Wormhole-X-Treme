'use strict';
// The transit routes' fixed parts, built with each wing (lib/campus.js ROUTES): a runway and a
// dial console by each transit gate, a rim in the floor at each end of the transit ring pair,
// and a beam pad with its button. The gates, the ring pair and the beam destinations are the
// plugin's own, made at fixture time (lib/transit.js); these are what a tester sees and presses.

const campus = require('../lib/campus');
const bp = require('../lib/blueprint');
const text = require('../lib/text');
const { SLABS_ODD } = require('../lib/rings');
const { GateKit } = require('../lib/gatekit');

const R = campus.ROUTES;
const P = campus.PALETTE;
const BUTTON = 'minecraft:stone_button[face=floor,facing=south]';

function board(out, version, wingId, id, at, lines) {
  out.cmd(bp.summonBoard(version, { id: `transit_${id}`, wing: wingId, at, spec: lines }));
}

/** A command block with a button on top: pressing it runs `command`. */
function button(out, x, z, command, what) {
  out.set(x, 0, z, `minecraft:command_block{Command:${text.quoteSingle(command)},TrackOutput:0b}`);
  out.set(x, 1, z, BUTTON);
  out.anchor(x, 1, z, 'minecraft:stone_button', what);
}

/** The prompt a beam button sends the nearest player: one click runs `beam to` as them. */
function beamPrompt(version, to) {
  return `tellraw @p[distance=..3] ${text.command(version, [
    { text: ':: ', color: 'dark_aqua' },
    { text: `[Beam to ${to}]`, color: 'aqua', underlined: true, click: { run: `/wormhole beam to ${to}` } },
  ])}`;
}

/** What each transit fixture occupies, per wing, for the layout check: { what, box }. */
function footprints() {
  const out = [];
  const kit = new GateKit(null);
  for (const [name, g] of Object.entries(R.gates)) {
    const geom = kit.place(g.shape, g.facing, g);
    const b = geom.bounds;
    const k = geom.button;
    out.push({ what: `transit gate ${name}`, wing: g.wing, box: bp.box3(Math.min(b.x0, k.x), g.floorY - 2, Math.min(b.z0, k.z), Math.max(b.x1, k.x), b.y1, Math.max(b.z1, k.z)) });
    const r = g.runway;
    out.push({ what: `runway of ${name}`, wing: g.wing, box: bp.box3(r.x0, -1, r.z0, r.x1, -1, r.z1) });
    const c = g.console;
    out.push({ what: `dial console of ${name}`, wing: g.wing, box: bp.box3(c.x, 0, c.z, c.x + c.dial.length - 1, 2, c.z) });
  }
  for (const e of R.rings.ends) {
    out.push({ what: `transit ring ${e.name}`, wing: e.wing, box: bp.box3(e.x - 3, -1, e.z - 3, e.x + 3, 3, e.z + 3) });
  }
  for (const p of R.beams) {
    out.push({ what: `beam pad ${p.name}`, wing: p.wing, box: bp.box3(p.x - 2, -1, p.z - 2, p.x + 2, 3, p.z + 2) });
    out.push({ what: `beam button ${p.name}`, wing: p.wing, box: bp.box3(p.button.x - 1, 0, p.button.z, p.button.x + 1, 2, p.button.z) });
  }
  for (const m of R.mirrors) {
    out.push({ what: `mirror ${m.name}`, wing: m.wing, box: bp.box3(m.x, m.y, m.z, m.x, m.y, m.z) });
  }
  return out;
}

function structures(out, wingId, version) {
  for (const [name, g] of Object.entries(R.gates)) {
    if (g.wing !== wingId) continue;
    const r = g.runway;
    out.fill(bp.box3(r.x0, -1, r.z0, r.x1, -1, r.z1), 'minecraft:polished_deepslate');
    out.set(Math.floor((r.x0 + r.x1) / 2), -1, r.z1, P.guide);
    const c = g.console;
    c.dial.forEach((to, i) => {
      button(out, c.x + i, c.z, `wormhole gate dial ${name} ${to}`, `dial ${to} from ${name}`);
      board(out, version, wingId, `dial_${name}_${to}`, { x: c.x + i + 0.5, y: 1.9, z: c.z + 0.5 },
        [{ text: to, color: 'aqua', bold: true }]);
    });
    const kit = new GateKit(null);
    const k = kit.place(g.shape, g.facing, g).button;
    board(out, version, wingId, `gate_${name}`, { x: k.x + 2.5, y: 2.2, z: k.z + 0.5 },
      [{ text: `GATE ${name.toUpperCase()}`, color: 'aqua', bold: true }, '\n',
        { text: `press the DHD, then /dial ${c.dial[0]}`, color: 'gray' }, '\n',
        { text: 'or press a button on the console', color: 'gray' }]);
  }

  const rings = R.rings;
  for (const e of rings.ends) {
    if (e.wing !== wingId) continue;
    const other = rings.ends.find((x) => x !== e);
    for (const [dx, dz] of SLABS_ODD) out.set(e.x + dx, -1, e.z + dz, 'minecraft:polished_deepslate');
    out.set(e.x, -1, e.z, P.guide);
    out.anchor(e.x, -1, e.z, P.guide, `centre of the ${e.name} ring pad`);
    board(out, version, wingId, `ring_${e.name}`, { x: e.x + 0.5, y: 2, z: e.z + 4.5 },
      [{ text: `RING PAD ${e.name.toUpperCase()}`, color: 'green', bold: true }, '\n',
        { text: `step in, stand still · to ${other.name}`, color: 'gray' }, '\n',
        { text: 'the rings recharge for 30 s after a trip', color: 'dark_gray' }]);
  }

  for (const p of R.beams) {
    if (p.wing !== wingId) continue;
    out.fill(bp.box3(p.x - 2, -1, p.z - 2, p.x + 2, -1, p.z + 2), 'minecraft:waxed_cut_copper');
    out.fill(bp.box3(p.x - 1, -1, p.z - 1, p.x + 1, -1, p.z + 1), P.guide);
    for (const [dx, dz] of [[-2, -2], [2, -2], [-2, 2], [2, 2]]) {
      out.fill(bp.box3(p.x + dx, 0, p.z + dz, p.x + dx, 2, p.z + dz), P.console);
      out.set(p.x + dx, 3, p.z + dz, P.guide);
    }
    out.anchor(p.x, -1, p.z, P.guide, `centre of the ${p.name} beam pad`);
    const b = p.button;
    out.set(b.x - 1, 0, b.z, P.console);
    out.set(b.x + 1, 0, b.z, P.console);
    button(out, b.x, b.z, beamPrompt(version, p.to), `beam button to ${p.to}`);
    board(out, version, wingId, `beam_${p.name}`, { x: b.x + 0.5, y: 2.2, z: b.z + 0.5 },
      [{ text: `BEAM PAD ${p.name.toUpperCase()}`, color: 'aqua', bold: true }, '\n',
        { text: `press, then click in chat · or /wormhole beam to ${p.to}`, color: 'gray' }]);
  }
}

module.exports = { structures, footprints, beamPrompt };
