'use strict'
// The mirror gallery: a dim museum hall lit by candles and amethyst, the mirrors hung like
// paintings with a plaque beside each, and at the far end a locked vault that the vault and
// locked mirrors lead into. Its panel picks a mirror to start from and one to go to; Run has the
// bot right-click the first until it opens onto the second, and punch through. A bare wall takes
// a banner you hang yourself, which joins as "yours".

const kit = require('../kit')
const p = require('./pieces')

const { v, sleep, serverCommand, narrate, teleport, messages } = kit
const { FLOOR, FEET } = p

const HALL = { x0: -32, z0: 38, x1: 32, z1: 66, top: -50 }
const VAULT = { x0: 32, z0: 42, x1: 46, z1: 62, top: -52 }
const BANNER_Y = FEET + 1
// Each mirror: its banner, the way it faces, and the look stamped on it. North wall banners face
// south into the hall, south wall banners face north; the vault's two hang inside it.
const MIRRORS = {
  hub: { x: -22, z: HALL.z0 + 1, facing: 'south', look: 'hub' },
  library: { x: -8, z: HALL.z0 + 1, facing: 'south', look: 'library' },
  plains: { x: 6, z: HALL.z0 + 1, facing: 'south', look: 'plains' },
  nether: { x: -22, z: HALL.z1 - 1, facing: 'north', look: 'nether' },
  jungle: { x: -8, z: HALL.z1 - 1, facing: 'north', look: 'jungle' },
  end: { x: 6, z: HALL.z1 - 1, facing: 'north', look: 'end' },
  vault: { x: 39, z: VAULT.z0 + 1, facing: 'south', look: 'vault' },
  locked: { x: 39, z: VAULT.z1 - 1, facing: 'north', look: 'locked' }
}
// The bare wall for a banner of your own.
const YOURS = { x: 20, z: HALL.z0 + 1, facing: 'south' }

const names = [...Object.keys(MIRRORS), 'yours']
const options = [
  { key: 'from', label: 'From', values: names },
  { key: 'to', label: 'To', values: names, start: 1 }
]

/** One step out from a banner's wall, into the room it faces. */
function out (m, by) {
  const [dx, , dz] = { south: [0, 0, 1], north: [0, 0, -1], east: [1, 0, 0], west: [-1, 0, 0] }[m.facing]
  return v(m.x + 0.5 + dx * by, FEET, m.z + 0.5 + dz * by)
}

function build () {
  const h = HALL
  p.room(h.x0, h.z0, h.x1, h.z1, h.top, { wall: 'deepslate_bricks', floor: 'dark_oak_planks', ceiling: 'deepslate_tiles' })
  p.fill(h.x0 + 1, FLOOR - 3, h.z0 + 1, h.x1 - 1, FLOOR - 1, h.z1 - 1, 'stone')
  // The walls the mirrors hang on are three deep, so each opening has solid wall behind and round it.
  p.fill(h.x0, FLOOR, h.z0 - 2, h.x1, h.top, h.z0 - 1, 'deepslate_bricks')
  p.fill(h.x0, FLOOR, h.z1 + 1, h.x1, h.top, h.z1 + 2, 'deepslate_bricks')
  // A runner down the middle, candles and amethyst for light.
  p.fill(h.x0 + 2, FLOOR, 51, h.x1 - 2, FLOOR, 53, 'red_wool')
  for (let x = h.x0 + 4; x < h.x1 - 2; x += 7) {
    p.setblock(x, FEET, 49, 'amethyst_block')
    p.setblock(x, FEET + 1, 49, 'candle[candles=3,lit=true]')
    p.setblock(x, FEET, 55, 'amethyst_block')
    p.setblock(x, FEET + 1, 55, 'candle[candles=3,lit=true]')
    p.setblock(x, h.top - 1, 52, 'amethyst_cluster[facing=down]')
  }

  // The vault: gold, and a door with nothing to open it from outside.
  const w = VAULT
  p.room(w.x0, w.z0, w.x1, w.z1, w.top, { wall: 'iron_block', floor: 'gold_block', ceiling: 'iron_block' })
  p.fill(w.x0 + 1, FLOOR - 3, w.z0 + 1, w.x1 - 1, FLOOR - 1, w.z1 - 1, 'stone')
  p.fill(w.x0, FLOOR, w.z0 - 2, w.x1, w.top, w.z0 - 1, 'iron_block')
  p.fill(w.x0, FLOOR, w.z1 + 1, w.x1, w.top, w.z1 + 2, 'iron_block')
  p.setblock(w.x0, FEET, 52, 'iron_door[facing=west,half=lower]')
  p.setblock(w.x0, FEET + 1, 52, 'iron_door[facing=west,half=upper]')
  p.sign(w.x0 - 1, FEET + 2, 52, 'west', [['VAULT', 'gold'], 'Locked.', 'Try a mirror.'])
  for (const z of [w.z0 + 3, w.z1 - 3]) {
    p.setblock(w.x0 + 2, FEET, z, 'chest[facing=east]')
    p.setblock(w.x1 - 2, FEET, z, 'chest[facing=west]')
  }

  // The mirrors, from the console, each with its plaque. They are captured in the background.
  serverCommand('wx config mirror-per-world-limit 0')
  for (const [label, m] of Object.entries(MIRRORS)) {
    p.setblock(m.x, BANNER_Y, m.z, `white_wall_banner[facing=${m.facing}]`)
    serverCommand(`wx mirror create ${label} world ${m.x} ${BANNER_Y} ${m.z}`)
    serverCommand(`wx mirror set ${label} -stamp ${m.look}`)
    const plaque = out(m, 0)
    p.sign(plaque.x - 0.5 + 2, BANNER_Y, plaque.z - 0.5, m.facing, [[label.toUpperCase(), 'gold'], `look: ${m.look}`])
  }
  p.sign(YOURS.x + 2, BANNER_Y, YOURS.z, YOURS.facing, [['YOUR MIRROR', 'gold'], 'Hang a wall', 'banner to my', 'left: "yours"'])
  p.setblock(YOURS.x, FLOOR, YOURS.z + 1, 'yellow_concrete')
}

