'use strict'
// The parts of the lab that are not a bay: the hub, an underground base in the style of the SGC,
// with the corridors to each bay; and the offworld site in the nether that the lab gate and the
// beam bay can reach, a ruined clearing with its own gate.

const kit = require('../kit')
const p = require('./pieces')

const { serverCommand } = kit
const { FLOOR, FEET } = p

const HUB = { x0: -24, z0: -8, x1: 24, z1: 36, top: -52 }
const SPAWN = { x: 0, z: 26 }
const GALLERY_DOOR = 16
const OFFWORLD = { x: 0, y: 65, z: 0 }

/** Every bay's mission-board sign, in the briefing room, keyed by bay. */
const BOARD = { gate: { x: -9, z: 35 }, ring: { x: -3, z: 35 }, beam: { x: 3, z: 35 }, mirror: { x: 9, z: 35 } }
const BOARD_Y = FEET + 2

const book = [
  'THE LAB\n\nEach bay has a control panel. Every sign on it has a next button under it: press it to step through the choices.',
  'Press RUN and WxBot builds what the signs show and makes the trip while you watch.\n\nPull RESET to take the bay back to empty.',
  'The lamp over each panel is green when the bay is idle, amber while a run goes, and red when the last run failed.',
  'The mission board in this room shows the last run in each bay.\n\nIn the build pads you are in creative: build a gate or lay two ring circles, then press that pad\'s Run.',
  'Say "stop" in chat to shut the lab down.'
]

function buildHub () {
  const h = HUB
  p.room(h.x0, h.z0, h.x1, h.z1, h.top, { wall: 'gray_concrete', floor: 'polished_andesite', ceiling: 'light_gray_concrete' })
  p.fill(h.x0 + 1, FLOOR - 3, h.z0 + 1, h.x1 - 1, FLOOR - 1, h.z1 - 1, 'stone')
  for (let x = h.x0 + 4; x < h.x1; x += 8) {
    p.fill(x, FEET, h.z1, x, h.top - 1, h.z1, 'iron_block')
    p.setblock(x, h.top - 1, (h.z0 + h.z1) / 2, 'sea_lantern')
    p.setblock(x, h.top - 1, h.z0 + 6, 'sea_lantern')
    p.setblock(x, h.top - 1, h.z1 - 6, 'sea_lantern')
  }
  // Doorways to the gate room (north), and through the gate room's side walls to the ring room and
  // the beam bay; and south to the mirror gallery. Short corridors bridge the gaps between rooms.
  p.doorway(0, h.z0, 'x')
  corridor(44, 50, -30, 'x')
  corridor(-50, -44, -30, 'x')
  // To the gallery, off to one side of the mission board: through the hub's south wall and the
  // gallery's north wall, three deep between them.
  for (const z of [h.z1, h.z1 + 1, h.z1 + 2]) p.doorway(GALLERY_DOOR, z, 'x')

  // The briefing room: a long table with seats, and the lectern that says how the lab works.
  p.fill(-6, FEET, 16, 6, FEET, 18, 'dark_oak_planks')
  for (let x = -5; x <= 5; x += 2) {
    p.setblock(x, FEET, 15, 'dark_oak_stairs[facing=south]')
    p.setblock(x, FEET, 19, 'dark_oak_stairs[facing=north]')
  }
  p.lectern(0, FEET, 23, 'north', 'The Lab', book)
  p.sign(-2, FEET + 1, h.z1 - 1, 'north', [['SGC', 'aqua'], 'Stargate', 'Command', 'Level 28'])
  p.sign(2, FEET + 1, h.z1 - 1, 'north', [['BRIEFING', 'aqua'], ['ROOM', 'aqua']])

  // The mission board: a sign per bay with its last run.
  p.fill(-11, FEET, h.z1, 11, FEET + 3, h.z1, 'polished_blackstone')
  p.sign(0, FEET + 3, h.z1 - 1, 'north', [['MISSION BOARD', 'gold'], 'last run in', 'each bay'])
  for (const [bay, at] of Object.entries(BOARD)) {
    p.sign(at.x, BOARD_Y, at.z, 'north', [[bay.toUpperCase(), 'aqua'], 'no runs yet'])
  }

  // The stairwell: stairs up to a landing, with the floor numbers on the wall.
  for (let i = 0; i < 6; i++) p.setblock(h.x1 - 2, FEET + i, h.z0 + 3 + i, 'polished_andesite_stairs[facing=south]')
  p.fill(h.x1 - 4, FEET + 5, h.z0 + 9, h.x1 - 1, FEET + 5, h.z0 + 12, 'polished_andesite')
  p.sign(h.x1 - 1, FEET + 1, h.z0 + 2, 'west', [['STAIRS', 'aqua'], 'Level 28', 'up: Level 27'])
  p.sign(h.x1 - 1, FEET + 7, h.z0 + 11, 'west', [['LEVEL 27', 'aqua'], 'restricted'])
  // Signposts to each bay.
  p.sign(0, FEET + 4, h.z0 + 1, 'south', [['GATE ROOM', 'aqua'], 'north'])
  p.sign(GALLERY_DOOR, FEET + 4, h.z1 - 1, 'north', [['MIRROR', 'aqua'], ['GALLERY', 'aqua'], 'south'])
  p.sign(h.x0 + 1, FEET + 2, 4, 'east', [['RING ROOM', 'aqua'], 'through the', 'gate room, east'])
  p.sign(h.x1 - 1, FEET + 2, 4, 'west', [['BEAM BAY', 'aqua'], 'through the', 'gate room, west'])

  serverCommand(`setworldspawn ${SPAWN.x} ${FEET} ${SPAWN.z}`)
}

