'use strict'
// Joins a real server as a player and takes it through a gate, a beam and a ring, checking where it
// comes out each time:
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
  throw new Error(`${what} did not happen within ${seconds}s; ${where()}`)
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

async function waitForObserver () {
  console.log(`Waiting for someone to watch: join localhost:25599 with Minecraft ${version}, any name.`)
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
  let answer = null
  // Chat arrives as "<name> text" on every version Mineflayer speaks.
  const listen = (text) => {
    const said = text.match(/^<([^>]+)> *([yn])/i)
    if (said && said[1] === observer) answer = said[2].toLowerCase()
  }
  bot.on('messagestr', listen)
  bot.chat(`Did you see ${what}? y or n`)
  try {
    await waitFor(() => answer !== null, observeWait, `${observer} answering`)
  } catch {
    return 'no answer'
  } finally {
    bot.removeListener('messagestr', listen)
  }
  return answer === 'y' ? 'saw it' : 'did NOT see it'
}

// ---------------------------------------------------------------------------------------------
// The trips. Each throws on the first thing that is not as it should be.

/**
 * Two Standard gates built from the console where travel-boot.sh builds them, with a floor laid
 * in front of each. The bot presses Abydos's DHD, dials Chulak, waits for the kawoosh to clear,
 * walks in, and must come out at Chulak's arrival point; then Abydos must shut on its own.
 */
const gate = {
  name: 'gate',
  sight: 'WxBot press the DHD, Abydos open, WxBot walk in and come out of Chulak, then Abydos shut',
  vantage: [9.5, -53, 14.5, 180, 25],
  async run () {
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
    await bot.activateBlock(button)
    await sleep(1000)
    bot.chat('/dial Chulak')

    // Portal blocks fill the opening, then the kawoosh in front of it comes and goes.
    await waitFor(() => bot.blockAt(opening).name !== 'air', 20,
      `Abydos opening (heard: ${JSON.stringify(heard)})`)
    await sleep(1000)
    await waitFor(() => bot.blockAt(kawoosh).name === 'air', 15, 'the kawoosh clearing')

    const chulak = v(18.5, -60, -1.5)
    await walk(v(-1.5, -60, -3.5), () => near(chulak, 1.5), 15)
    await waitFor(() => near(chulak, 1.5), 5, 'arriving at Chulak')
    console.log(`  arrived at Chulak; ${where()}`)

    await waitFor(() => bot.blockAt(opening).name === 'air', 90, 'Abydos shutting behind the bot')
  }
}

/**
 * A beam destination saved where the bot stands facing east, then beamed to from twenty blocks
 * off: the bot must land there, facing east.
 */
const beam = {
  name: 'beam',
  sight: 'WxBot vanish from the west and land at the east spot, facing east',
  vantage: [0.5, -57, 32.5, 180, 25],
  async run () {
    await teleport(10.5, -63, 20.5, -90)
    messages()
    bot.chat('/wormhole beam admin set Home')
    await sleep(1500)
    const set = messages()
    await teleport(-10.5, -63, 20.5, 0)
    messages()
    bot.chat('/wormhole beam to Home')
    await waitFor(() => near(v(10.5, -63, 20.5), 1), 30,
      `beaming to Home (setting it said ${JSON.stringify(set)}; beaming said ${JSON.stringify(heard)})`)
    // Mineflayer keeps yaw in radians, turning the other way from 180 degrees on: Bukkit's -90, east,
    // is its 3/2 pi.
    const off = Math.abs(((bot.entity.yaw - 1.5 * Math.PI) % (2 * Math.PI) + 3 * Math.PI) % (2 * Math.PI) - Math.PI)
    if (off > 0.1) throw new Error(`arrived facing ${bot.entity.yaw.toFixed(2)} radians, not east (${(1.5 * Math.PI).toFixed(2)})`)
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
  async run () {
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

    await teleport(0.5, -63, 34.5, 0)
    await sleep(2000)
    messages()
    const partner = v(30.5, -63, 40.5)
    // The countdown is 100 ticks by default before the rings even rise.
    await walk(v(0.5, -63, 40.5), () => near(partner, 3), 10)
    await waitFor(() => near(partner, 3), 30, `arriving in the partner ring (heard: ${JSON.stringify(heard)})`)
    console.log(`  arrived in the partner ring; ${where()}`)
  }
}

// ---------------------------------------------------------------------------------------------

async function main () {
  bot = mineflayer.createBot({ host: '127.0.0.1', port: 25599, username: name, auth: 'offline', version })
  bot.on('messagestr', (text) => heard.push(text))
  const gone = new Promise((resolve, reject) => {
    bot.once('kicked', (reason) => reject(new Error(`kicked: ${JSON.stringify(reason)}`)))
    bot.once('end', (reason) => reject(new Error(`disconnected: ${reason}`)))
    bot.once('error', reject)
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
  await sleep(2000)

  if (observe) await waitForObserver()

  const results = []
  for (const trip of [gate, beam, ring]) {
    await showObserver(...trip.vantage)
    await say(`Trip: ${trip.name}. Watch for ${trip.sight}.`)
    let outcome = 'PASS'
    let detail = ''
    try {
      await Promise.race([trip.run(), gone])
    } catch (e) {
      outcome = 'FAIL'
      detail = e.message
    }
    bot.clearControlStates()
    console.log(`${outcome} ${trip.name}${detail ? ': ' + detail : ''}`)
    const seen = observe ? await askObserver(trip.sight) : 'not watched'
    results.push({ trip: trip.name, outcome, seen, detail })
  }

  console.log('\nplayer journeys on ' + version + ':')
  for (const r of results) {
    console.log(`  ${r.outcome.padEnd(4)} ${r.trip.padEnd(5)} observer: ${r.seen}${r.detail ? '  (' + r.detail + ')' : ''}`)
  }
  const failed = results.some((r) => r.outcome !== 'PASS' || r.seen === 'did NOT see it')
  if (observe) await say(failed ? 'Some trips failed; see the summary in the terminal.' : 'All trips passed. Stopping the server.')
  bot.quit()
  await sleep(1000)
  process.exit(failed ? 1 : 0)
}

main().catch((e) => {
  console.error('player journeys could not run: ' + e.message)
  process.exit(1)
})
