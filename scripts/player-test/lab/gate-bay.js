'use strict'
// The stargate bay: the gate room of the lab. Its panel picks a shape, a material group, a portal,
// an iris, how to dial and who travels; Run has the bot build that gate as a player does, dial the
// arrival gate, Midway across the room or Offworld in the nether, and take the traveller through.
// Beside it is a pad to build a gate yourself, which Run there has the bot complete and dial.

const kit = require('../kit')
const p = require('./pieces')

const { v, sleep, serverCommand, narrate, teleport, nameAt, waitFor, heard, messages } = kit
const { FLOOR, FEET } = p

// The room, and where things stand in it.
const ROOM = { x0: -44, z0: -72, x1: 44, z1: -8, top: -30 }
// The lab gate's DHD block, facing south; the gate is built north of it.
const LAB = { x: 8, z: -44 }
// Everything a lab gate of any shape can reach, cleared before each build.
const LAB_AREA = { x0: -10, y0: FLOOR - 2, z0: -56, x1: 17, y1: ROOM.top - 1, z1: -43 }
// The lanes a boat, minecart or horse runs down in front of it.
const LANES = { x0: -4, z0: -48, x1: 16, z1: -25 }
const MIDWAY = { x: -24, z: -44 }
const OFFWORLD = { x: 0, y: 65, z: 0 }
// The build-it-yourself pad, and its own Run and Reset.
const PAD = { x0: 22, z0: -70, x1: 42, z1: -38 }

const groups = {
  Standard: 'obsidian',
  Atlantis: 'lapis_block',
  Universe: 'polished_blackstone',
  MilkyWay: 'deepslate'
}

const options = [
  { key: 'shape', label: 'Shape', values: ['Standard', 'Large', 'Grand', 'Massive', 'Minimal', 'Horizontal', 'StandardSignDial', 'MinimalSignDial', 'HorizontalSignDial'], show: (value) => value.replace('SignDial', ' + sign') },
  { key: 'group', label: 'Materials', values: Object.keys(groups) },
  { key: 'portal', label: 'Portal', values: ['default', 'LAVA', 'NETHER_PORTAL', 'WATER'], show: (value) => value.toLowerCase().replace('_', ' ') },
  { key: 'iris', label: 'Far iris', values: ['none', 'shut, code', 'shut, no code'] },
  { key: 'dial', label: 'Dial by', values: ['DHD', 'console', 'sign', 'redstone'] },
  { key: 'traveller', label: 'Traveller', values: ['walk', 'boat', 'minecart', 'horse', 'wolf'] },
  { key: 'dest', label: 'Destination', values: ['Midway', 'Offworld'] }
]

/** The arrival gates, as kit.gateAt sees them, and the world each is in. */
function arrivalGate (name) {
  if (name === 'Offworld') return { label: 'Offworld', nether: true, ...kit.gateAt('Standard', OFFWORLD.x, OFFWORLD.y, OFFWORLD.z) }
  return { label: 'Midway', nether: false, ...kit.gateAt('Standard', MIDWAY.x, FEET, MIDWAY.z) }
}

/** The lab gate of a shape, with its DHD block sunk so its arrival point is on the room's floor. */
function labGate (shape) {
  const rise = kit.shapes[shape].arrival[1]
  const g = kit.gateAt(shape, LAB.x, FEET - rise, LAB.z)
  // Pressed from a block and a half south of the button, clear of the DHD, not from the side.
  return { ...g, stand: [LAB.x + 0.5, FEET, LAB.z + 2.5] }
}

/** Moves the bot to a spot in the overworld or the nether. */
async function goTo (x, y, z, yaw, nether) {
  if (!nether) return teleport(x, y, z, yaw)
  kit.inNether(`tp ${kit.name} ${x} ${y} ${z} ${yaw} 0`)
  await waitFor(() => kit.inTheNether() && kit.near(v(x, y, z), 0.5), 15, `arriving in the nether at ${x} ${y} ${z}`)
  await sleep(1000)
}

