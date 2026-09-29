'use strict'
// Joins a real server as a player and takes it through gates of several kinds, an iris, a beam, a
// ring and a mirror, in a boat, a minecart and on a horse, and with its wolves, checking where it
// comes out each time:
//   node journeys.js <minecraft-version>
// Started by player-boot.sh through boot-test.sh, which passes BOOT_CONSOLE, a file whose new lines
// go to the server console, and BOOT_LOG, the server's log. Setting up is done from the console;
// everything a player would do, the bot does as a player.
//
// OBSERVE=1 waits for someone to join and watch, flies them to each trip, and asks them in chat
// whether they saw it happen. Their answers go in the summary, and a "no" fails the run.
//
// The steps each trip is made of are kit.js's, which the lab (lab.js) shares.

const kit = require('./kit')
const {
  name, sleep, v, serverCommand, logSize, waitForLog, waitFor, heard, messages, where, near, teleport,
  walk, say, narrate, waitForObserver, showObserver, askObserver, askObserverChoice, compass,
  startClock, stamp, traceGate, ringPerimeter, nameAt, standardGate, visit, dialAndOpen, walkThrough,
  waitForShut, describeRide, getOff, drive, clearAround,
  inNether, inTheNether, clearMobs, layBoatLane, boatInto, layRails, minecartInto, saddledHorse,
  rideInto, wolfNear, waitForWolf, tamedWolf, walkInWithWolf, ringInto, beamTo, yawOff,
  turnMirror, punchMirror
} = kit

const version = process.argv[2]
const observe = process.env.OBSERVE === '1'
// TRIPS, a comma-separated list of trip names, runs only those, in that order; a name given twice
// runs that trip again, as a watcher's rerun does.
const only = process.env.TRIPS ? process.env.TRIPS.split(',') : null

if (!version || !process.env.BOOT_CONSOLE || !process.env.BOOT_LOG) {
  console.error('usage: BOOT_CONSOLE=<file> BOOT_LOG=<file> node journeys.js <minecraft-version>')
  process.exit(2)
}
kit.configure({ observe, observeWait: Number(process.env.OBSERVE_WAIT || 600), version })

// Set once the bot has joined; every trip runs after that.
let bot

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
    // A trip before this one can leave the bot far off, and fill and setblock need loaded chunks.
    await visit(this.vantage)
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
    // Without its floor the bot drops to the ground and walks under the opening, not into it.
    if (nameAt(v(-2, -61, 1)) !== 'stone') throw new Error(`no floor in front of Abydos: -2 -61 1 is ${nameAt(v(-2, -61, 1))}`)
    const button = bot.blockAt(v(0, -60, 1))
    if (!button || !button.name.endsWith('_button')) {
      throw new Error(`no DHD button at 0 -60 1, but ${button ? button.name : 'an unloaded block'}`)
    }
    // Watched to see the gate open, so it must start empty, or that check proves nothing.
    const opening = v(-2, -58, -3)
    const kawoosh = v(-2, -60, -2)
    if (nameAt(opening) !== 'air') throw new Error(`Abydos's opening is ${nameAt(opening)} before dialling`)
    messages()
    startClock()
    const untrace = traceGate({ min: v(-6, -61, -6), max: v(3, -51, 2) })
    try {
      narrate("Pressing Abydos's DHD")
      await bot.activateBlock(button)
      await sleep(1000)
      startClock()
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
        await waitFor(() => kawooshAt !== null, 60, () => `the kawoosh (heard: ${JSON.stringify(heard)})`)
        narrate(`${kawooshAt} kawoosh; waiting for it to fall back`)
        await waitFor(() => bot.blockAt(kawoosh).name === 'air', 15, 'the kawoosh falling back')
        await waitFor(() => filledAt !== null || nameAt(opening) !== 'air', 15, 'Abydos\'s opening filling')
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

      await waitFor(() => nameAt(opening) === 'air', 90, 'Abydos shutting behind the bot')
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
    await visit(this.vantage)
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
    if (yawOff(-90) > 0.1) throw new Error(`arrived facing ${bot.entity.yaw.toFixed(2)} radians, not east (${(1.5 * Math.PI).toFixed(2)})`)
    narrate('Landed at Home, facing east')
  }
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
    await visit(this.vantage)
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
    await ringInto(v(0.5, -63, 40.5), v(30.5, -63, 40.5), 'the right ring')
  }
}

