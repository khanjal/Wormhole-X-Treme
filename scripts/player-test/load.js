'use strict'
// The client half of scripts/perf-boot.sh: sets up one scenario from the console, joins BOTS
// Mineflayer bots and keeps them at it, and measures the server's ticks with the WxPerfTick plugin
// for DURATION seconds:
//   node load.js <minecraft-version>     run once, as boot-test.sh's BOOT_CLIENT
//   node load.js --table <folder>        print the results in a folder as a table
// Run from perf-boot.sh, which sets BOOT_CONSOLE, BOOT_LOG, BOOT_PORT and the settings below; see
// its header for what each scenario does. The same steps run with and without Wormhole, so a run
// without it is the control: its commands then fail as unknown, and only the vanilla blocks stand.

const fs = require('fs')
const path = require('path')

const env = (key, fallback) => (process.env[key] === undefined || process.env[key] === '' ? fallback : process.env[key])

if (process.argv[2] === '--table') {
  printTable(process.argv[3])
  process.exit(0)
}

const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const version = process.argv[2]
const consoleFile = process.env.BOOT_CONSOLE
const logFile = process.env.BOOT_LOG
const port = Number(env('BOOT_PORT', 25599))
const scenario = env('SCENARIO', 'viewers')
const bots = Number(env('BOTS', 1))
const duration = Number(env('DURATION', 120))
const warmup = Number(env('WARMUP', 45))
const viewWait = Number(env('VIEW_WAIT', 300))
const withPlugin = env('PLUGIN', 'yes') === 'yes'
const label = env('LABEL', `${scenario}-${bots}`)
const resultFile = env('PERF_RESULT', '')
const spacing = Number(env('SITE_SPACING', 336))
const moves = env('MOVES', 'fidget')
const spark = env('SPARK', '0') === '1'
// Idle steps the number of gates and mirrors rather than bots, since it has none.
const sites = (key, fallback) => Number(env(key, scenario === 'idle' ? bots : fallback))
const gates = sites('GATES', 4)
const mirrors = sites('MIRRORS', 4)

const SCENARIOS = {
  idle: { gates: true, mirrors: true, bots: false },
  viewers: { gates: false, mirrors: true, bots: true },
  wanderers: { gates: true, mirrors: true, bots: true },
  gateview: { gates: true, mirrors: false, bots: true }
}
const plan = SCENARIOS[scenario]

