'use strict';
// The decoration guardrail (creative pass 5.3). Every block a `decorate` function writes is
// checked against what the tests need left alone; an overlap is a sentence in validateLayout's
// problems, and the pack is not written. What is protected:
//   - every volume a build says must be air (cells, the shaft, the tunnel, the far sites' strips)
//     and every chamber's footprint (walls, gallery, door, board, pylon; a desk's console);
//   - every cell's floor, which its reset lays back as plain quartz;
//   - the gates the tests build or keep: the far gates and the transit gates (bounds, button and
//     the pit GateKit digs), with the far gates' aprons;
//   - every ring pad a chamber lays outside a cell (R5, the range tunnel, the transit pair);
//   - every beam pad and spot outside a cell (the transit pads, B1's far pads, B2's spots);
//   - the lanes from the Motor Pool to G1, the tp plates, the transit fixtures;
//   - the walk-in lines the chambers and routes use, and the stage 4 mirror spots;
//   - every anchor a structure (not a decoration) put down, so the self-test still reads it;
//   - anything a blueprint registered with keepClear.

const campus = require('../../lib/campus');
const bp = require('../../lib/blueprint');
const { GateKit } = require('../../lib/gatekit');

const O = campus.OVERWORLD;

// `entities`: an entity a decoration summons (a plaque, a console's label) is in the way here too,
// not only a block: a ring swaps everything inside it, a gate sweeps its opening, a pad beams, and
// a cell's tests select the entities in it.
function ring(what, dim, x, y, z, r = 3) {
  return { what, dim, box: bp.box3(x - r, y - 1, z - r, x + r, y + 4, z + r), entities: true };
}

function pad(what, dim, x, y, z, r = 2) {
  return { what, dim, box: bp.box3(x - r, y - 1, z - r, x + r, y + 3, z + r), entities: true };
}

function gate(what, dim, geom, floorY) {
  const b = geom.bounds;
  const k = geom.button;
  return { what, dim, box: bp.box3(Math.min(b.x0, k.x), Math.min(b.y0, floorY - 2), Math.min(b.z0, k.z), Math.max(b.x1, k.x), b.y1, Math.max(b.z1, k.z)), entities: true };
}

