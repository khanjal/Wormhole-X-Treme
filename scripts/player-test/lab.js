'use strict'
// The lab: a test world laid out as a laboratory, with a bay for gates, rings, beams and mirrors,
// where you pick what to test on a panel of signs, press Run, and watch the bot make the trip.
//   node lab.js <minecraft-version>
// Started by scripts/run-lab.js once the server is up. It builds the lab from console commands on the fresh
// world, then waits: for presses on the panels, which it hears in chat, and for "stop" in chat.
//
// LAB_SELFTEST=1 has the bot press the panels itself, as a watcher would, through a run of every
// bay with a spread of settings, a build pad each, and every Reset, and exits failing if any did.
// LAB_SELFTEST names a comma-separated list of bays to test only those.
//
// Every trip goes through kit.js, the same code the CI trips (journeys.js) use; the lab only lays
// out the world and says which gate, ring, beam or mirror to take.

const kit = require('./kit')
const p = require('./lab/pieces')
const { Panel } = require('./lab/panel')
const world = require('./lab/world')
const gateBay = require('./lab/gate-bay')
const ringBay = require('./lab/ring-bay')
const beamBay = require('./lab/beam-bay')
const mirrorBay = require('./lab/mirror-bay')

const { v, sleep, serverCommand, say, narrate, teleport } = kit
const { FLOOR, FEET } = p

const version = process.argv[2]
const selftest = process.env.LAB_SELFTEST && process.env.LAB_SELFTEST !== '0' ? process.env.LAB_SELFTEST : null

if (!version || !process.env.BOOT_CONSOLE || !process.env.BOOT_LOG) {
  console.error('usage: BOOT_CONSOLE=<file> BOOT_LOG=<file> node lab.js <minecraft-version>')
  process.exit(2)
}
kit.configure({ observe: true, version })

const bays = [gateBay, ringBay, beamBay, mirrorBay]
// The overworld the lab covers, kept loaded so its buttons and command blocks work with nobody
// near them and the console can build in it.
const AREA = { x0: -112, z0: -80, x1: 112, z1: 80 }

const lab = {
  panels: {},
  busy: null,
  results: [],
  // What the last press of each switch did, for the self-test to wait on.
  pressed: 0,
  finished: 0
}

/** A panel's bay, and whether it is a build pad's. */
function bayOf (key) {
  const pad = key.endsWith('pad')
  return { bay: bays.find((b) => b.name === (pad ? key.slice(0, -3) : key)), pad }
}

