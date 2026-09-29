'use strict'
// A bay's control panel: a waist-high console with an option sign for each choice, a next button
// under each sign, a big Run button, a Reset lever and the bay's status lamp. Every button and the
// lever sit on a command block that says what was pressed ("lab:next gate shape"), so its meaning
// can be read in-game by looking at the block, and the bot hears it in chat. The bot owns the
// state: signs cannot cycle on their own, since right-clicking one opens its editor, so the bot
// moves to the next value and rewrites the sign.

const p = require('./pieces')

const { FEET } = p

const STEPS = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }
// The direction along the console, left to right for someone facing it.
const ALONG = { south: [1, 0], north: [-1, 0], east: [0, -1], west: [0, 1] }

class Panel {
  /**
   * `at` is the console's first column: the block the first option's button sits on. `facing` is
   * the side the operator stands on. `options` are the bay's choices; `pad` makes a panel with
   * only Run and Reset, for a build-it-yourself pad.
   */
  constructor (bay, at, { options = [], pad = false, title }) {
    this.bay = bay
    this.pad = pad
    this.key = pad ? `${bay}pad` : bay
    this.title = title
    this.facing = at.facing
    this.options = options.map((option) => ({ ...option, index: option.start || 0 }))
    const [dx, dz] = STEPS[at.facing]
    const [ax, az] = ALONG[at.facing]
    this.front = { dx, dz }
    this.column = (i) => ({ x: at.x + ax * 2 * i, z: at.z + az * 2 * i })
    const n = this.options.length
    this.runAt = this.column(n)
    this.lampAt = this.column(n + 0.5)
    this.resetAt = this.column(n + 1)
    this.ends = [this.column(-0.5), this.column(n + 1.5)]
  }

  /** The value each option shows now, keyed by option. */
  values () {
    return Object.fromEntries(this.options.map((o) => [o.key, o.values[o.index]]))
  }

  show (option) {
    const value = option.values[option.index]
    return option.show ? option.show(value) : value
  }

  build () {
    const { dx, dz } = this.front
    const [a, b] = this.ends
    // The console: two blocks high, iron-edged.
    p.fill(a.x, FEET, a.z, b.x, FEET + 1, b.z, 'light_gray_concrete')
    p.setblock(a.x, FEET + 1, a.z, 'iron_block')
    p.setblock(b.x, FEET + 1, b.z, 'iron_block')
    this.options.forEach((option, i) => {
      const c = this.column(i)
      this.writeSign(option, i)
      p.commandButton(c.x, FEET, c.z, this.facing, `lab:next ${this.key} ${option.key}`)
    })
    const r = this.runAt
    p.commandButton(r.x, FEET, r.z, this.facing, `lab:run ${this.key}`, { button: 'polished_blackstone_button' })
    p.sign(r.x + dx, FEET + 1, r.z + dz, this.facing, [['RUN', 'green'], this.title, this.pad ? 'the pad' : '', 'press below'])
    const s = this.resetAt
    p.commandButton(s.x, FEET, s.z, this.facing, `lab:reset ${this.key}`, { lever: true })
    p.sign(s.x + dx, FEET + 1, s.z + dz, this.facing, [['RESET', 'red'], this.title, this.pad ? 'the pad' : '', 'pull below'])
    this.status('idle')
  }

  writeSign (option, i) {
    const { dx, dz } = this.front
    const c = this.column(i)
    p.sign(c.x + dx, FEET + 1, c.z + dz, this.facing, [[option.label, 'aqua'], [this.show(option), 'white'], '', 'next below'])
  }

  /** Moves an option to its next value and rewrites its sign; returns the new value, or null. */
  next (key) {
    const i = this.options.findIndex((o) => o.key === key)
    if (i < 0) return null
    const option = this.options[i]
    option.index = (option.index + 1) % option.values.length
    const { dx, dz } = this.front
    const c = this.column(i)
    p.rewrite(c.x + dx, FEET + 1, c.z + dz, [[option.label, 'aqua'], [this.show(option), 'white'], '', 'next below'])
    return option.values[option.index]
  }

  /**
   * The status lamp atop the console, between Run and Reset: green idle, amber running, red for a
   * failed run. Red is a redstone lamp, so the console block under it becomes redstone first.
   */
  status (state) {
    const at = this.lampAt
    const x = Math.round(at.x)
    const z = Math.round(at.z)
    if (state === 'failed') {
      p.setblock(x, FEET + 1, z, 'redstone_block')
      p.setblock(x, FEET + 2, z, 'redstone_lamp')
    } else {
      p.setblock(x, FEET + 1, z, 'light_gray_concrete')
      p.setblock(x, FEET + 2, z, state === 'running' ? 'ochre_froglight' : 'verdant_froglight')
    }
  }

  /** Puts the Reset lever back up, so the next pull is a rising edge again. */
  releaseReset () {
    const { dx, dz } = this.front
    const s = this.resetAt
    p.setblock(s.x + dx, FEET, s.z + dz, `lever[face=wall,facing=${this.facing},powered=false]`)
  }

  /** Where the button for an option, Run or Reset is, for the self-test to press. */
  switchFor (what) {
    const { dx, dz } = this.front
    const c = what === 'run' ? this.runAt : what === 'reset' ? this.resetAt : this.column(this.options.findIndex((o) => o.key === what))
    return { x: c.x + dx, y: FEET, z: c.z + dz }
  }

  /** Where an option's sign is, for the self-test to read. */
  signFor (key) {
    const { dx, dz } = this.front
    const c = this.column(this.options.findIndex((o) => o.key === key))
    return { x: c.x + dx, y: FEET + 1, z: c.z + dz }
  }

  /** Where someone stands to use the console, facing it: yaw in Bukkit degrees. */
  standing () {
    const { dx, dz } = this.front
    const mid = this.column((this.options.length + 1) / 2)
    const yaw = { south: 180, north: 0, east: 90, west: -90 }[this.facing]
    return { x: mid.x + dx * 2 + 0.5, y: FEET, z: mid.z + dz * 2 + 0.5, yaw }
  }
}

module.exports = { Panel }
