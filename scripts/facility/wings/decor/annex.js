'use strict';
// The Annex as the Observatory (creative pass 3.8): a purpur runway from the Annex gate, a
// console that dials home, end rods on purpur pillars beside the apron, a mirror pier for stage 4.

const bp = require('../../lib/blueprint');

function decorate(out, w, version) {
  const y = w.room.y0;
  out.fill(bp.box3(999, y - 1, 1005, 1001, y - 1, 1011), 'minecraft:purpur_pillar');
  bp.console(out, version, { x: 992, y, z: 1006, wing: 'annex' }, 'x', [{ command: 'wormhole gate dial Annex Ops', label: 'Dial Ops', color: 'light_purple' }]);
  for (const [x, z] of [[993, 1005], [1007, 1005], [993, 1011], [1007, 1011]]) {
    bp.pillar(out, x, z, y, 3, { body: 'minecraft:purpur_pillar', cap: 'minecraft:end_rod' });
  }
  // The mirror pier (banners at y + 1 on both faces), down through the platform: solid two blocks
  // round each opening below as well as above.
  out.fill(bp.box3(1010, y - 2, 1017, 1014, y + 4, 1017), 'minecraft:end_stone_bricks');
  out.cmd(bp.plaque(version, { id: 'observatory', wing: 'annex', at: { x: 1000.5, y: y + 5.5, z: 1008.5 }, text: 'THE OBSERVATORY', colour: 'light_purple', sub: 'the Annex · press the button to dial home' }));
}

module.exports = { decorate };