function build (lab) {
  const { x0, z0, x1, z1, top } = ROOM
  p.room(x0, z0, x1, z1, top, { wall: 'gray_concrete', floor: 'polished_andesite', ceiling: 'light_gray_concrete' })
  // Solid stone under the floor, so a gate can be sunk into it.
  p.fill(x0 + 1, FLOOR - 3, z0 + 1, x1 - 1, FLOOR - 1, z1 - 1, 'stone')
  // Iron ribs up the walls, every eight blocks.
  for (let x = x0 + 4; x < x1; x += 8) {
    p.fill(x, FEET, z0, x, top - 1, z0, 'iron_block')
    p.fill(x, FEET, z1, x, top - 1, z1, 'iron_block')
  }
  // Ceiling lights, lit by redstone blocks in the ceiling, which a wormhole dims.
  lab.gateLights = []
  for (let x = x0 + 6; x < x1 - 2; x += 10) {
    for (let z = z0 + 6; z < z1 - 2; z += 10) {
      p.setblock(x, top - 1, z, 'redstone_lamp')
      lab.gateLights.push({ x, y: top, z })
    }
  }
  p.setLamps(lab.gateLights, true)
  // Hazard striping round the lab gate's chamber and the lanes in front of it.
  p.hazardBorder(LAB_AREA.x0 - 1, LAB_AREA.z0 - 1, LAB_AREA.x1 + 1, LANES.z1 + 1)
  p.sign(LAB.x - 12, FEET + 1, LANES.z1 + 2, 'south', [['TEST CHAMBER', 'gold'], 'Keep clear', 'while a run', 'is going'])

  // Midway, the arrival gate across the room, and a line of rails out of it for the minecart.
  // Its bottom row is in the floor, and a build is refused over anything but air or its own blocks.
  const { min, max } = arrivalGate('Midway').frame
  p.fill(min.x, min.y, min.z, max.x, max.y, max.z, 'air')
  serverCommand(`wx gate build Standard Midway world ${MIDWAY.x} ${FEET} ${MIDWAY.z} south`)
  p.sign(MIDWAY.x - 1, FEET + 6, MIDWAY.z + 2, 'south', [['MIDWAY', 'aqua'], 'arrival gate'])

  // The control room: a gallery over the panel looking north through tinted glass, with the seven
  // chevron lamps over its window and a ladder up the south wall.
  const cy = FEET + 6
  p.fill(-12, cy - 1, -22, 12, cy - 1, -9, 'smooth_stone')
  p.fill(-12, cy, -22, 12, cy + 4, -22, 'tinted_glass')
  p.fill(-12, cy + 5, -22, 12, cy + 5, -9, 'gray_concrete')
  p.fill(-12, cy, -21, -12, cy + 4, -9, 'gray_concrete')
  p.fill(12, cy, -21, 12, cy + 4, -9, 'gray_concrete')
  lab.chevrons = p.lampStrip(-6, 7, cy + 6, -22, 1)
  p.fill(-11, cy - 1, -9, -11, cy - 1, -9, 'air')
  p.fill(-11, FEET, -9, -11, cy, -9, 'ladder[facing=north]')
  p.sign(0, cy + 1, -21, 'south', [['CONTROL ROOM', 'aqua'], 'Level 28'])

  // The Atlantis corner: a chamber behind a glass wall with the sea on the other side.
  p.fill(x0 + 1, FLOOR, z0 + 1, x0 + 12, FLOOR, z0 + 12, 'prismarine_bricks')
  p.fill(x0 + 1, FEET, z0 + 1, x0 + 12, top - 1, z0 + 4, 'water')
  p.fill(x0 + 1, FEET, z0 + 5, x0 + 12, top - 1, z0 + 5, 'glass')
  for (let x = x0 + 2; x <= x0 + 11; x += 3) {
    p.setblock(x, FLOOR, z0 + 2, 'sea_lantern')
    p.fill(x, FEET, z0 + 2, x, FEET + 3, z0 + 2, 'kelp_plant')
    p.setblock(x, FEET + 4, z0 + 2, 'kelp')
    p.setblock(x, FLOOR, z0 + 8, 'sea_lantern')
  }
  p.sign(x0 + 6, FEET + 1, z0 + 6, 'south', [['ATLANTIS', 'aqua'], 'Lantea, city', 'of the Ancients'])

  // A stable for the ridden trips and a kennel for the pets, by the east wall.
  p.fill(x1 - 12, FEET, z1 - 16, x1 - 1, FEET, z1 - 1, 'oak_fence', 'outline')
  p.fill(x1 - 11, FEET, z1 - 15, x1 - 2, FEET, z1 - 2, 'air')
  p.fill(x1 - 11, FLOOR, z1 - 15, x1 - 2, FLOOR, z1 - 9, 'hay_block')
  p.fill(x1 - 11, FLOOR, z1 - 7, x1 - 2, FLOOR, z1 - 2, 'coarse_dirt')
  p.fill(x1 - 11, FEET, z1 - 8, x1 - 2, FEET, z1 - 8, 'oak_fence')
  p.sign(x1 - 7, FEET + 1, z1 - 17, 'south', [['STABLE', 'gold'], 'and kennel'])

  // The MALP: a minecart with a chest on a short spur, which goes through first on a minecart run.
  p.fill(-40, FEET, -30, -40, FEET, -20, 'rail[shape=north_south]')
  serverCommand(`summon chest_minecart -39.5 ${FEET} -24.5 {Tags:["malp"],NoGravity:0b}`)
  p.sign(-38, FEET + 1, -20, 'south', [['M.A.L.P.', 'gold'], 'Mobile Analytic', 'Laboratory Probe'])

  // The build-it-yourself pad.
  p.fill(PAD.x0, FLOOR, PAD.z0, PAD.x1, FLOOR, PAD.z1, 'smooth_stone')
  p.fill(PAD.x0 + 1, FLOOR - 3, PAD.z0 + 1, PAD.x1 - 1, FLOOR - 1, PAD.z1 - 1, 'stone')
  p.hazardBorder(PAD.x0, PAD.z0, PAD.x1, PAD.z1)
  p.sign(PAD.x0 + 10, FEET + 1, PAD.z1 + 2, 'south', [['BUILD PAD', 'gold'], '/wormhole gate', 'build <shape>', 'then press Run'])
}

