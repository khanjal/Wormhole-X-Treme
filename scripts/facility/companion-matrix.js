'use strict';
// The companion desks' matrix cells (chambers/companion-*.js), merged into MATRIX by matrix.js.
// Each is marked `with` (it runs only when run-facility --with installed every companion named)
// or `without` (a paired run: only with --with, and none of those installed), so a self-test
// without --with runs none of them and is what it always was.

const REGIONS = ['worldguard', 'luckperms'];
const MAP = ['dynmap'];

const cell = (desk, c, companions, expect = 'PASS', extra = {}) => ({ name: `${desk} ${c}`, values: { case: c }, expect, with: companions, ...extra });

const CELLS = {
  // #236, Dynmap markers (chambers/companion-map.js).
  map: [
    ...['gate', 'dial', 'cross-world'].map((c) => cell('map', c, MAP)),
    cell('map', 'rename', MAP, 'FAIL:the gate was renamed', {
      known: 'the #236 checklist\'s "gate edit <A> name X" has nothing to run: gate edit has no name field (its fields: portal, iris, light, woosh, redstone, owner, custom, idc, group, spin, iris-animation), so a gate cannot be renamed and its label never moves',
    }),
    cell('map', 'escaped', MAP),
    cell('map', 'iris hidden', MAP, 'FAIL:MapB stayed idle for a full redraw after its opening was drawn', {
      known: '#236: a visible gate dialled from a hidden iris gate (map-show-iris-gates false) is drawn open, not idle, so the map shows it connected to something unseen: MapScanner marks each gate open from its own isGatePortalOpen, and the far end of a wormhole is open too; only the dialler\'s target is filtered',
    }),
    ...['rings', 'beams', 'mirrors', 'remove', 'restart'].map((c) => cell('map', c, MAP)),
    cell('map', 'reload', MAP, 'REFUSED:Dynmap 3.7 and 3.8 have no /dynmap reload (their subcommands stop at render, purge, pause, stats and the like), and nothing else reloads Dynmap alone'),
    ...['layer off', 'off'].map((c) => cell('map', c, MAP)),
    { name: 'map absent', values: { case: 'absent' }, expect: 'PASS', without: MAP },
  ],
  // The tester groups in LuckPerms, and the console's group switch (chambers/companion-perms.js).
  perms: ['default', 'visitor', 'builder', 'operator', 'console'].map((c) => cell('perms', c, ['luckperms'])),
  // #240, WorldGuard region flags (chambers/companion-regions.js).
  regions: [
    ...['dial refused', 'walk-in refused', 'cart refused', 'shut allowed', 'iris allowed', 'hand build refused', 'preview refused',
      'straddling refused', 'hand build allowed', 'member refused', 'nonmembers flag', 'owner refused', 'op allowed', 'no nodes',
      'flag cleared', 'switched off'].map((c) => cell('regions', c, REGIONS)),
    { name: 'regions absent', values: { case: 'absent' }, expect: 'PASS', without: ['worldguard'] },
  ],
};

module.exports = { CELLS };
