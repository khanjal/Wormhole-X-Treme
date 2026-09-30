'use strict';
// The chamber contract. Every test in the facility is one object in its own file here, named
// in lib/campus.js by `logic: '<file>'`. Stages 2 onward add chambers against this contract
// and nothing else; the runner is facility.js (runChamber, resetChamber).
//
//   module.exports = {
//     id: 'g1',                 the campus chamber it drives (lib/campus.js CHAMBERS)
//     wing: 'gates',
//     title: 'Test Stand',
//     cell,                     the campus box ({ x0, x1, z0, z1, y0, h }): what reset puts back
//     seat,                     { x, y, z, yaw, pitch }: where Watch puts you (from the layout)
//     options: {                every option, each a list of values; the first is the default.
//       shape: [ 'Standard', { value: 'Grand', label: 'Grand', why: 'hover text' }, ... ],
//     },
//     needs: (values) => ({ config: { 'gate-sound-volume': '0.5' } }),
//                               settings to hold during the run; applied before stage and put
//                               back after the checks (after reset, for a Stage)
//     refuses: (values, version) => null | 'reason',
//                               a combination the chamber will not run (or cannot on this
//                               version); the self-test expects REFUSED:<reason>, and nothing
//                               in the world changes
//     stage: async (ctx, values) => {},   fixture only
//     run: async (ctx, values) => {},     the trip; returns nothing; throws only if it could not
//                                         make the trip at all
//     checks: (ctx, values) => [ { name, test: async () => boolean, afterReset } ],
//                               predicates on the world, read after run(): the first false one
//                               fails the run and is named on the board. After a reset the
//                               same checks are read again and must each equal `afterReset`
//                               (default false: nothing of the run is left); null skips one.
//     reset: 'wx:reset/g1',     the mcfunction that puts the cell back (generated from the
//                               campus); the runner also kills every entity tagged ctx.tag
//     cleanup: async (ctx) => {},   optional: the plugin's side of a reset (remove the gate,
//                               force the far end, clear an iris code); runs before the reset
//                               function, and before every run
//     fixture: async (ctx) => 'what', optional: something the chamber keeps for the whole
//                               session (the Relay, the gallery); built once after the build,
//                               and again after a reset of its cell, which is judged by it
//   }
//
// ctx (built by facility.js for one run):
//   server   lib/server.js Server: run(cmd) -> { lines, errors }, until(cmd, re, ms, what)
//   probe    lib/probe.js Probe: teleport, walk, click, standOn, entityByTag
//   config   lib/config.js Config (already holding needs.config)
//   board    lib/board.js Boards
//   version  the server's Minecraft version
//   tag      the run's entity tag (wx_run_<id>): give it to everything you summon
//   observed an object run() may record packet-level observations in, for checks to read
//   step(name)  advances the bossbar ("G1 · 3/7 · dialling Relay"); await it, or its output
//               lands in the middle of the next command's
//   menagerie   lib/menagerie.js: animals, pets, vehicles and armoury items by version
//   watch       lib/observe.js: tick-exact launches, arrivals and per-tick tracking
//   facility    the Facility itself, for what the rest does not cover
//   layout   the cell's derived positions (door, seat, board, pylon; lib/blueprint.js)
//
// Nothing in a chamber sends a console command except through ctx.server.run, so every command
// the facility sends is greppable, and nothing waits by sleeping: wait for a block, a packet,
// a log line or a score, with a deadline.

const campus = require('../lib/campus');

/** The console's entries: every campus chamber in order, with its logic module if it has one. */
function entries() {
  return campus.CHAMBERS.filter((c) => c.kind !== 'tunnel' || c.logic).map((def, i) => ({
    number: i + 1,
    def,
    chamber: def.logic ? require(`./${def.logic}`) : null,
    values: {},
  }));
}

module.exports = { entries };