if (!version || !consoleFile || !logFile || !plan) {
  console.error(`usage: BOOT_CONSOLE=<file> BOOT_LOG=<file> SCENARIO=<${Object.keys(SCENARIOS).join('|')}> node load.js <minecraft-version>`)
  process.exit(2)
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const v = (x, y, z) => new Vec3(x, y, z)
const say = (text) => console.log(`[load ${label}] ${text}`)

function send (command) {
  fs.appendFileSync(consoleFile, command + '\n')
}

function logSize () {
  return fs.statSync(logFile).size
}

/** The first match of `pattern` in the log after byte `from`, waiting up to `seconds`; null if none. */
async function logMatch (pattern, seconds, from) {
  const deadline = Date.now() + seconds * 1000
  do {
    const found = fs.readFileSync(logFile).subarray(from).toString('utf8').match(pattern)
    if (found) return found
    await sleep(250)
  } while (Date.now() < deadline)
  return null
}

async function needLog (pattern, seconds, from, what) {
  const found = await logMatch(pattern, seconds, from)
  if (!found) throw new Error(`${what}: the server never logged ${pattern} within ${seconds}s`)
  return found
}

// ---------------------------------------------------------------------------------------------
// The world. A grass floor at -64, so everything stands at -63. Sites are SITE_SPACING apart, by
// default further than twice the view depth, so each window is drawn whole, as a lone one is.

const FLOOR = -63
const mirrorAt = (i) => ({ name: `perfM${i}`, x: i * spacing, z: 400 })
// A gate's frame reaches a block below where it is built, so gates stand at -60 on a stone floor.
const GATE_FLOOR = -60
const gateAt = (i) => ({ from: `perfS${i}`, to: `perfT${i}`, x: i * spacing, z: -400, toZ: -700 })

// An open gate holds no chunk ticket, and the tickers skip a gate or mirror whose chunk is not
// loaded, so idle, wanderers and gateview keep every site force-loaded, both ends of each gate,
// or they would measure only the sites a bot stands at; siteCheck() says whether they were.
// KEEP_LOADED=0 or 1 overrides that.
const keepLoaded = env('KEEP_LOADED', scenario === 'viewers' ? '0' : '1') === '1'

async function loaded (x, z, body) {
  send(`forceload add ${x - 16} ${z - 16} ${x + 16} ${z + 16}`)
  await sleep(1500)
  await body()
  if (!keepLoaded) send(`forceload remove ${x - 16} ${z - 16} ${x + 16} ${z + 16}`)
}

/**
 * How many gates have their chunk loaded, and how many mirrors' chunks are, asked of the server with
 * execute if loaded. After the run, also how many gates are still open and swept: an item dropped
 * into each opening must come out at the far gate. The horizon is only drawn to clients, so the
 * opening's real block is air whether it is open or not, and cannot say.
 */
async function siteCheck (when) {
  const at = logSize()
  const tag = `WXCHECK${when}`
  if (plan.gates) {
    for (let i = 0; i < gates; i++) {
      const g = gateAt(i)
      // A Standard gate built facing south at x, z opens round x - 1.5, z - 2.5.
      send(`execute if loaded ${g.x - 2} -58 ${g.z - 3} run say ${tag} gate-loaded ${g.from}`)
      send(`execute if loaded ${g.x - 2} -58 ${g.toZ - 3} run say ${tag} far-loaded ${g.to}`)
    }
    if (when === 'after' && withPlugin) {
      // Both ends loaded for the test, or an item could neither be swept nor found: a gate view
      // run keeps only the gates its bots stand at loaded.
      for (let i = 0; i < gates && !keepLoaded; i++) {
        const g = gateAt(i)
        for (const z of [g.z, g.toZ]) send(`forceload add ${g.x - 16} ${z - 16} ${g.x + 16} ${z + 16}`)
      }
      await sleep(keepLoaded ? 0 : 3000)
      for (let i = 0; i < gates; i++) {
        const g = gateAt(i)
        send(`summon item ${g.x - 1.5} -57.5 ${g.z - 2.5} {Item:{id:"minecraft:diamond",count:1},Tags:["wxcheck"]}`)
      }
      // The entity sweep runs about once a second; asked twice, and a gate counted once.
      for (const wait of [5000, 6000]) {
        await sleep(wait)
        for (let i = 0; i < gates; i++) {
          const g = gateAt(i)
          send(`execute if entity @e[type=item,tag=wxcheck,x=${g.x - 1.5},y=-60,z=${g.toZ - 1.5},distance=..6] run say ${tag} gate-open-${wait} ${g.from}`)
        }
      }
    }
  }
  if (plan.mirrors) {
    for (let i = 0; i < mirrors; i++) {
      const m = mirrorAt(i)
      send(`execute if loaded ${m.x} -62 ${m.z} run say ${tag} mirror-loaded ${m.name}`)
    }
  }
  send(`say ${tag} done`)
  await needLog(new RegExp(`${tag} done`), 20, at, 'checking the sites')
  const text = fs.readFileSync(logFile).subarray(at).toString('utf8')
  const count = (kind) => (text.match(new RegExp(`${tag} ${kind} `, 'g')) || []).length
  const open = new Set([...text.matchAll(new RegExp(`${tag} gate-open-\\d+ (\\S+)`, 'g'))].map((m) => m[1]))
  return {
    gatesLoaded: count('gate-loaded'),
    farLoaded: count('far-loaded'),
    gatesOpen: when === 'after' && withPlugin && plan.gates ? open.size : null,
    mirrorsLoaded: count('mirror-loaded')
  }
}

async function setUpWorld () {
  const from = logSize()
  send('difficulty peaceful')
  send('time set day')
  // Old and new names: the rules were renamed in 1.21.11, and the unknown one is only an error.
  for (const rule of ['doMobSpawning', 'spawn_mobs', 'doDaylightCycle', 'advance_time', 'doWeatherCycle',
    'advance_weather', 'logAdminCommands', 'log_admin_commands']) send(`gamerule ${rule} false`)
  if (withPlugin) {
    // The gate-view settings only where there are gates: 1.10.0 has no gate-view-funnel-min-length, and
    // a viewer run against it would stop on the unknown key.
    const gateSettings = plan.gates ? [['gate-view', env('GATE_VIEW', 'horizon')], ['gate-view-funnel-min-length', env('FUNNEL_MIN', 0)]] : []
    for (const [key, value] of [['timeout-shutdown', 0], ['max-open-seconds', 0], ['mirror-per-world-limit', 0], ...gateSettings]) {
      const at = logSize()
      send(`wx config ${key} ${value}`)
      await needLog(/is now/, 20, at, `wx config ${key} ${value}`)
    }
    // Extra settings, "key value" pairs split by commas, for a run that needs another.
    for (const pair of env('WX_CONFIG', '').split(',').map((p) => p.trim()).filter(Boolean)) {
      const at = logSize()
      send(`wx config ${pair}`)
      await needLog(/is now/, 20, at, `wx config ${pair}`)
    }
  }
  if (plan.mirrors) {
    for (let i = 0; i < mirrors; i++) {
      const m = mirrorAt(i)
      await loaded(m.x, m.z, async () => {
        // A wall three deep and two out on every side of the banner, as mirror create wants it.
        send(`fill ${m.x - 3} -64 ${m.z - 3} ${m.x + 3} -59 ${m.z - 1} stone`)
        send(`setblock ${m.x} -62 ${m.z} white_wall_banner[facing=south]`)
        await sleep(300)
        if (withPlugin) {
          const at = logSize()
          send(`wx mirror create ${m.name} world ${m.x} -62 ${m.z}`)
          await needLog(new RegExp(`${m.name}.{0,12} is this banner`), 20, at, `making mirror ${m.name}`)
        }
      })
    }
  }
  if (plan.gates) {
    for (let i = 0; i < gates; i++) {
      const g = gateAt(i)
      await loaded(g.x, g.z, async () => {
        await loaded(g.x, g.toZ, async () => {
          if (!withPlugin) return
          const at = logSize()
          send(`wx gate build Standard ${g.from} world ${g.x} ${GATE_FLOOR} ${g.z} south`)
          send(`wx gate build Standard ${g.to} world ${g.x} ${GATE_FLOOR} ${g.toZ} south`)
          await needLog(new RegExp(`Built ${g.from} at`), 20, at, `building ${g.from}`)
          await needLog(new RegExp(`Built ${g.to} at`), 20, at, `building ${g.to}`)
          // "keep" spares the iris switch under each DHD.
          for (const z of [g.z, g.toZ]) send(`fill ${g.x - 8} ${GATE_FLOOR - 1} ${z - 2} ${g.x + 5} ${GATE_FLOOR - 1} ${z + 16} stone keep`)
          const dialled = logSize()
          send(`wx gate dial ${g.from} ${g.to}`)
          await needLog(/Stargates connected/, 20, dialled, `dialling ${g.from} to ${g.to}`)
        })
      })
    }
  }
  say(`world set up in ${((Date.now() - started) / 1000).toFixed(0)}s (log from byte ${from})`)
}

/** Waits until every mirror's room is captured, as mirror debug says. */
async function mirrorsCaptured () {
  if (!withPlugin || !plan.mirrors) return
  for (let i = 0; i < mirrors; i++) {
    const { name } = mirrorAt(i)
    let line = null
    const deadline = Date.now() + 180000
    while (Date.now() < deadline) {
      const at = logSize()
      send(`wx mirror debug ${name} -all`)
      const found = await logMatch(/file: ([^\r\n]*)/, 5, at)
      line = found ? found[1] : 'no file line'
      if (/^\d+ bytes/.test(line) && !line.includes('being taken')) break
      await sleep(2000)
    }
    say(`${name} capture: ${line}`)
  }
}

// ---------------------------------------------------------------------------------------------
// The bots.

/** Where bot k stands: rows of five in front of its site, two blocks apart, starting `front` out. */
function slot (k) {
  const row = Math.floor(k / 5)
  const across = ((k % 5) - 2) * 1.5
  if (scenario === 'viewers') {
    const m = mirrorAt(k % mirrors)
    const j = Math.floor(k / mirrors)
    return { x: m.x + 0.5 + ((j % 5) - 2) * 1.5, z: m.z + 3.5 + Math.floor(j / 5) * 2, site: m.name }
  }
  if (scenario === 'gateview') {
    const g = gateAt(k % gates)
    const j = Math.floor(k / gates)
    // A Standard gate built facing south at x opens round x - 1.5, 2.5 behind its z.
    return { x: g.x - 1.0 + ((j % 5) - 2) * 1.5, y: GATE_FLOOR, z: g.z + 2.5 + Math.floor(j / 5) * 2, site: g.from }
  }
  // Wanderers start spread over the box they wander in, by spawn, far from every site.
  return { x: across * 4, z: row * 6 - 24, site: null }
}

const WANDER = 48
const party = []

function botName (k) {
  return `Perf${String(k + 1).padStart(2, '0')}`
}

async function join (k) {
  const name = botName(k)
  const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, auth: 'offline', version })
  const member = { k, name, bot, heard: [], blocks: 0, packets: 0, walked: 0, last: null, kicked: null, slot: slot(k) }
  bot.on('messagestr', (text) => { member.heard.push(text) })
  bot._client.on('block_change', () => { member.blocks++; member.packets++ })
  bot._client.on('multi_block_change', (packet) => {
    member.blocks += (packet.records && packet.records.length) || 0
    member.packets++
  })
  bot.on('kicked', (reason) => { member.kicked = JSON.stringify(reason) })
  bot.on('end', (reason) => { if (!member.kicked) member.kicked = `disconnected: ${reason}` })
  bot.on('error', (e) => { if (!member.kicked) member.kicked = `error: ${e.message}` })
  await Promise.race([new Promise((resolve) => bot.once('spawn', resolve)),
    sleep(60000).then(() => { throw new Error(`${name} never spawned within 60s (${member.kicked})`) })])
  party.push(member)
  return member
}

