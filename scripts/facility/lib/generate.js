'use strict';
// Compiles the campus into the datapack: wx:build/<wing> and wx:reset/<chamber>, each with a
// sentinel block it resets first and sets last, and a manifest of what each function should
// have made (sentinel, anchor blocks, volumes that must be air) for the builder and self-test.

const path = require('path');
const campus = require('./campus');
const datapack = require('./datapack');
const wings = require('../wings');

// Sentinels sit out of sight, one per function: under the Ops floor in the overworld, under the
// Range floor in the nether, under the Annex platform in the End.
function sentinelAt(dim, index) {
  if (dim === campus.NETHER) return [-29 + index, 62, -29];
  if (dim === campus.END) return [981 + index, 58, 981];
  return [-19 + (index % 39), -3, -19 + 2 * Math.floor(index / 39)];
}

/** Writes the facility pack into <world>/datapacks/wx and returns its manifest. */
function writeFacilityPack(worldFolder, version) {
  const problems = [...new Set(wings.validateLayout(version))];
  if (problems.length) throw new Error(`the campus layout does not fit together:\n  ${problems.join('\n  ')}`);
  const { builds, resets } = wings.allBlueprints(version);
  const counters = {};
  const functions = {};
  const manifest = [];
  for (const f of [...builds, ...resets]) {
    counters[f.dim] = (counters[f.dim] || 0) + 1;
    const sentinel = { at: sentinelAt(f.dim, counters[f.dim] - 1), block: campus.PALETTE.sentinel };
    const compiled = f.bp.compile(sentinel);
    functions[f.fn] = compiled.body;
    manifest.push({
      fn: f.fn, id: `wx:${f.fn}`, wing: f.wing, chamber: f.chamber, dim: f.dim, sentinel,
      commands: compiled.commands, blocks: compiled.blocks, anchors: f.bp.anchors, clear: f.bp.clear,
    });
  }
  const root = datapack.writePack(worldFolder, version, functions);
  return { root: path.resolve(root), functions: manifest };
}

module.exports = { writeFacilityPack, sentinelAt };
