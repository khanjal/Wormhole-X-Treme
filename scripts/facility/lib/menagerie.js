'use strict';
// The supply of travellers: animals, pets, vehicles, mobs and the armoury's items, by version.
// Everything summoned carries tags (wx_run_<chamber> for a run, wx_stock_<kind> while it waits
// in its pen), so it is found and killed by tag, never by entity id.
//
// Version switches, each checked by the stage 2 self-test on 1.20.4, 1.21.11 and 26.1.2:
//  - boats split by wood at 1.21.2 (boat -> oak_boat, chest_boat -> oak_chest_boat);
//  - saddles moved into the equipment slot at 1.21.5 (SaddleItem / Saddle:1b before);
//  - item stacks: NBT tags before 1.20.5, components after, and the enchantments component
//    lost its `levels` wrapper at 1.21.5; a custom name is a JSON string until 1.21.5;
//  - the wind charge exists from 1.21 only.

const crypto = require('crypto');
const campus = require('./campus');
const { atLeast } = require('./version');

// ---- ids and uuids ----------------------------------------------------------------------------

function entityId(version, kind) {
  const split = atLeast(version, '1.21.2');
  const ids = {
    boat: split ? 'oak_boat' : 'boat',
    chest_boat: split ? 'oak_chest_boat' : 'chest_boat',
  };
  return `minecraft:${ids[kind] || kind}`;
}

/** Whether a kind of thing exists on this version at all. */
function exists(version, kind) {
  if (kind === 'wind_charge') return atLeast(version, '1.21');
  return true;
}

/** The UUID an offline-mode server gives a player name, as Minecraft's four-int array. */
function offlineUuidInts(name) {
  const md5 = crypto.createHash('md5').update(`OfflinePlayer:${name}`).digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  return [0, 4, 8, 12].map((i) => md5.readInt32BE(i));
}

function uuidNbt(name) {
  return `[I;${offlineUuidInts(name).join(',')}]`;
}

// ---- items ----------------------------------------------------------------------------------

/**
 * An item argument for `give`, `item replace` and summons, by version. spec:
 *   { id, count, name, enchant: { loyalty: 3 }, potion: 'slowness', fireworks: { flight: 1 } }
 */
function itemArg(version, spec) {
  const id = spec.id.includes(':') ? spec.id : `minecraft:${spec.id}`;
  if (!atLeast(version, '1.20.5')) {
    const tags = [];
    if (spec.name) tags.push(`display:{Name:'${JSON.stringify({ text: spec.name })}'}`);
    if (spec.enchant) tags.push(`Enchantments:[${Object.entries(spec.enchant).map(([e, l]) => `{id:"minecraft:${e}",lvl:${l}s}`).join(',')}]`);
    if (spec.potion) tags.push(`Potion:"minecraft:${spec.potion}"`);
    if (spec.fireworks) tags.push(`Fireworks:{Flight:${spec.fireworks.flight}b}`);
    return `${id}${tags.length ? `{${tags.join(',')}}` : ''}`;
  }
  const modern = atLeast(version, '1.21.5');
  const comps = [];
  if (spec.name) comps.push(modern ? `custom_name={text:${JSON.stringify(spec.name)}}` : `custom_name='${JSON.stringify({ text: spec.name })}'`);
  if (spec.enchant) {
    const levels = `{${Object.entries(spec.enchant).map(([e, l]) => `"minecraft:${e}":${l}`).join(',')}}`;
    comps.push(modern ? `enchantments=${levels}` : `enchantments={levels:${levels}}`);
  }
  if (spec.potion) comps.push(`potion_contents={potion:"minecraft:${spec.potion}"}`);
  if (spec.fireworks) comps.push(`fireworks={flight_duration:${spec.fireworks.flight}}`);
  return `${id}${comps.length ? `[${comps.join(',')}]` : ''}`;
}

/** An item stack as NBT (for an item entity's Item, a dispenser's Items, a saddle slot). */
function itemNbt(version, spec, extra = '') {
  const id = spec.id.includes(':') ? spec.id : `minecraft:${spec.id}`;
  const count = spec.count || 1;
  if (!atLeast(version, '1.20.5')) {
    const tag = itemArg(version, { ...spec, id }).slice(id.length);
    return `{id:"${id}",Count:${count}b${tag ? `,tag:${tag}` : ''}${extra}}`;
  }
  const comps = [];
  const modern = atLeast(version, '1.21.5');
  if (spec.name) comps.push(modern ? `"minecraft:custom_name":{text:${JSON.stringify(spec.name)}}` : `"minecraft:custom_name":'${JSON.stringify({ text: spec.name })}'`);
  if (spec.enchant) {
    const levels = `{${Object.entries(spec.enchant).map(([e, l]) => `"minecraft:${e}":${l}`).join(',')}}`;
    comps.push(`"minecraft:enchantments":${modern ? levels : `{levels:${levels}}`}`);
  }
  if (spec.potion) comps.push(`"minecraft:potion_contents":{potion:"minecraft:${spec.potion}"}`);
  if (spec.fireworks) comps.push(`"minecraft:fireworks":{flight_duration:${spec.fireworks.flight}}`);
  return `{id:"${id}",count:${count}${comps.length ? `,components:{${comps.join(',')}}` : ''}${extra}}`;
}