/**
 * Removes whatever the last run built in the lab gate's chamber, and puts the floor, the lanes and
 * the stone under them back as build() left them.
 */
async function clearLab (lab) {
  serverCommand('wx gate remove LabGate -destroy')
  for (const dest of ['Midway', 'Offworld']) {
    serverCommand(`wx gate force ${dest}`)
    serverCommand(`wx gate edit ${dest} idc -clear`)
  }
  await sleep(1500)
  const a = LAB_AREA
  p.fill(a.x0, FEET, a.z0, a.x1, a.y1, a.z1, 'air')
  p.fill(a.x0, a.y0, a.z0, a.x1, FLOOR - 1, a.z1, 'stone')
  p.fill(a.x0, FLOOR, a.z0, a.x1, FLOOR, a.z1, 'polished_andesite')
  p.fill(LANES.x0, FEET, LANES.z0, LANES.x1, FEET + 2, LANES.z1, 'air')
  p.fill(LANES.x0, FLOOR - 1, LANES.z0, LANES.x1, FLOOR - 1, LANES.z1, 'stone')
  p.fill(LANES.x0, FLOOR, LANES.z0, LANES.x1, FLOOR, LANES.z1, 'polished_andesite')
  // Midway's line, and the MALP back on its spur.
  const midway = arrivalGate('Midway')
  p.fill(midway.opening.x, FEET, midway.front, midway.opening.x, FEET, midway.front + 17, 'air')
  const offworld = arrivalGate('Offworld')
  p.fill(offworld.opening.x, offworld.walkY, offworld.front, offworld.opening.x, offworld.walkY, offworld.front + 17, 'air', '', 'nether')
  for (const kind of ['boat', 'oak_boat', 'minecart', 'chest_minecart', 'horse', 'wolf']) {
    serverCommand(`kill @e[type=${kind},x=${ROOM.x0},y=${FLOOR - 3},z=${ROOM.z0},dx=${ROOM.x1 - ROOM.x0},dy=40,dz=${ROOM.z1 - ROOM.z0}]`)
    kit.inNether(`kill @e[type=${kind},x=${OFFWORLD.x},y=${OFFWORLD.y},z=${OFFWORLD.z},distance=..30]`)
  }
  serverCommand(`summon chest_minecart -39.5 ${FEET} -24.5 {Tags:["malp"]}`)
  if (lab.chevrons) p.setLamps(lab.chevrons, false)
  if (lab.gateLights) p.setLamps(lab.gateLights, true)
  await sleep(1000)
}