async function placeBots () {
  for (const member of party) {
    const { x, y = FLOOR, z } = member.slot
    // Facing north, at the window, for those with one: Bukkit's yaw 180.
    send(`tp ${member.name} ${x} ${y} ${z} 180 0`)
  }
  await sleep(3000)
}

/** Head turns, and a short step aside and back, as somebody looking does; or a random walk. */
function behave () {
  const timers = []
  for (const member of party) {
    const bot = member.bot
    if (scenario === 'wanderers') {
      let target = null
      let stuckSince = Date.now()
      let lastAt = bot.entity.position.clone()
      timers.push(setInterval(() => {
        const p = bot.entity.position
        if (member.last) member.walked += p.distanceTo(member.last)
        member.last = p.clone()
        if (!target || Math.hypot(p.x - target.x, p.z - target.z) < 1.5) {
          target = v((Math.random() * 2 - 1) * WANDER, FLOOR, (Math.random() * 2 - 1) * WANDER)
        }
        if (p.distanceTo(lastAt) > 0.5) {
          lastAt = p.clone()
          stuckSince = Date.now()
        } else if (Date.now() - stuckSince > 2000) {
          // Walked into another bot: hop and go somewhere else.
          bot.setControlState('jump', true)
          setTimeout(() => bot.setControlState('jump', false), 300)
          target = null
          stuckSince = Date.now()
        }
        if (target) bot.lookAt(v(target.x, p.y + 1.6, target.z), true).catch(() => {})
        bot.setControlState('forward', true)
        bot.setControlState('sprint', member.k % 3 === 0)
      }, 100))
    } else if (moves === 'fidget') {
      // A step every five to nine seconds, at its own moment: bots stepping in step would line
      // their redraws up in one tick, which no crowd of players does.
      let step = 0
      let nextStep = Math.floor(Math.random() * 18)
      let side = member.k % 2 === 0 ? 'left' : 'right'
      timers.push(setInterval(() => {
        const p = bot.entity.position
        if (member.last) member.walked += p.distanceTo(member.last)
        member.last = p.clone()
        // Mineflayer's yaw 0 is north.
        bot.look((Math.random() - 0.5) * 0.8, (Math.random() - 0.5) * 0.4, false).catch(() => {})
        if (++step >= nextStep) {
          const going = side
          bot.setControlState(going, true)
          setTimeout(() => bot.setControlState(going, false), 250)
          side = side === 'left' ? 'right' : 'left'
          step = 0
          nextStep = 10 + Math.floor(Math.random() * 9)
        }
      }, 450 + Math.floor(Math.random() * 100)))
    }
  }
  return () => {
    for (const t of timers) clearInterval(t)
    for (const member of party) member.bot.clearControlStates()
  }
}