/** The armoury: what each launcher needs in hand, by name. */
const ARMOURY = {
  bow: { id: 'bow' },
  crossbow: { id: 'crossbow' },
  multishot: { id: 'crossbow', enchant: { multishot: 1 } },
  piercing: { id: 'crossbow', enchant: { piercing: 4 } },
  arrow: { id: 'arrow', count: 16 },
  spectral_arrow: { id: 'spectral_arrow', count: 16 },
  tipped_arrow: { id: 'tipped_arrow', count: 16, potion: 'slowness' },
  trident: { id: 'trident', enchant: { loyalty: 3 } },
  snowball: { id: 'snowball', count: 16 },
  egg: { id: 'egg', count: 16 },
  ender_pearl: { id: 'ender_pearl', count: 16 },
  splash_potion: { id: 'splash_potion', potion: 'slowness' },
  lingering_potion: { id: 'lingering_potion', potion: 'slowness' },
  firework_rocket: { id: 'firework_rocket', count: 16, fireworks: { flight: 3 } },
  fire_charge: { id: 'fire_charge', count: 16 },
  wind_charge: { id: 'wind_charge', count: 16 },
  relic: { id: 'diamond_sword', name: 'Relic of the Stand', enchant: { sharpness: 3 } },
};

// ---- animals ------------------------------------------------------------------------------

/** Kinds that can be saddled, and how, by version. */
function saddleNbt(version, kind) {
  if (atLeast(version, '1.21.5')) return 'equipment:{saddle:{id:"minecraft:saddle",count:1}}';
  if (kind === 'pig' || kind === 'strider') return 'Saddle:1b';
  return atLeast(version, '1.20.5') ? 'SaddleItem:{id:"minecraft:saddle",count:1}' : 'SaddleItem:{id:"minecraft:saddle",Count:1b}';
}

const MOUNTS = ['horse', 'camel', 'donkey', 'llama', 'pig', 'strider'];
const PETS = ['wolf', 'cat', 'parrot'];

/** The pen a stocked animal waits in (menagerie structures in lib/campus.js). */
function penOf(kind) {
  const M = campus.MENAGERIE;
  const pen = M.pens.find((p) => p.id === kind) || (PETS.includes(kind) ? M.pens.find((p) => p.id === 'kennel') : null);
  if (pen) {
    const b = pen.box;
    return { x: Math.floor((b.x0 + b.x1) / 2) + 0.5, y: 0, z: Math.floor((b.z0 + b.z1) / 2) + 0.5 };
  }
  if (kind === 'strider') return { x: M.lavaTrough.x0 + 3.5, y: 0, z: M.lavaTrough.z + 0.5 };
  return null;
}

/**
 * The NBT that says which block a hanging entity (an item frame) occupies. Without it Paper logs
 * "Block-attached entity at invalid position: null" as an ERROR (the summon still works). The
 * server classes name one key each: TileX/Y/Z on 1.20.4, `block_pos` on 1.21.11 and 26.1.2;
 * where between them it changed is not checked, so both go, each ignored where it is unknown.
 * Either is refused unless near the entity's position as its NBT is read (a summon places it
 * only after), so `Pos` goes with them.
 */
function hangingAt(x, y, z) {
  return `Pos:[${x + 0.5}d,${y + 0.5}d,${z + 0.5}d],TileX:${x},TileY:${y},TileZ:${z},block_pos:[I;${x},${y},${z}]`;
}

class Menagerie {
  constructor(srv, version) {
    this.srv = srv;
    this.version = version;
  }

  async must(command) {
    const r = await this.srv.run(command);
    if (r.errors.length) throw new Error(`${command}: ${r.errors.join(' ')}`);
    return r;
  }