// ---------------------------------------------------------------------------------------------
// More gates. Each pair is dialled and walked through as the gate trip does, by dialAndOpen and
// walkThrough, and each trip ends by waiting for the gate to shut, so it can be run again.

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
    // A run cut short can leave the iris either way, or the wormhole still open, with the iris shut
    // over it or not; it starts idle and shut. Cimmeria's opening is what says the wormhole is gone.
    const idle = () => ['air', 'stone'].includes(nameAt(tollan.opening)) && nameAt(cimmeria.opening) === 'air'
    if (!idle()) {
      narrate('Waiting for the wormhole an earlier run left open to shut')
      await waitFor(idle, 120, () => `the wormhole shutting (Tollan shows ${nameAt(tollan.opening)}, Cimmeria ${nameAt(cimmeria.opening)})`)
    }
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
    // A mirror is only a banner until its room is captured, which took 13 to 20 seconds here, not
    // the second once assumed: a click before Beta's was done drew nothing, one run in five.
    for (const [label] of this.banners) await this.captured(label)
  },
  /** Waits for a mirror's room to be captured, as `mirror debug <name> -all` reports it. */
  async captured (label) {
    const deadline = Date.now() + 60000
    let line = 'nothing'
    while (Date.now() < deadline) {
      const from = heard.length
      bot.chat(`/wormhole mirror debug ${label} -all`)
      await sleep(2000)
      line = heard.slice(from).find((l) => l.includes('file: ')) || 'no file line'
      if (/file: \d+ bytes/.test(line) && !line.includes('being taken')) {
        narrate(`${label}'s room is captured`)
        return
      }
    }
    throw new Error(`${label}'s room was not captured within 60s (${line.trim()})`)
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
    const banner = v(ax, -62, az)
    // What the bot's client holds behind Alpha's wall, short of the floor, for a failure to say.
    const drawnBehind = () => {
      const cells = []
      for (let x = ax - 20; x <= ax + 20; x++) for (let z = az - 40; z <= az - 4; z++) for (let y = -63; y <= -56; y++) {
        const name = nameAt(v(x, y, z))
        if (name !== 'air') cells.push(`${name} at ${x} ${y} ${z}`)
      }
      return cells.length === 0 ? 'nothing' : `${cells.length} blocks (${cells.slice(0, 8).join(', ')}${cells.length > 8 ? ', ...' : ''})`
    }
    // A view stands a barrier in for the opening, the wall behind the banner.
    const opening = [v(ax, -63, az - 1), v(ax, -62, az - 1)]
    const seen = () => `the opening shows ${opening.map(nameAt).join(' and ')}, the banner reads ${nameAt(banner)}, and behind the wall the bot has ${drawnBehind()}`
    await teleport(ax + 0.5, -63, az + 3.5, 180)
    narrate('At Alpha, which shows its own room')
    // Only with the view drawn does no gold behind the wall mean anything.
    await waitFor(() => opening.every((cell) => nameAt(cell) === 'barrier'), 10, () => `Alpha's view being drawn for the bot (${seen()})`)
    await sleep(1000)
    if (gold()) {
      // A run just before left Alpha open onto Beta; it settles back once nobody is near it.
      narrate('Alpha still shows Beta from the last run; stepping away until it settles')
      await teleport(ax + 0.5, -63, az + 40.5, 180)
      await sleep(5000)
      await teleport(ax + 0.5, -63, az + 3.5, 180)
      await waitFor(() => opening.every((cell) => nameAt(cell) === 'barrier'), 10, () => `Alpha's view being drawn for the bot (${seen()})`)
      await sleep(1000)
    }
    if (gold()) throw new Error(`gold at ${gold()} behind Alpha before it was turned to Beta (a run just before may have left it open onto Beta; a fresh server gives the check a clean start)`)
    messages()
    narrate('Right-clicking Alpha to open it onto Beta')
    await turnMirror(banner, 'south', 'Beta')
    await waitFor(() => gold() !== null, 10, () => `Beta's gold block showing behind Alpha (heard ${JSON.stringify([...new Set(heard)])}; ${seen()})`)
    narrate(`Beta's room shows through Alpha: gold at ${gold()}`)
    await sleep(2000)
    await punchMirror(banner, 'south', v(bx + 0.5, -63, bz + 0.5), 'Beta')
  }
}