/** What one bot's /wormhole mirror debug -views says: the windows it is drawn, and how many blocks. */
async function viewsOf (member) {
  const from = member.heard.length
  member.bot.chat('/wormhole mirror debug -views')
  // The answer comes as one message of many lines; waited for, since a busy server answers late.
  // It ends at "last redraw" for a viewer, or at "looking into: no window" for anyone else.
  const deadline = Date.now() + 15000
  let lines = []
  while (Date.now() < deadline) {
    lines = member.heard.slice(from).flatMap((text) => text.split('\n'))
    if (lines.some((l) => /looking into:\s*no window|last redraw/.test(l))) break
    await sleep(100)
  }
  const into = lines.map((l) => l.match(/looking into:\s*(.*)$/)).find(Boolean)
  const drawn = lines.map((l) => l.match(/\bdrawn:\s*(\d+) blocks/)).find(Boolean)
  return { into: into ? into[1].trim() : 'no answer', drawn: drawn ? Number(drawn[1]) : 0, lines }
}

/** Whether a run is one whose bots must be drawn a window for it to measure anything. */
function needsViews () {
  return withPlugin && party.length > 0 && (scenario === 'viewers' ||
    (scenario === 'gateview' && env('GATE_VIEW', 'horizon') !== 'horizon'))
}