/** A short corridor, five wide, between two rooms' walls at x0 and x1, along z = at. */
function corridor (x0, x1, at, along) {
  p.fill(x0, FLOOR, at - 3, x1, FEET + 4, at + 3, 'gray_concrete')
  p.fill(x0, FEET, at - 2, x1, FEET + 3, at + 2, 'air')
  p.fill(x0, FLOOR - 3, at - 2, x1, FLOOR - 1, at + 2, 'stone')
  p.doorway(x0, at, along === 'x' ? 'z' : 'x')
  p.doorway(x1, at, along === 'x' ? 'z' : 'x')
  p.fill(x0 + 1, FEET + 3, at, x1 - 1, FEET + 3, at, 'sea_lantern')
}

/**
 * The offworld site: a clearing walled off from the nether in glass, overgrown stone, a campfire
 * someone left behind, and its gate, Offworld, with its own DHD.
 */
async function buildOffworld () {
  const { x, y, z } = OFFWORLD
  const n = 'nether'
  kit.inNether(`forceload add ${x - 32} ${z - 32} ${x + 32} ${z + 32}`)
  // Forceloading is asynchronous on newer servers: wait until the far corners are loaded.
  for (let tries = 0; ; tries++) {
    const from = kit.logSize()
    serverCommand(`execute in minecraft:the_nether if loaded ${x - 15} ${y} ${z - 15} if loaded ${x + 15} ${y} ${z + 21}`)
    const said = await kit.waitForLog(/Test (passed|failed)/, 10, from).catch(() => null)
    if (said && said[1] === 'passed') break
    if (tries >= 30) throw new Error('the Offworld chunks never loaded')
    await kit.sleep(1000)
  }
  // One fill, under the 32768 blocks one takes: split, a hollow fill would leave floors between.
  p.fill(x - 15, y - 5, z - 15, x + 15, y + 19, z + 21, 'glass', 'hollow', n)
  p.fill(x - 14, y - 4, z - 14, x + 14, y - 2, z + 20, 'stone', '', n)
  p.fill(x - 14, y - 1, z - 14, x + 14, y - 1, z + 20, 'grass_block', '', n)
  for (let i = -12; i <= 12; i += 5) {
    p.setblock(x + i, y, z - 12, 'mossy_cobblestone', n)
    p.setblock(x + i, y + 1, z - 12, 'mossy_stone_bricks', n)
    p.setblock(x - 12, y, z + i + 6, 'cracked_stone_bricks', n)
    p.setblock(x + 12, y, z + i + 6, 'mossy_cobblestone_wall', n)
  }
  p.setblock(x + 8, y, z + 14, 'campfire[lit=true]', n)
  p.setblock(x + 9, y, z + 14, 'oak_log[axis=x]', n)
  // Its bottom row is in the grass, and a build is refused over anything but air or its own blocks.
  const { min, max } = kit.gateAt('Standard', x, y, z).frame
  p.fill(min.x, min.y, min.z, max.x, max.y, max.z, 'air', '', n)
  serverCommand(`wx gate build Standard Offworld world_nether ${x} ${y} ${z} south`)
  p.sign(x + 3, y + 1, z + 2, 'south', [['OFFWORLD', 'aqua'], 'P3X-888'], { where: n })
  // Offworld's beam pad.
  p.fill(x + 5, y - 1, z + 7, x + 7, y - 1, z + 9, 'sea_lantern', '', n)
}

module.exports = { HUB, SPAWN, BOARD, BOARD_Y, OFFWORLD, buildHub, buildOffworld }
