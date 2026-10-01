'use strict';
// Tick-exact observation of launched things (projectiles, dropped items, XP), by datapack.
//
// A console command arrives several ticks after a shot, and an arrow crosses a gate in two or
// three, so nothing here is done from the bot. A tick function does it instead:
//  - anything of a tracked type that appears within 6 of a `wx_launch` marker is tagged
//    `wx_launched` on its first tick, counted in #launched, and the first one's data is kept in
//    storage wx:obs `first`;
//  - the first tick something launched, or something tracked that has never been seen before
//    (the plugin re-makes a projectile at the far gate, as a new entity without tags), is within
//    5 of a `wx_far` marker, its position and motion go into fake scores (#x #y #z, x100, and
//    #vx #vy #vz, x1000), #same records whether it is the launched entity itself, it is tagged
//    `wx_arrived`, and with #freeze 1 it is held still (every tick, since the plugin sets the
//    velocity again a tick after the crossing) so the bot can read it.
// Anything already lying about when the watch was armed is `wx_seen` and never counts.

const { atLeast } = require('./version');

const OBJECTIVE = 'wx_obs';

/** Entity types a launch can produce, by version; optional entries so no version fails to load. */
function trackedTypes(version) {
  const types = ['arrow', 'spectral_arrow', 'trident', 'snowball', 'egg', 'ender_pearl', 'fireball', 'small_fireball',
    'llama_spit', 'firework_rocket', 'item', 'experience_orb'];
  if (atLeast(version, '1.21.5')) types.push('splash_potion', 'lingering_potion');
  else types.push('potion');
  if (atLeast(version, '1.21')) types.push('wind_charge');
  // Travellers the facility summons and tags itself (so only their arrival is caught here).
  types.push('minecart', 'hopper_minecart', 'horse', 'camel', 'pig', 'donkey', 'llama', 'strider', 'wolf', 'cat', 'parrot',
    'armor_stand', 'zombie', atLeast(version, '1.21.2') ? 'oak_boat' : 'boat');
  return types.map((t) => ({ id: `minecraft:${t}`, required: false }));
}

