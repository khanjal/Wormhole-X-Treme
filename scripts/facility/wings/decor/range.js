'use strict';
// The Range as Forward Base (creative pass 3.7): a runway from the Range gate, a console that
// dials home, blast walls beside the gate's apron, a mirror pier for stage 4, soul lanterns.

const bp = require('../../lib/blueprint');

function decorate(out, w, version) {
  const y = w.room.y0;
  out.fill(bp.box3(-1, y - 1, -15, 1, y - 1, -9), 'minecraft:chiseled_polished_blackstone');
  bp.console(out, version, { x: -8, y, z: -14, wing: 'range' }, 'x', [{ command: 'wormhole gate dial Range Ops', label: 'Dial Ops', color: 'red' }]);
  // Blast walls beside the apron (x -6..6 stays clear for what G1 sends through).
  for (const x of [-8, 8]) out.fill(bp.box3(x, y, -12, x, y + 3, -8), 'minecraft:polished_blackstone_bricks');
  // A free-standing pier for the Range mirror (banner at y + 1): down through the floor, so it is
  // solid two blocks round the opening below as well as above.
  out.fill(bp.box3(-17, y - 2, -25, -13, y + 4, -25), 'minecraft:polished_blackstone_bricks');
  for (const [x, z] of [[-29, -29], [29, -29], [-29, 10], [29, 10]]) out.set(x, y, z, 'minecraft:soul_lantern');
  out.cmd(bp.plaque(version, { id: 'forward', wing: 'range', at: { x: 0.5, y: y + 5.5, z: -9.5 }, text: 'FORWARD BASE', colour: 'red', sub: 'the Range · press the button to dial home' }));
}

module.exports = { decorate };