/** Everything decoration may not touch, as [{ what, dim, box }]. */
function forbidden(builds) {
  const out = [];
  const dimOf = (ch) => campus.wing(ch.wing).dim;
  for (const ch of campus.CHAMBERS) {
    const dim = dimOf(ch);
    if (ch.kind === 'tunnel') {
      out.push({ what: `the ${ch.id}'s clear volume`, dim, box: bp.interior(ch.box) });
      continue;
    }
    for (const b of bp.layoutOf(ch).footprint) out.push({ what: `${ch.id}'s footprint`, dim, box: b });
    if (ch.kind === 'cell') {
      for (const b of bp.cellClear(ch)) out.push({ what: `${ch.id}'s clear volume`, dim, box: b, entities: true });
      const b = ch.box;
      out.push({ what: `${ch.id}'s floor`, dim, box: bp.box3(b.x0, b.y0 - 4, b.z0, b.x1, b.y0 - 1, b.z1) });
    }
  }
  for (const f of builds) {
    for (const c of f.bp.clear) out.push({ what: `${c.id}'s clear volume`, dim: f.dim, box: c.box });
    for (const k of f.bp.keep) out.push({ what: k.id, dim: f.dim, box: k.box });
    for (const a of f.bp.anchors.filter((x) => !x.decor)) out.push({ what: `the anchor "${a.what}"`, dim: f.dim, box: bp.box3(...a.at, ...a.at) });
  }

  const kit = new GateKit(null);
  for (const [name, g] of Object.entries(campus.GATES.far)) {
    out.push(gate(`the ${name} gate`, g.dim, kit.place(g.shape, g.facing, g), g.floorY));
  }
  out.push({ what: 'the Range gate\'s apron', dim: campus.NETHER, box: bp.box3(-6, 64, -21, 6, 69, -4) });
  out.push({ what: 'the Annex gate\'s apron', dim: campus.END, box: bp.box3(994, 60, 999, 1006, 65, 1012) });
  for (const [name, g] of Object.entries(campus.ROUTES.gates)) out.push(gate(`the transit gate ${name}`, g.dim, kit.place(g.shape, g.facing, g), g.floorY));
  for (const t of require('../transit').footprints()) out.push({ what: t.what, dim: campus.wing(t.wing).dim, box: t.box });

  // Ring pads outside the cells.
  for (const e of campus.ROUTES.rings.ends) out.push(ring(`the transit ring ${e.name}`, O, e.x, e.y, e.z));
  for (const e of require('../../chambers/r5-edit').ENDS) out.push(ring('an R5 ring', O, e.x, e.y, e.z));
  const tunnel = require('../../chambers/r6-range');
  out.push(ring('the tunnel\'s first ring', O, tunnel.A.x, 0, tunnel.A.z));
  for (const d of tunnel.options.distance) out.push(ring(`the tunnel ring ${d.value} out`, O, tunnel.A.x + Number(d.value), 0, tunnel.A.z));

  // Beam pads and spots outside the cells.
  for (const p of campus.ROUTES.beams) out.push(pad(`the beam pad ${p.name}`, O, p.x, p.y, p.z));
  const { PADS } = require('../../chambers/b1-pads');
  for (const [name, p] of Object.entries(PADS)) if (p.dim !== O) out.push(pad(`B1's Pad-${name}`, p.dim, p.x, p.y, p.z));
  const { SPOTS } = require('../../chambers/b2-dispatch');
  out.push(pad('B2\'s Dock', O, SPOTS.DOCK.x, 0, SPOTS.DOCK.z, 1));
  out.push(pad('B2\'s Den', O, SPOTS.DEN.x, 0, SPOTS.DEN.z, 1));
  out.push(pad('B2\'s nether spot', campus.NETHER, SPOTS.NETHER_SPOT.x, SPOTS.NETHER_SPOT.y, SPOTS.NETHER_SPOT.z, 1));
  out.push({ what: 'B2\'s standing spots', dim: O, box: bp.box3(Math.floor(SPOTS.STAND.x) - 1, 0, Math.floor(SPOTS.STAND.z) - 1, Math.floor(SPOTS.STAND.x) + 1, 2, Math.floor(SPOTS.STAND.z) + 3) });

  // The lanes, the plates.
  out.push({ what: 'the Motor Pool lanes', dim: O, box: bp.box3(0, -1, -100, 118, 3, -92) });
  const c = campus.TRANSIT.centre;
  for (const p of campus.TRANSIT.plates) out.push({ what: `the ${p.to} plate`, dim: O, box: bp.box3(c.x + p.dx, -1, c.z + p.dz, c.x + p.dx, 1, c.z + p.dz) });
  const wings = require('..');
  for (const w of campus.WINGS) {
    if (w.id === 'ops') continue;
    const h = w.id === 'systems' ? { x: 3, y: 6, z: -17 } : wings.homePlate(w);
    out.push({ what: `the plate home from ${w.id}`, dim: w.dim, box: bp.box3(h.x, h.y - 1, h.z, h.x, h.y + 1, h.z) });
  }

  // Walk-in lines (y 0 and up: a floor stripe under one is fine) and the stage 4 mirror spots.
  const walk = (what, x0, z0, x1, z1) => out.push({ what, dim: O, box: bp.box3(x0, 0, z0, x1, 3, z1) });
  walk('R3\'s walk-in line', 68, 4, 70, 6);
  walk('the tunnel\'s walk-in line', 109, -1, 112, 1);
  for (const e of require('../../chambers/r5-edit').ENDS) walk('an R5 walk-in line', e.x - 6, e.z, e.x, e.z);
  for (const e of campus.ROUTES.rings.ends) walk(`the walk into the ${e.name} ring`, e.x, e.z, e.x + 1, e.z + 6);
  for (const g of Object.values(campus.ROUTES.gates)) walk('a transit gate\'s walk lane', g.runway.x0, g.runway.z0 - 5, g.runway.x1, g.runway.z1);
  for (const p of campus.ROUTES.beams) walk(`the ${p.name} beam button's standing spot`, p.button.x - 1, p.button.z, p.button.x + 1, p.button.z + 2);
  // A mirror's wall patch (a room's wall: decoration may not replace it; a pier is decoration and
  // is the wall) and the air in front of it, where a traveller lands.
  for (const m of campus.ROUTES.mirrors) {
    const s = m.facing === 'north' ? -1 : 1;
    const f = m.floorY;
    if (m.wall === 'room') out.push({ what: `the ${m.name} mirror's wall`, dim: m.dim, box: bp.box3(m.x - 1, f, m.z - s, m.x + 1, m.y + 1, m.z - s) });
    out.push({ what: `the air in front of the ${m.name} mirror`, dim: m.dim, box: bp.box3(m.x - 1, f, m.z, m.x + 1, m.y + 1, m.z + 3 * s) });
  }
  // M1's one-per-world refusal is tried on the Annex pier's other face: the air in front of it.
  const spare = require('../../chambers/m1-round').END_SPARE;
  const ss = spare.facing === 'north' ? -1 : 1;
  out.push({ what: 'the air in front of M1\'s spare banner in the End', dim: spare.dim, box: bp.box3(spare.x - 1, spare.floorY, spare.z, spare.x + 1, spare.y + 1, spare.z + 3 * ss) });
  return out;
}

/**
 * The guardrail: every decoration box against every forbidden box in its dimension; a summoned
 * decoration only against the boxes an entity can be in the way in.
 */
function check(builds) {
  const problems = [];
  const bad = forbidden(builds);
  for (const f of builds) {
    for (const d of f.bp.decorBoxes()) {
      for (const k of bad) {
        if (d.entity && !k.entities) continue;
        if (k.dim === f.dim && bp.overlaps(d.box, k.box)) {
          problems.push(`${f.fn}: decoration ${d.block.replace(/\{.*$/, '')} at ${fmt(d.box)} reaches into ${k.what} (${fmt(k.box)})`);
        }
      }
    }
  }
  return problems;
}

function fmt(b) {
  return `${b.x0} ${b.y0} ${b.z0}..${b.x1} ${b.y1} ${b.z1}`;
}

module.exports = { forbidden, check };