  /**
   * Stocks one of each animal in its pen, NoAI, silent, tagged wx_stock_<kind>; and hangs the
   * armoury's wares on its inside wall in glow frames that cannot be turned or emptied, tagged
   * wx_decor so no chamber's `kill @e[tag=wx_run_*]` touches them (creative pass 3.6).
   */
  async stock() {
    const a = campus.MENAGERIE.armoury;
    await this.srv.run('kill @e[type=minecraft:glow_item_frame,tag=wx_decor]');
    for (const [i, id] of ['bow', 'crossbow', 'trident', 'spectral_arrow', 'firework_rocket', 'snowball'].entries()) {
      const at = [a.x1 - 1, 1, a.z0 + 2 + i];
      await this.must(`summon minecraft:glow_item_frame ${at.join(' ')} {${hangingAt(...at)},Facing:4b,Fixed:1b,Invulnerable:1b,Tags:["wx_decor"],Item:${itemNbt(this.version, { id })}}`);
    }
    for (const kind of [...MOUNTS, ...PETS]) {
      const at = penOf(kind);
      if (!at) continue;
      await this.srv.run(`kill @e[tag=wx_stock_${kind}]`);
      await this.must(`summon ${entityId(this.version, kind)} ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["wx_stock","wx_stock_${kind}"]}`);
    }
  }

  /**
   * Hands over an animal for a run: the stocked one if it is there (and a fresh one goes into
   * the pen), else a new one. Tagged `tag`, AI on, teleported to `at`, then saddled or tamed.
   * opts: { saddled, owner (player name), sitting }.
   */
  async animal(kind, at, tag, opts = {}) {
    const stock = `@e[tag=wx_stock_${kind},limit=1]`;
    const r = await this.srv.run(`tag ${stock} add ${tag}`);
    const fromPen = r.errors.length === 0 && r.lines.some((l) => /Added tag/.test(l));
    if (fromPen) {
      await this.must(`tag @e[tag=${tag},tag=wx_stock_${kind},limit=1] remove wx_stock`);
      await this.must(`tag @e[tag=${tag},tag=wx_stock_${kind},limit=1] add wx_kind_${kind}`);
      await this.must(`tag @e[tag=${tag},tag=wx_kind_${kind},limit=1] remove wx_stock_${kind}`);
    } else {
      await this.must(`summon ${entityId(this.version, kind)} ${at.x} ${at.y} ${at.z} {PersistenceRequired:1b,Tags:["${tag}","wx_kind_${kind}"]}`);
    }
    const sel = `@e[tag=${tag},tag=wx_kind_${kind},limit=1]`;
    const nbt = ['NoAI:0b', 'Silent:1b'];
    if (opts.saddled && kind !== 'llama') nbt.push(saddleNbt(this.version, kind));
    if (['horse', 'camel', 'donkey', 'llama'].includes(kind)) nbt.push('Tame:1b');
    if (opts.owner) nbt.push(`Owner:${uuidNbt(opts.owner)}`);
    if (opts.sitting !== undefined) nbt.push(`Sitting:${opts.sitting ? 1 : 0}b`);
    await this.must(`data merge entity ${sel} {${nbt.join(',')}}`);
    await this.must(`execute in ${campus.OVERWORLD} run tp ${sel} ${at.x} ${at.y} ${at.z} ${at.yaw || 0} 0`);
    if (fromPen) await this.restock(kind);
    return sel;
  }

  async restock(kind) {
    const at = penOf(kind);
    if (!at) return;
    await this.srv.run(`summon ${entityId(this.version, kind)} ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["wx_stock","wx_stock_${kind}"]}`);
  }

  /** Summons any other traveller (vehicle, mob, stand, orb, frame) with the run's tag. */
  async summon(kind, at, tag, nbt = '', tags = []) {
    const extra = nbt ? `,${nbt}` : '';
    const all = [tag, `wx_kind_${kind}`, ...tags].map((t) => `"${t}"`).join(',');
    await this.must(`summon ${entityId(this.version, kind)} ${at.x} ${at.y} ${at.z} {Tags:[${all}]${extra}}`);
    return `@e[tag=${tag},tag=wx_kind_${kind},limit=1]`;
  }

  /** Gives a player an armoury item by name (ARMOURY), `count` times its stack. */
  async give(player, name) {
    const spec = ARMOURY[name];
    if (!spec) throw new Error(`no armoury item ${name}`);
    await this.must(`give ${player} ${itemArg(this.version, spec)} ${spec.count || 1}`);
  }
}

module.exports = {
  Menagerie, entityId, exists, offlineUuidInts, uuidNbt, itemArg, itemNbt, hangingAt, ARMOURY, MOUNTS, PETS, saddleNbt, penOf,
};