/**
 * Builds a gate the way a player does: a DHD button hung on a block of the group's frame, `gate
 * build <shape> <group>` looking at it to stand the preview on it, `preview place`, and `gate
 * complete`. A sign-dialled shape gets its dial sign hung first, naming the gate on its top line.
 */
async function buildAsPlayer (g, name, group, idc) {
  const { x, y, z } = g.anchor
  const { min, max } = g.frame
  // The frame's cells must be clear for the preview to be placed, below the floor too.
  p.fill(min.x, min.y, min.z, max.x, max.y, max.z, 'air')
  // A sunk DHD gets a pit in the floor over its button, to reach it through.
  if (y < FEET) p.fill(x, y, z + 1, x, FLOOR, z + 1, 'air')
  p.setblock(x, y, z, groups[group])
  p.setblock(x, y, z + 1, 'stone_button[face=wall,facing=south]')
  await sleep(500)
  await teleport(x + 0.5, FEET, z + 2.5, 180)
  messages()
  narrate(`Standing a ${g.shape} preview in ${group} on the DHD`)
  await kit.bot.lookAt(g.button.offset(0.5, 0.5, 0.06), true)
  await sleep(300)
  kit.bot.chat(`/wormhole gate build ${g.shape} ${group}`)
  await sleep(1500)
  const shown = messages()
  if (!shown.some((line) => line.includes('on your DHD'))) throw new Error(`the preview was not stood on the DHD: ${JSON.stringify(shown)}`)
  narrate(`Placing the preview and naming it ${name}`)
  await kit.bot.lookAt(g.centre.offset(0.5, 0.5, 0.5), true)
  await sleep(300)
  kit.bot.chat('/wormhole gate preview place')
  await sleep(2000)
  if (g.sign) {
    p.sign(g.dial.x, g.dial.y, g.dial.z, 'south', [name], { waxed: false })
    await sleep(500)
  }
  kit.bot.chat(`/wormhole gate complete ${name}${idc ? ' idc=' + idc : ''}`)
  await sleep(2000)
  const said = messages()
  if (!said.some((line) => line.includes('successfully constructed'))) throw new Error(`${name} was not built: ${JSON.stringify(said)}`)
  // The floor back round an upright gate's foot; a flat gate's own layer is the floor.
  if (!g.flat && min.y <= FLOOR) p.fill(min.x, min.y, min.z, max.x, FLOOR, max.z, 'polished_andesite', 'replace air')
}

/** Right-clicks a dial sign until it shows `dest` selected, and returns once it does. */
async function selectOnSign (g, dest) {
  const bot = kit.bot
  const read = () => kit.signText(g.dial).catch(() => '')
  for (let click = 1; !(await read()).includes(`»${dest}«`); click++) {
    if (click > 12) throw new Error(`the dial sign never showed ${dest} selected; it reads ${await read()}`)
    narrate(`Turning the dial sign (${click})`)
    await bot.lookAt(g.dial.offset(0.5, 0.5, 0.95), true)
    await bot.activateBlock(bot.blockAt(g.dial))
    await sleep(700)
  }
  narrate(`The dial sign shows ${dest}`)
}

/**
 * Makes the far iris what the panel says: none, or a code and shut. The code is set from the
 * console, which hangs the far gate's lever; the bot shuts the iris at that lever.
 */
async function setFarIris (dest, iris) {
  if (iris === 'none') return
  const from = kit.logSize()
  serverCommand(`wx gate edit ${dest.label} idc 1234`)
  await kit.waitForLog(new RegExp(`IDC for gate: ${dest.label} is:1234`), 20, from)
  await goTo(...dest.stand, 180, dest.nether)
  const lever = kit.bot.blockAt(dest.lever)
  if (!lever || lever.name !== 'lever') throw new Error(`no iris lever at ${dest.lever} on ${dest.label}, but ${lever ? lever.name : 'an unloaded block'}`)
  narrate(`Shutting ${dest.label}'s iris at its lever`)
  await kit.bot.activateBlock(lever)
  await waitFor(() => nameAt(dest.opening) !== 'air', 10, () => `${dest.label}'s iris showing (it shows ${nameAt(dest.opening)})`)
  narrate(`${dest.label}'s iris is shut: ${nameAt(dest.opening)}`)
}

