'use strict';
// The Menagerie and Motor Pool as the Yard (creative pass 3.6): lanterns on the pen corners, hay
// in each stable, posts in the kennel, a copper edge round the boathouse pool, a depot roof over
// the rail loop, and hazard rows along the arrow range.

const campus = require('../../lib/campus');
const bp = require('../../lib/blueprint');

const P = campus.PALETTE;

function decorate(out, w, version) {
  const M = campus.MENAGERIE;
  for (const pen of M.pens) {
    const b = pen.box;
    for (const [x, z] of [[b.x0, b.z0], [b.x1, b.z0], [b.x0, b.z1], [b.x1, b.z1]]) out.set(x, 1, z, 'minecraft:lantern');
    if (pen.id === 'kennel') {
      for (const x of [b.x0 + 1, b.x1 - 1]) out.fill(bp.box3(x, 0, b.z0 + 1, x, 1, b.z0 + 1), 'minecraft:oak_log');
    } else {
      out.set(b.x0 + 1, 0, b.z0 + 1, 'minecraft:hay_block');
    }
  }

  // The boathouse pool's edge, one out, short of the canal where it leaves on the west.
  const bh = M.boathouse;
  out.fill(bp.box3(bh.x0 - 1, -1, bh.z0 - 1, bh.x1 + 1, -1, bh.z0 - 1), 'minecraft:waxed_cut_copper');
  out.fill(bp.box3(bh.x0, -1, bh.z1 + 1, bh.x1 + 1, -1, bh.z1 + 1), 'minecraft:waxed_cut_copper');
  out.fill(bp.box3(bh.x1 + 1, -1, bh.z0, bh.x1 + 1, -1, bh.z1), 'minecraft:waxed_cut_copper');
  out.fill(bp.box3(bh.x0 - 1, -1, bh.z0, bh.x0 - 1, -1, -101), 'minecraft:waxed_cut_copper');
  out.cmd(bp.plaque(version, { id: 'slipway', wing: 'menagerie', at: { x: bh.x1 + 0.5, y: 2.3, z: bh.z0 - 0.5 }, text: 'SLIPWAY', colour: 'gold' }));

  // The rail yard's depot roof on copper posts, lit underneath.
  const r = M.railLoop;
  out.fill(bp.box3(r.x0, 4, r.z0, r.x1, 4, r.z1), P.trim);
  for (const [x, z] of [[r.x0 - 1, r.z0 - 1], [r.x1 + 1, r.z0 - 1], [r.x0 - 1, r.z1 + 1], [r.x1 + 1, r.z1 + 1]]) {
    out.fill(bp.box3(x, 0, z, x, 3, z), P.console);
  }
  for (let x = r.x0 + 2; x < r.x1; x += 4) {
    for (let z = r.z0 + 2; z < r.z1; z += 4) out.set(x, 3, z, 'minecraft:light[level=15]');
  }

  // The arrow range's hazard rows, one out from its coloured edges.
  const g = M.arrowRange;
  for (const z of [g.z0 - 1, g.z1 + 1]) {
    for (let x = g.x0; x <= g.x1; x++) out.set(x, -1, z, (x - g.x0) % 2 ? P.hazardB : P.hazardA);
  }
}

module.exports = { decorate };
