'use strict'
// The ring room: a captured Goa'uld chamber of sandstone and gold. Its panel picks the ring
// pattern, the slab it is laid in, fast or slow, and how far apart the pair stands; Run lays the
// pair, links it, and walks the bot into the first ring to come out in the second. A pad beside
// it takes two circles you lay yourself, which Run there has the bot make a pair and ride.

const kit = require('../kit')
const p = require('./pieces')

const { v, sleep, serverCommand, narrate, teleport, messages } = kit
const { FLOOR, FEET } = p

const ROOM = { x0: 50, z0: -62, x1: 100, z1: -8, top: -45 }
const A = { x: 62, z: -36 }
// Where the pair's second ring may stand: A plus the distance, east.
const ROW = { x0: 56, z0: -40, x1: 96, z1: -32 }
// The build-it-yourself pads, each marked by a gold block under its middle.
const PADS = [{ x: 62, z: -53 }, { x: 84, z: -53 }]
const PAD_AREA = { x0: 56, z0: -59, x1: 90, z1: -47 }

const options = [
  { key: 'pattern', label: 'Pattern', values: ['ODD', 'EVEN'] },
  { key: 'slab', label: 'Slab', values: ['smooth_stone', 'sandstone', 'quartz', 'oak', 'deepslate_tile', 'cut_copper'], show: (value) => value.replace('_', ' ') },
  { key: 'style', label: 'Style', values: ['CONCURRENT', 'SEQUENTIAL'], show: (value) => value.toLowerCase() },
  { key: 'distance', label: 'Apart', values: ['12', '20', '30'], show: (value) => `${value} blocks` }
]

function build () {
  const { x0, z0, x1, z1, top } = ROOM
  p.room(x0, z0, x1, z1, top, { wall: 'cut_sandstone', floor: 'smooth_sandstone', ceiling: 'sandstone' })
  p.fill(x0 + 1, FLOOR - 3, z0 + 1, x1 - 1, FLOOR - 1, z1 - 1, 'stone')
  // Gold trim along the top of the walls, and hieroglyph-style carving at eye height.
  p.fill(x0, top - 1, z0, x1, top - 1, z0, 'gold_block')
  p.fill(x0, top - 1, z1, x1, top - 1, z1, 'gold_block')
  for (let x = x0 + 2; x < x1; x += 2) {
    p.setblock(x, FEET + 1, z0, 'chiseled_sandstone')
    p.setblock(x, FEET + 2, z0, x % 4 === 0 ? 'chiseled_red_sandstone' : 'chiseled_sandstone')
  }
  for (let z = z0 + 2; z < z1; z += 2) {
    p.setblock(x1, FEET + 1, z, 'chiseled_sandstone')
    p.setblock(x0, FEET + 1, z, 'chiseled_sandstone')
  }
  // The raised platform the pair stands on, and braziers at its corners.
  p.fill(ROW.x0 - 1, FLOOR, ROW.z0 - 1, ROW.x1 + 1, FLOOR, ROW.z1 + 1, 'chiseled_sandstone')
  p.fill(ROW.x0, FLOOR, ROW.z0, ROW.x1, FLOOR, ROW.z1, 'smooth_sandstone')
  for (const [x, z] of [[ROW.x0 - 2, ROW.z0 - 2], [ROW.x1 + 2, ROW.z0 - 2], [ROW.x0 - 2, ROW.z1 + 2], [ROW.x1 + 2, ROW.z1 + 2]]) {
    p.setblock(x, FEET, z, 'gold_block')
    p.setblock(x, FEET + 1, z, 'campfire[lit=true]')
  }
  for (let x = x0 + 4; x < x1; x += 8) {
    p.setblock(x, FEET + 3, z0 + 1, 'wall_torch[facing=south]')
    p.setblock(x, FEET + 3, z1 - 1, 'wall_torch[facing=north]')
  }
  // The pads: bare floor with a gold block under each middle, inside hazard striping.
  p.fill(PAD_AREA.x0, FLOOR, PAD_AREA.z0, PAD_AREA.x1, FLOOR, PAD_AREA.z1, 'smooth_stone')
  p.hazardBorder(PAD_AREA.x0, PAD_AREA.z0, PAD_AREA.x1, PAD_AREA.z1)
  for (const pad of PADS) p.setblock(pad.x, FLOOR, pad.z, 'gold_block')
  p.sign(PADS[0].x + 11, FEET + 1, PAD_AREA.z1 + 1, 'south', [['RING PADS', 'gold'], 'Lay a circle', 'round each gold', 'block, then Run'])
  p.sign(A.x - 4, FEET + 1, ROW.z1 + 3, 'south', [['TRANSPORT', 'gold'], ['RINGS', 'gold'], 'Goa uld, stolen'])
}

