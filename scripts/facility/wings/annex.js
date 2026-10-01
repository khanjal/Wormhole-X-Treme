'use strict';
// The Annex, in the End: an open purpur platform over the void with a glass lip one block high
// all round, so nothing walks off it. (1000, 1000) is inside the outer-islands ring, so the
// space above the platform is cleared first in case an island reaches into it.

const bp = require('../lib/blueprint');

function shell(out, w) {
  const r = w.room;
  const top = r.y0 + r.h - 1;
  out.fill(bp.box3(r.x0 - 1, r.y0, r.z0 - 1, r.x1 + 1, top, r.z1 + 1), 'minecraft:air');
  out.fill(bp.box3(r.x0 - 1, r.y0 - 1, r.z0 - 1, r.x1 + 1, r.y0 - 1, r.z1 + 1), 'minecraft:purpur_block');
  bp.room(out, { ...r, h: 1 }, { wall: 'minecraft:glass', roof: null, skirting: null });
  out.fill(bp.box3(1000, r.y0 - 1, r.z0, 1000, r.y0 - 1, r.z1), 'minecraft:sea_lantern');
  out.anchor(r.x0 - 1, r.y0 - 1, r.z0 - 1, 'minecraft:purpur_block', 'annex platform corner');
  // The north strip, clear of the entrance, the plate home and the fixtures desk.
  out.mustBeClear('annex', bp.box3(r.x0, r.y0, r.z0, r.x1, top, r.z0 + 10));
}

module.exports = { shell };