/**
 * Two Standard gates with a lane of blue ice leading into the first. The bot gets into a boat on
 * the ice, dials, and drives it into the opening; it must come out at the far gate still in the
 * boat, and still be in it a moment later, once the plugin has re-seated it.
 */
const boat = {
  name: 'boat',
  sight: 'WxBot dial the left gate, get into a boat on the ice, drive it into the gate, and come out of the right gate still in the boat',
  vantage: [9.5, -53, 176.5, 180, 25],
  gates: [['Dakara', 0, 160], ['Kheb', 20, 160]],
  async setup () {
    await visit(this.vantage)
    compass(10, 166)
    const from = logSize()
    for (const [label, bx, bz] of this.gates) {
      serverCommand(`wx gate build Standard ${label} world ${bx} -60 ${bz} south`)
      await waitForLog(new RegExp(`Built ${label} at ${bx} -60 ${bz}`), 20, from)
      serverCommand(standardGate(bx, bz).floor)
      layBoatLane(standardGate(bx, bz))
    }
    await sleep(1000)
  },
  cleanup () {
    const [[, fx, fz]] = this.gates
    clearAround(['boat', 'oak_boat'], fx + 10, -60, fz, 30)
  },
  async run () {
    const [[fromLabel, fx, fz], [toLabel, tx, tz]] = this.gates
    const from = standardGate(fx, fz)
    const to = standardGate(tx, tz)
    clearAround(['boat', 'oak_boat'], fx + 10, -60, fz, 30)
    await sleep(500)
    const { portal } = await dialAndOpen(from, fromLabel, toLabel)
    if (portal !== 'water') throw new Error(`${fromLabel}'s opening filled with ${portal}, not water`)

    await boatInto(from, to, toLabel)
    clearAround(['boat', 'oak_boat'], fx + 10, -60, fz, 30)
    await waitForShut(to.opening, toLabel)
  }
}

/**
 * A rail line into a gate's opening and another out of the far gate, each ending at a stone
 * bumper. The bot sits in a minecart at the start of the first; powering its rail launches the
 * cart off the bumper, powered rails keep it going, and it rolls off the rail's end into the
 * opening. It must come out on the far line still in the minecart.
 */
const minecart = {
  name: 'minecart',
  sight: 'WxBot dial the left gate, get into a minecart on the rails behind it, roll into the gate, and come out of the right gate on its rails still in the minecart',
  vantage: [69.5, -53, 178.5, 180, 25],
  gates: [['Hebridan', 60, 160], ['Tagrea', 80, 160]],
  async setup () {
    await visit(this.vantage)
    compass(70, 166)
    const from = logSize()
    for (const [label, bx, bz] of this.gates) {
      serverCommand(`wx gate build Standard ${label} world ${bx} -60 ${bz} south`)
      await waitForLog(new RegExp(`Built ${label} at ${bx} -60 ${bz}`), 20, from)
      serverCommand(standardGate(bx, bz).floor)
      serverCommand(`fill ${bx - 4} -61 ${bz + 3} ${bx} -61 ${bz + 15} stone`)
      // Powered all the time, on redstone blocks, except the first, which the run powers to launch.
      layRails(standardGate(bx, bz), label === this.gates[0][0])
    }
    await sleep(1000)
  },
  cleanup () {
    const [[, fx, fz]] = this.gates
    clearAround('minecart', fx + 10, -60, fz, 30)
  },
  async run () {
    const [[fromLabel, fx, fz], [toLabel, tx, tz]] = this.gates
    const from = standardGate(fx, fz)
    const to = standardGate(tx, tz)
    clearAround('minecart', fx + 10, -60, fz, 30)
    await sleep(500)
    const { portal } = await dialAndOpen(from, fromLabel, toLabel)
    if (portal !== 'water') throw new Error(`${fromLabel}'s opening filled with ${portal}, not water`)

    await minecartInto(from, to, toLabel)
    clearAround('minecart', fx + 10, -60, fz, 30)
    await waitForShut(to.opening, toLabel)
  }
}

/**
 * A saddled horse, ridden by the bot the whole way: through a gate, then beamed into a ring, then
 * carried by that ring to its partner. After each it must still be on the same horse. Each leg is
 * checked on its own, and the trip fails naming every one that did not hold.
 */