function functions() {
  return {
    tick: [
      '# wx:tick: tags launches and catches arrivals; see scripts/facility/lib/observe.js',
      'execute as @e[type=minecraft:marker,tag=wx_far] at @s as @e[type=#wx:tracked,tag=!wx_arrived,distance=..5] unless entity @s[tag=wx_seen,tag=!wx_launched] run function wx:observe/arrive',
      // Not the item `give` drops for its pickup animation (Age 5999, PickupDelay 32767): Probe
      // is given its bow and arrows at the launch marker.
      'execute as @e[type=minecraft:marker,tag=wx_launch] at @s as @e[type=#wx:tracked,tag=!wx_seen,distance=..6] unless entity @s[type=minecraft:item,nbt={Age:5999s,PickupDelay:32767s}] run function wx:observe/launch',
      'tag @e[type=#wx:tracked,tag=!wx_seen] add wx_seen',
      `execute if score #freeze ${OBJECTIVE} matches 1 as @e[tag=wx_hold] run data merge entity @s {NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}`,
      'execute as @e[tag=wx_track,limit=1] run function wx:observe/track',
      '',
    ].join('\n'),
    // Per-tick tracking of one entity tagged wx_track (a cart run at an iris): the least and
    // greatest x and z of its position, and its latest position and motion, all x10000.
    'observe/track': [
      `execute store result score #tx ${OBJECTIVE} run data get entity @s Pos[0] 10000`,
      `execute store result score #tz ${OBJECTIVE} run data get entity @s Pos[2] 10000`,
      `execute store result score #ty ${OBJECTIVE} run data get entity @s Pos[1] 10000`,
      `scoreboard players operation #tymin ${OBJECTIVE} < #ty ${OBJECTIVE}`,
      `execute store result score #tvx ${OBJECTIVE} run data get entity @s Motion[0] 10000`,
      `execute store result score #tvz ${OBJECTIVE} run data get entity @s Motion[2] 10000`,
      `scoreboard players operation #txmin ${OBJECTIVE} < #tx ${OBJECTIVE}`,
      `scoreboard players operation #txmax ${OBJECTIVE} > #tx ${OBJECTIVE}`,
      `scoreboard players operation #tzmin ${OBJECTIVE} < #tz ${OBJECTIVE}`,
      `scoreboard players operation #tzmax ${OBJECTIVE} > #tz ${OBJECTIVE}`,
      `scoreboard players add #tticks ${OBJECTIVE} 1`,
      '',
    ].join('\n'),
    // A launch: counted, so a check can say something was launched at all (a shot a shut iris
    // destroys leaves nothing else behind), and the first one's data kept, to say what it was.
    'observe/launch': [
      'tag @s add wx_launched',
      `scoreboard players add #launched ${OBJECTIVE} 1`,
      `execute if score #launched ${OBJECTIVE} matches 1 run data modify storage wx:obs first set from entity @s`,
      '',
    ].join('\n'),
    'observe/arrive': [
      'tag @s add wx_arrived',
      'tag @s add wx_seen',
      `scoreboard players add #arrived ${OBJECTIVE} 1`,
      // Taken by the plugin before the tick function could tag it as launched: still one launched.
      `execute unless entity @s[tag=wx_launched] run scoreboard players add #fresh ${OBJECTIVE} 1`,
      `execute if entity @s[tag=wx_launched] run scoreboard players set #same ${OBJECTIVE} 1`,
      `execute store result score #x ${OBJECTIVE} run data get entity @s Pos[0] 100`,
      `execute store result score #y ${OBJECTIVE} run data get entity @s Pos[1] 100`,
      `execute store result score #z ${OBJECTIVE} run data get entity @s Pos[2] 100`,
      `execute store result score #vx ${OBJECTIVE} run data get entity @s Motion[0] 1000`,
      `execute store result score #vy ${OBJECTIVE} run data get entity @s Motion[1] 1000`,
      `execute store result score #vz ${OBJECTIVE} run data get entity @s Motion[2] 1000`,
      `execute if score #freeze ${OBJECTIVE} matches 1 run tag @s add wx_hold`,
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

const HOLDERS = ['#arrived', '#same', '#launched', '#fresh', '#x', '#y', '#z', '#vx', '#vy', '#vz'];

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
    await this.srv.run('data remove storage wx:obs first');
    await this.srv.run('kill @e[type=minecraft:marker,tag=wx_watch]');
    await this.srv.run('kill @e[tag=wx_launched]');
    await this.srv.run('kill @e[tag=wx_arrived]');
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

  /** The first arrival: { count, same, at: {x,y,z}, motion: {x,y,z} } in blocks and blocks/tick. */
  async arrival() {
    const count = (await this.score('#arrived')) || 0;
    const same = (await this.score('#same')) === 1;
    const at = { x: (await this.score('#x')) / 100, y: (await this.score('#y')) / 100, z: (await this.score('#z')) / 100 };
    const motion = { x: (await this.score('#vx')) / 1000, y: (await this.score('#vy')) / 1000, z: (await this.score('#vz')) / 1000 };
    return { count, same, at, motion };
  }

  /**
   * What was launched since the watch was armed: how many things the tick function tagged, how
   * many arrived untagged (the plugin took them first), and the first one's data as SNBT.
   */
  async launches() {
    const tagged = (await this.score('#launched')) || 0;
    const fresh = (await this.score('#fresh')) || 0;
    const r = await this.srv.run('data get storage wx:obs first');
    return { tagged, fresh, first: r.lines.join(' ') };
  }

  /** The item stacks launched and never arrived, still in the world: [count, ...], one per entity. */
  async leftBehindCounts() {
    const r = await this.srv.run('execute as @e[type=minecraft:item,tag=wx_launched,tag=!wx_arrived] run data get entity @s Item');
    return r.lines.map((l) => /[Cc]ount: (\d+)/.exec(l)).filter(Boolean).map((m) => Number(m[1]));
  }

  /** Waits (bounded) until at least `n` things have arrived. */
  async waitArrival(ms = 8000, n = 1) {
    const deadline = Date.now() + ms;
    for (;;) {
      const got = await this.score('#arrived');
      if (got >= n) return got;
      if (Date.now() > deadline) return got || 0;
      await new Promise((resolve) => { setTimeout(resolve, 100); });
    }
  }

  /** Starts per-tick tracking of one entity (by selector); clears the last track. */
  async track(selector) {
    await this.srv.run('tag @e[tag=wx_track] remove wx_track');
    for (const [h, v] of [['#txmin', 2147483647], ['#tzmin', 2147483647], ['#tymin', 2147483647], ['#txmax', -2147483648], ['#tzmax', -2147483648], ['#tticks', 0]]) {
      await this.srv.run(`scoreboard players set ${h} ${OBJECTIVE} ${v}`);
    }
    const r = await this.srv.run(`tag ${selector} add wx_track`);
    if (!r.lines.some((l) => /Added tag/.test(l))) throw new Error(`nothing to track: ${selector} (${r.lines.join(' ')})`);
  }

  /** The track so far, in blocks and blocks per tick. */
  async trackResult() {
    const n = async (h) => (await this.score(h)) / 10000;
    return {
      ticks: await this.score('#tticks'),
      x: await n('#tx'), y: await n('#ty'), ymin: await n('#tymin'), z: await n('#tz'), vx: await n('#tvx'), vz: await n('#tvz'),
      xmin: await n('#txmin'), xmax: await n('#txmax'), zmin: await n('#tzmin'), zmax: await n('#tzmax'),
    };
  }

  async untrack() {
    await this.srv.run('tag @e[tag=wx_track] remove wx_track');
  }

  /** How many launched things never arrived (left on the near side). */
  async leftBehind() {
    const r = await this.srv.run('execute if entity @e[tag=wx_launched,tag=!wx_arrived]');
    // "Test passed, count: 4" (1.20.4) or "Test passed. Count: 4", and a bare "Test passed" for one.
    const m = r.lines.map((l) => /count: (\d+)/i.exec(l)).find(Boolean);
    if (m) return Number(m[1]);
    return r.lines.some((l) => /Test passed/.test(l)) ? 1 : 0;
  }
}

module.exports = { OBJECTIVE, trackedTypes, functions, tags, Watch };
