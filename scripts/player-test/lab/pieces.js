'use strict'
// The small pieces the lab is built from: a room, a doorway, a strip of lamps, a sign, a book. Each
// sends console commands, so the lab is put together on a fresh world every time and survives a
// Minecraft version change, rather than being a world save that would need upgrading.
//
// The lab stands on a raised floor: floor blocks at FLOOR, and a player's feet at FEET. Under it,
// down to the flat world's grass, is stone, so a gate can be sunk into it.

const kit = require('../kit')

const FLOOR = -60
const FEET = -59

/** Sends a console command, in the overworld unless `where` says the nether. */
function run (command, where = 'overworld') {
  if (where === 'nether') kit.inNether(command)
  else kit.serverCommand(command)
}

/**
 * Fills a box, split into slabs of at most 32768 blocks, the most one /fill takes. `mode` is
 * appended as it is (hollow, outline, keep, replace air, and so on).
 */
function fill (x0, y0, z0, x1, y1, z1, block, mode = '', where) {
  const [ax, bx] = [Math.min(x0, x1), Math.max(x0, x1)]
  const [ay, by] = [Math.min(y0, y1), Math.max(y0, y1)]
  const [az, bz] = [Math.min(z0, z1), Math.max(z0, z1)]
  const perLayer = (bx - ax + 1) * (bz - az + 1)
  // Whole layers at a time, or rows of one layer when a layer alone is too big.
  if (perLayer <= 32768) {
    const layers = Math.max(1, Math.floor(32768 / perLayer))
    for (let y = ay; y <= by; y += layers) {
      run(`fill ${ax} ${y} ${az} ${bx} ${Math.min(by, y + layers - 1)} ${bz} ${block} ${mode}`.trim(), where)
    }
    return
  }
  const rows = Math.max(1, Math.floor(32768 / (bx - ax + 1)))
  for (let y = ay; y <= by; y++) {
    for (let z = az; z <= bz; z += rows) {
      run(`fill ${ax} ${y} ${z} ${bx} ${y} ${Math.min(bz, z + rows - 1)} ${block} ${mode}`.trim(), where)
    }
  }
}

function setblock (x, y, z, block, where) {
  run(`setblock ${x} ${y} ${z} ${block}`, where)
}

/**
 * A room: walls and a ceiling of `wall`, a floor of `floor` at FLOOR, and air inside from FEET up
 * to the ceiling. The floor and walls run from (x0, z0) to (x1, z1), both included.
 */
function room (x0, z0, x1, z1, top, { wall = 'gray_concrete', floor = 'polished_andesite', ceiling = wall, where } = {}) {
  // Wall by wall: an outline fill split into slabs would leave a floor at every split.
  fill(x0 + 1, FEET, z0 + 1, x1 - 1, top - 1, z1 - 1, 'air', '', where)
  fill(x0, FLOOR, z0, x1, top, z0, wall, '', where)
  fill(x0, FLOOR, z1, x1, top, z1, wall, '', where)
  fill(x0, FLOOR, z0, x0, top, z1, wall, '', where)
  fill(x1, FLOOR, z0, x1, top, z1, wall, '', where)
  fill(x0, top, z0, x1, top, z1, ceiling, '', where)
  fill(x0 + 1, FLOOR, z0 + 1, x1 - 1, FLOOR, z1 - 1, floor, '', where)
}

/**
 * A doorway three wide and three tall through a wall, framed in `frame` with hazard striping
 * under it. `along` is the axis the wall runs along: 'x' for a wall facing north or south.
 */
function doorway (x, z, along, { frame = 'iron_block', where } = {}) {
  const span = (d) => along === 'x' ? [x + d, z] : [x, z + d]
  for (let d = -2; d <= 2; d++) {
    const [px, pz] = span(d)
    for (let y = FEET; y <= FEET + 3; y++) {
      const edge = Math.abs(d) === 2 || y === FEET + 3
      setblock(px, y, pz, edge ? frame : 'air', where)
    }
    setblock(px, FLOOR, pz, d % 2 === 0 ? 'yellow_concrete' : 'black_concrete', where)
  }
}

/** Shuts or opens a three-wide doorway made by doorway(), as blast doors do. */
function blastDoor (x, z, along, shut, where) {
  const [a, b] = along === 'x' ? [[x - 1, z], [x + 1, z]] : [[x, z - 1], [x, z + 1]]
  fill(a[0], FEET, a[1], b[0], FEET + 2, b[1], shut ? 'iron_block' : 'air', '', where)
}