/** Lights the chevron lamps one by one until `open()` says the gate is open, or it is stopped. */
function chevronLamps (lab) {
  let lit = 0
  let stopped = false
  const timer = setInterval(() => {
    if (stopped || lit >= lab.chevrons.length) return
    p.setLamps([lab.chevrons[lit++]], true)
  }, 800)
  return {
    open () {
      stopped = true
      clearInterval(timer)
      p.setLamps(lab.chevrons, true)
      // The gate draws power: the room's lights dim while the wormhole is open.
      p.setLamps(lab.gateLights, false)
    },
    stop () {
      stopped = true
      clearInterval(timer)
      p.setLamps(lab.chevrons, false)
      p.setLamps(lab.gateLights, true)
    }
  }
}

/** Why a combination on the panel cannot be run, or null. */
function refusal (values) {
  const shape = kit.shapes[values.shape]
  if ((values.dial === 'sign' || values.dial === 'redstone') && !shape.sign) return `dialling by ${values.dial} needs a sign-dial shape, not ${values.shape}`
  if (values.iris === 'shut, code' && (values.dial === 'sign' || values.dial === 'redstone')) return 'a dial sign cannot give an iris code'
  if (shape.flat && ['boat', 'minecart', 'horse'].includes(values.traveller)) return `a ${values.traveller} cannot drop into a gate that lies flat`
  return null
}

/** The dial the panel asks for, as dialAndOpen takes it. */
function dialFor (lab, values, g, dest) {
  const code = values.iris === 'shut, code' ? ' 1234' : ''
  switch (values.dial) {
    case 'DHD':
      return dest.label + code
    case 'console':
      return async () => {
        narrate(`Dialling ${dest.label} from the console`)
        serverCommand(`wx gate dial LabGate ${dest.label}${code}`)
      }
    case 'sign':
      return async () => {
        await selectOnSign(g, dest.label)
        narrate('Pressing the DHD to dial what the sign shows')
        await kit.bot.activateBlock(kit.bot.blockAt(g.button))
      }
    case 'redstone':
      return async () => {
        await selectOnSign(g, dest.label)
        narrate('Pulling the lever on the redstone block')
        await kit.bot.activateBlock(kit.bot.blockAt(g.rd))
      }
  }
}

/** Takes the traveller the panel names through the open lab gate `g` to `dest`. */
async function travel (values, g, dest) {
  switch (values.traveller) {
    case 'walk':
      await teleport(g.arrival.x, g.walkY, g.front + 3.5, 180)
      await kit.walkThrough(g, dest.arrival, dest.label)
      return
    case 'boat':
      return kit.boatInto(g, dest, dest.label)
    case 'minecart': {
      // The MALP goes first, as it would.
      narrate('Sending the MALP through first')
      serverCommand(`tp @e[type=chest_minecart,tag=malp,limit=1] ${g.arrival.x} ${g.walkY} ${g.front + 10.5}`)
      await sleep(4000)
      return kit.minecartInto(g, dest, dest.label)
    }
    case 'horse': {
      const horse = await kit.saddledHorse(v(g.arrival.x, g.walkY, g.front + 7.5))
      return kit.rideInto(horse, g, dest, dest.label)
    }
    case 'wolf': {
      serverCommand('wx config pets-follow-owner true')
      const tag = `labwolf${Date.now() % 100000}`
      const wolf = await kit.tamedWolf(v(g.arrival.x - 2, g.walkY, g.front + 4.5), tag)
      await teleport(g.arrival.x, g.walkY, g.front + 3.5, 180)
      return kit.walkInWithWolf(wolf, tag, g, dest, dest.label, dest.nether)
    }
  }
}