const mount = {
  name: 'mount',
  sight: 'WxBot ride a horse through the left gate and out of the right one, beam on it into the ring behind, and ride the rings to the right ring, never getting off',
  vantage: [133.5, -51, 200.5, 180, 35],
  gates: [['Orban', 120, 160], ['Sahal', 140, 160]],
  rings: [120, 150],
  ringZ: 186,
  async setup () {
    await visit(this.vantage)
    compass(130, 166)
    const from = logSize()
    for (const [label, bx, bz] of this.gates) {
      serverCommand(`wx gate build Standard ${label} world ${bx} -60 ${bz} south`)
      await waitForLog(new RegExp(`Built ${label} at ${bx} -60 ${bz}`), 20, from)
      serverCommand(standardGate(bx, bz).floor)
      serverCommand(`fill ${bx - 4} -61 ${bz + 3} ${bx} -61 ${bz + 8} stone`)
    }
    for (const x of this.rings) {
      for (const [dx, dz] of ringPerimeter()) serverCommand(`setblock ${x + dx} -63 ${this.ringZ + dz} smooth_stone_slab`)
    }
    await sleep(1000)
    narrate('Saving a beam destination, Corral, in the middle of the left ring')
    await teleport(this.rings[0] + 0.5, -63, this.ringZ + 0.5, 180)
    messages()
    bot.chat('/wormhole beam admin set Corral')
    await sleep(1500)
    const set = messages()
    narrate('Making the two circles a ring pair')
    const made = []
    for (const x of this.rings) {
      await teleport(x + 0.5, -63, this.ringZ + 0.5, 0)
      messages()
      bot.chat('/wormhole ring create')
      await sleep(2000)
      made.push(...messages())
    }
    if (!made.some((line) => line.includes('is live'))) throw new Error(`the pair was not made: ${JSON.stringify(made)} (the beam: ${JSON.stringify(set)})`)
    // Off the pad, so it does not carry the bot back and forth while other trips run.
    await teleport(this.rings[1] + 0.5, -63, this.ringZ + 8.5, 180)
  },
  cleanup () {
    clearAround('horse', 135, -60, 175, 40)
  },
  async run () {
    const [[fromLabel, fx, fz], [toLabel, tx, tz]] = this.gates
    const from = standardGate(fx, fz)
    const to = standardGate(tx, tz)
    clearAround('horse', 135, -60, 175, 40)
    await sleep(500)
    const { portal } = await dialAndOpen(from, fromLabel, toLabel)
    if (portal !== 'water') throw new Error(`${fromLabel}'s opening filled with ${portal}, not water`)

    const horse = await saddledHorse(v(fx - 1.5, -60, fz + 5.5))

    const onHorse = () => bot.vehicle === horse
    // Each leg is checked on its own, so one that fails does not hide the next; one that leaves
    // the bot off the horse skips the rest, having nothing to ride.
    const failed = []
    const leg = async (label, body) => {
      if (!onHorse()) {
        failed.push(`${label}: not run, the bot being off the horse (${describeRide()})`)
        return
      }
      try {
        await body()
      } catch (e) {
        failed.push(`${label}: ${e.message}`)
        narrate(`${label} FAILED: ${e.message}`)
      }
    }

    await leg('gate', () => rideInto(horse, from, to, toLabel))

    const corral = v(this.rings[0] + 0.5, -63, this.ringZ + 0.5)
    await leg('beam', async () => {
      messages()
      narrate('Beaming, on the horse, to Corral in the left ring')
      bot.chat('/wormhole beam to Corral')
      const inCorral = () => onHorse() && bot.vehicle.position.distanceTo(corral) < 1.5
      await waitFor(inCorral, 30, () => `landing in Corral on the horse (${describeRide()}; heard ${JSON.stringify(heard)})`)
      await sleep(2000)
      if (!inCorral()) throw new Error(`not on the horse in Corral a moment after landing (${describeRide()})`)
      narrate('Landed in Corral on the horse')
    })

    await leg('ring', async () => {
      if (!onHorse()) throw new Error(`not run, the bot being off the horse (${describeRide()})`)
      if (bot.vehicle.position.distanceTo(corral) > 1.5) throw new Error(`not run, the horse not being in Corral (${describeRide()})`)
      const partner = v(this.rings[1] + 0.5, -63, this.ringZ + 0.5)
      const inPartner = () => onHorse() && Math.hypot(bot.vehicle.position.x - partner.x, bot.vehicle.position.z - partner.z) < 3
      // A ring arms on its rider moving inside it; landing by beam is not a move. A ring used a
      // moment ago recharges first, and says for how long.
      for (let attempt = 1; !inPartner(); attempt++) {
        if (!onHorse()) throw new Error(`off the horse before the rings came (${describeRide()})`)
        messages()
        narrate('Stepping the horse about in the ring; its countdown runs before the rings rise')
        await drive(corral.offset(0.6, 0, 0), 0.1, inPartner, 2)
        await drive(corral, 0.1, inPartner, 2)
        const until = Date.now() + 30000
        let recharge = null
        while (Date.now() < until && !inPartner() && !recharge) {
          recharge = heard.join(' ').match(/Ready in (\d+) seconds?/)
          await sleep(100)
        }
        if (inPartner()) break
        if (!recharge || attempt > 2) throw new Error(`the rings did not carry the horse to the right ring within 30s (${describeRide()}; heard ${JSON.stringify(heard)})`)
        narrate(`The rings are recharging; waiting ${recharge[1]} s to go again`)
        await sleep((Number(recharge[1]) + 1) * 1000)
      }
      await sleep(2000)
      if (!inPartner()) throw new Error(`not on the horse in the right ring a moment after arriving (${describeRide()})`)
      narrate('Came out in the right ring on the horse')
      console.log(`  ${describeRide()}`)
    })

    await getOff()
    clearAround('horse', 135, -60, 175, 40)
    await teleport(this.rings[1] + 0.5, -63, this.ringZ + 8.5, 180)
    await waitForShut(to.opening, toLabel)
    if (failed.length > 0) throw new Error(failed.join('; '))
  }
}

