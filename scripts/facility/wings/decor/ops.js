'use strict';
// Ops, decorated (creative pass 1 and 2.1): the compass rose and the stripes from it to each
// transit fixture, the Gate Room's two pylons with their lit caps, the Briefing Room's window,
// sculk sensor and consoles, light under the mezzanine, and a wayfinding tab at each doorway.

const campus = require('../../lib/campus');
const bp = require('../../lib/blueprint');

const P = campus.PALETTE;

function decorate(out, w, version) {
  // The compass rose: a sea lantern at the centre, a spoke in each department's colour.
  out.set(0, -1, 0, P.guide);
  bp.stripe(out, [[0, -1], [0, -2]], 'cyan'); // to the runway's foot, the gate north
  bp.stripe(out, [[1, 0], [13, 0], [13, -2]], 'lime'); // east to the ring pad
  bp.stripe(out, [[-1, 0], [-13, 0], [-13, -3]], 'light_blue'); // west to the beam pad
  bp.stripe(out, [[0, 1], [0, 12], [-10, 12], [-10, 16]], 'magenta'); // south to the mirror (stage 4)

  // The Gate Room's pylons, the boards hanging in front of them; the one lamp in the facility.
  for (const x of [-6, 6]) bp.pillar(out, x, -15, 0, 4, { body: P.console, cap: P.guide });

  // The Briefing Room: a tinted window over the gate, two more consoles, the version's block.
  const m = campus.OPS.mezzanine;
  out.fill(bp.box3(-8, m.floorY + 1, m.z1, 8, m.floorY + 2, m.z1), P.cellGlass);
  for (const x of [-12, 12]) out.set(x, m.floorY + 1, -19, P.console);
  out.set(12, m.floorY + 2, -19, 'minecraft:calibrated_sculk_sensor');

  // Light under the mezzanine, over the service corridor.
  for (let x = -18; x <= 18; x += 4) out.set(x, m.floorY - 1, -17, 'minecraft:light[level=15]');

  // Wayfinding tabs at each doorway out of Ops, and the rooms' names.
  const tab = (id, at, words, colour) => out.cmd(bp.plaque(version, { id: `ops_${id}`, wing: 'ops', at, text: words, colour }));
  tab('n', { x: 0.5, y: 2.4, z: -19.5 }, '▲ Gate Dynamics · the Hangar', 'dark_aqua');
  tab('e', { x: 19.5, y: 2.4, z: 0.5 }, '► Ring Transit · the Concourse', 'green');
  tab('w', { x: -19.5, y: 2.4, z: 0.5 }, '◄ Beam Physics · the Transporter Bay', 'aqua');
  tab('s', { x: 0.5, y: 2.4, z: 19.5 }, '▼ Mirror Optics · the Looking-Glass Gallery', 'light_purple');
  tab('gateroom', { x: 0.5, y: 6.6, z: -12.5 }, 'THE GATE ROOM', 'white');
  tab('mirror', { x: -9.5, y: 2.2, z: 18.5 }, 'MIRROR · stage 4', 'dark_gray');
}

module.exports = { decorate };