/** Waits, up to VIEW_WAIT, for every bot to be drawn a window; returns how many were. */
async function waitForViews () {
  if (!needsViews()) return null
  const deadline = Date.now() + viewWait * 1000
  const drawn = new Set()
  let last = null
  do {
    for (const member of party) {
      if (drawn.has(member.name)) continue
      const seen = await viewsOf(member)
      if (member === party[0]) last = seen
      if (seen.drawn > 0) drawn.add(member.name)
    }
    if (drawn.size === party.length) break
    await sleep(5000)
  } while (Date.now() < deadline)
  say(`${drawn.size}/${party.length} bots drawn after ${((Date.now() - started) / 1000).toFixed(0)}s`)
  if (last) say(`  ${party[0].name}: ${last.lines.join('\n  ')}`)
  return { drawn: drawn.size, into: last ? last.into : null }
}

// ---------------------------------------------------------------------------------------------

const started = Date.now()

async function main () {
  // Joined first, so the world is set up with a player there and its chunks are sent once.
  if (plan.bots) {
    for (let k = 0; k < bots; k++) {
      await join(k)
      await sleep(300)
    }
    const at = logSize()
    for (const member of party) send(`op ${member.name}`)
    await needLog(new RegExp(`Made ${botName(bots - 1)} a server operator`), 30, at, 'opping the bots')
    for (const member of party) send(`gamemode creative ${member.name}`)
    say(`${party.length} bots joined`)
  }
  await setUpWorld()
  await mirrorsCaptured()
  if (plan.bots) await placeBots()
  const stop = behave()
  await sleep(warmup * 1000)
  const firstLook = await waitForViews()
  await sleep(10000)
  const warmedAt = Date.now()
  const sitesBefore = await siteCheck('before')

  for (const member of party) { member.blocks = 0; member.packets = 0; member.walked = 0 }
  let at = logSize()
  send(`perftick start ${label}`)
  await needLog(/WXPERF started/, 30, at, 'starting the tick log')
  if (spark) send(`spark profiler start --only-ticks-over 50 --timeout ${duration + 30}`)
  const mspt = []
  const end = Date.now() + duration * 1000
  while (Date.now() < end) {
    await sleep(5000)
    const from = logSize()
    send('mspt')
    // The 5s, 10s and 1m windows, each avg/min/max.
    const found = await logMatch(/([\d.]+)\/([\d.]+)\/([\d.]+), [\d.]+\/[\d.]+\/[\d.]+, [\d.]+\/[\d.]+\/[\d.]+/, 3, from)
    if (found) mspt.push([Number(found[1]), Number(found[3])])
  }
  at = logSize()
  if (spark) send('spark profiler stop --save-to-file')
  send('perftick stop')
  const result = JSON.parse((await needLog(/WXPERF result (\{.*\})/, 60, at, 'stopping the tick log'))[1])

  // Whether each bot was being drawn a window, asked after the run so the asking is not measured.
  const views = []
  if (withPlugin && plan.bots && scenario !== 'wanderers') {
    for (const member of party) {
      // A bot still fidgeting can be caught mid-step out of its window's sight: asked twice more,
      // a moment apart, before it counts as never drawn (one of thirty at N=30, both jars, #609).
      let seen = await viewsOf(member)
      for (let retry = 0; retry < 2 && seen.drawn === 0; retry++) {
        await sleep(2000)
        seen = await viewsOf(member)
      }
      // Where one still not drawn stands, against its slot, so a row can say why.
      const p = member.bot.entity.position
      const where = seen.drawn === 0 ? ` (at ${p.x.toFixed(1)},${p.y.toFixed(1)},${p.z.toFixed(1)}; slot ${member.slot.x},${member.slot.z})` : ''
      views.push({ bot: member.name, site: member.slot.site, ...seen, into: seen.into + where })
    }
  }
  const sitesAfter = await siteCheck('after')
  stop()
  const drawnBots = views.filter((x) => x.drawn > 0)
  // A run that should have drawn every bot a window, or kept every gate open and loaded, and did
  // not, measured something else: its row says so.
  const invalid = []
  if (needsViews() && drawnBots.length < party.length) invalid.push(`${drawnBots.length}/${party.length} bots drawn`)
  for (const [when, sites] of [['before', sitesBefore], ['after', sitesAfter]]) {
    if (sites.gatesOpen !== null && sites.gatesOpen < gates) invalid.push(`${sites.gatesOpen}/${gates} gates open ${when}`)
    if (keepLoaded && plan.gates && sites.gatesLoaded < gates) invalid.push(`${sites.gatesLoaded}/${gates} gate chunks loaded ${when}`)
    if (keepLoaded && plan.gates && sites.farLoaded < gates) invalid.push(`${sites.farLoaded}/${gates} far gate chunks loaded ${when}`)
    if (keepLoaded && plan.mirrors && sites.mirrorsLoaded < mirrors) invalid.push(`${sites.mirrorsLoaded}/${mirrors} mirror chunks loaded ${when}`)
  }
  const out = {
    label,
    scenario,
    bots: plan.bots ? bots : 0,
    gates: plan.gates ? gates : 0,
    mirrors: plan.mirrors ? mirrors : 0,
    plugin: withPlugin,
    jar: env('JAR_LABEL', withPlugin ? 'wormhole' : 'none'),
    gateView: env('GATE_VIEW', 'horizon'),
    funnelMin: Number(env('FUNNEL_MIN', 0)),
    durationS: duration,
    warmupS: Math.round((warmedAt - started) / 1000),
    tick: result,
    mspt: {
      windows: mspt.length,
      meanOfAvg: mspt.length ? mspt.reduce((s, w) => s + w[0], 0) / mspt.length : null,
      maxOfMax: mspt.length ? Math.max(...mspt.map((w) => w[1])) : null
    },
    invalid,
    validity: {
      keepLoaded,
      sitesBefore,
      sitesAfter,
      firstLook,
      botsDrawn: views.length ? drawnBots.length : null,
      drawnBlocks: views.map((x) => x.drawn),
      looking: views.map((x) => `${x.bot}@${x.site}: ${x.into}`),
      // The first bot's whole answer, for how its windows were drawn (whole or clipped, depth, capture).
      sample: views.length ? views[0].lines : null,
      blockChangesPerBot: party.length ? Math.round(party.reduce((s, m) => s + m.blocks, 0) / party.length) : 0,
      walkedPerBot: party.length ? Math.round(party.reduce((s, m) => s + m.walked, 0) / party.length) : 0,
      kicked: party.filter((m) => m.kicked).map((m) => `${m.name}: ${m.kicked}`)
    }
  }
  say(JSON.stringify(out))
  if (resultFile) fs.writeFileSync(resultFile, JSON.stringify(out, null, 2) + '\n')
  for (const member of party) member.bot.quit()
  await sleep(1000)
  if (invalid.length) console.error(`[load ${label}] INVALID: ${invalid.join('; ')}`)
  process.exit(out.validity.kicked.length || invalid.length ? 1 : 0)
}

