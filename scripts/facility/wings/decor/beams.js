'use strict';
// Beam Physics as the Transporter Bay (creative pass 3.4): light-blue stripes from the BeamLab
// pad to B1's door and to the Dispatch Desk, the dispatch office round B2, and B1's pad names on
// its gallery.

const campus = require('../../lib/campus');
const bp = require('../../lib/blueprint');

const P = campus.PALETTE;

function decorate(out, w, version) {
  bp.stripe(out, [[-49, 0], [-49, -8]], 'light_blue'); // to B1's door
  bp.stripe(out, [[-44, 3], [-44, 20], [-57, 20]], 'light_blue'); // to the Dispatch Desk

  // The dispatch office: a copper wall behind the desk (as seen from its seat, to the north) with
  // a fake control board on it, and two more consoles.
  out.fill(bp.box3(-62, 0, 21, -57, 2, 21), P.console);
  for (const [i, words] of ['GOTO', 'SEND', 'COST'].entries()) {
    out.cmd(bp.plaque(version, { id: `b2_${words}`, wing: 'beams', at: { x: -61.5 + i * 2, y: 3.4, z: 21.5 }, text: words, colour: 'aqua' }));
  }
  for (const x of [-70, -50]) out.set(x, 0, 22, P.console);

  // B1's pads, named on its gallery at the x of each pad.
  const { PADS } = require('../../chambers/b1-pads');
  const g = bp.cellLayout(campus.chamber('b1')).gallery;
  for (const [name, p] of Object.entries(PADS)) {
    if (p.dim !== campus.OVERWORLD) continue;
    const high = p.z < 0;
    out.cmd(bp.plaque(version, { id: `b1_${name}`, wing: 'beams', at: { x: p.x + 0.5, y: high ? 3.6 : 2.8, z: g.z0 + 1.5 }, text: `Pad-${name}`, colour: 'aqua' }));
  }
  out.cmd(bp.plaque(version, { id: 'bay', wing: 'beams', at: { x: -43.5, y: 2.4, z: 3.5 }, text: '◄ B1 Pad Array · ▼ B2 Dispatch Desk', colour: 'white' }));
}

module.exports = { decorate };
