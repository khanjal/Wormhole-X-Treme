'use strict';
// Mirror Optics as the Looking-Glass Gallery (creative pass 3.5), the parts that are not stage
// 4's: magenta stripes to M1, M2 and M3, an amethyst frame round the Optics mirror's wall patch
// (which itself stays white concrete), a row of purple panes high on the lobby's north wall, and
// seat plaques on M1's gallery.

const bp = require('../../lib/blueprint');

function decorate(out, w, version) {
  bp.stripe(out, [[0, 41], [0, 47]], 'magenta'); // M1
  bp.stripe(out, [[-1, 46], [-38, 46], [-38, 73], [-23, 73]], 'magenta'); // M2
  bp.stripe(out, [[1, 46], [38, 46], [38, 73], [23, 73]], 'magenta'); // M3

  // The frame for the Optics mirror at (-10, 1, 40), on the north wall z 39.
  const wallZ = w.room.z0 - 1;
  out.fill(bp.box3(-12, 0, wallZ, -12, 4, wallZ), 'minecraft:amethyst_block');
  out.fill(bp.box3(-8, 0, wallZ, -8, 4, wallZ), 'minecraft:amethyst_block');
  out.fill(bp.box3(-11, 4, wallZ, -9, 4, wallZ), 'minecraft:amethyst_block');
  // The one coloured glass in the facility: high on the lobby's north wall, clear of the frame.
  out.fill(bp.box3(-30, 5, wallZ, -14, 6, wallZ), 'minecraft:purple_stained_glass_pane');
  out.fill(bp.box3(-6, 5, wallZ, 30, 6, wallZ), 'minecraft:purple_stained_glass_pane');

  for (const x of [-20, 20]) {
    out.cmd(bp.plaque(version, { id: `m1_seat_${x}`, wing: 'mirrors', at: { x: x + 0.5, y: 2.2, z: 71.5 }, text: 'SEAT', colour: 'light_purple', sub: 'watch a mirror from here' }));
  }
  out.cmd(bp.plaque(version, { id: 'optics', wing: 'mirrors', at: { x: -9.5, y: 5.6, z: 40.5 }, text: 'MIRROR OPTICS · stage 4', colour: 'dark_gray' }));
}

module.exports = { decorate };