main().catch((e) => {
  console.error(`[load ${label}] could not run: ${e.message}`)
  process.exit(1)
})

// ---------------------------------------------------------------------------------------------

/** Prints every result in a folder as a Markdown table, a control beside each run it controls. */
function printTable (folder) {
  // perf-boot.sh writes boot-test's exit status beside each result: a bot kicked, or a warning from
  // the plugin, fails the boot test whatever the bots found.
  const statusOf = (f) => {
    try {
      return fs.readFileSync(path.join(folder, f.replace(/\.json$/, '.status')), 'utf8').trim()
    } catch {
      return null
    }
  }
  const rows = fs.readdirSync(folder).filter((f) => f.endsWith('.json'))
    .map((f) => ({ ...JSON.parse(fs.readFileSync(path.join(folder, f), 'utf8')), bootStatus: statusOf(f) }))
    .sort((a, b) => a.scenario.localeCompare(b.scenario) || (a.bots + a.gates + a.mirrors) - (b.bots + b.gates + b.mirrors) ||
      `${a.gateView}${a.funnelMin}`.localeCompare(`${b.gateView}${b.funnelMin}`) || Number(a.plugin) - Number(b.plugin) ||
      a.jar.localeCompare(b.jar))
  // Gates open/loaded and mirrors loaded, at the end of the run; older results lack it.
  const sites = (r) => {
    const s = r.validity.sitesAfter
    if (!s) return '-'
    const far = s.farLoaded === undefined ? '' : `, ${s.farLoaded} far ends loaded`
    return [r.gates ? `gates ${s.gatesOpen === null ? '-' : s.gatesOpen} open, ${s.gatesLoaded} loaded${far}` : '', r.mirrors ? `mirrors ${s.mirrorsLoaded} loaded` : '']
      .filter(Boolean).join('; ') || '-'
  }
  const valid = (r) => {
    const why = [...(r.invalid || [])]
    if (r.bootStatus !== null && r.bootStatus !== '0') why.push(`boot test status ${r.bootStatus}`)
    return why.length ? `INVALID: ${why.join('; ')}` : 'ok'
  }
  const f = (n, d = 2) => (n === null || n === undefined ? '-' : Number(n).toFixed(d))
  console.log('| scenario | bots | gates | mirrors | gate-view | jar | mean | p95 | p99 | max | >50ms | heap MB: start/end after GC, high-water | bots drawn | blocks drawn/bot | mspt max | sites | valid |')
  console.log('|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|')
  for (const r of rows) {
    const t = r.tick
    const drawn = r.validity.drawnBlocks.length ? Math.round(r.validity.drawnBlocks.reduce((s, x) => s + x, 0) / r.validity.drawnBlocks.length) : '-'
    const view = r.scenario === 'gateview' ? `${r.gateView}/f${r.funnelMin}` : '-'
    console.log(`| ${r.scenario} | ${r.bots} | ${r.gates} | ${r.mirrors} | ${view} | ${r.jar} | ${f(t.mean)} | ${f(t.p95)} | ${f(t.p99)} | ${f(t.max, 1)} | ${t.over50} | ${t.heapStartMB}/${t.heapEndMB}/${t.heapPeakMB} | ${r.validity.botsDrawn === null ? '-' : `${r.validity.botsDrawn}/${r.bots}`} | ${drawn} | ${f(r.mspt.maxOfMax, 1)} | ${sites(r)} | ${valid(r)} |`)
  }
}