/** One run of the stargate bay, with the panel's `values`. Returns a line for the mission board. */
async function run (lab, values) {
  const why = refusal(values)
  if (why) throw Object.assign(new Error(`Not run: ${why}.`), { notRun: true })
  await clearLab(lab)
  const g = labGate(values.shape)
  const dest = arrivalGate(values.dest)
  await buildAsPlayer(g, 'LabGate', values.group)
  if (values.portal !== 'default') {
    const from = kit.logSize()
    serverCommand('wx gate edit LabGate custom true')
    serverCommand(`wx gate edit LabGate portal ${values.portal}`)
    await kit.waitForLog(new RegExp(`LabGate portal material set to: ${values.portal}`), 20, from)
  }
  if (values.dial === 'redstone') {
    serverCommand('wx gate edit LabGate redstone true')
    p.setblock(g.rd.x, g.rd.y, g.rd.z, 'lever[face=floor,facing=south,powered=false]')
  }
  await setFarIris(dest, values.iris)
  // Lanes lie on the lab's floor and in the air over it, never in the gate's own blocks.
  if (values.traveller === 'boat') kit.layBoatLane(g, (command) => serverCommand(`${command} replace polished_andesite`))
  if (values.traveller === 'minecart') {
    kit.layRails(g, true, (command) => serverCommand(command.startsWith('fill') ? `${command} replace air` : command))
    kit.layRails(dest, false, (command) => p.run(command, dest.nether ? 'nether' : 'overworld'))
  }
  await sleep(1000)

  const lamps = chevronLamps(lab)
  try {
    if (values.iris === 'shut, no code') {
      // No code for a shut iris: the dial must be refused, and nothing opens.
      await teleport(...g.stand, 180)
      const dial = dialFor(lab, values, g, dest)
      messages()
      if (typeof dial === 'function') await dial()
      else {
        await kit.bot.activateBlock(kit.bot.blockAt(g.button))
        await sleep(1000)
        kit.bot.chat(`/dial ${dial}`)
      }
      await sleep(6000)
      if (nameAt(g.opening) !== 'air') throw new Error(`LabGate opened (${nameAt(g.opening)}) onto a shut iris without the code`)
      const said = heard.filter((line) => /Remote Iris is active/.test(line))
      narrate(`Refused, as it should be${said.length ? ': "Remote Iris is active"' : ''}`)
      return 'refused: iris shut'
    }
    const { portal } = await kit.dialAndOpen(g, 'LabGate', dialFor(lab, values, g, dest))
    lamps.open()
    const expected = values.portal === 'default' ? null : values.portal.toLowerCase()
    if (expected && portal !== expected) throw new Error(`LabGate's opening filled with ${portal}, not ${expected}`)
    await travel(values, g, dest)
    kit.getOff().catch(() => {})
    await kit.waitForShut(dest.opening, dest.label)
    return `through to ${dest.label}`
  } finally {
    lamps.stop()
    if (values.dial === 'redstone') p.setblock(g.rd.x, g.rd.y, g.rd.z, 'lever[face=floor,facing=south,powered=false]')
    if (kit.inTheNether()) await teleport(g.arrival.x, FEET, LANES.z1 - 1, 180)
  }
}

/**
 * The pad's Run: whatever gate a player built on the pad, the bot completes as LabPad by clicking
 * its DHD button, dials Midway from it, and walks through. The button's facing says which way the
 * gate faces, and the blocks that fill when it opens say where the opening is.
 */