/** Where a mirror hangs: one of the gallery's, or the banner you hung, made a mirror now. */
async function mirrorFor (label) {
  if (label !== 'yours') return MIRRORS[label]
  const banner = kit.bot.blockAt(v(YOURS.x, BANNER_Y, YOURS.z))
  if (!banner || !banner.name.endsWith('_wall_banner')) {
    throw Object.assign(new Error(`Not run: hang a wall banner on the yellow-marked wall first (there is ${banner ? banner.name : 'nothing loaded'} there).`), { notRun: true })
  }
  const from = kit.logSize()
  serverCommand(`wx mirror create yours world ${YOURS.x} ${BANNER_Y} ${YOURS.z}`)
  await kit.waitForLog(/Mirror .{0,12}yours.{0,12} is this banner|is .{0,12}yours.{0,12} now/, 20, from).catch(() => {})
  await captured('yours')
  return YOURS
}

/** Waits for a mirror's room to be captured, as `mirror debug <name> -all` reports it. */
async function captured (label) {
  const deadline = Date.now() + 60000
  let line = 'nothing'
  while (Date.now() < deadline) {
    const from = kit.heard.length
    kit.bot.chat(`/wormhole mirror debug ${label} -all`)
    await sleep(2000)
    line = kit.heard.slice(from).find((l) => l.includes('file: ')) || 'no file line'
    if (/file: \d+ bytes/.test(line) && !line.includes('being taken')) return
  }
  throw new Error(`${label}'s room was not captured within 60s (${line.trim()})`)
}

async function run (lab, values) {
  if (values.from === values.to) throw Object.assign(new Error('Not run: pick two different mirrors.'), { notRun: true })
  const from = await mirrorFor(values.from)
  const to = await mirrorFor(values.to)
  await captured(values.from)
  await captured(values.to)
  const stand = out(from, 3)
  await teleport(stand.x, FEET, stand.z, { south: 180, north: 0, east: 90, west: -90 }[from.facing])
  narrate(`At ${values.from}; right-clicking it until it opens onto ${values.to}`)
  messages()
  const banner = v(from.x, BANNER_Y, from.z)
  // A mirror steps through the others by name, one a click.
  await kit.turnMirror(banner, from.facing, values.to, names.length + 2)
  narrate(`${values.from} opens onto ${values.to}`)
  await sleep(2000)
  await kit.punchMirror(banner, from.facing, v(to.x + 0.5, FEET, to.z + 0.5), values.to)
  return `${values.from} to ${values.to}`
}

module.exports = {
  name: 'mirror',
  title: 'MIRRORS',
  options,
  panel: { x: -28, z: 46, facing: 'west' },
  vantage: [-24, FEET + 3, 52, -90, 10],
  build,
  run,
  async reset () {
    serverCommand('wx mirror remove yours')
    for (const label of Object.keys(MIRRORS)) serverCommand(`wx mirror set ${label} -start -none`)
    // A mirror left open onto another settles back once nobody is near it.
    await teleport(0.5, FEET, 20.5, 180)
    await sleep(1000)
  },
  MIRRORS,
  YOURS,
  BANNER_Y,
  captured,
  HALL
}
