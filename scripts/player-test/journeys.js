'use strict'
// Joins a real server as a player and takes it through gates of several kinds, an iris, a beam, a
// ring and a mirror, checking where it comes out each time:
//   node journeys.js <minecraft-version>
// Started by player-boot.sh through boot-test.sh, which passes BOOT_CONSOLE, a file whose new lines
// go to the server console, and BOOT_LOG, the server's log. Setting up is done from the console;
// everything a player would do, the bot does as a player.
//
// OBSERVE=1 waits for someone to join and watch, flies them to each trip, and asks them in chat
// whether they saw it happen. Their answers go in the summary, and a "no" fails the run.

const fs = require('fs')
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const version = process.argv[2]
const consoleFile = process.env.BOOT_CONSOLE
const logFile = process.env.BOOT_LOG
const observe = process.env.OBSERVE === '1'
const observeWait = Number(process.env.OBSERVE_WAIT || 600)
const name = 'WxBot'
const port = Number(process.env.BOOT_PORT || 25599)
// TRIPS, a comma-separated list of trip names, runs only those, in that order; a name given twice
// runs that trip again, as a watcher's rerun does.
const only = process.env.TRIPS ? process.env.TRIPS.split(',') : null

if (!version || !consoleFile || !logFile) {
  console.error('usage: BOOT_CONSOLE=<file> BOOT_LOG=<file> node journeys.js <minecraft-version>')
  process.exit(2)
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const v = (x, y, z) => new Vec3(x, y, z)

function serverCommand (command) {
  fs.appendFileSync(consoleFile, command + '\n')
}

function logSize () {
  return fs.statSync(logFile).size
}

/** Waits for the log, from byte `from` on, to match; returns the match. */
async function waitForLog (pattern, seconds, from) {
  const deadline = Date.now() + seconds * 1000
  while (Date.now() < deadline) {
    const found = fs.readFileSync(logFile).subarray(from).toString('utf8').match(pattern)
    if (found) return found
    await sleep(250)
  }
  throw new Error(`the server never logged ${pattern} within ${seconds}s`)
}

async function waitFor (test, seconds, what) {
  const deadline = Date.now() + seconds * 1000
  while (Date.now() < deadline) {
    if (test()) return
    await sleep(100)
  }
  // A function, for a description read when it fails, such as what the bot has heard by then.
  throw new Error(`${typeof what === 'function' ? what() : what} did not happen within ${seconds}s; ${where()}`)
}

/** What the server has said to the bot, oldest first, since the last call. */
const heard = []
function messages () {
  return heard.splice(0, heard.length)
}

let bot

function where () {
  const p = bot.entity.position
  return `the bot is at ${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}`
}

/** Within `across` blocks of `target` on the ground plane, and `up` blocks of its height. */
function near (target, across, up = 1.5) {
  const p = bot.entity.position
  return Math.hypot(p.x - target.x, p.z - target.z) <= across && Math.abs(p.y - target.y) <= up
}

async function teleport (x, y, z, yaw) {
  serverCommand(`tp ${name} ${x} ${y} ${z} ${yaw} 0`)
  await waitFor(() => near(v(x, y, z), 0.5), 10, `a teleport to ${x} ${y} ${z}`)
  // Let the chunks round it arrive before anything looks at blocks there.
  await sleep(1000)
}

/**
 * Walks on the level towards `target` until `done`, or until the bot stands on the target, and
 * stops. Whatever happens next (a ring's countdown, say) is the caller's to wait for.
 */
async function walk (target, done, seconds) {
  const deadline = Date.now() + seconds * 1000
  try {
    while (Date.now() < deadline) {
      if (done()) return
      const p = bot.entity.position
      if (Math.hypot(p.x - target.x, p.z - target.z) < 0.3) break
      await bot.lookAt(v(target.x, p.y + bot.entity.height * 0.9, target.z), true)
      bot.setControlState('forward', true)
      await sleep(50)
    }
  } finally {
    bot.clearControlStates()
  }
}

async function say (text) {
  console.log(text)
  if (observe) bot.chat(text)
}

// Someone watching: their name once they join, and where to put them for each trip.
let observer = null

/**
 * Says what the bot is doing now on the watcher's action bar, not in chat, and in the terminal.
 * The action bar fades in about two seconds, so the line is sent again until the next one.
 */
let doing = null
function narrate (text) {
  console.log(`  ${text}`)
  doing = text
  showDoing()
}
function showDoing () {
  if (observer && doing) serverCommand(`title ${observer} actionbar ${JSON.stringify({ text: doing, color: 'yellow' })}`)
}
setInterval(showDoing, 1500).unref()

async function waitForObserver () {
  console.log(`Waiting for someone to watch: join localhost:${port} with Minecraft ${version}, any name.`)
  await waitFor(() => Object.keys(bot.players).some((player) => player !== name), observeWait,
    'someone joining to watch')
  observer = Object.keys(bot.players).find((player) => player !== name)
  serverCommand(`gamemode spectator ${observer}`)
  await say(`${observer} is watching. After each trip, say y if you saw it happen as described, n if not.`)
}

async function showObserver (x, y, z, yaw, pitch) {
  if (observer) {
    serverCommand(`tp ${observer} ${x} ${y} ${z} ${yaw} ${pitch}`)
    await sleep(3000)
  }
}

async function askObserver (what) {
  if (!observer) return 'not watched'
  if (!bot.players[observer]) return 'left'
  let answer = null
  // Chat arrives as "<name> text" on every version Mineflayer speaks. Only a whole y, yes, n or no
  // counts, so a remark that happens to start with one is not taken as the answer.
  const listen = (text) => {
    const said = text.match(/^<([^>]+)> *(y|yes|n|no)[.!]? *$/i)
    if (said && said[1] === observer) answer = said[2][0].toLowerCase()
  }
  bot.on('messagestr', listen)
  bot.chat(`Did you see ${what}? y or n`)
  try {
    await waitFor(() => answer !== null || !bot.players[observer], observeWait, `${observer} answering`)
  } catch {
    return 'no answer'
  } finally {
    bot.removeListener('messagestr', listen)
  }
  if (answer === null) return 'left'
  return answer === 'y' ? 'saw it' : 'did NOT see it'
}

/** Asks the watcher to pick one of `choices`; null if they leave or do not answer in time. */
async function askObserverChoice (question, choices) {
  let answer = null
  const listen = (text) => {
    const said = text.match(/^<([^>]+)> *(\S+?)[.!]? *$/)
    if (said && said[1] === observer && choices.test(said[2].toLowerCase())) answer = said[2].toLowerCase()
  }
  bot.on('messagestr', listen)
  bot.chat(question)
  try {
    await waitFor(() => answer !== null || !bot.players[observer], observeWait, `${observer} choosing`)
  } catch {
    return null
  } finally {
    bot.removeListener('messagestr', listen)
  }
  return answer
}

/**
 * Lays an arrow pointing north into the ground, its tip at (x, tipZ), with an N beyond the tip,
 * so someone watching knows which way they face. Flush with the floor, so nothing walks into it.
 */
function compass (x, tipZ) {
  const y = -64
  const set = (dx, z, block) => serverCommand(`setblock ${x + dx} ${y} ${z} ${block}`)
  set(0, tipZ, 'red_concrete')
  for (let dx = -1; dx <= 1; dx++) set(dx, tipZ + 1, 'red_concrete')
  for (let dx = -2; dx <= 2; dx++) set(dx, tipZ + 2, 'red_concrete')
  for (let dz = 3; dz <= 6; dz++) set(0, tipZ + dz, 'red_concrete')
  // Read facing north: its left leg west, its right leg east, the diagonal from top left down.
  const top = tipZ - 7
  for (let i = 0; i < 5; i++) {
    set(-2, top + i, 'white_concrete')
    set(2, top + i, 'white_concrete')
    set(-2 + i, top + i, 'white_concrete')
  }
}

/**
 * Logs every change to the gate's blocks the bot is sent while tracing, with the time since the
 * dial, so a run shows whether the bot stepped in before the dialling was over.
 */
let traceStart = 0
function stamp () {
  return `+${((Date.now() - traceStart) / 1000).toFixed(1)}s`
}
function traceGate (box) {
  const describe = (block) => {
    if (!block) return 'nothing'
    const lit = block.getProperties ? block.getProperties().lit : undefined
    return lit === undefined ? block.name : `${block.name}${lit ? ' (lit)' : ' (off)'}`
  }
  const listener = (before, after) => {
    const p = after.position
    if (p.x < box.min.x || p.x > box.max.x || p.y < box.min.y || p.y > box.max.y || p.z < box.min.z || p.z > box.max.z) return
    const was = describe(before)
    const now = describe(after)
    if (was !== now) console.log(`    ${stamp()} ${p.x} ${p.y} ${p.z}: ${was} -> ${now}`)
  }
  bot.on('blockUpdate', listener)
  return () => bot.removeListener('blockUpdate', listener)
}

// ---------------------------------------------------------------------------------------------
// The trips. Each throws on the first thing that is not as it should be. `setup` runs once, the
// first time a trip runs; `run` can run again, for someone watching who asks to see it again.

/**
 * Two Standard gates built from the console where travel-boot.sh builds them, with a floor laid
 * in front of each. The bot presses Abydos's DHD, dials Chulak, and waits for the kawoosh to come
 * and go; the opening must not fill before it. It walks in, and must come out at Chulak's arrival
 * point; then Abydos must shut on its own.
 */
const gate = {
  name: 'gate',
  sight: 'WxBot press the DHD, Abydos open, WxBot walk in and come out of Chulak, then Abydos shut',
  vantage: [9.5, -53, 14.5, 180, 25],
  async setup () {
    compass(10, 5)
    const from = logSize()
    serverCommand('wx gate build Standard Abydos world 0 -60 0 south')
    serverCommand('wx gate build Standard Chulak world 20 -60 0 south')
    await waitForLog(/Built Abydos at 0 -60 0 in world\. Opening centred on -1\.5 -57\.5 -2\.5; arrivals at -1\.5 -60\.0 -1\.5/, 20, from)
    await waitForLog(/Built Chulak at 20 -60 0 in world\. Opening centred on 18\.5 -57\.5 -2\.5; arrivals at 18\.5 -60\.0 -1\.5/, 20, from)
    // The gates stand on nothing; floor the three rows in front of each at the opening's foot.
    // "keep" leaves the iris switch block under each DHD alone.
    serverCommand('fill -4 -61 -2 0 -61 2 stone keep')
    serverCommand('fill 16 -61 -2 20 -61 2 stone keep')
    await sleep(1000)
  },
  async run () {
    // Two rows back from the opening, clear of the kawoosh, with the DHD button beside.
    await teleport(-1.5, -60, 1.5, 180)
    const button = bot.blockAt(v(0, -60, 1))
    if (!button || !button.name.endsWith('_button')) {
      throw new Error(`no DHD button at 0 -60 1, but ${button ? button.name : 'an unloaded block'}`)
    }
    // Watched to see the gate open, so it must start empty, or that check proves nothing.
    const opening = v(-2, -58, -3)
    const kawoosh = v(-2, -60, -2)
    if (bot.blockAt(opening).name !== 'air') throw new Error(`Abydos's opening is ${bot.blockAt(opening).name} before dialling`)
    messages()
    traceStart = Date.now()
    const untrace = traceGate({ min: v(-6, -61, -6), max: v(3, -51, 2) })
    try {
      narrate("Pressing Abydos's DHD")
      await bot.activateBlock(button)
      await sleep(1000)
      traceStart = Date.now()
      bot.chat('/dial Chulak')
      narrate('Dialled Chulak; waiting for the chevrons to lock and the kawoosh')

      // The chevrons lock, the kawoosh splashes out in front and falls back, and only then does
      // the opening fill. A kawoosh lasts well under a second, so watch for it rather than poll.
      let kawooshAt = null
      let filledAt = null
      const watch = (before, after) => {
        if (after.name === 'air') return
        if (kawooshAt === null && after.position.equals(kawoosh)) kawooshAt = stamp()
        if (filledAt === null && after.position.equals(opening)) filledAt = stamp()
      }
      bot.on('blockUpdate', watch)
      try {
        await waitFor(() => kawooshAt !== null, 60, `the kawoosh (heard: ${JSON.stringify(heard)})`)
        narrate(`${kawooshAt} kawoosh; waiting for it to fall back`)
        await waitFor(() => bot.blockAt(kawoosh).name === 'air', 15, 'the kawoosh falling back')
        await waitFor(() => filledAt !== null || bot.blockAt(opening).name !== 'air', 15, 'Abydos\'s opening filling')
      } finally {
        bot.removeListener('blockUpdate', watch)
      }
      const early = filledAt !== null && Number(filledAt.slice(1, -1)) < Number(kawooshAt.slice(1, -1))
      narrate(`${stamp()} opening filled at ${filledAt}; walking in`)

      const chulak = v(18.5, -60, -1.5)
      await walk(v(-1.5, -60, -3.5), () => near(chulak, 1.5), 15)
      await waitFor(() => near(chulak, 1.5), 5, 'arriving at Chulak')
      narrate(`${stamp()} arrived at Chulak; waiting for Abydos to shut`)
      console.log(`  ${where()}`)

      await waitFor(() => bot.blockAt(opening).name === 'air', 90, 'Abydos shutting behind the bot')
      narrate(`${stamp()} Abydos shut`)
      // Checked last, so a watcher still sees the whole trip.
      if (early) throw new Error(`Abydos's opening filled at ${filledAt}, before the kawoosh at ${kawooshAt}`)
    } finally {
      untrace()
    }
  }
}

/**
 * A beam destination saved where the bot stands facing east, then beamed to from twenty blocks
 * off: the bot must land there, facing east.
 */
const beam = {
  name: 'beam',
  sight: 'WxBot vanish on your left (west) and land on your right (east), facing right (east); the arrow points north, away from you',
  vantage: [0.5, -57, 32.5, 180, 25],
  set: [],
  async setup () {
    compass(0, 22)
    narrate('Saving a beam destination, Home, here, facing east')
    await teleport(10.5, -63, 20.5, -90)
    messages()
    bot.chat('/wormhole beam admin set Home')
    await sleep(1500)
    this.set = messages()
  },
  async run () {
    const set = this.set
    narrate('Moving to the west spot')
    await teleport(-10.5, -63, 20.5, 0)
    messages()
    narrate('Beaming to Home')
    bot.chat('/wormhole beam to Home')
    await waitFor(() => near(v(10.5, -63, 20.5), 1), 30,
      `beaming to Home (setting it said ${JSON.stringify(set)}; beaming said ${JSON.stringify(heard)})`)
    // Mineflayer keeps yaw in radians, turning the other way from 180 degrees on: Bukkit's -90, east,
    // is its 3/2 pi.
    const off = Math.abs(((bot.entity.yaw - 1.5 * Math.PI) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI)
    if (off > 0.1) throw new Error(`arrived facing ${bot.entity.yaw.toFixed(2)} radians, not east (${(1.5 * Math.PI).toFixed(2)})`)
    narrate('Landed at Home, facing east')
  }
}

/** The perimeter of the odd ring pattern, as RingPattern.ODD works it out, as [dx, dz]. */
function ringPerimeter () {
  const profile = [3, 5, 7, 7, 7, 5, 3]
  const size = profile.length
  const filled = profile.map((width) => {
    const start = (size - width) / 2
    return Array.from({ length: size }, (_, column) => column >= start && column < start + width)
  })
  const at = (row, column) => row >= 0 && row < size && column >= 0 && column < size && filled[row][column]
  const edge = []
  for (let row = 0; row < size; row++) {
    for (let column = 0; column < size; column++) {
      if (at(row, column) && !(at(row - 1, column) && at(row + 1, column) && at(row, column - 1) && at(row, column + 1))) {
        edge.push([column - 3, row - 3])
      }
    }
  }
  return edge
}

/**
 * Two circles of slabs laid from the console and made into a ring pair by the bot standing in
 * each; then the bot walks into the first from outside and must come out in the second.
 */
const ring = {
  name: 'ring',
  sight: 'WxBot walk into the left ring, the rings rise, and WxBot come out in the right ring',
  vantage: [15.5, -55, 54.5, 180, 30],
  async setup () {
    compass(15, 44)
    narrate('Laying two circles of slabs and making them a ring pair')
    const anchors = [0, 30]
    for (const x of anchors) {
      for (const [dx, dz] of ringPerimeter()) {
        serverCommand(`setblock ${x + dx} -63 ${40 + dz} smooth_stone_slab`)
      }
    }
    await sleep(1000)
    const made = []
    for (const x of anchors) {
      await teleport(x + 0.5, -63, 40.5, 0)
      messages()
      bot.chat('/wormhole ring create')
      await sleep(2000)
      made.push(...messages())
    }
    if (!made.some((line) => line.includes('is live'))) throw new Error(`the pair was not made: ${JSON.stringify(made)}`)
  },
  async run () {
    const partner = v(30.5, -63, 40.5)
    // A ring used a moment ago recharges first, and says for how long; wait that out and go again.
    for (let attempt = 1; ; attempt++) {
      await teleport(0.5, -63, 34.5, 0)
      await sleep(2000)
      messages()
      narrate('Walking into the left ring; its countdown runs before the rings rise')
      // The countdown is 100 ticks by default before the rings even rise.
      await walk(v(0.5, -63, 40.5), () => near(partner, 3), 10)
      const until = Date.now() + 30000
      let recharge = null
      while (Date.now() < until && !near(partner, 3) && !recharge) {
        recharge = heard.join(' ').match(/Ready in (\d+) seconds?/)
        await sleep(100)
      }
      if (near(partner, 3)) break
      if (!recharge || attempt > 2) throw new Error(`arriving in the partner ring did not happen within 30s (heard: ${JSON.stringify(heard)}); ${where()}`)
      narrate(`The rings are recharging; waiting ${recharge[1]} s to go again`)
      await teleport(0.5, -63, 34.5, 0)
      await sleep((Number(recharge[1]) + 1) * 1000)
    }
    narrate('Came out in the right ring')
    console.log(`  ${where()}`)
  }
}

// ---------------------------------------------------------------------------------------------
// More gates. Each pair is dialled and walked through as the gate trip does, by dialAndOpen and
// walkThrough, and each trip ends by waiting for the gate to shut, so it can be run again.

/** What the bot sees at a block: its name, or "unloaded". */
function nameAt (pos) {
  const block = bot.blockAt(pos)
  return block ? block.name : 'unloaded'
}

function inside (box, p) {
  return p.x >= box.min.x && p.x <= box.max.x && p.y >= box.min.y && p.y <= box.max.y && p.z >= box.min.z && p.z <= box.max.z
}

/**
 * The places that matter on a Standard gate facing south with its DHD button hung on the block at
 * (bx, -60, bz), as `wx gate build Standard <name> world bx -60 bz south` builds it: the gate trip's
 * numbers, moved.
 */
function standardGate (bx, bz) {
  return {
    button: v(bx, -60, bz + 1),
    lever: v(bx, -61, bz + 1),
    stand: [bx - 1.5, -60, bz + 1.5],
    opening: v(bx - 2, -58, bz - 3),
    kawoosh: v(bx - 2, -60, bz - 2),
    arrival: v(bx - 1.5, -60, bz - 1.5),
    into: v(bx - 1.5, -60, bz - 3.5),
    box: { min: v(bx - 6, -61, bz - 6), max: v(bx + 3, -51, bz + 2) },
    // The three rows in front at the opening's foot; "keep" leaves the iris switch block alone.
    floor: `fill ${bx - 4} -61 ${bz - 2} ${bx} -61 ${bz + 2} stone keep`
  }
}

/** Stands the bot on the ground under a trip's vantage, so its chunks are loaded to set blocks in. */
async function visit (vantage) {
  await teleport(vantage[0], -63, vantage[2], 180)
}

/**
 * Presses a gate's DHD, dials, and waits for the kawoosh to come and fall back and the opening to
 * fill; the opening must be empty before, and must not fill before the kawoosh. Returns what
 * filled it and every block the gate's area turned into on the way.
 */
async function dialAndOpen (gate, label, dial) {
  await teleport(...gate.stand, 180)
  const button = bot.blockAt(gate.button)
  if (!button || !button.name.endsWith('_button')) {
    throw new Error(`no DHD button at ${gate.button}, but ${button ? button.name : 'an unloaded block'}`)
  }
  if (nameAt(gate.opening) !== 'air') throw new Error(`${label}'s opening is ${nameAt(gate.opening)} before dialling`)
  messages()
  let kawooshAt = null
  let filledAt = null
  const seen = new Set()
  const watch = (before, after) => {
    if (!after || after.name === 'air') return
    if (inside(gate.box, after.position)) seen.add(after.name)
    if (kawooshAt === null && after.position.equals(gate.kawoosh)) kawooshAt = stamp()
    if (filledAt === null && after.position.equals(gate.opening)) filledAt = stamp()
  }
  traceStart = Date.now()
  const untrace = traceGate(gate.box)
  bot.on('blockUpdate', watch)
  try {
    narrate(`Pressing ${label}'s DHD`)
    await bot.activateBlock(button)
    await sleep(1000)
    traceStart = Date.now()
    bot.chat(`/dial ${dial}`)
    narrate(`Dialled ${dial.split(' ')[0]}; waiting for the chevrons to lock and the kawoosh`)
    await waitFor(() => kawooshAt !== null, 60, () => `the kawoosh (heard: ${JSON.stringify(heard)})`)
    narrate(`${kawooshAt} kawoosh; waiting for it to fall back`)
    await waitFor(() => nameAt(gate.kawoosh) === 'air', 15, 'the kawoosh falling back')
    await waitFor(() => filledAt !== null || nameAt(gate.opening) !== 'air', 15, `${label}'s opening filling`)
  } finally {
    bot.removeListener('blockUpdate', watch)
    untrace()
  }
  if (filledAt !== null && Number(filledAt.slice(1, -1)) < Number(kawooshAt.slice(1, -1))) {
    throw new Error(`${label}'s opening filled at ${filledAt}, before the kawoosh at ${kawooshAt}`)
  }
  const portal = nameAt(gate.opening)
  narrate(`${stamp()} ${label}'s opening is ${portal}`)
  return { portal, seen }
}

/** Walks into a gate's opening, and must come out within 1.5 blocks of `arrival`. */
async function walkThrough (gate, arrival, label) {
  narrate(`Walking in; should come out at ${label}`)
  await walk(gate.into, () => near(arrival, 1.5), 15)
  await waitFor(() => near(arrival, 1.5), 5, `arriving at ${label}`)
  narrate(`${stamp()} arrived at ${label}`)
  console.log(`  ${where()}`)
}

/** Waits, standing where it can see it, for an opening to empty once the gate shuts on its own. */
async function waitForShut (opening, label) {
  narrate(`Waiting for ${label} to shut`)
  await waitFor(() => nameAt(opening) === 'air', 90, `${label} shutting`)
  narrate(`${label} shut`)
}

/**
 * A gate pair in the Atlantis group, built as a player builds one: a DHD button hung on a block of
 * the group's frame, `/wormhole gate build Standard Atlantis` looking at it to stand the preview on
 * it, `preview place` and `gate complete`. The frame must come out lapis, the chevrons must light
 * as sea lanterns rather than the Standard group's lamps, and the bot must still travel.
 */
const atlantis = {
  name: 'atlantis',
  sight: 'WxBot build a blue (lapis) gate on the left, dial the one on the right, its chevrons light as sea lanterns, and WxBot walk in on the left and come out on the right',
  vantage: [79.5, -53, 14.5, 180, 25],
  gates: [['Lantea', 70, 0], ['Asuras', 90, 0]],
  async setup () {
    await visit(this.vantage)
    compass(80, 5)
    for (const [label, bx, bz] of this.gates) {
      const gate = standardGate(bx, bz)
      // Somewhere to stand while building, the rest of the floor being in the gate's way; set
      // from where it loads the chunk.
      await teleport(gate.stand[0], -63, gate.stand[2], 180)
      serverCommand(`setblock ${bx} -60 ${bz} lapis_block`)
      serverCommand(`setblock ${bx} -60 ${bz + 1} stone_button[face=wall,facing=south]`)
      serverCommand(`fill ${bx - 4} -61 ${bz + 1} ${bx} -61 ${bz + 2} stone`)
      await sleep(500)
      // Square in front of the button: it is a sliver against the block it hangs on, which the
      // server's line of sight must meet.
      await teleport(gate.button.x + 0.5, -60, gate.button.z + 1.5, 180)
      messages()
      narrate(`Standing a Standard preview in Atlantis on ${label}'s DHD`)
      await bot.lookAt(gate.button.offset(0.5, 0.5, 0.06), true)
      // Mineflayer sends a turn with its next movement, so let it go before the command.
      await sleep(300)
      bot.chat('/wormhole gate build Standard Atlantis')
      await sleep(1500)
      const shown = messages()
      if (!shown.some((line) => line.includes('on your DHD'))) {
        throw new Error(`the preview was not stood on ${label}'s DHD: ${JSON.stringify(shown)}`)
      }
      narrate(`Placing the preview and naming it ${label}`)
      await teleport(...gate.stand, 180)
      await bot.lookAt(gate.opening.offset(0.5, 0.5, 0.5), true)
      await sleep(300)
      bot.chat('/wormhole gate preview place')
      await sleep(1500)
      bot.chat(`/wormhole gate complete ${label}`)
      await sleep(1500)
      const said = messages()
      if (!said.some((line) => line.includes('successfully constructed'))) {
        throw new Error(`${label} was not built: ${JSON.stringify(said)}`)
      }
      // A plain frame block, top left of the ring: the group's frame, not obsidian.
      const frame = nameAt(v(bx - 3, -55, bz - 3))
      if (frame !== 'lapis_block') throw new Error(`${label}'s frame is ${frame}, not lapis_block`)
      serverCommand(gate.floor)
    }
    await sleep(1000)
  },
  async run () {
    const from = standardGate(...this.gates[0].slice(1))
    const to = standardGate(...this.gates[1].slice(1))
    const { portal, seen } = await dialAndOpen(from, 'Lantea', 'Asuras')
    if (portal !== 'water') throw new Error(`Lantea's opening filled with ${portal}, not water`)
    if (!seen.has('sea_lantern')) throw new Error(`no chevron lit as a sea lantern; the gate showed ${[...seen].join(', ')}`)
    await walkThrough(from, to.arrival, 'Asuras')
    await waitForShut(to.opening, 'Asuras')
  }
}

/**
 * Two Horizontal gates, which lie flat in the floor, built from the console. The bot dials one,
 * steps off the floor into its opening after the kawoosh, and must come out at the other's arrival
 * point, which the console's build line gives.
 */
const horizontal = {
  name: 'horizontal',
  sight: 'WxBot dial the flat gate on the left, the kawoosh rise out of the floor, and WxBot drop in and come out of the flat gate on the right',
  vantage: [10.5, -52, 92.5, 180, 40],
  gates: [['Edora', 0, 80], ['Langara', 20, 80]],
  arrivals: [],
  /** Holder (bx, -63, bz), facing south: seven layers north from the DHD's, the opening one block deep. */
  gate (bx, bz) {
    return {
      button: v(bx, -63, bz + 1),
      stand: [bx + 2.5, -62, bz + 2.5],
      opening: v(bx + 1, -63, bz - 3),
      kawoosh: v(bx + 1, -62, bz - 3),
      into: v(bx + 2.5, -62, bz - 2.5),
      box: { min: v(bx - 3, -64, bz - 7), max: v(bx + 5, -58, bz + 2) }
    }
  },
  async setup () {
    await visit(this.vantage)
    compass(10, 80)
    this.arrivals = []
    for (const [label, bx, bz] of this.gates) {
      // A floor flush with the gate's top, the gate's own seven by seven left clear to build in,
      // and the row its button and sign hang on filled back round them afterwards.
      serverCommand(`fill ${bx - 4} -63 ${bz - 8} ${bx + 6} -63 ${bz + 3} stone`)
      serverCommand(`fill ${bx - 2} -63 ${bz - 6} ${bx + 4} -63 ${bz + 1} air`)
      const from = logSize()
      serverCommand(`wx gate build Horizontal ${label} world ${bx} -63 ${bz} south`)
      const built = await waitForLog(new RegExp(`Built ${label} at ${bx} -63 ${bz} in world\\. Opening centred on \\S+ \\S+ \\S+; arrivals at (\\S+) (\\S+) (\\S+)\\.`), 20, from)
      this.arrivals.push(v(Number(built[1]), Number(built[2]), Number(built[3])))
      serverCommand(`fill ${bx - 2} -63 ${bz + 1} ${bx + 4} -63 ${bz + 1} stone keep`)
    }
    console.log(`  arrivals: ${this.arrivals.join(', ')}`)
    await sleep(1000)
  },
  async run () {
    const from = this.gate(this.gates[0][1], this.gates[0][2])
    const to = this.gate(this.gates[1][1], this.gates[1][2])
    const { portal } = await dialAndOpen(from, 'Edora', 'Langara')
    if (portal !== 'water') throw new Error(`Edora's opening filled with ${portal}, not water`)
    await walkThrough(from, this.arrivals[1], 'Langara')
    await waitForShut(to.opening, 'Langara')
  }
}

/**
 * A pair whose portals are not water: Netu's set to lava and Vorash's to nether portal from the
 * console. The bot must see lava fill Netu's opening after the kawoosh, still travel through it,
 * and see Vorash's opening as nether portal when it comes out.
 */
const lava = {
  name: 'lava',
  sight: 'WxBot dial the left gate, lava fill it after the kawoosh, and WxBot walk into the lava and come out of the right gate, which shows a purple nether portal',
  vantage: [79.5, -53, 54.5, 180, 25],
  async setup () {
    await visit(this.vantage)
    compass(80, 45)
    const from = logSize()
    serverCommand('wx gate build Standard Netu world 70 -60 40 south')
    serverCommand('wx gate build Standard Vorash world 90 -60 40 south')
    await waitForLog(/Built Netu at 70 -60 40/, 20, from)
    await waitForLog(/Built Vorash at 90 -60 40/, 20, from)
    // A gate's own materials are used only once it is in custom mode.
    for (const [label, material] of [['Netu', 'LAVA'], ['Vorash', 'NETHER_PORTAL']]) {
      serverCommand(`wx gate edit ${label} custom true`)
      serverCommand(`wx gate edit ${label} portal ${material}`)
      await waitForLog(new RegExp(`${label} portal material set to: ${material}`), 20, from)
    }
    serverCommand(standardGate(70, 40).floor)
    serverCommand(standardGate(90, 40).floor)
    await sleep(1000)
  },
  async run () {
    const from = standardGate(70, 40)
    const to = standardGate(90, 40)
    const { portal } = await dialAndOpen(from, 'Netu', 'Vorash')
    if (portal !== 'lava') throw new Error(`Netu's opening filled with ${portal}, not lava`)
    await walkThrough(from, to.arrival, 'Vorash')
    await waitFor(() => nameAt(to.opening) === 'nether_portal', 5, () => `Vorash's opening showing nether portal (it is ${nameAt(to.opening)})`)
    narrate("Vorash's opening is nether portal")
    await waitForShut(to.opening, 'Vorash')
  }
}

/**
 * Cimmeria dials Tollan, whose iris has a code, 1234, and a lever. With Tollan's iris shut, the
 * bot sees it drawn in stone across Tollan's opening, and a dial without the code is refused. With
 * the code the dial opens it; then the bot shuts it again at Tollan's lever, and walking into
 * Cimmeria must bounce it back ("Remote Iris is locked!"), not take it to Tollan. It opens the
 * iris at the lever, and then walking in must take it to Tollan.
 */
const iris = {
  name: 'iris',
  sight: "WxBot shut the right gate's iris (stone), fail to dial it, dial it with the code, shut the iris again and bounce off the left gate, then open the iris and come out of the right gate",
  vantage: [79.5, -53, 94.5, 180, 25],
  async setup () {
    await visit(this.vantage)
    compass(80, 85)
    const from = logSize()
    serverCommand('wx gate build Standard Cimmeria world 70 -60 80 south')
    serverCommand('wx gate build Standard Tollan world 90 -60 80 south idc=1234')
    await waitForLog(/Built Cimmeria at 70 -60 80/, 20, from)
    await waitForLog(/Built Tollan at 90 -60 80/, 20, from)
    serverCommand(standardGate(70, 80).floor)
    serverCommand(standardGate(90, 80).floor)
    await sleep(1000)
  },
  /** Flips Tollan's iris lever from in front of Tollan, and waits to see its opening become `shows`. */
  async flipIris (tollan, shows, why) {
    await teleport(...tollan.stand, 180)
    const lever = bot.blockAt(tollan.lever)
    if (!lever || lever.name !== 'lever') throw new Error(`no iris lever at ${tollan.lever}, but ${lever ? lever.name : 'an unloaded block'}`)
    narrate(`At Tollan's iris lever: ${why}`)
    messages()
    await bot.activateBlock(lever)
    await waitFor(() => nameAt(tollan.opening) === shows, 10,
      () => `Tollan's opening showing ${shows} (it shows ${nameAt(tollan.opening)}; heard ${JSON.stringify(heard)})`)
    narrate(`Tollan's opening shows ${shows}`)
  },
  async run () {
    const cimmeria = standardGate(70, 80)
    const tollan = standardGate(90, 80)
    await teleport(...tollan.stand, 180)
    // A run cut short can leave the iris either way; it starts shut.
    if (nameAt(tollan.opening) === 'air') await this.flipIris(tollan, 'stone', 'shutting the iris')
    if (nameAt(tollan.opening) !== 'stone') throw new Error(`Tollan's shut iris shows ${nameAt(tollan.opening)}, not stone`)

    await teleport(...cimmeria.stand, 180)
    narrate("Pressing Cimmeria's DHD and dialling Tollan without the code")
    messages()
    await bot.activateBlock(bot.blockAt(cimmeria.button))
    await sleep(1000)
    bot.chat('/dial Tollan')
    await waitFor(() => heard.some((line) => line.includes('Remote Iris is active')), 10,
      () => `the dial being refused (heard ${JSON.stringify(heard)})`)
    await sleep(2000)
    if (nameAt(cimmeria.opening) !== 'air') throw new Error(`Cimmeria opened (${nameAt(cimmeria.opening)}) onto a shut iris`)
    narrate('Refused: the remote iris is shut')
    await sleep(1000)

    const { portal } = await dialAndOpen(cimmeria, 'Cimmeria', 'Tollan 1234')
    if (portal !== 'water') throw new Error(`Cimmeria's opening filled with ${portal}, not water`)
    // Heard since dialAndOpen emptied the list, so this is the dial with the code.
    if (!heard.some((line) => line.includes('IDC accepted'))) throw new Error(`the code was not accepted (heard ${JSON.stringify(heard)})`)

    await this.flipIris(tollan, 'stone', 'shutting the iris over the open wormhole')
    await teleport(...cimmeria.stand, 180)
    messages()
    narrate('Walking into Cimmeria with Tollan\'s iris shut; should bounce back')
    await walk(cimmeria.into, () => near(tollan.arrival, 1.5) || heard.some((line) => line.includes('Remote Iris is locked')), 10)
    await sleep(2000)
    if (near(tollan.arrival, 3)) throw new Error('came out at Tollan through a shut iris')
    if (!heard.some((line) => line.includes('Remote Iris is locked'))) {
      throw new Error(`walking in was not refused by the iris (heard ${JSON.stringify(heard)}); ${where()}`)
    }
    narrate('Bounced: "Remote Iris is locked!"')

    await this.flipIris(tollan, 'water', 'opening the iris')
    await teleport(...cimmeria.stand, 180)
    await walkThrough(cimmeria, tollan.arrival, 'Tollan')
    await waitForShut(tollan.opening, 'Tollan')
  }
}

/**
 * Two quantum mirrors, Alpha and Beta, made by the bot from white wall banners hung on stone walls,
 * with a gold block standing in Beta's room. At Alpha the bot must not see gold behind the wall
 * while Alpha shows its own room; right-clicked, Alpha must show Beta's room, gold block and all;
 * punched, it must put the bot at Beta's banner.
 */
const mirror = {
  name: 'mirror',
  sight: 'WxBot right-click the left banner, which opens onto the room of the right one (a gold block), then punch it and appear at the right banner',
  vantage: [20.5, -56, 128.5, 180, 20],
  banners: [['Alpha', 0, 110], ['Beta', 40, 110]],
  async setup () {
    await visit(this.vantage)
    compass(20, 118)
    const from = logSize()
    serverCommand('wx config mirror-per-world-limit 0')
    await waitForLog(/MIRROR_PER_WORLD_LIMIT is now 0/, 20, from)
    for (const [, x, z] of this.banners) {
      // Setting blocks needs the chunk loaded, which a player nearby does.
      await teleport(x + 0.5, -63, z + 3.5, 180)
      // Two blocks of wall out on every side of the opening, and three deep.
      serverCommand(`fill ${x - 3} -64 ${z - 3} ${x + 3} -59 ${z - 1} stone`)
      serverCommand(`setblock ${x} -62 ${z} white_wall_banner[facing=south]`)
    }
    serverCommand('setblock 40 -63 113 gold_block')
    await sleep(1000)
    for (const [label, x, z] of this.banners) {
      await teleport(x + 0.5, -63, z + 3.5, 180)
      if (!nameAt(v(x, -62, z)).endsWith('wall_banner')) throw new Error(`no banner at ${x} -62 ${z}, but ${nameAt(v(x, -62, z))}`)
      narrate(`Making the banner a mirror called ${label}`)
      await bot.lookAt(v(x + 0.5, -61.8, z + 0.9), true)
      await sleep(300)
      messages()
      bot.chat(`/wormhole mirror create ${label}`)
      await sleep(2000)
      const said = messages()
      if (said.some((line) => /cannot|needs|not a banner|Look at/i.test(line)) || said.length === 0) {
        throw new Error(`${label} was not made: ${JSON.stringify(said)}`)
      }
      console.log(`  ${JSON.stringify(said)}`)
    }
    // Its capture is taken within a second of being made.
    await sleep(2000)
  },
  async run () {
    const [[, ax, az], [, bx, bz]] = this.banners
    const behind = { min: v(ax - 20, -64, az - 40), max: v(ax + 20, -40, az - 1) }
    const gold = () => {
      for (let x = behind.min.x; x <= behind.max.x; x++) {
        for (let z = behind.min.z; z <= behind.max.z; z++) {
          for (let y = behind.min.y; y <= -56; y++) {
            const block = bot.blockAt(v(x, y, z))
            if (block && block.name === 'gold_block') return block.position
          }
        }
      }
      return null
    }
    await teleport(ax + 0.5, -63, az + 3.5, 180)
    narrate('At Alpha, which shows its own room')
    await sleep(3000)
    if (gold()) throw new Error(`gold at ${gold()} behind Alpha before it was turned to Beta`)
    const banner = v(ax, -62, az)
    messages()
    narrate('Right-clicking Alpha to open it onto Beta')
    await bot.lookAt(banner.offset(0.5, 0.2, 0.9), true)
    // Its answer, above the hotbar, says the click was taken. Once in a full run it was not heard and
    // nothing changed, so a click that goes unanswered is tried again, as a player would, and said.
    const chose = () => heard.some((line) => line.includes("opens onto 'Beta'"))
    for (let click = 1; !chose(); click++) {
      if (click > 3) throw new Error(`three right-clicks on Alpha went unanswered (heard ${JSON.stringify(heard)}; the banner reads ${nameAt(banner)})`)
      if (click > 1) narrate(`Right-click ${click - 1} went unanswered; clicking again`)
      await bot.activateBlock(bot.blockAt(banner), v(0, 0, 1), v(0.5, 0.2, 1))
      const until = Date.now() + 4000
      while (Date.now() < until && !chose()) await sleep(100)
    }
    try {
      await waitFor(() => gold() !== null, 10, "Beta's gold block showing behind Alpha")
    } catch (e) {
      let drawn = 0
      for (let x = ax - 5; x <= ax + 5; x++) for (let z = az - 15; z <= az - 4; z++) for (let y = -64; y <= -58; y++) if (nameAt(v(x, y, z)) !== 'air') drawn++
      throw new Error(`${e.message} (heard ${JSON.stringify(heard)}; ${drawn} blocks drawn behind the wall; the banner reads ${nameAt(banner)})`)
    }
    narrate(`Beta's room shows through Alpha: gold at ${gold()}`)
    await sleep(2000)
    narrate('Punching Alpha to go through')
    await bot.lookAt(banner.offset(0.5, 0.2, 0.9), true)
    bot._client.write('block_dig', { status: 0, location: banner, face: 3 })
    bot.swingArm()
    bot._client.write('block_dig', { status: 1, location: banner, face: 3 })
    const beta = v(bx + 0.5, -63, bz + 0.5)
    await waitFor(() => near(beta, 1.5), 10, () => `arriving at Beta's banner (heard ${JSON.stringify(heard)})`)
    narrate('Came out at Beta')
    console.log(`  ${where()}`)
  }
}

// ---------------------------------------------------------------------------------------------

/** Clears creatures a grass world spawns, sparing the plugin's own display entities. */
function clearMobs () {
  serverCommand('kill @e[type=!player,type=!block_display,type=!item_display,type=!text_display,type=!interaction]')
}

async function main () {
  bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, auth: 'offline', version })
  bot.on('messagestr', (text) => heard.push(text))
  const gone = new Promise((resolve, reject) => {
    bot.once('kicked', (reason) => reject(new Error(`kicked: ${JSON.stringify(reason)}`)))
    bot.once('end', (reason) => reject(new Error(`disconnected: ${reason}`)))
    // on, not once: a second error with no listener would end the process before the summary.
    bot.on('error', reject)
  })
  gone.catch(() => {})
  await Promise.race([new Promise((resolve) => bot.once('spawn', resolve)), gone,
    sleep(60000).then(() => { throw new Error('never spawned within 60s') })])

  const from = logSize()
  serverCommand(`op ${name}`)
  await waitForLog(new RegExp(`Made ${name} a server operator`), 20, from)
  // Creative, so a kawoosh or a fall cannot kill it partway; peaceful and day for anyone watching.
  serverCommand(`gamemode creative ${name}`)
  serverCommand('difficulty peaceful')
  serverCommand('time set day')
  // A grass floor spawns animals, which would wander into a gate or a ring. The rule was renamed
  // in 1.21.11; whichever name this server does not know is only an error in its log.
  serverCommand('gamerule doMobSpawning false')
  serverCommand('gamerule spawn_mobs false')
  clearMobs()
  await sleep(2000)

  if (observe) await waitForObserver()

  const every = [gate, beam, ring, atlantis, horizontal, lava, iris, mirror]
  const trips = only ? only.map((n) => every.find((t) => t.name === n)).filter(Boolean) : every
  const results = []
  const setUp = new Set()
  async function take (trip, again) {
    await showObserver(...trip.vantage)
    await say(`Trip: ${trip.name}${again ? ', again' : ''}. Watch for ${trip.sight}.`)
    let outcome = 'PASS'
    let detail = ''
    try {
      if (!setUp.has(trip)) {
        setUp.add(trip)
        clearMobs()
        await Promise.race([trip.setup(), gone])
      }
      await Promise.race([trip.run(), gone])
    } catch (e) {
      outcome = 'FAIL'
      detail = e.message
    }
    bot.clearControlStates()
    console.log(`${outcome} ${trip.name}${detail ? ': ' + detail : ''}`)
    const seen = observe ? await askObserver(trip.sight) : 'not watched'
    results.push({ trip: trip.name + (again ? '*' : ''), outcome, seen, detail })
  }
  for (const trip of trips) await take(trip, setUp.has(trip))

  // Someone watching can see any trip again before the server stops.
  while (observe && bot.players[observer]) {
    const choice = await askObserverChoice(
      `Say ${every.map((t) => t.name).join(', ')} to see that trip again, all for every one, or done to stop.`,
      /^(gate|beam|ring|atlantis|horizontal|lava|iris|mirror|all|again|done|stop)$/)
    if (!choice || choice === 'done' || choice === 'stop') break
    const again = (choice === 'all' || choice === 'again') ? [...new Set(trips)] : every.filter((t) => t.name === choice)
    for (const trip of again) await take(trip, true)
  }

  console.log('\nplayer journeys on ' + version + ':')
  for (const r of results) {
    console.log(`  ${r.outcome.padEnd(4)} ${r.trip.padEnd(11)} observer: ${r.seen}${r.detail ? '  (' + r.detail + ')' : ''}`)
  }
  // Watched, only a "y" counts: no answer, or a watcher who left, did not confirm anything.
  const failed = results.some((r) => r.outcome !== 'PASS' || (observe && r.seen !== 'saw it'))
  if (observe) await say(failed ? 'Some trips failed; see the summary in the terminal.' : 'All trips passed. Stopping the server.')
  bot.quit()
  await sleep(1000)
  process.exit(failed ? 1 : 0)
}

main().catch((e) => {
  console.error('player journeys could not run: ' + e.message)
  process.exit(1)
})
