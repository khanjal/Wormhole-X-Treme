'use strict';
// Ring Transit as the Concourse (creative pass 3.3): a lime stripe along the Concourse's south
// side with spurs to each door, the Lab pad, the R5 desk and the tunnel; the Shaft Window (a light
// well under R3's glass gallery, open to the shaft); distance markers in the range tunnel; and
// the Build Bench's checklist.

const campus = require('../../lib/campus');
const bp = require('../../lib/blueprint');

const LIME = 'lime';

function decorate(out, w, version) {
  // The Concourse stripe runs along z 4, south of the R5 pads and north of R2/R3/R4's doors.
  bp.stripe(out, [[41, 4], [110, 4], [110, 1]], LIME); // to the tunnel mouth
  bp.stripe(out, [[54, 3], [54, 0]], LIME); // to the Lab pad
  bp.stripe(out, [[69, 5], [69, 6]], LIME); // R3's door
  bp.stripe(out, [[78, 3], [78, -7], [77, -7]], LIME); // R1's door
  bp.stripe(out, [[100, 3], [100, -2]], LIME); // the R5 desk

  // The Shaft Window: under R3's glass gallery (campus: galleryFloor), a well open to the shaft,
  // with a sea-lantern strip down its far face, so the seat looks straight down 60 blocks.
  const r3 = campus.chamber('r3');
  const g = bp.cellLayout(r3).gallery;
  const depth = r3.shaft;
  out.fill(bp.box3(g.x0, -depth, g.z0, g.x1, -1, g.z1), 'minecraft:air');
  out.fill(bp.box3(g.x0, -depth - 1, g.z0, g.x1, -depth - 1, g.z1), campus.PALETTE.cellWall);
  const mid = Math.floor((g.x0 + g.x1) / 2);
  out.fill(bp.box3(mid, -depth, g.z1 + 1, mid, -1, g.z1 + 1), campus.PALETTE.guide);

  // The range tunnel's distance markers: bands at the tunnel's edges, beside each far ring.
  const tunnel = campus.chamber('tunnel');
  const A = require('../../chambers/r6-range').A;
  for (const [d, colour, words] of [[64, LIME, '64'], [128, LIME, '128'], [250, LIME, '250 · inside the limit'], [257, 'red', '257 · one past 256']]) {
    const x = A.x + d;
    out.set(x, -1, tunnel.box.z0, `minecraft:${colour}_concrete`);
    out.set(x, -1, tunnel.box.z1, `minecraft:${colour}_concrete`);
    out.cmd(bp.plaque(version, { id: `tunnel_${d}`, wing: 'rings', at: { x: x + 0.5, y: 3.4, z: tunnel.box.z1 + 0.5 }, text: words, colour: colour === 'red' ? 'red' : 'green' }));
  }

  // The Build Bench's checklist, over its door; a tab at the Concourse's start.
  out.cmd(bp.summonBoard(version, {
    id: 'r4_checklist', wing: 'rings', at: { x: 93.5, y: 2.6, z: 3.5 }, scale: 0.7,
    spec: [{ text: 'TRY: ', color: 'green', bold: true }, { text: 'mixed slabs · mixed halves · a double slab · a filled disc · a low ceiling · something inside · a hole in the floor', color: 'gray' }],
  }));
  out.cmd(bp.plaque(version, { id: 'concourse', wing: 'rings', at: { x: 44.5, y: 2.4, z: 3.5 }, text: '► R1 · R2 · R3 · R4 · R5 · the range tunnel', colour: 'white' }));
}

module.exports = { decorate };