/** Takes up any ring pair standing at `at` (a player's feet in it), as its owner would. */
async function removeRingAt (at) {
  await teleport(at.x + 0.5, FEET, at.z + 0.5, 0)
  messages()
  kit.bot.chat('/wormhole ring remove')
  await sleep(1500)
}

/** Takes up the pair and any slabs on the platform, leaving it bare. */
async function clearRow () {
  await removeRingAt(A)
  // The second ring may be at any distance; one removal takes both ends.
  p.fill(ROW.x0, FEET, ROW.z0, ROW.x1, FEET + 4, ROW.z1, 'air')
}

async function clearPads () {
  for (const pad of PADS) await removeRingAt(pad)
  p.fill(PAD_AREA.x0 + 1, FEET, PAD_AREA.z0 + 1, PAD_AREA.x1 - 1, FEET + 4, PAD_AREA.z1 - 1, 'air')
}

/** Lays a ring's circle of bottom slabs round the anchor (x, z). */
function layCircle (x, z, pattern, slab) {
  for (const [dx, dz] of kit.ringPerimeter(pattern)) p.setblock(x + dx, FEET, z + dz, `${slab}_slab[type=bottom]`)
}

/** Sets the style of the ring the bot stands in, as its owner would. */
async function setStyle (at, style) {
  await teleport(at.x + 0.5, FEET, at.z + 0.5, 0)
  messages()
  kit.bot.chat(`/wormhole ring edit style ${style === 'SEQUENTIAL' ? 'slow' : 'fast'}`)
  await sleep(1500)
  console.log(`  ${JSON.stringify(messages())}`)
}

async function run (lab, values) {
  await clearRow()
  const b = { x: A.x + Number(values.distance), z: A.z }
  narrate(`Laying two ${values.pattern} circles of ${values.slab} slabs, ${values.distance} blocks apart`)
  layCircle(A.x, A.z, values.pattern, values.slab)
  layCircle(b.x, b.z, values.pattern, values.slab)
  await sleep(1000)
  const from = kit.logSize()
  serverCommand(`wx ring build world ${A.x} ${FEET} ${A.z} ${b.x} ${FEET} ${b.z}`)
  const made = await kit.waitForLog(/Ring pair ([0-9a-f]{8}) is live/, 20, from).catch(() => {
    throw new Error(`the pair was not made; the console said: ${kit.logSince(from).split('\n').filter((l) => /ring|Ring/.test(l)).slice(-3).join(' / ')}`)
  })
  narrate(`Ring pair ${made[1]} is live`)
  if (values.style !== 'CONCURRENT') {
    await setStyle(A, values.style)
    await setStyle(b, values.style)
  }
  await kit.ringInto(v(A.x + 0.5, FEET, A.z + 0.5), v(b.x + 0.5, FEET, b.z + 0.5), 'the far ring')
  return `through, ${values.pattern} ${values.slab}, ${values.style.toLowerCase()}`
}

/** The pads' Run: the bot stands in each circle a player laid, makes them a pair, and rides it. */
async function runPad () {
  const made = []
  for (const pad of PADS) {
    await teleport(pad.x + 0.5, FEET, pad.z + 0.5, 0)
    messages()
    narrate('Making the circle on this pad a ring')
    kit.bot.chat('/wormhole ring create')
    await sleep(2000)
    made.push(...messages())
  }
  if (!made.some((line) => line.includes('is live'))) throw new Error(`the pads did not make a pair: ${JSON.stringify(made)}`)
  const [a, b] = PADS
  await kit.ringInto(v(a.x + 0.5, FEET, a.z + 0.5), v(b.x + 0.5, FEET, b.z + 0.5), 'the other pad')
  return 'pad rings through'
}

module.exports = {
  name: 'ring',
  title: 'RINGS',
  options,
  panel: { x: 56, z: -20, facing: 'south' },
  padPanel: { x: 70, z: -44, facing: 'south' },
  vantage: [A.x + 10, FEET + 4, -14, 180, 20],
  build,
  run,
  runPad,
  async reset () {
    await clearRow()
    await clearPads()
  },
  PADS,
  layCircle,
  ROOM
}
