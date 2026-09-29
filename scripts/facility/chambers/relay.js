'use strict';
// The far gates, as session fixtures: Relay in its cell across the gate hall, Range in the
// nether and Annex in the End (lib/campus.js GATES.far). G1 dials them; nothing runs here.

const campus = require('../lib/campus');
const { GateKit } = require('../lib/gatekit');
const { cellLayout } = require('../lib/blueprint');

const def = campus.chamber('relay');

/** Where each far gate is, for the chambers that dial it. */
function farGates() {
  const kit = new GateKit(null);
  return Object.fromEntries(Object.entries(campus.GATES.far).map(([name, f]) => [name, { name, dim: f.dim, geom: kit.place(f.shape, f.facing, f) }]));
}

module.exports = {
  id: 'relay',
  wing: 'gates',
  title: def.title,
  cell: def.box,
  seat: cellLayout(def).seat,
  options: {},
  refuses: () => 'the Relay is a fixture: dial it from G1',
  async stage() {},
  async run() {},
  checks: () => [],
  reset: 'wx:reset/relay',

  /** Builds the three far gates (again, if they are there); throws if the plugin refuses one. */
  async fixture(ctx) {
    const kit = new GateKit(ctx.server);
    const made = [];
    for (const g of Object.values(farGates())) {
      if (await kit.exists(g.name)) await kit.remove(g.name);
      await kit.build(g.name, g.geom, { dim: g.dim, floorY: campus.GATES.far[g.name].floorY });
      made.push(g.name);
    }
    return `built ${made.join(', ')}`;
  },

  farGates,
};
