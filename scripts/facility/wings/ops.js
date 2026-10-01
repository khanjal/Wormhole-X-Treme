'use strict';
// Ops: the atrium's own structures. The mezzanine (Systems) on the north side with a stair,
// the eight tp plates in the service corridor under it, and the three Ops boards: welcome, the
// Ops wall (one line per chamber, and the transit routes) and the fault counter. The transit
// fixtures themselves (gate, ring pad, beam pad) are in ./transit.js.

const campus = require('../lib/campus');
const bp = require('../lib/blueprint');

const P = campus.PALETTE;

function structures(out, w, version) {
  const m = campus.OPS.mezzanine;
  const y = m.floorY;
  out.fill(bp.box3(m.x0, y, m.z0, m.x1, y, m.z1), P.trim);
  out.fill(bp.box3(m.x0, y + 1, m.z1, m.x1, y + 1, m.z1), P.rail);
  // The stair up the west side, rising north, and a gap in the railing where it arrives.
  const st = campus.OPS.stair;
  for (let k = 0; k <= y; k++) {
    const z = st.z1 - k;
    out.fill(bp.box3(st.x, k, z, st.x + 1, k, z), 'minecraft:quartz_stairs[facing=north]');
    if (k > 0) out.fill(bp.box3(st.x, 0, z, st.x + 1, k - 1, z), P.trim);
  }
  out.fill(bp.box3(st.x, y + 1, m.z1, st.x + 1, y + 1, m.z1), 'minecraft:air');
  out.anchor(0, y, -17, P.trim, 'mezzanine floor');

  // The service corridor: eight plates in a row under the mezzanine, a label over each, and a
  // board at each end. The fallback that works with the plugin down.
  const c = campus.TRANSIT.centre;
  for (const x of [m.x0 + 1, m.x1 - 1]) {
    out.cmd(bp.summonBoard(version, {
      id: `service_${x < 0 ? 'w' : 'e'}`, wing: 'ops', at: { x: x + 0.5, y: 2.2, z: c.z + 0.5 },
      spec: [{ text: 'SERVICE CORRIDOR', color: 'white', bold: true }, '\n',
        { text: 'plates always work, even with the plugin down', color: 'gray' }],
    }));
  }
  for (const p of campus.TRANSIT.plates) {
    const x = c.x + p.dx;
    const z = c.z + p.dz;
    const dest = campus.wing(p.to);
    bp.plate(out, x, 0, z, p.to);
    out.cmd(bp.summonBoard(version, {
      id: `plate_${p.dir}`, wing: 'ops', at: { x: x + 0.5, y: 1.3, z: z + 0.5 },
      spec: [{ text: dest.title, color: dest.text, bold: true }],
    }));
  }

  // A plate home from the mezzanine.
  bp.plate(out, 3, y + 1, -17, { wing: 'ops', to: campus.TRANSIT.home });
  out.cmd(bp.summonBoard(version, {
    id: 'home_systems', wing: 'ops', at: { x: 3.5, y: y + 2.4, z: -16.5 },
    spec: [{ text: 'Operations floor', color: 'white', bold: true }],
  }));

  out.cmd(bp.summonBoard(version, {
    id: 'welcome', wing: 'ops', at: campus.OPS.welcome, scale: 1.4,
    spec: [{ text: 'WORMHOLE RESEARCH FACILITY', color: 'aqua', bold: true }, '\n',
      { text: 'gate north, ring pad east, beam pad west · say ! for the console', color: 'gray' }, '\n',
      { text: 'tp plates in the service corridor under the balcony', color: 'dark_gray' }],
  }));
  out.cmd(bp.summonBoard(version, {
    id: 'opswall', wing: 'ops', at: campus.OPS.wall,
    spec: [{ text: 'OPS WALL', color: 'white', bold: true }, '\n', { text: 'waiting for the bot', color: 'gray' }],
  }));
  out.cmd(bp.summonBoard(version, {
    id: 'faults', wing: 'ops', at: campus.OPS.faults,
    spec: [{ text: 'Plugin log: ', color: 'white' }, { text: 'not watched yet', color: 'gray' }],
  }));
}

module.exports = { structures };
