'use strict';
// The gallery's studio (lib/gallery.js and its scene families): a white floor, a backdrop and two
// side walls far from the campus (which ends at z 92), with its own forceload, and the few helpers
// every scene family shares. The walls are why a view from a corner never looks out past the end
// of the backdrop; the floor is flat white concrete, whose texture is plain, so it does not
// shimmer from frame to frame.
//
// The gate's opening is in the plane z = plane, facing south; the camera stands to the south of it
// looking north, the partner gate sixty blocks behind the camera. The other families (rings,
// mirrors) use the same floor between the backdrop and the camera.

const campus = require('./campus');
const gatekit = require('./gatekit');

const O = campus.OVERWORLD;

const STUDIO = {
  plane: 400, half: 70, floor: 'minecraft:white_concrete', backdrop: 'minecraft:white_concrete',
  backdropAt: 374, backdropHigh: 48, partnerAt: 470, sideAt: 46,
};

/** The chunk rectangle the studio forceloads, as `forceload` takes it: x1 z1 x2 z2. */
function forceRect(s = STUDIO) {
  return [-s.half - 16, s.backdropAt - 16, s.half + 16, s.partnerAt + 40];
}

/** The camera's eye height above Probe's feet. */
const EYE = 1.62;

/** Yaw and pitch (Minecraft's: 0 faces south, -90 east, pitch down positive) for looking from `from` at `to`. */
function lookAt(from, to) {
  const dx = to.x - from.x;
  const dz = to.z - from.z;
  return {
    yaw: Math.round((Math.atan2(-dx, dz) * 180) / Math.PI * 10) / 10 || 0,
    pitch: Math.round((Math.atan2(from.y - to.y, Math.hypot(dx, dz)) * 180) / Math.PI * 10) / 10 || 0,
  };
}

/**
 * Where Probe stands (feet, the form `Probe.teleport` takes) for its eye to be at `eye` looking at
 * `target`: its feet stay on the floor (y 0) however low the eye is asked to be.
 */
function standAt(eye, target) {
  const e = { ...eye, y: Math.max(eye.y, EYE) };
  return { x: Math.round(e.x * 10) / 10, y: Math.round((e.y - EYE) * 10) / 10, z: Math.round(e.z * 10) / 10, ...lookAt(e, target) };
}

/** Forceloads the studio's chunks and lays its floor and backdrop. Returns the problems it met. */
async function buildStudio(srv, dim = O) {
  const s = STUDIO;
  const problems = [];
  const run = async (cmd) => {
    const r = await srv.run(`execute in ${dim} run ${cmd}`);
    if (r.errors.length) problems.push(`${cmd}: ${r.errors.join(' ')}`);
  };
  const [x0, z0, x1, z1] = forceRect(s);
  await run(`forceload add ${x0} ${z0} ${x1} ${z1}`);
  const points = [];
  for (let cx = Math.floor(x0 / 16); cx <= Math.floor(x1 / 16); cx++) {
    for (let cz = Math.floor(z0 / 16); cz <= Math.floor(z1 / 16); cz++) points.push([cx * 16 + 8, 0, cz * 16 + 8]);
  }
  await srv.waitLoaded(dim, points, 120000);
  await run(`fill ${-s.half} -1 ${z0} ${s.half} -1 ${z1} ${s.floor}`);
  await run(`fill ${-s.sideAt} 0 ${s.backdropAt} ${s.sideAt} ${s.backdropHigh} ${s.backdropAt} ${s.backdrop}`);
  for (const x of [-s.sideAt, s.sideAt]) await run(`fill ${x} 0 ${s.backdropAt} ${x} ${s.backdropHigh} ${s.partnerAt + 30} ${s.backdrop}`);
  return problems;
}

/**
 * Repaints the floor round a box and sweeps up what was dropped: a gate build puts the campus's
 * floor block back where it made room, and a flush gate taken down leaves its trench, so the
 * studio floor would show patches and pits. `box` is { x0, x1, z0, z1 }.
 */
async function tidyBox(srv, box, dim = O) {
  await srv.run(`execute in ${dim} run fill ${box.x0} -1 ${box.z0} ${box.x1} -1 ${box.z1} ${STUDIO.floor} replace ${gatekit.FLOORS[dim]}`);
  // What a removed gate drops: an item is an entity the viewer cannot draw, and shows as a magenta square.
  await srv.run(`execute in ${dim} run kill @e[type=minecraft:item,x=0,y=0,z=${STUDIO.plane},distance=..150]`);
}

/** True for a block that is air, or not loaded. */
function isAir(block) {
  return !block || block.name === 'air' || block.name === 'cave_air';
}

/**
 * Runs a server command whose own answer must match `answer`; returns the answer's lines. A WARN
 * from something else that lands before the fence is not the command's failure, so it is not judged.
 */
async function must(srv, command, answer) {
  const r = await srv.run(command);
  if (!r.lines.some((l) => answer.test(l))) throw new Error(`${command}: ${[...r.lines, ...r.errors].join(' ') || 'no answer'}`);
  return r.lines;
}

/**
 * The `/tick rate` a reel is recorded at, and the slowdown it really is: { rate, factor }. The rate
 * is 20 over the slowdown asked for, to the thousandth, and the factor is worked back from the
 * rate, so a GIF is retimed by the speed the server ran at, not the one that was asked for. A
 * server that cannot be slowed (before 1.20.3) runs at 20, factor 1.
 */
function slowdown(slow, canSlow) {
  if (!(slow >= 1 && slow <= 20)) throw new Error(`the gallery's slowdown is from 1 to 20, not ${slow}`);
  const rate = canSlow ? Math.round((20 / slow) * 1000) / 1000 : 20;
  return { rate, factor: 20 / rate };
}

module.exports = { O, STUDIO, forceRect, EYE, lookAt, standAt, buildStudio, tidyBox, isAir, must, slowdown };
