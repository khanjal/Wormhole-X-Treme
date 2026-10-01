'use strict';
// Gate Dynamics as the Hangar (creative pass 3.2): a cyan stripe from the entrance to every
// door (round the Hall gate, the lanes and the cells), the observation deck and control room
// over G1's west gallery, hazard rows beside the lanes, roof beams, and plaques.

const campus = require('../../lib/campus');
const bp = require('../../lib/blueprint');

const P = campus.PALETTE;
const CYAN = 'cyan';

function hazardRow(out, x0, x1, z) {
  for (let x = x0; x <= x1; x++) out.set(x, -1, z, (x - x0) % 2 ? P.hazardB : P.hazardA);
}

function decorate(out, w, version) {
  // Stripes. The spine runs up x -5 (west of the Hall gate's walk lane and of the lanes' end at
  // x 0) to G1's door; branches at z -56 go west to G4, G5 and G2 and east to G3; the Relay's goes
  // round G1's south-east corner, north of the lanes.
  bp.stripe(out, [[-5, -42], [-5, -101], [-1, -101]], CYAN);
  bp.stripe(out, [[-6, -56], [-37, -56], [-37, -73]], CYAN); // G4
  bp.stripe(out, [[-37, -74], [-37, -107], [-31, -107], [-31, -123]], CYAN); // G5
  bp.stripe(out, [[-38, -56], [-69, -56], [-69, -157]], CYAN); // G2
  bp.stripe(out, [[-4, -56], [37, -56], [37, -73]], CYAN); // G3
  bp.stripe(out, [[0, -101], [23, -101], [23, -117], [38, -117]], CYAN); // Relay

  // Hazard rows along the lanes' north side: a taxiway.
  hazardRow(out, 24, 70, -101);
  hazardRow(out, 0, 38, -91);
  out.cmd(bp.plaque(version, { id: 'lanes', wing: 'gates', at: { x: 75.5, y: 2.4, z: -95.5 }, text: 'MOTOR POOL LANES', colour: 'dark_aqua', sub: 'canal · rails · run-up' }));

  // The observation deck over G1's west gallery, with the control room in its south bay.
  const deck = { x0: -29, x1: -22, z0: -141, z1: -103 };
  bp.deck(out, deck, 6, { rail: ['w', 'n'], stair: { x0: -29, x1: -27, z: -97 } });
  out.fill(bp.box3(-26, 7, deck.z1, -22, 7, deck.z1), P.rail);
  for (const z of [-110, -107, -104]) out.set(-24, 7, z, P.console);
  out.cmd(bp.summonBoard(version, {
    id: 'g1_control', wing: 'gates', at: { x: -25.5, y: 9.2, z: -106.5 },
    spec: [{ text: 'G1 CONTROL', color: 'dark_aqua', bold: true }, '\n', { text: 'the run\'s steps show here', color: 'gray' }],
  }));
  out.cmd(bp.plaque(version, { id: 'deck', wing: 'gates', at: { x: -28.5, y: 2.4, z: -96.5 }, text: '▲ Observation deck', colour: 'dark_aqua' }));

  // The G2 gallery's gates named from outside, the Relay's plaque, a tab at each fork.
  const gal = campus.GATES.gallery;
  for (const [name, cx] of gal.row) {
    out.cmd(bp.plaque(version, { id: `g2_${name}`, wing: 'gates', at: { x: cx + 0.5, y: 3.4, z: -146.5 }, text: name, colour: 'aqua' }));
  }
  out.cmd(bp.plaque(version, { id: 'relay', wing: 'gates', at: { x: 53.5, y: 3.4, z: -129.5 }, text: 'RELAY', colour: 'aqua', sub: 'the far end of G1\'s trips' }));
  out.cmd(bp.plaque(version, { id: 'fork', wing: 'gates', at: { x: -4.5, y: 2.4, z: -55.5 }, text: '◄ G2 G4 G5 · ▲ G1 · G3 ►', colour: 'white' }));

  // Roof beams: the glass roof in a white-concrete frame every 20 blocks.
  const r = w.room;
  const roofY = r.y0 + r.h;
  for (let z = -160; z >= r.z0 && z <= r.z1; z += 20) out.fill(bp.box3(r.x0, roofY, z, r.x1, roofY, z), P.wall);
  for (let x = -60; x <= 60; x += 20) out.fill(bp.box3(x, roofY, r.z0, x, roofY, r.z1), P.wall);
}

module.exports = { decorate };
