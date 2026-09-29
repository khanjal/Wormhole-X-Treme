'use strict';
// Tick-exact observation of launched things (projectiles, dropped items, XP), by datapack.
//
// A console command arrives several ticks after a shot, and an arrow crosses a gate in two or
// three, so nothing here is done from the bot. A tick function does it instead:
//  - anything of a tracked type that appears within 6 of a `wx_launch` marker is tagged
//    `wx_launched` on its first tick;
//  - the first tick a `wx_launched` thing is within 5 of a `wx_far` marker (the far gate's exit),
//    its position and motion are stored in fake scores (#x #y #z #vx #vy #vz, x100 / x1000), it
//    is tagged `wx_arrived`, and, if #freeze is 1, it is stopped where it is (no gravity, no
//    motion, no pickup) so the bot can read it at leisure.
// The plugin must carry the thing through as itself: a copy made at the far end has no tag and
// never counts as arrived, which is the point.

const { atLeast } = require('./version');

const OBJECTIVE = 'wx_obs';

/** Entity types a launch can produce, by version; optional entries so no version fails to load. */
function trackedTypes(version) {
  const types = ['arrow', 'spectral_arrow', 'trident', 'snowball', 'egg', 'ender_pearl', 'fireball', 'small_fireball',
    'llama_spit', 'firework_rocket', 'item', 'experience_orb'];
  if (atLeast(version, '1.21.5')) types.push('splash_potion', 'lingering_potion');
  else types.push('potion');
  if (atLeast(version, '1.21')) types.push('wind_charge');
  return types.map((t) => ({ id: `minecraft:${t}`, required: false }));
}

function functions() {
  return {
    tick: [
      '# wx:tick: tags launches and catches arrivals; see scripts/facility/lib/observe.js',
      'execute as @e[type=minecraft:marker,tag=wx_launch] at @s as @e[type=#wx:tracked,tag=!wx_seen,distance=..6] run tag @s add wx_launched',
      'tag @e[type=#wx:tracked,tag=!wx_seen] add wx_seen',
      'execute as @e[type=minecraft:marker,tag=wx_far] at @s as @e[tag=wx_launched,tag=!wx_arrived,distance=..5] run function wx:observe/arrive',
      '',
    ].join('\n'),
    'observe/arrive': [
      `tag @s add wx_arrived`,
      `scoreboard players add #arrived ${OBJECTIVE} 1`,
      `execute store result score #x ${OBJECTIVE} run data get entity @s Pos[0] 100`,
      `execute store result score #y ${OBJECTIVE} run data get entity @s Pos[1] 100`,
      `execute store result score #z ${OBJECTIVE} run data get entity @s Pos[2] 100`,
      `execute store result score #vx ${OBJECTIVE} run data get entity @s Motion[0] 1000`,
      `execute store result score #vy ${OBJECTIVE} run data get entity @s Motion[1] 1000`,
      `execute store result score #vz ${OBJECTIVE} run data get entity @s Motion[2] 1000`,
      `execute if score #freeze ${OBJECTIVE} matches 1 run data merge entity @s {NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}`,
      `execute if score #freeze ${OBJECTIVE} matches 1 if entity @s[type=minecraft:item] run data merge entity @s {PickupDelay:32767s}`,
      '',
    ].join('\n'),
  };
}

function tags(version) {
  return {
    'function/minecraft:tick': ['wx:tick'],
    'entity_type/wx:tracked': trackedTypes(version),
  };
}

const HOLDERS = ['#arrived', '#x', '#y', '#z', '#vx', '#vy', '#vz'];

/** Reads and clears the arrival scores and the markers around one launch. */
class Watch {
  constructor(srv) {
    this.srv = srv;
  }

  async prepare() {
    await this.srv.run(`scoreboard objectives add ${OBJECTIVE} dummy`);
  }

  /** Clears the last launch: scores, markers, and the tags on anything left over. */
  async clear() {
    for (const h of HOLDERS) await this.srv.run(`scoreboard players set ${h} ${OBJECTIVE} 0`);
    await this.srv.run('kill @e[type=minecraft:marker,tag=wx_watch]');
    await this.srv.run('kill @e[tag=wx_launched]');
  }

  /** Arms a launch: a marker where things are launched, one at the far exit, and freezing. */
  async arm(dim, launch, far, { freeze = true } = {}) {
    await this.clear();
    await this.srv.run(`scoreboard players set #freeze ${OBJECTIVE} ${freeze ? 1 : 0}`);
    await this.srv.run(`execute in ${dim} run summon minecraft:marker ${launch.x} ${launch.y} ${launch.z} {Tags:["wx_watch","wx_launch"]}`);
    await this.srv.run(`execute in ${far.dim || dim} run summon minecraft:marker ${far.x} ${far.y} ${far.z} {Tags:["wx_watch","wx_far"]}`);
  }

  /** Stops tagging new launches (so a second shot is not mistaken for the first). */
  async disarm() {
    await this.srv.run('kill @e[type=minecraft:marker,tag=wx_launch]');
  }

  async score(holder) {
    const r = await this.srv.run(`scoreboard players get ${holder} ${OBJECTIVE}`);
    const m = r.lines.map((l) => /has (-?\d+) \[/.exec(l)).find(Boolean);
    return m ? Number(m[1]) : null;
  }

  /** The first arrival: { count, at: {x,y,z}, motion: {x,y,z} } in blocks and blocks/tick. */
  async arrival() {
    const count = (await this.score('#arrived')) || 0;
    const at = { x: (await this.score('#x')) / 100, y: (await this.score('#y')) / 100, z: (await this.score('#z')) / 100 };
    const motion = { x: (await this.score('#vx')) / 1000, y: (await this.score('#vy')) / 1000, z: (await this.score('#vz')) / 1000 };
    return { count, at, motion };
  }

  /** Waits (bounded) until something has arrived. */
  async waitArrival(ms = 8000) {
    const deadline = Date.now() + ms;
    for (;;) {
      const n = await this.score('#arrived');
      if (n > 0) return n;
      if (Date.now() > deadline) return 0;
      await new Promise((resolve) => { setTimeout(resolve, 100); });
    }
  }

  /** How many launched things never arrived (left on the near side). */
  async leftBehind() {
    const r = await this.srv.run('execute if entity @e[tag=wx_launched,tag=!wx_arrived]');
    const m = r.lines.map((l) => /count: (\d+)/.exec(l)).find(Boolean);
    return m ? Number(m[1]) : 0;
  }
}

module.exports = { OBJECTIVE, trackedTypes, functions, tags, Watch };
