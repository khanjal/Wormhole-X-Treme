'use strict';
// The Range, in the nether: a glass enclosure (a box, not a true dome: a stepped dome is many
// more fills for no test's benefit) on a blackstone floor, cleared of netherrack first. The far
// ends of the cross-world tests (gate, pad, ring end, mirror) arrive in stage 4.

const bp = require('../lib/blueprint');

function shell(out, w) {
  const r = w.room;
  const top = r.y0 + r.h - 1;
  out.fill(bp.box3(r.x0, r.y0, r.z0, r.x1, top, r.z1), 'minecraft:air');
  bp.room(out, r, { wall: 'minecraft:glass', roof: 'minecraft:glass', floor: 'minecraft:polished_blackstone', skirting: 'minecraft:polished_blackstone_bricks' });
  out.fill(bp.box3(0, r.y0 - 1, r.z0, 0, r.y0 - 1, r.z1), 'minecraft:shroomlight');
  out.fill(bp.box3(r.x0, r.y0 - 1, 0, r.x1, r.y0 - 1, 0), 'minecraft:shroomlight');
  // The south strip, clear of the entrance, the plate home and the fixtures desk.
  out.mustBeClear('range', bp.box3(r.x0, r.y0, 12, r.x1, top, r.z1));
}

module.exports = { shell };
