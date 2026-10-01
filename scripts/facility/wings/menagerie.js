'use strict';
// Menagerie and Motor Pool: the supply of travellers, as structure only this stage. Pens and a
// kennel, a lava trough, a boathouse pool, a rail loop, an armoury alcove, an arrow range, and
// the three lanes (canal, rails, run-up) from here through the corridor to the G1 stand.
// Fences and trough rails are nether brick: nothing flammable sits beside the lava.

const campus = require('../lib/campus');
const bp = require('../lib/blueprint');

const P = campus.PALETTE;
const FENCE = 'minecraft:nether_brick_fence';

function fenceRing(out, b, gapAt) {
  out.fill(bp.box3(b.x0, 0, b.z0, b.x1, 0, b.z0), FENCE);
  out.fill(bp.box3(b.x0, 0, b.z1, b.x1, 0, b.z1), FENCE);
  out.fill(bp.box3(b.x0, 0, b.z0, b.x0, 0, b.z1), FENCE);
  out.fill(bp.box3(b.x1, 0, b.z0, b.x1, 0, b.z1), FENCE);
  if (gapAt) out.set(gapAt.x, 0, gapAt.z, 'minecraft:air');
}

function label(out, version, id, x, z, words, color = 'gold') {
  out.cmd(bp.summonBoard(version, {
    id: `label_${id}`, wing: 'menagerie', at: { x: x + 0.5, y: 2.3, z: z + 0.5 },
    spec: [{ text: words, color, bold: true }],
  }));
}

function structures(out, w, version) {
  const M = campus.MENAGERIE;
  for (const pen of M.pens) {
    const b = pen.box;
    const midX = Math.floor((b.x0 + b.x1) / 2);
    fenceRing(out, b, { x: midX, z: b.z1 });
    label(out, version, pen.id, midX, Math.floor((b.z0 + b.z1) / 2), pen.id === 'kennel' ? 'Kennel' : `Stable · ${pen.id}`);
  }

  const t = M.lavaTrough;
  out.fill(bp.box3(t.x0, -1, t.z, t.x1, -1, t.z), 'minecraft:lava');
  out.fill(bp.box3(t.x0, 0, t.z - 1, t.x1, 0, t.z - 1), FENCE);
  out.fill(bp.box3(t.x0, 0, t.z + 1, t.x1, 0, t.z + 1), FENCE);
  label(out, version, 'trough', Math.floor((t.x0 + t.x1) / 2), t.z + 1, 'Strider trough', 'red');

  const bh = M.boathouse;
  out.fill(bp.box3(bh.x0, -1, bh.z0, bh.x1, -1, bh.z1), 'minecraft:water');
  label(out, version, 'boathouse', Math.floor((bh.x0 + bh.x1) / 2), bh.z0 - 1, 'Boathouse');

  const r = M.railLoop;
  out.fill(bp.box3(r.x0 + 1, 0, r.z0, r.x1 - 1, 0, r.z0), 'minecraft:rail[shape=east_west]');
  out.fill(bp.box3(r.x0 + 1, 0, r.z1, r.x1 - 1, 0, r.z1), 'minecraft:rail[shape=east_west]');
  out.fill(bp.box3(r.x0, 0, r.z0 + 1, r.x0, 0, r.z1 - 1), 'minecraft:rail[shape=north_south]');
  out.fill(bp.box3(r.x1, 0, r.z0 + 1, r.x1, 0, r.z1 - 1), 'minecraft:rail[shape=north_south]');
  out.set(r.x0, 0, r.z0, 'minecraft:rail[shape=south_east]');
  out.set(r.x1, 0, r.z0, 'minecraft:rail[shape=south_west]');
  out.set(r.x0, 0, r.z1, 'minecraft:rail[shape=north_east]');
  out.set(r.x1, 0, r.z1, 'minecraft:rail[shape=north_west]');
  label(out, version, 'railyard', Math.floor((r.x0 + r.x1) / 2), Math.floor((r.z0 + r.z1) / 2), 'Rail yard');

  const a = M.armoury;
  bp.room(out, { x0: a.x0 + 1, x1: a.x1 - 1, z0: a.z0 + 1, z1: a.z1 - 1, y0: 0, h: a.h }, { roof: P.trim });
  const mz = Math.floor((a.z0 + a.z1) / 2);
  out.fill(bp.box3(a.x0, 0, mz, a.x0, 2, mz + 1), 'minecraft:air');
  label(out, version, 'armoury', a.x0 - 1, mz, 'Armoury');

  const g = M.arrowRange;
  out.fill(bp.box3(g.x0, -1, g.z0, g.x1, -1, g.z0), `minecraft:${w.colour}_concrete`);
  out.fill(bp.box3(g.x0, -1, g.z1, g.x1, -1, g.z1), `minecraft:${w.colour}_concrete`);
  out.fill(bp.box3(g.target.x, 0, g.target.z - 1, g.target.x, 2, g.target.z + 1), P.trim);
  out.set(g.target.x - 1, g.target.y, g.target.z, 'minecraft:target');
  label(out, version, 'range', g.x0, Math.floor((g.z0 + g.z1) / 2), 'Arrow range');

  // The lanes run from here, through the corridor and the gate hall's east wall, to G1.
  for (const lane of campus.LANES) {
    if (lane.kind === 'water') out.fill(bp.box3(lane.x0, -1, lane.z, lane.x1, -1, lane.z), 'minecraft:water');
    else if (lane.kind === 'rail') out.fill(bp.box3(lane.x0, 0, lane.z, lane.x1, 0, lane.z), 'minecraft:rail[shape=east_west]');
    else out.fill(bp.box3(lane.x0, -1, lane.z0, lane.x1, -1, lane.z1), `minecraft:${w.colour}_concrete`);
  }
  out.anchor(campus.LANES[1].x0, 0, campus.LANES[1].z, 'minecraft:rail', 'rail lane at the G1 end');
}

module.exports = { structures };
