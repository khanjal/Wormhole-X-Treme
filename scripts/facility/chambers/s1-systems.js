'use strict';
// S1, the Systems console (design 3.6): the plugin's settings, sounds and permissions, audited.
// A desk with no cell: a case that needs a gate builds one on G1's Stand position (as the
// Permissions Desk does) and cleanup runs G1's own reset; rings use the Concourse floor and a
// beam the transit pads. Each group of cases is its own file in chambers/s1/:
//
//   console.js      `wormhole config` against every setting the plugin's source declares
//   sounds.js       the plugin's sounds, as packets: which, how many, volume, pitch; and silenced
//   gates.js        a gate setting at a time, changed and its effect seen
//   rings.js        the ring settings: defaults a new pair takes, refusals, outline, timings
//   more.js         mirrors, logging, integrations, and the settings read only at start
//   permissions.js  a non-op with no permissions plugin: the fallback, the nodes, use-is-teleport
//
// The settings no case changes are listed in docs/DEVELOPMENT.md, each with why.

const campus = require('../lib/campus');
const { deskLayout } = require('../lib/blueprint');
const { GateKit } = require('../lib/gatekit');

const def = campus.chamber('s1');
const GROUPS = ['console', 'sounds', 'gates', 'rings', 'more', 'permissions'].map((name) => ({ name, mod: require(`./s1/${name}`) }));

const CASES = GROUPS.flatMap((g) => g.mod.cases.map((x) => ({ ...x, group: g.name })));

/** The group that runs a case. */
function groupOf(o) {
  const hit = CASES.find((x) => x.value === o.case);
  return hit ? GROUPS.find((g) => g.name === hit.group).mod : null;
}

async function cleanup(ctx) {
  const fac = ctx.facility;
  for (const g of GROUPS) if (g.mod.cleanup) await g.mod.cleanup(ctx);
  const kit = new GateKit(ctx.server);
  for (const p of [fac.probe, fac.probe2]) if (p) p.bot.chat('/wormhole gate preview clear -all');
  if (await kit.exists('Sys')) await kit.remove('Sys');
  if (await kit.exists('Relay')) await kit.force('Relay');
  const reset = fac.manifest.functions.find((f) => f.fn === 'reset/g1');
  if (reset) {
    const r = await fac.runFunction(reset);
    if (!r.ok) throw new Error(`G1's reset: ${r.detail}`);
  }
}

module.exports = {
  id: 's1',
  wing: 'systems',
  title: def.title,
  seat: deskLayout(def).seat,
  options: { case: CASES },
  needs: (o) => {
    const g = groupOf(o);
    return { config: g && g.needs ? g.needs(o) : {} };
  },
  refuses: (o, version, fac) => {
    const g = groupOf(o);
    return g && g.refuses ? g.refuses(o, version, fac) : null;
  },
  async stage(ctx, o) {
    const g = groupOf(o);
    if (g.stage) await g.stage(ctx, o);
  },
  async run(ctx, o) {
    return groupOf(o).run(ctx, o);
  },
  checks(ctx, o) {
    // A false check says what it saw instead in the log, where the summary names only the check.
    return groupOf(o).checks(ctx.observed, o, ctx).map((x) => ({
      ...x,
      test: async () => {
        try {
          const ok = await x.test();
          if (!ok) console.log(`  s1 ${o.case}: false: ${x.name}`);
          return ok;
        } catch (e) {
          console.log(`  s1 ${o.case}: false: ${x.name}: ${e.message}`);
          return false;
        }
      },
    }));
  },
  cleanup,
  async afterRestore(ctx) {
    for (const g of GROUPS) if (g.mod.afterRestore) await g.mod.afterRestore(ctx);
  },
  CASES,
};