/** Hazard striping, alternating yellow and black, round the edge of a floor area. */
function hazardBorder (x0, z0, x1, z1, where) {
  for (let x = x0; x <= x1; x++) {
    for (const z of [z0, z1]) setblock(x, FLOOR, z, (x + z) % 2 === 0 ? 'yellow_concrete' : 'black_concrete', where)
  }
  for (let z = z0; z <= z1; z++) {
    for (const x of [x0, x1]) setblock(x, FLOOR, z, (x + z) % 2 === 0 ? 'yellow_concrete' : 'black_concrete', where)
  }
}

/**
 * A row of redstone lamps along x at height y, each lit by a redstone block behind it (at z +
 * `behind`), which lampStrip.set swaps for stone to put it out. Returns the lamps' switches.
 */
function lampStrip (x0, count, y, z, behind, where) {
  const lamps = []
  for (let i = 0; i < count; i++) {
    const x = x0 + i * 2
    setblock(x, y, z + behind, 'stone', where)
    setblock(x, y, z, 'redstone_lamp', where)
    lamps.push({ x, y, z: z + behind })
  }
  return lamps
}

/** Lights or puts out lamps from lampStrip. */
function setLamps (lamps, lit, where) {
  for (const lamp of lamps) setblock(lamp.x, lamp.y, lamp.z, lit ? 'redstone_block' : 'stone', where)
}

// ---------------------------------------------------------------------------------------------
// Text. 1.21.5 moved a sign's lines and a book's pages from JSON held in strings to text
// components written as SNBT, so each is written for the version the server runs.

function newText () {
  return kit.versionAtLeast('1.21.5')
}

/** One line of text, optionally coloured, as a sign's messages or a book's pages hold it. */
function text (line, color) {
  // No quotes or backslashes, and a line break written as an escape: a console command is one line.
  const clean = String(line).replace(/["'\\]/g, '')
  if (newText()) {
    const body = clean.replace(/\n/g, '\\n')
    return color ? `{text:"${body}",color:"${color}"}` : `"${body}"`
  }
  // JSON inside an SNBT string: the escape is itself escaped once.
  const body = clean.replace(/\n/g, '\\\\n')
  const json = color ? `{"text":"${body}","color":"${color}"}` : `{"text":"${body}"}`
  return `'${json}'`
}

/** The front of a sign holding up to four lines, each a string or [string, colour]. */
function signData (lines, waxed = true) {
  const four = [...lines, '', '', '', ''].slice(0, 4)
  const messages = four.map((line) => Array.isArray(line) ? text(line[0], line[1]) : text(line))
  return `{front_text:{messages:[${messages.join(',')}]}${waxed ? ',is_waxed:1b' : ''}}`
}

/** A waxed wall sign facing `facing` holding `lines`. */
function sign (x, y, z, facing, lines, { wood = 'oak', where, waxed = true } = {}) {
  setblock(x, y, z, `${wood}_wall_sign[facing=${facing}]${signData(lines, waxed)}`, where)
}

/** Rewrites a sign's lines in place. */
function rewrite (x, y, z, lines, where) {
  run(`data merge block ${x} ${y} ${z} ${signData(lines)}`, where)
}

/** A lectern facing `facing` holding a written book of `pages`, each a string. */
function lectern (x, y, z, facing, title, pages, where) {
  const book = pages.map((page) => text(page)).join(',')
  let data
  if (!kit.versionAtLeast('1.20.5')) {
    data = `{Book:{id:"minecraft:written_book",Count:1b,tag:{title:"${title}",author:"WxBot",pages:[${book}]}}}`
  } else {
    data = `{Book:{id:"minecraft:written_book",count:1,components:{"minecraft:written_book_content":{title:"${title}",author:"WxBot",pages:[${book}]}}}}`
  }
  setblock(x, y, z, `lectern[facing=${facing},has_book=true]${data}`, where)
}

/**
 * A command block at (x, y, z) that says `said` when powered, and a button (or lever) on its face
 * towards `facing`. Pressing it puts "[@] said" in chat, which the bot hears, and in the log.
 */
function commandButton (x, y, z, facing, said, { button = 'stone_button', lever = false, where } = {}) {
  setblock(x, y, z, `command_block{Command:"say ${said}",auto:0b}`, where)
  const [dx, dz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[facing]
  const switchBlock = lever ? `lever[face=wall,facing=${facing},powered=false]` : `${button}[face=wall,facing=${facing}]`
  setblock(x + dx, y, z + dz, switchBlock, where)
  return { x: x + dx, y, z: z + dz }
}

module.exports = {
  FLOOR,
  FEET,
  run,
  fill,
  setblock,
  room,
  doorway,
  blastDoor,
  hazardBorder,
  lampStrip,
  setLamps,
  text,
  signData,
  sign,
  rewrite,
  lectern,
  commandButton
}