async function runPad (lab) {
  const bot = kit.bot
  await teleport((PAD.x0 + PAD.x1) / 2 + 0.5, FEET, PAD.z1 + 2.5, 180)
  const buttons = bot.findBlocks({
    matching: (block) => block.name.endsWith('_button'),
    maxDistance: 40,
    count: 20
  }).filter((pos) => pos.x > PAD.x0 && pos.x < PAD.x1 && pos.z > PAD.z0 && pos.z < PAD.z1)
  if (buttons.length !== 1) throw new Error(`the pad should hold one gate with one DHD button; it has ${buttons.length} buttons`)
  const button = bot.blockAt(buttons[0])
  const facing = button.getProperties().facing
  const [fx, fz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[facing]
  serverCommand('wx gate remove LabPad')
  await sleep(500)
  await teleport(button.position.x + 0.5 + fx * 2, button.position.y, button.position.z + 0.5 + fz * 2, 180)
  messages()
  narrate('Completing the pad gate as LabPad')
  bot.chat('/wormhole gate complete LabPad')
  await sleep(1500)
  await bot.lookAt(button.position.offset(0.5, 0.5, 0.5), true)
  let said = messages()
  // A player with the preview pending completes it at once; anyone else is asked to click its DHD.
  if (!said.some((line) => line.includes('successfully constructed'))) {
    narrate('Clicking its DHD to finish it')
    await bot.activateBlock(button)
    await sleep(2000)
    said = said.concat(messages())
  }
  if (!said.some((line) => line.includes('successfully constructed'))) throw new Error(`the pad gate was not completed: ${JSON.stringify(said)}`)

  // Where it opens: every block in the pad that fills with portal once it is dialled.
  const filled = []
  const watch = (before, after) => {
    const at = after.position
    if (at.x > PAD.x0 && at.x < PAD.x1 && at.z > PAD.z0 && at.z < PAD.z1 && ['water', 'lava', 'nether_portal'].includes(after.name)) filled.push(at.clone())
  }
  bot.on('blockUpdate', watch)
  try {
    narrate('Pressing the DHD and dialling Midway')
    await bot.activateBlock(bot.blockAt(button.position))
    await sleep(1000)
    bot.chat('/dial Midway')
    await waitFor(() => filled.length > 0, 60, () => `the pad gate opening (heard: ${JSON.stringify(heard)})`)
    await sleep(2000)
  } finally {
    bot.removeListener('blockUpdate', watch)
  }
  const mid = filled.reduce((sum, at) => sum.plus(at), v(0, 0, 0)).scaled(1 / filled.length)
  const flat = filled.every((at) => at.y === filled[0].y)
  const midway = arrivalGate('Midway')
  if (flat) {
    await teleport(mid.x + 0.5 + fx * 3, filled[0].y + 1, mid.z + 0.5 + fz * 3, 180)
    await kit.walk(v(mid.x + 0.5, filled[0].y + 1, mid.z + 0.5), () => kit.near(midway.arrival, 1.5), 15)
  } else {
    const floor = Math.min(...filled.map((at) => at.y))
    await teleport(mid.x + 0.5 + fx * 4, floor, mid.z + 0.5 + fz * 4, 180)
    await kit.walk(v(mid.x + 0.5 - fx, floor, mid.z + 0.5 - fz), () => kit.near(midway.arrival, 1.5), 15)
  }
  await waitFor(() => kit.near(midway.arrival, 1.5), 5, 'arriving at Midway')
  narrate('Came out at Midway')
  await kit.waitForShut(midway.opening, 'Midway')
  return 'pad gate through to Midway'
}

/** Takes the pad back to bare floor, the LabPad gate and all. */
async function resetPad () {
  serverCommand('wx gate remove LabPad -destroy')
  await sleep(1000)
  p.fill(PAD.x0 + 1, FEET, PAD.z0 + 1, PAD.x1 - 1, ROOM.top - 1, PAD.z1 - 1, 'air')
  p.fill(PAD.x0 + 1, FLOOR - 3, PAD.z0 + 1, PAD.x1 - 1, FLOOR - 1, PAD.z1 - 1, 'stone')
  p.fill(PAD.x0 + 1, FLOOR, PAD.z0 + 1, PAD.x1 - 1, FLOOR, PAD.z1 - 1, 'smooth_stone')
}

module.exports = {
  name: 'gate',
  title: 'STARGATE',
  options,
  panel: { x: -10, z: -20, facing: 'south' },
  padPanel: { x: 30, z: -34, facing: 'south' },
  pad: PAD,
  vantage: [LAB.x - 4, FEET + 6, -16, 180, 15],
  build,
  run,
  runPad,
  async reset (lab) {
    await clearLab(lab)
    await resetPad()
  },
  // For the self-test, which stands a preview on the pad as a player would.
  PAD,
  LAB,
  arrivalGate,
  labGate,
  refusal,
  setFarIris,
  OFFWORLD,
  ROOM
}
