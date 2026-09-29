'use strict'
// The beam bay: Asgard-clean, white quartz and a floor of sea lanterns under a glass roof open to
// the sky, with a marked landing pad for each destination. Its panel picks the pad (or Offworld,
// in the nether), which way the traveller should face on landing, and who travels; Run saves the
// destination there, facing that way, and beams the bot, a horse under it, or it and its wolf.

const kit = require('../kit')
const p = require('./pieces')

const { v, sleep, serverCommand, narrate, teleport, messages, waitFor } = kit
const { FLOOR, FEET } = p

const ROOM = { x0: -100, z0: -62, x1: -50, z1: -8, top: -45 }
const PADS = {
  Alpha: { x: -88, y: FEET, z: -44, colour: 'light_blue' },
  Beta: { x: -62, y: FEET, z: -44, colour: 'lime' },
  Gamma: { x: -75, y: FEET, z: -54, colour: 'magenta' }
}
// Offworld's pad is in the nether clearing, beside its gate.
const OFFWORLD_PAD = { x: 6, y: 65, z: 8 }
const LAUNCH = { x: -75, z: -30 }
// Bukkit's yaw for each facing.
const YAW = { south: 0, west: 90, north: 180, east: -90 }

const options = [
  { key: 'dest', label: 'Land on', values: ['Alpha', 'Beta', 'Gamma', 'Offworld'] },
  { key: 'facing', label: 'Facing', values: ['north', 'east', 'south', 'west'] },
  { key: 'traveller', label: 'Traveller', values: ['bot', 'horse', 'wolf'] }
]

function build () {
  const { x0, z0, x1, z1, top } = ROOM
  p.room(x0, z0, x1, z1, top, { wall: 'quartz_block', floor: 'smooth_quartz', ceiling: 'glass' })
  p.fill(x0 + 1, FLOOR - 3, z0 + 1, x1 - 1, FLOOR - 1, z1 - 1, 'stone')
  // A floor of sea lanterns set in quartz.
  for (let x = x0 + 2; x < x1; x += 3) {
    for (let z = z0 + 2; z < z1; z += 3) p.setblock(x, FLOOR, z, 'sea_lantern')
  }
  p.fill(x0, top - 1, z0, x1, top - 1, z0, 'quartz_pillar')
  p.fill(x0, top - 1, z1, x1, top - 1, z1, 'quartz_pillar')
  // A marked landing pad for each destination, three by three, named on a post beside it.
  for (const [label, pad] of Object.entries(PADS)) {
    p.fill(pad.x - 1, FLOOR, pad.z - 1, pad.x + 1, FLOOR, pad.z + 1, `${pad.colour}_concrete`)
    p.setblock(pad.x, FLOOR, pad.z, 'sea_lantern')
    p.setblock(pad.x + 3, FEET, pad.z, 'quartz_pillar')
    p.sign(pad.x + 3, FEET, pad.z + 1, 'south', [['PAD', 'aqua'], [label.toUpperCase(), 'white']])
  }
  // Where a traveller stands to be beamed.
  p.fill(LAUNCH.x - 1, FLOOR, LAUNCH.z - 1, LAUNCH.x + 1, FLOOR, LAUNCH.z + 1, 'white_concrete')
  p.sign(LAUNCH.x + 3, FEET + 1, LAUNCH.z + 3, 'south', [['BEAM BAY', 'aqua'], 'Asgard', 'transporter'])
}

/** The pad a destination names, and whether it is in the nether. */
function padFor (dest) {
  if (dest === 'Offworld') return { ...OFFWORLD_PAD, nether: true }
  return { ...PADS[dest], nether: false }
}

/** Saves LabBeam where the bot stands on `pad`, facing `facing`, as an admin does. */
async function saveDestination (pad, facing) {
  const yaw = YAW[facing]
  if (pad.nether) {
    kit.inNether(`tp ${kit.name} ${pad.x + 0.5} ${pad.y} ${pad.z + 0.5} ${yaw} 0`)
    await waitFor(() => kit.inTheNether() && kit.near(v(pad.x + 0.5, pad.y, pad.z + 0.5), 0.5), 15, 'arriving on the Offworld pad')
    await sleep(1000)
  } else {
    await teleport(pad.x + 0.5, pad.y, pad.z + 0.5, yaw)
  }
  messages()
  kit.bot.chat('/wormhole beam admin remove LabBeam')
  await sleep(800)
  kit.bot.chat('/wormhole beam admin set LabBeam')
  await sleep(1500)
  const said = messages()
  if (!said.some((line) => line.includes('set to your current location'))) throw new Error(`LabBeam was not saved: ${JSON.stringify(said)}`)
  narrate(`Saved LabBeam on the pad, facing ${facing}`)
}

