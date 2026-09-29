'use strict'
// What journeys.js and the lab (lab.js) share: the bot, the console, and every step a trip is made
// of. A lab run and a CI run go through the same code here, given different gates and travellers,
// so the lab cannot drift from what CI checks.
//
// Both are started through boot-test.sh, which passes BOOT_CONSOLE, a file whose new lines go to
// the server console, and BOOT_LOG, the server's log.

const fs = require('fs')
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')

const consoleFile = process.env.BOOT_CONSOLE
const logFile = process.env.BOOT_LOG
const port = Number(process.env.BOOT_PORT || 25599)
const name = 'WxBot'
// Set by configure(): whether anyone watches, how long to wait for them, and the version.
const settings = { observe: false, observeWait: 600, version: null }

function configure (options) {
  Object.assign(settings, options)
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const v = (x, y, z) => new Vec3(x, y, z)

function serverCommand (command) {
  fs.appendFileSync(consoleFile, command + '\n')
}

function logSize () {
  return fs.statSync(logFile).size
}

/** What the server has logged from byte `from` on. */
function logSince (from) {
  return fs.readFileSync(logFile).subarray(from).toString('utf8')
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
  if (settings.observe) bot.chat(text)
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
  console.log(`Waiting for someone to watch: join localhost:${port} with Minecraft ${settings.version}, any name.`)
  await waitFor(() => Object.keys(bot.players).some((player) => player !== name), settings.observeWait,
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
    await waitFor(() => answer !== null || !bot.players[observer], settings.observeWait, `${observer} answering`)
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
    await waitFor(() => answer !== null || !bot.players[observer], settings.observeWait, `${observer} choosing`)
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
/** Starts the clock stamp() counts from. */
function startClock () {
  traceStart = Date.now()
}
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

/**
 * The perimeter of a ring pattern, as RingPattern works it out, as [dx, dz] from the anchor: the
 * row profile of the pattern's filled squares, and the squares on its edge. ODD is 7 across, EVEN
 * 6 across and off centre by half a block, its anchor the square just north-west of the middle.
 */
const ringProfiles = { ODD: [3, 5, 7, 7, 7, 5, 3], EVEN: [2, 4, 6, 6, 4, 2] }
function ringPerimeter (pattern = 'ODD') {
  const profile = ringProfiles[pattern]
  const size = profile.length
  const half = Math.floor((size - 1) / 2)
  const filled = profile.map((width) => {
    const start = (size - width) / 2
    return Array.from({ length: size }, (_, column) => column >= start && column < start + width)
  })
  const at = (row, column) => row >= 0 && row < size && column >= 0 && column < size && filled[row][column]
  const edge = []
  for (let row = 0; row < size; row++) {
    for (let column = 0; column < size; column++) {
      if (at(row, column) && !(at(row - 1, column) && at(row + 1, column) && at(row, column - 1) && at(row, column + 1))) {
        edge.push([column - half, row - half])
      }
    }
  }
  return edge
}

/** What the bot sees at a block: its name, or "unloaded". */
function nameAt (pos) {
  const block = bot.blockAt(pos)
  return block ? block.name : 'unloaded'
}

function inside (box, p) {
  return p.x >= box.min.x && p.x <= box.max.x && p.y >= box.min.y && p.y <= box.max.y && p.z >= box.min.z && p.z <= box.max.z
}

/**
 * Each shipped shape built facing south with its DHD button hung on the block at the origin, as
 * `wx gate build <shape> <name> world 0 0 0 south` or a player's preview builds it: the frame's
 * extent, the middle of the opening and the arrival point, as the console's "Built" line gives
 * them, and the iris switch. Standard is the gate trip's own; the rest are worked out from the
 * shape files. A SignDial shape is its plain shape plus a dial sign on `dial` and redstone at `rd`.
 */
const shapes = {
  Standard: { frame: [[-5, 1], [-1, 5], [-3, 0]], centre: [-1.5, 2.5, -2.5], arrival: [-1.5, 0, -1.5], iris: [0, -1, 0] },
  Large: { frame: [[-7, 2], [-1, 8], [-4, 0]], centre: [-2.0, 4.0, -3.5], arrival: [-2.5, 0, -2.5], iris: [0, -1, 0] },
  Grand: { frame: [[-15, 6], [-2, 19], [-10, 0]], centre: [-4.0, 9.3, -8.5], arrival: [-4.5, 1, -7.5], iris: [0, -1, 0] },
  Massive: { frame: [[-16, 6], [-1, 21], [-8, 0]], centre: [-4.5, 10.5, -6.5], arrival: [-4.5, 2, -3.5], iris: [0, -1, 0] },
  Minimal: { frame: [[-1, 0], [-2, 0], [-1, 0]], centre: [-0.5, 0.0, -0.5], arrival: [-0.5, -1, 0.5], iris: [0, -1, 0] },
  Horizontal: { frame: [[-2, 4], [0, 0], [-6, 0]], centre: [1.5, 0.5, -2.5], arrival: [1.5, 1, -1.5], iris: [2, 0, 0], flat: true }
}
shapes.StandardSignDial = { ...shapes.Standard, frame: [[-5, 1], [-1, 5], [-3, 0]], sign: true }
shapes.MinimalSignDial = { ...shapes.Minimal, frame: [[-1, 1], [-2, 0], [-1, 0]], sign: true }
shapes.HorizontalSignDial = { ...shapes.Horizontal, sign: true }

/**
 * The places that matter on a gate of `shape` facing south with its DHD button hung on the block at
 * (bx, by, bz): where to stand to press it, the opening, the kawoosh's first block in front of it,
 * the arrival point, where to walk to go in, and the box to trace. `walkY` is the level a player
 * stands on in front of it, the arrival's.
 */
function gateAt (shape, bx, by, bz) {
  const s = shapes[shape]
  if (!s) throw new Error(`no shape called ${shape}; there are ${Object.keys(shapes).join(', ')}`)
  const at = ([x, y, z]) => v(bx + x, by + y, bz + z)
  const centre = at(s.centre)
  const arrival = at(s.arrival)
  const opening = centre.floored()
  const [[x0, x1], [y0, y1], [z0, z1]] = s.frame
  return {
    shape,
    flat: Boolean(s.flat),
    sign: Boolean(s.sign),
    anchor: v(bx, by, bz),
    button: v(bx, by, bz + 1),
    lever: at(s.iris).offset(0, 0, 1),
    // Beside the button, a row back, lined up with the arrival point.
    stand: [arrival.x, arrival.y, bz + 1.5],
    walkY: arrival.y,
    opening,
    centre,
    // Upright, a block in front of the opening at the arrival's level; flat, a block above it.
    kawoosh: s.flat ? opening.offset(0, 1, 0) : v(opening.x, arrival.y, opening.z + 1),
    arrival,
    into: s.flat ? v(centre.x, arrival.y, centre.z) : v(arrival.x, arrival.y, opening.z - 0.5),
    // The front row of the opening, which a lane in front of the gate runs up to.
    front: opening.z + 1,
    dial: v(bx + 1, by, bz + 1),
    rd: v(bx, by + 1, bz),
    frame: { min: v(bx + x0, by + y0, bz + z0), max: v(bx + x1, by + y1, bz + z1) },
    box: { min: v(bx + x0 - 1, by + y0, bz + z0 - 3), max: v(bx + x1 + 2, by + y1 + 4, bz + z1 + 2) }
  }
}

/**
 * The places that matter on a Standard gate facing south with its DHD button hung on the block at
 * (bx, by, bz), as `wx gate build Standard <name> world bx by bz south` builds it, with the floor
 * the trips lay in front of it.
 */
function standardGate (bx, bz, by = -60) {
  return {
    ...gateAt('Standard', bx, by, bz),
    // The three rows in front at the opening's foot; "keep" leaves the iris switch block alone.
    floor: `fill ${bx - 4} ${by - 1} ${bz - 2} ${bx} ${by - 1} ${bz + 2} stone keep`
  }
}

/** Stands the bot on the ground under a trip's vantage, so its chunks are loaded to set blocks in. */
async function visit (vantage) {
  await teleport(vantage[0], -63, vantage[2], 180)
}

/**
 * Presses a gate's DHD, dials, and waits for the kawoosh to come and fall back and the opening to
 * fill; the opening must be empty before, and must not fill before the kawoosh. Returns what
 * filled it and every block the gate's area turned into on the way. `dial` is what to type after
 * `/dial`, or a function that dials some other way (a sign, redstone, the console) instead of the
 * DHD and `/dial`.
 */
async function dialAndOpen (gate, label, dial) {
  await teleport(...gate.stand, 180)
  const button = bot.blockAt(gate.button)
  if (typeof dial !== 'function' && (!button || !button.name.endsWith('_button'))) {
    throw new Error(`no DHD button at ${gate.button}, but ${button ? button.name : 'an unloaded block'}`)
  }
  if (nameAt(gate.opening) !== 'air') throw new Error(`${label}'s opening is ${nameAt(gate.opening)} before dialling`)
  // Air, unless a rail runs through where the kawoosh splashes; it falls back to what was there.
  const rest = nameAt(gate.kawoosh)
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
    if (typeof dial === 'function') {
      await dial()
      narrate('Dialled; waiting for the chevrons to lock and the kawoosh')
    } else {
      narrate(`Pressing ${label}'s DHD`)
      await bot.activateBlock(button)
      await sleep(1000)
      traceStart = Date.now()
      bot.chat(`/dial ${dial}`)
      narrate(`Dialled ${dial.split(' ')[0]}; waiting for the chevrons to lock and the kawoosh`)
    }
    await waitFor(() => kawooshAt !== null, 60, () => `the kawoosh (heard: ${JSON.stringify(heard)})`)
    narrate(`${kawooshAt} kawoosh; waiting for it to fall back`)
    await waitFor(() => nameAt(gate.kawoosh) === rest, 15, 'the kawoosh falling back')
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

// ---------------------------------------------------------------------------------------------
// Riding through, and pets. A ridden boat or horse is moved by its rider's client, which tells
// the server where it is each tick; Mineflayer does not simulate one, so drive() does that part.

/** The nearest entity called `kind` (a regex on Mineflayer's name) within `within` of `at`. */
function entityNear (kind, at, within) {
  let best = null
  for (const entity of Object.values(bot.entities)) {
    if (entity === bot.entity || !kind.test(entity.name || '')) continue
    const d = entity.position.distanceTo(at)
    if (d <= within && (!best || d < best.position.distanceTo(at))) best = entity
  }
  return best
}

/** What the bot is riding, if its name matches `kind`, else null. */
function riding (kind) {
  return bot.vehicle && kind.test(bot.vehicle.name || '') ? bot.vehicle : null
}

function describeRide () {
  if (!bot.vehicle) return 'riding nothing'
  const p = bot.vehicle.position
  return `riding a ${bot.vehicle.name} at ${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}`
}

/** Waits for a summoned entity to show up near where it was put, and returns it. */
async function findSummoned (kind, at, label) {
  let found = null
  await waitFor(() => (found = entityNear(kind, at, 3)) !== null, 10, `the ${label} appearing at ${at}`)
  return found
}

/** Right-clicks an entity to get on it, and waits to be riding it. */
async function mountOn (entity, label) {
  narrate(`Getting on the ${label}`)
  await bot.lookAt(entity.position.offset(0, 0.5, 0), true)
  bot.mount(entity)
  await waitFor(() => bot.vehicle === entity, 5, `getting on the ${label}`)
}

/** Gets off, by the console: Mineflayer's own dismount sends a jump on 1.21.2 and later. */
async function getOff () {
  if (!bot.vehicle) return
  serverCommand(`ride ${name} dismount`)
  await waitFor(() => !bot.vehicle, 5, 'getting off')
}

/**
 * Drives what the bot is riding in a straight line on the level towards `target`, `speed` blocks
 * a tick, reporting each step as a client does (vehicle_move). Stops on `done`, on arriving, or
 * once the bot is no longer riding what it set off on, as when a gate takes the vehicle.
 */
async function drive (target, speed, done, seconds) {
  const vehicle = bot.vehicle
  const at = vehicle.position.clone()
  const deadline = Date.now() + seconds * 1000
  while (Date.now() < deadline && !done() && bot.vehicle === vehicle) {
    const dx = target.x - at.x
    const dz = target.z - at.z
    const d = Math.hypot(dx, dz)
    if (d < 0.05) return
    const step = Math.min(speed, d)
    at.x += dx / d * step
    at.z += dz / d * step
    bot._client.write('vehicle_move', { x: at.x, y: at.y, z: at.z, yaw: Math.atan2(-dx, dz) * 180 / Math.PI, pitch: 0, onGround: true })
    await sleep(50)
  }
}

/**
 * Kills the entities of each type within `radius` of (x, y, z) that a run left. A type this server
 * does not know, such as boat after 1.21.2 split boats by wood, is only an error in its log.
 */
function clearAround (types, x, y, z, radius) {
  for (const type of [].concat(types)) serverCommand(`kill @e[type=${type},x=${x},y=${y},z=${z},distance=..${radius}]`)
}

/** A Minecraft UUID as the four signed ints NBT stores it as. */
function uuidInts (uuid) {
  const hex = uuid.replace(/-/g, '')
  return [0, 8, 16, 24].map((i) => parseInt(hex.slice(i, i + 8), 16) | 0)
}

/** Runs a console command in the nether, where the console's own commands would not reach. */
function inNether (command) {
  serverCommand(`execute in minecraft:the_nether run ${command}`)
}

function inTheNether () {
  return /nether/.test(bot.game.dimension)
}

/** Clears creatures a grass world spawns, sparing the plugin's own display entities. */
function clearMobs () {
  serverCommand('kill @e[type=!player,type=!block_display,type=!item_display,type=!text_display,type=!interaction]')
}

// ---------------------------------------------------------------------------------------------
// Travellers other than the bot on foot. Each takes a gate that is already open, and the gate it
// should come out of, and fails unless the traveller comes out there as it should. The CI trips
// and the lab's stargate bay both go through these.

/**
 * A lane of blue ice three wide from a gate's opening back fifteen rows, at its floor, clear of the
 * iris switch under the DHD. `run` sends each command, so a lane can be laid in another world.
 */
function layBoatLane (g, run = serverCommand) {
  const x = g.opening.x
  run(`fill ${x - 1} ${g.walkY - 1} ${g.front} ${x + 1} ${g.walkY - 1} ${g.front + 14} blue_ice`)
}

/**
 * Puts a boat on the ice in front of the open gate `from`, gets in, and drives it into the opening;
 * it must come out at `to` still in the boat, and still be in it a moment later, once the plugin
 * has re-seated it. Gets off afterwards.
 */
async function boatInto (from, to, toLabel) {
  const start = v(from.arrival.x, from.walkY, from.front + 12.5)
  narrate('Putting a boat on the ice, facing the gate')
  // Boats were split by wood in 1.21.2; whichever name this server does not know is only an
  // error in its log.
  for (const type of ['oak_boat', 'boat']) serverCommand(`summon ${type} ${start.x} ${start.y} ${start.z} {Rotation:[180f,0f]}`)
  await teleport(from.arrival.x, from.walkY, from.front + 14.5, 180)
  const ride = await findSummoned(/boat$/, start, 'boat')
  await mountOn(ride, 'boat')
  narrate('Driving the boat into the gate')
  const arrival = v(to.arrival.x, to.walkY, to.arrival.z)
  const arrived = () => riding(/boat$/) && bot.vehicle.position.distanceTo(arrival) < 4
  await drive(from.into, 0.5, arrived, 15)
  await waitFor(arrived, 10, () => `coming out at ${toLabel} in the boat (${describeRide()})`)
  narrate(`${stamp()} came out at ${toLabel} in the boat`)
  await sleep(2000)
  if (!riding(/boat$/)) throw new Error(`thrown out of the boat after arriving at ${toLabel}; ${where()}`)
  console.log(`  ${describeRide()}`)
  await getOff()
}

/**
 * A rail line from a gate's opening back seventeen rows to a stone bumper. The rail stops short of
 * the opening: a portal block is air on the server while the gate is open, so a rail in it would
 * go, and a cart rolls off the end into the opening. With `launcher`, powered rails on redstone
 * blocks keep a cart going, and the last one, by the bumper, is powered by the run to launch it.
 */
function layRails (g, launcher, run = serverCommand) {
  const x = g.opening.x
  const y = g.walkY
  run(`fill ${x} ${y} ${g.front} ${x} ${y} ${g.front + 16} rail[shape=north_south]`)
  run(`setblock ${x} ${y} ${g.front + 17} stone`)
  if (!launcher) return
  for (const dz of [12, 8, 4]) {
    run(`setblock ${x} ${y - 1} ${g.front + dz} redstone_block`)
    run(`setblock ${x} ${y} ${g.front + dz} powered_rail[shape=north_south]`)
  }
  run(`setblock ${x} ${y} ${g.front + 16} powered_rail[shape=north_south]`)
}

/**
 * Sits the bot in a minecart at the start of `from`'s line and powers its first rail: the cart
 * pushes off the bumper, rolls off the end of the line into the open gate, and must come out on
 * `to`'s line still in the cart, a moment later too. Gets off afterwards.
 */
async function minecartInto (from, to, toLabel) {
  const x = from.opening.x
  serverCommand(`setblock ${x} ${from.walkY - 1} ${from.front + 16} stone`)
  await sleep(500)
  const start = v(from.arrival.x, from.walkY, from.front + 16.5)
  narrate('Putting a minecart at the start of the line')
  serverCommand(`summon minecart ${start.x} ${start.y} ${start.z}`)
  await teleport(from.arrival.x + 1, from.walkY, from.front + 15.5, 180)
  const cart = await findSummoned(/minecart$/, start, 'minecart')
  await mountOn(cart, 'minecart')
  narrate('Powering the first rail; the cart pushes off the bumper towards the gate')
  serverCommand(`setblock ${x} ${from.walkY - 1} ${from.front + 16} redstone_block`)
  // Anywhere on the far line: it arrives heading out of the gate and runs on to the bumper.
  const onFarLine = () => riding(/minecart$/) && Math.abs(bot.vehicle.position.x - to.arrival.x) < 1 &&
    bot.vehicle.position.z > to.front - 1 && bot.vehicle.position.z < to.front + 18
  await waitFor(onFarLine, 20, () => `coming out at ${toLabel} in the minecart (${describeRide()})`)
  narrate(`${stamp()} came out at ${toLabel} in the minecart`)
  await sleep(3000)
  if (!onFarLine()) throw new Error(`not in the minecart on ${toLabel}'s line a moment after arriving (${describeRide()})`)
  console.log(`  ${describeRide()}`)
  await getOff()
}

/**
 * Leads out a tame horse at `start`, saddles it from a spot two blocks south of it, and gets on.
 * Returns the horse.
 */
async function saddledHorse (start) {
  serverCommand(`clear ${name}`)
  serverCommand(`give ${name} saddle`)
  await sleep(500)
  narrate('Leading out a tame horse and saddling it')
  serverCommand(`summon horse ${start.x} ${start.y} ${start.z} {Tame:1b,Rotation:[180f,0f]}`)
  await teleport(start.x, start.y, start.z + 2, 180)
  const horse = await findSummoned(/^horse$/, start, 'horse')
  const saddle = bot.inventory.items().find((item) => item.name === 'saddle')
  if (!saddle) throw new Error('the bot was given no saddle')
  await bot.equip(saddle, 'hand')
  await bot.lookAt(horse.position.offset(0, 1, 0), true)
  await bot.activateEntity(horse)
  await sleep(500)
  // An empty hand, so right-clicking gets on rather than using what it holds.
  bot.setQuickBarSlot((bot.quickBarSlot + 1) % 9)
  await sleep(300)
  await mountOn(horse, 'horse')
  return horse
}

/** Rides `horse` into the open gate `from`; it must come out at `to`, still on the same horse. */
async function rideInto (horse, from, to, toLabel) {
  const onHorse = () => bot.vehicle === horse
  narrate('Riding the horse into the gate')
  const arrival = v(to.arrival.x, to.walkY, to.arrival.z)
  const through = () => onHorse() && bot.vehicle.position.distanceTo(arrival) < 4
  await drive(from.into, 0.4, through, 15)
  await waitFor(through, 10, () => `coming out at ${toLabel} on the horse (${describeRide()})`)
  await sleep(2000)
  if (!through()) throw new Error(`not on the horse at ${toLabel} a moment after arriving (${describeRide()})`)
  narrate(`${stamp()} came out at ${toLabel} on the horse`)
  console.log(`  ${describeRide()}`)
}

/** The wolf with this UUID, if the bot can see it within `within` of where it is. */
function wolfNear (uuid, within) {
  const wolf = Object.values(bot.entities).find((e) => e.uuid === uuid)
  return wolf && wolf.position.distanceTo(bot.entity.position) <= within ? wolf : null
}

/** Where the server has the wolf tagged `tag`, for a failure to say. */
async function serverHasWolf (tag) {
  const from = logSize()
  serverCommand(`data get entity @e[type=wolf,tag=${tag},limit=1] Pos`)
  return waitForLog(/following entity data: \[[^\]]*\]|No entity was found/, 5, from).then((m) => m[0], () => 'nothing said')
}

/** Waits to see a wolf within `within` of the bot, saying where the server has it if not. */
async function waitForWolf (wolf, tag, within, seconds, what) {
  try {
    await waitFor(() => wolfNear(wolf.uuid, within), seconds, what)
  } catch (e) {
    throw new Error(`${e.message}; the server says of it: ${await serverHasWolf(tag)}`)
  }
}

/** A wolf tamed to the bot, summoned where it is put, as the bot sees it. */
async function tamedWolf (at, tag) {
  const from = logSize()
  serverCommand(`summon wolf ${at.x} ${at.y} ${at.z} {Owner:[I;${uuidInts(bot.player.uuid).join(',')}],Tags:["${tag}"]}`)
  serverCommand(`data get entity @e[type=wolf,tag=${tag},limit=1] Owner`)
  await waitForLog(/has the following entity data: \[I;/, 10, from)
    .catch(() => { throw new Error(`the wolf ${tag} was not tamed to the bot: data get showed no Owner`) })
  return findSummoned(/^wolf$/, at, 'wolf')
}

/**
 * Walks into the open gate `from` with `wolf` following, and must come out at `to`, `inOther`
 * saying whether that is in the nether; the wolf must then arrive beside the bot.
 */
async function walkInWithWolf (wolf, tag, from, to, toLabel, inOther) {
  // It has wandered while the gate opened; within twelve blocks is what counts as following.
  await waitForWolf(wolf, tag, 12, 10, 'the wolf coming near before going in')
  narrate(`Walking in with the wolf ${wolfNear(wolf.uuid, 30).position.distanceTo(bot.entity.position).toFixed(1)} blocks off; should come out at ${toLabel}`)
  await walk(from.into, () => inTheNether() === inOther && near(to.arrival, 1.5), 15)
  await waitFor(() => inTheNether() === inOther && near(to.arrival, 1.5), 10, `arriving at ${toLabel}`)
  narrate(`${stamp()} arrived at ${toLabel}; waiting for the wolf to follow`)
  await waitForWolf(wolf, tag, 5, 8, `the wolf arriving beside the bot at ${toLabel}`)
  narrate(`${stamp()} the wolf came through`)
}

// ---------------------------------------------------------------------------------------------
// Rings, beams and mirrors, as the CI trips and the lab's bays both take them.

/**
 * Walks into the ring anchored at `anchor` (a block's middle, where a player stands) from six
 * blocks north of it, and waits to come out in the ring at `partner`. A ring used a moment ago
 * recharges first, and says for how long; that is waited out and it goes again.
 */
async function ringInto (anchor, partner, label = 'the partner ring') {
  for (let attempt = 1; ; attempt++) {
    await teleport(anchor.x, anchor.y, anchor.z - 6, 0)
    await sleep(2000)
    messages()
    narrate('Walking into the ring; its countdown runs before the rings rise')
    // The countdown is 100 ticks by default before the rings even rise.
    await walk(anchor, () => near(partner, 3), 10)
    const until = Date.now() + 30000
    let recharge = null
    while (Date.now() < until && !near(partner, 3) && !recharge) {
      recharge = heard.join(' ').match(/Ready in (\d+) seconds?/)
      await sleep(100)
    }
    if (near(partner, 3)) break
    if (!recharge || attempt > 2) throw new Error(`arriving in ${label} did not happen within 30s (heard: ${JSON.stringify(heard)}); ${where()}`)
    narrate(`The rings are recharging; waiting ${recharge[1]} s to go again`)
    await teleport(anchor.x, anchor.y, anchor.z - 6, 0)
    await sleep((Number(recharge[1]) + 1) * 1000)
  }
  narrate(`Came out in ${label}`)
  console.log(`  ${where()}`)
}

/**
 * Beams to `to` and waits to land at `spot`, in the nether or not. A beam straight after arriving by
 * one is refused while the arrival still plays, as it should be, so that is waited out and asked again.
 */
async function beamTo (to, spot, nether) {
  for (let attempt = 1; ; attempt++) {
    messages()
    bot.chat(`/wormhole beam to ${to}`)
    const until = Date.now() + 30000
    while (Date.now() < until && !(inTheNether() === nether && near(spot, 1)) && !heard.some((l) => l.includes('already beaming'))) await sleep(100)
    if (inTheNether() === nether && near(spot, 1)) return
    if (attempt > 3 || !heard.some((l) => l.includes('already beaming'))) {
      throw new Error(`beaming to ${to} did not happen within 30s (heard ${JSON.stringify(heard)}); ${where()}`)
    }
    narrate('Still arriving from the last beam; asking again in a moment')
    await sleep(2000)
  }
}

/**
 * How far, in radians, the bot faces from Bukkit's yaw `degrees` (0 south, 90 west, 180 north, -90
 * east). Mineflayer keeps yaw in radians, turning the other way: Bukkit's -90, east, is its 3/2 pi.
 */
function yawOff (degrees) {
  const want = Math.PI - degrees * Math.PI / 180
  return Math.abs(((bot.entity.yaw - want) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI)
}

/** A wall banner's facing as the unit step out from its wall, and the face index block_dig takes. */
const bannerFaces = {
  south: { step: [0, 0, 1], dig: 3 },
  north: { step: [0, 0, -1], dig: 2 },
  east: { step: [1, 0, 0], dig: 5 },
  west: { step: [-1, 0, 0], dig: 4 }
}

/** Where on a wall banner the bot aims, a little in front of its face and low on it. */
function bannerAim (banner, facing) {
  const [dx, , dz] = bannerFaces[facing].step
  return banner.offset(0.5 + dx * 0.4, 0.2, 0.5 + dz * 0.4)
}

/**
 * Right-clicks the mirror at `banner` until it says it opens onto `to`. Its answer, above the
 * hotbar, says the click was taken; a click that goes unanswered is tried again, as a player
 * would. The likely miss is aim, not the plugin: the server raytraces from the bot's own look, and
 * on a thin wall banner that can land on the wall behind, which the plugin rightly ignores.
 * `clicks` bounds it: a mirror steps through the others by name, one a click.
 */
async function turnMirror (banner, facing, to, clicks = 3) {
  const [dx, , dz] = bannerFaces[facing].step
  await bot.lookAt(bannerAim(banner, facing), true)
  const chose = () => heard.some((line) => line.includes(`opens onto '${to}'`))
  for (let click = 1; !chose(); click++) {
    if (click > clicks) throw new Error(`${clicks} right-clicks on the mirror never opened it onto ${to} (heard ${JSON.stringify(heard)}; the banner reads ${nameAt(banner)})`)
    if (click > 1) narrate(`Right-click ${click - 1} did not open it onto ${to}; clicking again`)
    const answered = heard.length
    await bot.activateBlock(bot.blockAt(banner), v(dx, 0, dz), v(0.5 + dx * 0.5, 0.2, 0.5 + dz * 0.5))
    const until = Date.now() + 4000
    while (Date.now() < until && !chose() && !heard.slice(answered).some((line) => line.includes('opens onto'))) await sleep(100)
  }
}

/** Punches the mirror at `banner` to go through, and waits to be put at `arrival`. */
async function punchMirror (banner, facing, arrival, label) {
  narrate('Punching the mirror to go through')
  await bot.lookAt(bannerAim(banner, facing), true)
  const face = bannerFaces[facing].dig
  bot._client.write('block_dig', { status: 0, location: banner, face })
  bot.swingArm()
  bot._client.write('block_dig', { status: 1, location: banner, face })
  await waitFor(() => near(arrival, 1.5), 10, () => `arriving at ${label} (heard ${JSON.stringify(heard)})`)
  narrate(`Came out at ${label}`)
  console.log(`  ${where()}`)
}

// ---------------------------------------------------------------------------------------------

let gone = null

/**
 * Joins the server as the bot, ops it, and readies the world: creative, peaceful, day, no mobs.
 * Returns a promise that rejects if the bot is kicked or disconnected, to race against.
 */
async function connect () {
  bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, auth: 'offline', version: settings.version })
  bot.on('messagestr', (text) => heard.push(text))
  // Mineflayer 4.39 notes a seat taken from a vehicle's passenger list, but not one given up: the
  // list without the bot, as when it is thrown off or gets off, left bot.vehicle set.
  bot._client.on('set_passengers', ({ entityId, passengers }) => {
    if (bot.vehicle && bot.vehicle.id === entityId && !passengers.includes(bot.entity.id)) {
      bot.vehicle = null
      bot.emit('dismount', bot.entities[entityId])
    }
  })
  // So a run shows the plugin re-seating the bot after a vehicle or a mount crosses.
  bot.on('mount', () => console.log(`    ${stamp()} seated on a ${bot.vehicle ? bot.vehicle.name : 'vehicle'}`))
  bot.on('dismount', (from) => console.log(`    ${stamp()} off the ${from ? from.name : 'vehicle'}`))
  gone = new Promise((resolve, reject) => {
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
  // Otherwise every console command, the watcher's action bar included, is echoed to the opped bot.
  serverCommand('gamerule logAdminCommands false')
  serverCommand('gamerule log_admin_commands false')
  clearMobs()
  await sleep(2000)
  return gone
}

/** Whether the server's version is `at least` major.minor.patch, as 1.21.5 or 26.1. */
function versionAtLeast (least) {
  const parts = (text) => text.split('.').map(Number)
  const have = parts(settings.version)
  const want = parts(least)
  for (let i = 0; i < Math.max(have.length, want.length); i++) {
    const a = have[i] || 0
    const b = want[i] || 0
    if (a !== b) return a > b
  }
  return true
}

module.exports = {
  get bot () { return bot },
  get observer () { return observer },
  get gone () { return gone },
  name,
  port,
  settings,
  configure,
  connect,
  versionAtLeast,
  sleep,
  v,
  serverCommand,
  logSize,
  logSince,
  waitForLog,
  waitFor,
  heard,
  messages,
  where,
  near,
  teleport,
  walk,
  say,
  narrate,
  waitForObserver,
  showObserver,
  askObserver,
  askObserverChoice,
  compass,
  startClock,
  stamp,
  traceGate,
  ringPerimeter,
  nameAt,
  inside,
  shapes,
  gateAt,
  standardGate,
  visit,
  dialAndOpen,
  walkThrough,
  waitForShut,
  entityNear,
  riding,
  describeRide,
  findSummoned,
  mountOn,
  getOff,
  drive,
  clearAround,
  uuidInts,
  inNether,
  inTheNether,
  clearMobs,
  layBoatLane,
  boatInto,
  layRails,
  minecartInto,
  saddledHorse,
  rideInto,
  wolfNear,
  serverHasWolf,
  waitForWolf,
  tamedWolf,
  walkInWithWolf,
  ringInto,
  beamTo,
  yawOff,
  bannerAim,
  turnMirror,
  punchMirror
}