function clock () {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** Writes a run's outcome on the mission board in the hub. */
function missionBoard (bay, outcome, detail) {
  const at = world.BOARD[bay.name]
  const colour = { PASS: 'green', FAIL: 'red', 'NOT RUN': 'yellow' }[outcome]
  p.rewrite(at.x, world.BOARD_Y, at.z, [[bay.name.toUpperCase(), 'aqua'], [outcome, colour], detail.slice(0, 15), clock()])
}

/** Three blasts of the klaxon round a bay's panel. */
async function klaxon (panel) {
  const at = panel.standing()
  for (let i = 0; i < 3; i++) {
    serverCommand(`playsound minecraft:block.note_block.didgeridoo master @a ${at.x} ${at.y} ${at.z} 4 0.6`)
    serverCommand(`playsound minecraft:block.bell.use master @a ${at.x} ${at.y} ${at.z} 4 0.5`)
    await sleep(700)
  }
}

/** Runs a bay's trip, or its pad's, with the panel's settings; one run at a time in the whole lab. */
async function run (key) {
  const { bay, pad } = bayOf(key)
  const panel = lab.panels[key]
  if (lab.busy) {
    const why = lab.busy === key
      ? `The ${bay.name} run is still going; that press is ignored until it ends.`
      : `Busy with the ${lab.busy} run; press Run again once its lamp goes green.`
    await say(why)
    return
  }
  lab.busy = key
  const values = lab.panels[bay.name].values()
  const settings = pad ? 'the pad' : Object.values(values).join(', ')
  await say(`${bay.title}: running ${settings}.`)
  panel.status('running')
  const doors = bay.name === 'gate'
  if (doors) p.blastDoor(0, world.HUB.z0, 'x', true)
  let outcome = 'PASS'
  let detail = ''
  kit.startClock()
  try {
    detail = await Promise.race([pad ? bay.runPad(lab) : bay.run(lab, values), kit.gone]) || ''
  } catch (e) {
    outcome = e.notRun ? 'NOT RUN' : 'FAIL'
    detail = e.message
  }
  kit.bot.clearControlStates()
  await kit.getOff().catch(() => {})
  if (doors) p.blastDoor(0, world.HUB.z0, 'x', false)
  panel.status(outcome === 'FAIL' ? 'failed' : 'idle')
  missionBoard(bay, outcome, outcome === 'PASS' ? detail : 'see chat')
  console.log(`${outcome} ${key} (${settings})${detail ? ': ' + detail : ''}`)
  if (outcome === 'FAIL') {
    await say(`${bay.title} FAILED: ${detail}`)
    klaxon(panel)
  } else {
    await say(`${bay.title}: ${outcome === 'PASS' ? 'passed' : 'not run'}${detail ? ' - ' + detail : ''}.`)
  }
  lab.results.push({ key, settings, outcome, detail })
  lab.busy = null
  lab.finished++
}

/** Pulls a bay back to empty. */
async function reset (key) {
  const { bay, pad } = bayOf(key)
  const panel = lab.panels[key]
  if (lab.busy) {
    await say(`Busy with the ${lab.busy} run; pull Reset again once its lamp goes green.`)
    panel.releaseReset()
    return
  }
  lab.busy = key
  await say(`${bay.title}: resetting${pad ? ' the pad' : ''}.`)
  try {
    await Promise.race([bay.reset(lab), kit.gone])
    panel.status('idle')
    lab.results.push({ key, settings: 'reset', outcome: 'PASS', detail: '' })
  } catch (e) {
    await say(`${bay.title}: the reset did not finish: ${e.message}`)
    lab.results.push({ key, settings: 'reset', outcome: 'FAIL', detail: e.message })
  }
  panel.releaseReset()
  lab.busy = null
  lab.finished++
}

/** Hears the panels: a command block's "[@] lab:next gate shape" and the like. */
function listen (text) {
  const heard = text.match(/^\[@\] lab:(next|run|reset) (\w+)(?: (\w+))?$/)
  if (!heard) return
  const [, what, key, option] = heard
  const panel = lab.panels[key]
  if (!panel) return
  lab.pressed++
  if (what === 'next') {
    if (lab.busy === key) {
      say(`The ${key} run is going; its panel is locked until it ends.`)
      return
    }
    const value = panel.next(option)
    if (value !== null) console.log(`  ${key} ${option}: ${value}`)
  } else if (what === 'run') {
    run(key).catch((e) => console.log(`run ${key}: ${e.message}`))
  } else {
    reset(key).catch((e) => console.log(`reset ${key}: ${e.message}`))
  }
}

/** Builds the whole lab from the console, in chunks the forceload keeps loaded. */
async function build () {
  narrate('Generating the lab')
  serverCommand(`forceload add ${AREA.x0} ${AREA.z0} ${AREA.x1} ${AREA.z1}`)
  // Command blocks say what was pressed; their own feedback would only echo it to the bot.
  for (const rule of ['commandBlockOutput false', 'command_block_output false', 'doDaylightCycle false', 'advance_time false', 'doWeatherCycle false', 'advance_weather false']) {
    serverCommand(`gamerule ${rule}`)
  }
  serverCommand('time set noon')
  serverCommand('weather clear')
  await sleep(5000)
  world.buildHub()
  for (const bay of bays) {
    await sleep(1000)
    bay.build(lab)
    lab.panels[bay.name] = new Panel(bay.name, bay.panel, { options: bay.options, title: bay.title })
    lab.panels[bay.name].build()
    if (bay.padPanel) {
      lab.panels[`${bay.name}pad`] = new Panel(bay.name, bay.padPanel, { pad: true, title: bay.title })
      lab.panels[`${bay.name}pad`].build()
    }
  }
  await world.buildOffworld()
  serverCommand('wx config pets-follow-owner true')
  // A lodestone under spawn is the last block the build sets; seeing it means the lab is up.
  const marker = v(world.SPAWN.x, FLOOR - 1, world.SPAWN.z)
  p.setblock(marker.x, marker.y, marker.z, 'lodestone')
  await sleep(5000)
  await teleport(world.SPAWN.x + 0.5, FEET, world.SPAWN.z + 0.5, 180)
  await kit.waitFor(() => kit.nameAt(marker) === 'lodestone', 120, 'the last block of the build')
  narrate('The lab is built')
}

/** Puts a watcher who joins in adventure mode in the hub, and tells them how the lab works. */
function welcome (player) {
  if (player.username === kit.name) return
  const who = player.username
  serverCommand(`gamemode adventure ${who}`)
  kit.watch(who)
  serverCommand(`tp ${who} ${world.SPAWN.x + 0.5} ${FEET} ${world.SPAWN.z + 0.5} 180 0`)
  setTimeout(() => {
    say(`Welcome to the lab, ${who}. The gate room is north, rings and beams through it east and west, and the mirror gallery south.`)
    say('Each panel: press the button under a sign for its next choice, the big button to Run, the lever to Reset. The lectern here explains the rest. Say "stop" to shut the lab down.')
  }, 3000)
}

/**
 * In a build pad a watcher is in creative, to build a gate or lay slabs; anywhere else in
 * adventure, so the panels work and nothing else breaks.
 */
function padModes () {
  const pads = [gateBay.pad, { x0: 56, z0: -59, x1: 90, z1: -47 }]
  serverCommand('tag @a remove labpad')
  for (const pad of pads) {
    serverCommand(`tag @a[x=${pad.x0},y=${FEET - 5},z=${pad.z0},dx=${pad.x1 - pad.x0},dy=40,dz=${pad.z1 - pad.z0}] add labpad`)
  }
  serverCommand(`gamemode creative @a[tag=labpad,gamemode=adventure,name=!${kit.name}]`)
  serverCommand(`gamemode adventure @a[tag=!labpad,gamemode=creative,name=!${kit.name}]`)
}

// ---------------------------------------------------------------------------------------------
// The self-test: the bot works the panels as a watcher would, so a run through here proves the
// buttons, the chat they send, the signs the bot rewrites and each bay's run, on this version.

/** Presses a switch on a panel and waits for the lab to have heard it. */
async function press (panel, what) {
  const at = panel.switchFor(what)
  // In front of the switch, a block out from it, as someone using the console stands.
  const { dx, dz } = panel.front
  const stand = v(at.x + 0.5 + dx, FEET, at.z + 0.5 + dz)
  if (!kit.near(stand, 0.5)) await teleport(stand.x, stand.y, stand.z, panel.standing().yaw)
  const before = lab.pressed
  const block = kit.bot.blockAt(v(at.x, at.y, at.z))
  if (!block || !/_button$|^lever$/.test(block.name)) throw new Error(`no switch for ${what} at ${at.x} ${at.y} ${at.z}, but ${block ? block.name : 'nothing loaded'}`)
  // A button stays in for a second after a press, and pressing it then does nothing.
  const up = () => kit.bot.blockAt(v(at.x, at.y, at.z)).getProperties().powered !== true
  await kit.waitFor(up, 5, `the ${what} button coming back out`)
  await kit.bot.lookAt(v(at.x + 0.5, at.y + 0.5, at.z + 0.5), true)
  await kit.bot.activateBlock(block)
  await kit.waitFor(() => lab.pressed > before, 10, `the lab hearing ${what} pressed on the ${panel.key} panel`)
}

/** Steps a panel's option round to `value` with its next button, and checks its sign shows it. */
async function setOption (panel, key, value) {
  const option = panel.options.find((o) => o.key === key)
  if (!option.values.includes(value)) throw new Error(`the ${panel.key} panel has no ${value} for ${key}`)
  for (let i = 0; option.values[option.index] !== value; i++) {
    if (i > option.values.length) throw new Error(`${key} never reached ${value}`)
    const was = option.index
    await press(panel, key)
    await kit.waitFor(() => option.index !== was, 5, `${key} moving on from ${option.values[was]}`)
  }
  const at = panel.signFor(key)
  const shows = option.show ? option.show(value) : value
  const reads = await kit.signText(at)
  if (!reads.includes(shows)) throw new Error(`the ${key} sign should show ${shows}, but reads ${reads}`)
}

/** Sets a panel's options, each to the scenario's value or its first, presses Run, and waits. */
async function scenario (key, wanted = {}, before) {
  const { bay } = bayOf(key)
  const main = lab.panels[bay.name]
  for (const option of main.options) {
    await setOption(main, option.key, wanted[option.key] || option.values[option.start || 0])
  }
  if (before) await before()
  const done = lab.finished
  await press(lab.panels[key], 'run')
  await kit.waitFor(() => lab.finished > done, 600, `the ${key} run finishing`)
  return lab.results[lab.results.length - 1]
}

async function pull (key) {
  const done = lab.finished
  await press(lab.panels[key], 'reset')
  await kit.waitFor(() => lab.finished > done, 300, `the ${key} reset finishing`)
  return lab.results[lab.results.length - 1]
}

/** Stands a Standard preview on a DHD in the gate pad and places it, as a watcher would. */
async function buildOnGatePad () {
  const x = 30
  const z = -50
  p.setblock(x, FEET, z, 'obsidian')
  p.setblock(x, FEET, z + 1, 'stone_button[face=wall,facing=south]')
  await sleep(500)
  await teleport(x + 0.5, FEET, z + 2.5, 180)
  await kit.bot.lookAt(v(x + 0.5, FEET + 0.5, z + 1.06), true)
  await sleep(300)
  kit.bot.chat('/wormhole gate build Standard')
  await sleep(1500)
  await kit.bot.lookAt(v(x - 1, FEET + 2.5, z - 2), true)
  await sleep(300)
  kit.bot.chat('/wormhole gate preview place')
  await sleep(2000)
}

const scenarios = {
  gate: [
    ['gate', {}],
    ['gate', { shape: 'Grand', group: 'Atlantis', portal: 'LAVA' }],
    ['gate', { shape: 'StandardSignDial', dial: 'sign', dest: 'Offworld' }],
    ['gate', { shape: 'StandardSignDial', dial: 'redstone' }],
    ['gate', { dial: 'console', iris: 'shut, code', traveller: 'minecart' }],
    ['gate', { iris: 'shut, no code' }],
    ['gate', { shape: 'Horizontal', group: 'MilkyWay' }],
    ['gate', { shape: 'Minimal' }],
    ['gate', { group: 'Universe', traveller: 'boat' }],
    ['gate', { shape: 'Large', traveller: 'horse' }],
    ['gate', { shape: 'Massive', group: 'Universe', traveller: 'wolf', dest: 'Offworld' }],
    ['gate', { dial: 'sign' }, null, 'NOT RUN'],
    ['gatepad', {}, buildOnGatePad]
  ],
  ring: [
    ['ring', {}],
    ['ring', { pattern: 'EVEN', slab: 'sandstone', style: 'SEQUENTIAL', distance: '20' }],
    ['ringpad', {}, async () => {
      for (const pad of ringBay.PADS) ringBay.layCircle(pad.x, pad.z, 'ODD', 'oak')
      await sleep(1000)
    }]
  ],
  beam: [
    ['beam', {}],
    ['beam', { dest: 'Offworld', facing: 'east', traveller: 'horse' }],
    ['beam', { dest: 'Gamma', facing: 'west', traveller: 'wolf' }]
  ],
  mirror: [
    ['mirror', {}],
    ['mirror', { from: 'library', to: 'vault' }],
    ['mirror', { from: 'yours', to: 'hub' }, async () => {
      const y = mirrorBay.YOURS
      p.setblock(y.x, mirrorBay.BANNER_Y, y.z, 'red_wall_banner[facing=south]')
      await sleep(1000)
    }]
  ]
}

/** After every Reset, each bay must be empty again. */
async function checkEmpty () {
  const failures = []
  const from = kit.logSize()
  serverCommand('wx list')
  serverCommand('wx mirror list')
  await sleep(2000)
  const said = kit.logSince(from)
  for (const gone of ['LabGate', 'LabPad']) if (said.includes(gone)) failures.push(`${gone} is still a gate`)
  if (/\byours\b.{0,12} -- /.test(said)) failures.push('the "yours" mirror is still a mirror')
  const a = v(62, FEET, -36 - 3)
  await teleport(62.5, FEET, -30.5, 180)
  if (kit.nameAt(a) !== 'air') failures.push(`the ring platform still has ${kit.nameAt(a)} at ${a}`)
  return failures
}

async function selfTest () {
  const which = selftest === '1' ? Object.keys(scenarios) : selftest.split(',')
  const outcomes = []
  for (const bay of which) {
    for (const [key, wanted, before, expected = 'PASS'] of scenarios[bay]) {
      let result
      try {
        result = await scenario(key, wanted, before)
      } catch (e) {
        result = { key, settings: JSON.stringify(wanted), outcome: 'FAIL', detail: `the self-test could not work the panel: ${e.message}` }
      }
      outcomes.push({ ...result, expected, ok: result.outcome === expected })
    }
  }
  for (const bay of which) {
    const keys = bays.find((b) => b.name === bay).padPanel ? [bay, `${bay}pad`] : [bay]
    for (const key of keys) {
      try {
        const result = await pull(key)
        outcomes.push({ ...result, expected: 'PASS', ok: result.outcome === 'PASS' })
      } catch (e) {
        outcomes.push({ key, settings: 'reset', outcome: 'FAIL', expected: 'PASS', ok: false, detail: e.message })
      }
    }
  }
  if (which.length === Object.keys(scenarios).length) {
    const left = await checkEmpty()
    outcomes.push({ key: 'empty', settings: 'after every reset', outcome: left.length ? 'FAIL' : 'PASS', expected: 'PASS', ok: left.length === 0, detail: left.join('; ') })
  }
  console.log(`\nlab self-test on ${version}:`)
  for (const o of outcomes) {
    console.log(`  ${o.ok ? 'ok  ' : 'BAD '} ${o.outcome.padEnd(7)} ${o.key.padEnd(8)} ${o.settings}${o.detail ? '  (' + o.detail + ')' : ''}`)
  }
  return outcomes.every((o) => o.ok)
}

// ---------------------------------------------------------------------------------------------

async function main () {
  await kit.connect()
  const gone = kit.gone
  await Promise.race([build(), gone])
  kit.bot.on('messagestr', listen)
  kit.bot.on('playerJoined', welcome)
  for (const player of Object.values(kit.bot.players)) welcome(player)
  setInterval(padModes, 2000).unref()

  if (selftest) {
    const passed = await Promise.race([selfTest(), gone])
    kit.bot.quit()
    await sleep(1000)
    process.exit(passed ? 0 : 1)
  }

  console.log(`The lab is up. Join localhost:${kit.port} with Minecraft ${version}, any name. Say "stop" in chat to shut it down.`)
  let stop = false
  kit.bot.on('messagestr', (text) => {
    if (/^<(?!WxBot>)[^>]+> *stop[.!]? *$/i.test(text)) stop = true
  })
  await Promise.race([kit.waitFor(() => stop, 24 * 3600, 'someone saying stop'), gone])
  await say('Shutting the lab down.')
  kit.bot.quit()
  await sleep(1000)
  process.exit(0)
}

main().catch((e) => {
  console.error('the lab could not run: ' + e.message)
  process.exit(1)
})