async function run (lab, values) {
  const pad = padFor(values.dest)
  const spot = v(pad.x + 0.5, pad.y, pad.z + 0.5)
  for (const kind of ['horse', 'wolf']) {
    serverCommand(`kill @e[type=${kind},x=${ROOM.x0},y=${FLOOR - 3},z=${ROOM.z0},dx=${ROOM.x1 - ROOM.x0},dy=30,dz=${ROOM.z1 - ROOM.z0}]`)
    kit.inNether(`kill @e[type=${kind},x=${OFFWORLD_PAD.x},y=${OFFWORLD_PAD.y},z=${OFFWORLD_PAD.z},distance=..20]`)
  }
  await saveDestination(pad, values.facing)
  await teleport(LAUNCH.x + 0.5, FEET, LAUNCH.z + 0.5, 180)

  if (values.traveller === 'horse') {
    const horse = await kit.saddledHorse(v(LAUNCH.x + 0.5, FEET, LAUNCH.z + 0.5))
    narrate(`Beaming, on the horse, to ${values.dest}`)
    messages()
    kit.bot.chat('/wormhole beam to LabBeam')
    const landed = () => kit.bot.vehicle === horse && kit.inTheNether() === pad.nether && kit.bot.vehicle.position.distanceTo(spot) < 1.5
    await waitFor(landed, 30, () => `landing on ${values.dest} on the horse (${kit.describeRide()}; heard ${JSON.stringify(kit.heard)})`)
    await sleep(2000)
    if (!landed()) throw new Error(`not on the horse on ${values.dest} a moment after landing (${kit.describeRide()})`)
    await kit.getOff()
    return `horse landed on ${values.dest}`
  }

  let wolf = null
  let tag = null
  if (values.traveller === 'wolf') {
    serverCommand('wx config pets-follow-owner true')
    tag = `labbeamwolf${Date.now() % 100000}`
    wolf = await kit.tamedWolf(v(LAUNCH.x - 2.5, FEET, LAUNCH.z + 0.5), tag)
    await kit.waitForWolf(wolf, tag, 10, 10, 'the wolf coming near before beaming')
  }
  narrate(`Beaming to ${values.dest}`)
  await kit.beamTo('LabBeam', spot, pad.nether)
  if (kit.yawOff(YAW[values.facing]) > 0.1) {
    throw new Error(`landed on ${values.dest} facing ${kit.bot.entity.yaw.toFixed(2)} radians, not ${values.facing}`)
  }
  narrate(`Landed on ${values.dest}, facing ${values.facing}`)
  if (wolf) {
    await kit.waitForWolf(wolf, tag, 5, 8, `the wolf arriving beside the bot on ${values.dest}`)
    narrate('The wolf came too')
  }
  if (pad.nether) await teleport(LAUNCH.x + 0.5, FEET, LAUNCH.z + 0.5, 180)
  return `landed on ${values.dest} facing ${values.facing}`
}

module.exports = {
  name: 'beam',
  title: 'BEAMS',
  options,
  panel: { x: -90, z: -20, facing: 'south' },
  vantage: [LAUNCH.x, FEET + 6, -14, 180, 25],
  build,
  run,
  async reset () {
    await teleport(LAUNCH.x + 0.5, FEET, LAUNCH.z + 0.5, 180)
    messages()
    kit.bot.chat('/wormhole beam admin remove LabBeam')
    await sleep(800)
    for (const kind of ['horse', 'wolf']) {
      serverCommand(`kill @e[type=${kind},x=${ROOM.x0},y=${FLOOR - 3},z=${ROOM.z0},dx=${ROOM.x1 - ROOM.x0},dy=30,dz=${ROOM.z1 - ROOM.z0}]`)
    }
  },
  OFFWORLD_PAD,
  ROOM
}