/**
 * Wolves tamed to the bot beside a gate whose partner is in the nether. Within one world vanilla
 * brings a following wolf to its owner itself, which a first version of this trip showed; it
 * cannot follow into another world. One wolf is told to sit and must stay put throughout. Then,
 * each with a following wolf of its own: with pets-follow-owner off, the bot beams to the nether
 * and the wolf must stay behind, which shows the other legs mean something; with it on, the bot
 * beams there and back and the wolf must come both ways; and it walks through the gate, and the
 * wolf must come out beside it. Every leg runs, and the trip fails naming each that did not hold.
 */
const pet = {
  name: 'pet',
  sight: 'a wolf sit by the gate throughout; WxBot beam away and back with a wolf beside it, then walk into the gate with another wolf at its heels, both vanishing to the nether',
  vantage: [177.5, -53, 176.5, 180, 25],
  home: ['Hala', 180, 160],
  away: ['Kelowna', 0, 0, 70],
  runs: 0,
  /** Where each gate's beam destination stands, on the floor beside it. */
  pad (bx, bz, by = -60) {
    return v(bx + 2.5, by, bz + 6.5)
  },
  async setup () {
    const from = logSize()
    const [homeLabel, hx, hz] = this.home
    await visit(this.vantage)
    compass(hx + 8, hz + 6)
    serverCommand(`wx gate build Standard ${homeLabel} world ${hx} -60 ${hz} south`)
    await waitForLog(new RegExp(`Built ${homeLabel} at ${hx} -60 ${hz}`), 20, from)
    serverCommand(standardGate(hx, hz).floor)
    serverCommand(`fill ${hx - 8} -61 ${hz - 2} ${hx + 4} -61 ${hz + 8} stone keep`)
    await sleep(500)
    const homePad = this.pad(hx, hz)
    await teleport(homePad.x, homePad.y, homePad.z, 180)
    messages()
    bot.chat(`/wormhole beam admin set ${homeLabel}Pad`)
    await sleep(1500)
    const savedHome = messages()

    // A room of air walled in glass, so no lava runs in, kept loaded to build in from the console.
    const [awayLabel, ax, az, ay] = this.away
    narrate('Clearing a room in the nether for the far gate')
    inNether(`forceload add ${ax - 16} ${az - 16} ${ax + 16} ${az + 16}`)
    await sleep(2000)
    inNether(`fill ${ax - 12} ${ay - 6} ${az - 12} ${ax + 12} ${ay + 14} ${az + 20} glass hollow`)
    await sleep(1000)
    serverCommand(`wx gate build Standard ${awayLabel} world_nether ${ax} ${ay} ${az} south`)
    await waitForLog(new RegExp(`Built ${awayLabel} at ${ax} ${ay} ${az}`), 20, from)
    inNether(standardGate(ax, az, ay).floor)
    inNether(`fill ${ax - 8} ${ay - 1} ${az - 2} ${ax + 4} ${ay - 1} ${az + 8} stone keep`)
    await sleep(500)
    const awayPad = this.pad(ax, az, ay)
    inNether(`tp ${name} ${awayPad.x} ${awayPad.y} ${awayPad.z} 180 0`)
    await waitFor(() => inTheNether() && near(awayPad, 0.5), 15, 'arriving in the nether')
    await sleep(1000)
    messages()
    bot.chat(`/wormhole beam admin set ${awayLabel}Pad`)
    await sleep(1500)
    const savedAway = messages()
    await teleport(...standardGate(hx, hz).stand, 180)
    console.log(`  beam destinations: ${JSON.stringify(savedHome)} ${JSON.stringify(savedAway)}`)
  },
  async setPets (on) {
    const from = logSize()
    serverCommand(`wx config pets-follow-owner ${on}`)
    await waitForLog(new RegExp(`PETS_FOLLOW_OWNER is now ${on}`), 20, from)
  },
  /** Its wolves, and the nether room setup force-loads, which only a run in progress needs. */
  cleanup () {
    const [, hx, hz] = this.home
    const [, ax, az, ay] = this.away
    clearAround('wolf', hx, -60, hz, 30)
    inNether(`kill @e[type=wolf,x=${ax},y=${ay},z=${az},distance=..30]`)
    inNether(`forceload remove ${ax - 16} ${az - 16} ${ax + 16} ${az + 16}`)
  },
  /** With the plugin's FINE log on, which says which pets it takes and why it leaves any. */
  async run () {
    const from = logSize()
    serverCommand('wx config log-level FINE')
    await waitForLog(/LOG_LEVEL is now FINE/, 20, from)
    try {
      await this.legs()
    } finally {
      serverCommand('wx config log-level INFO')
    }
  },
  async legs () {
    const [homeLabel, hx, hz] = this.home
    const [awayLabel, ax, az, ay] = this.away
    // Held loaded again for this run: the last one's cleanup let it go.
    inNether(`forceload add ${ax - 16} ${az - 16} ${ax + 16} ${az + 16}`)
    const home = standardGate(hx, hz)
    const away = standardGate(ax, az, ay)
    const homePad = this.pad(hx, hz)
    const awayPad = this.pad(ax, az, ay)
    const followAt = v(hx - 4.5, -60, hz + 4.5)
    const run = ++this.runs
    await teleport(...home.stand, 180)
    // Last run's wolves are killed wherever they are loaded, and told apart by UUID if not.
    clearAround('wolf', hx, -60, hz, 30)
    inNether(`kill @e[type=wolf,x=${ax},y=${ay},z=${az},distance=..30]`)
    serverCommand(`clear ${name}`)
    await this.setPets(true)
    await sleep(500)

    narrate('A wolf tamed to WxBot, told to sit; it must stay put through all of this')
    const sitAt = v(hx - 6.5, -60, hz + 7.5)
    const sitter = await tamedWolf(sitAt, `wxsitter${run}`)
    await teleport(sitAt.x + 1.5, -60, sitAt.z, 90)
    await bot.lookAt(sitter.position.offset(0, 0.4, 0), true)
    await bot.activateEntity(sitter)
    await sleep(1500)
    const sat = sitter.position.clone()

    // Each leg has a wolf of its own, so one left behind cannot spoil the next; a leg that fails
    // is noted and the rest still run, and the trip fails at the end with all of them.
    const failed = []
    const leg = async (label, body) => {
      try {
        await body()
        narrate(`${label}: as it should be`)
      } catch (e) {
        failed.push(`${label}: ${e.message}`)
        narrate(`${label} FAILED: ${e.message}`)
      }
      if (inTheNether()) {
        try {
          await beamTo(`${homeLabel}Pad`, homePad, false)
        } catch (e) {
          // Said, and the bot fetched home from the console, so the legs after still run.
          failed.push(`beaming home after ${label}: ${e.message}`)
          await teleport(...home.stand, 180)
        }
      }
      if (wolfNear(sitter.uuid, 60) && wolfNear(sitter.uuid, 60).position.distanceTo(sat) > 1) {
        failed.push(`the sitting wolf moved from ${sat} to ${wolfNear(sitter.uuid, 60).position} during ${label}`)
      }
    }

    let controlTag = null
    await leg('control', async () => {
      narrate('Control: with pets-follow-owner off, a following wolf must not come to the nether')
      const tag = `wxcontrol${run}`
      await teleport(...home.stand, 180)
      const wolf = await tamedWolf(followAt, tag)
      controlTag = tag
      await this.setPets(false)
      try {
        await beamTo(`${awayLabel}Pad`, awayPad, true)
        await sleep(4000)
        if (wolfNear(wolf.uuid, 60)) throw new Error('the wolf came to the nether with pets-follow-owner off, so something other than the plugin brings it, and the legs below prove nothing')
      } finally {
        await this.setPets(true)
      }
    })

    // Its not coming means something only while it is still here to come. Asked of the server, since
    // the bot, just back from the nether, may not have been sent the overworld's entities yet.
    if (controlTag) {
      const from = logSize()
      serverCommand(`data get entity @e[type=wolf,tag=${controlTag},limit=1] Pos`)
      await waitForLog(/has the following entity data: \[/, 10, from)
        .catch(() => failed.push('control: the wolf is gone, so its not coming to the nether proves nothing'))
    }

    await leg('beam', async () => {
      narrate('Beaming to the nether with a following wolf; it must come too')
      const tag = `wxbeamed${run}`
      await teleport(...home.stand, 180)
      const wolf = await tamedWolf(followAt, tag)
      await waitForWolf(wolf, tag, 10, 10, 'the wolf coming near before beaming')
      await beamTo(`${awayLabel}Pad`, awayPad, true)
      await waitForWolf(wolf, tag, 5, 8, `the wolf arriving beside the bot at ${awayLabel}Pad`)
      narrate('The wolf beamed to the nether too; beaming back, and it must come back')
      await beamTo(`${homeLabel}Pad`, homePad, false)
      await waitForWolf(wolf, tag, 5, 8, `the wolf arriving beside the bot back at ${homeLabel}Pad`)
    })

    await leg('gate', async () => {
      const tag = `wxgated${run}`
      await teleport(...home.stand, 180)
      const wolf = await tamedWolf(followAt, tag)
      await dialAndOpen(home, homeLabel, awayLabel)
      await walkInWithWolf(wolf, tag, home, away, `${awayLabel}, in the nether`, true)
      await sleep(2000)
      if (wolfNear(sitter.uuid, 60)) throw new Error(`the sitting wolf came through to ${awayLabel} too`)
    })

    await teleport(...home.stand, 180)
    const still = wolfNear(sitter.uuid, 30)
    if (!still) failed.push(`the sitting wolf is gone from beside ${homeLabel}`)
    else if (still.position.distanceTo(sat) > 1) failed.push(`the sitting wolf moved from ${sat} to ${still.position}`)
    else narrate('The sitting wolf is still where it sat')
    clearAround('wolf', hx, -60, hz, 30)
    await waitForShut(home.opening, homeLabel)
    if (failed.length > 0) throw new Error(failed.join('; '))
  }
}

// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------

async function main () {
  await kit.connect()
  const gone = kit.gone
  bot = kit.bot

  if (observe) await waitForObserver()

  const every = [gate, beam, ring, atlantis, horizontal, lava, iris, mirror, boat, minecart, mount, pet]
  const unknown = (only || []).filter((n) => !every.some((t) => t.name === n))
  if (unknown.length > 0) throw new Error(`TRIPS names no trip called ${unknown.join(', ')}; there are ${every.map((t) => t.name).join(', ')}`)
  const trips = only ? only.map((n) => every.find((t) => t.name === n)) : every
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
    // A trip that failed in the saddle would leave the next one mounted, or its vehicle behind.
    await getOff().catch((e) => console.log(`  getting off after ${trip.name}: ${e.message}`))
    if (trip.cleanup) {
      try { await trip.cleanup() } catch (e) { console.log(`  cleaning up after ${trip.name}: ${e.message}`) }
    }
    console.log(`${outcome} ${trip.name}${detail ? ': ' + detail : ''}`)
    const seen = observe ? await askObserver(trip.sight) : 'not watched'
    results.push({ trip: trip.name + (again ? '*' : ''), outcome, seen, detail })
  }
  for (const trip of trips) await take(trip, setUp.has(trip))

  // Someone watching can see any trip again before the server stops.
  while (observe && bot.players[kit.observer]) {
    const choice = await askObserverChoice(
      `Say ${every.map((t) => t.name).join(', ')} to see that trip again, all for every one, or done to stop.`,
      new RegExp(`^(${every.map((t) => t.name).join('|')}|all|again|done|stop)$`))
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
