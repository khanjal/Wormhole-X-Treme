'use strict';
// Gate settings, one at a time on Sys (G1's Stand position): each changed, and its effect seen,
// against the same thing done without it where the change could otherwise read as nothing.

const { Vec3 } = require('vec3');
const { GateKit } = require('../../lib/gatekit');
const iris = require('../../lib/iris');
const campus = require('../../lib/campus');
const { ticks } = require('../../lib/probe');
const text = require('../../lib/text');
const {
  O, GATE, GEOM, STAND, v, c, ear, toldSince, until, atButton, before, relayArrival, logMark, logSince,
  buildGate, builtChecks, walkThrough, watchLights,
} = require('./common');

const PREVIEW_AT = { x: -9.5, y: 0, z: -108.5, yaw: 180 };
const RANGE = campus.GATES.far.Range;
const IRIS_BLOCK = 'stone';

const cases = [
  v('timeout-activate', '`timeout-activate 3`: a gate lit at its DHD and never dialled goes dark after three seconds, and says so'),
  v('use cooldown', '`use-cooldown-enabled true`, 30 s: one trip, then the gate refuses Probe (an op too) until it is over'),
  {
    ...v('same world only', '`same-world-only true`: a dial to the Range still connects; walking in is refused; riding a cart in should be too'),
    expect: 'FAIL:riding a cart in, Probe stayed in the overworld too',
    known: 'same-world-only stops a player walking into a gate to another world but not one riding into it: a cart carries Probe to the Range, since only the walking path asks (WormholeXTremePlayerListener.refusedForCrossWorld; WormholeXTremeVehicleListener does not), though the setting says players may only teleport through gates whose destination is in the same world',
  },
  {
    ...v('cart to another world', 'the control: with same-world-only at its default, the same cart carries Probe to the Range'),
  },
  v('preview limits', '`gate-preview-max-blocks`: 10 refuses a preview as too many blocks, 0 says previews are off; the default stands one'),
  v('preview minutes', '`gate-preview-minutes 1`: a preview is gone a minute later, with nobody clearing it'),
  v('iris animation', '`gate-iris-animation instant`: a gate with none of its own shuts its iris in one step, where the default sweeps'),
  v('dial spin', '`gate-dial-spin none`: the chevrons lock about twice as fast as with the default top rest'),
  v('arrival splash', '`gate-arrival-splash-ticks 0`: no water over the eyes on arrival, where the default shows it'),
  v('log level', '`log-level FINE`: a DHD press is logged as [FINE], and not at INFO'),
  v('sign colours', 'sign-color-gate-name RED, -network GOLD, -owner BLUE and sign-glowing-text true: the name sign a gate is built with is written so'),
  {
    ...v('sign colour bad', '`sign-color-gate-name PINK`: like every other setting with a fixed set of values, refused, naming them'),
    expect: 'FAIL:refused, and the setting unchanged',
    known: 'the sign colours are not checked: `wormhole config sign-color-gate-name PINK` answers "SIGN_COLOR_GATE_NAME is now PINK." and the sign quietly falls back to its default colour (SignStyle.resolveColor), though every other setting with a fixed set of values refuses a bad one and names the options, as the guide says (docs/guide/SERVER.md); ParsedSetting.readText accepts any text for them',
  },
];

function needs(o) {
  return {
    'sign colours': { 'sign-color-gate-name': 'RED', 'sign-color-network': 'GOLD', 'sign-color-owner': 'BLUE', 'sign-glowing-text': 'true' },
    'timeout-activate': { 'timeout-activate': '3' },
    'use cooldown': { 'use-cooldown-enabled': 'true', 'use-cooldown-seconds': '30' },
    'same world only': { 'same-world-only': 'true' },
    'preview minutes': { 'gate-preview-minutes': '1' },
  }[o.case] || {};
}

async function stage(ctx, o) {
  const obs = ctx.observed;
  if (o.case === 'preview limits' || o.case === 'preview minutes') return;
  Object.assign(obs, await buildGate(ctx, { idc: o.case === 'iris animation' ? '3333' : null, net: o.case === 'sign colours' ? 'SysNet' : null }));
  if (o.case === 'same world only' || o.case === 'cart to another world') {
    // A rail line into the opening, as G1 lays one for a cart.
    const start = before(GEOM, 8);
    await ctx.server.run(`fill ${Math.floor(start.x)} 0 ${Math.floor(GEOM.opening[0].z) + 1} ${Math.floor(start.x)} 0 ${Math.floor(start.z)} minecraft:rail[shape=north_south]`);
  }
}

/**
 * Probe rides a cart down the rails into Sys's opening: { reached, rodeTo, cartTold }. Reached is
 * the cart at the opening's plane or through it, or the plugin's word that it was turned back.
 */
async function rideCart(ctx) {
  const probe = ctx.facility.probe;
  const at = before(GEOM, 6);
  const plane = GEOM.opening[0].z + 0.5;
  await ctx.menagerie.summon('minecart', { ...at, y: 0.1 }, ctx.tag);
  await probe.teleport({ x: at.x + GEOM.right.x * 1.5, y: 0, z: at.z + GEOM.right.z * 1.5, yaw: 90 }, O);
  await probe.mount(ctx.tag, 5000, 3);
  const t0 = Date.now();
  let reached = false;
  await ctx.server.run(`data merge entity @e[tag=${ctx.tag},tag=wx_kind_minecart,limit=1] {Motion:[0.0d,0.0d,${(-GEOM.normal.z * 0.6).toFixed(1)}d]}`);
  await until(async () => {
    if (probe.dimension !== O || Math.abs(probe.position.z - plane) <= 1) reached = true;
    return probe.dimension !== O;
  }, 8000, 1);
  const cartTold = toldSince(probe, t0);
  if (/Cross-world travel is disabled/.test(cartTold)) reached = true;
  const rodeTo = probe.dimension;
  // A passenger is not teleported across worlds: the cart goes first.
  await ctx.server.run(`kill @e[tag=${ctx.tag}]`);
  await ticks(10);
  return { reached, rodeTo, cartTold };
}

/** Dials Sys to `to` by console with Probe watching from in front; returns { dial, drawn }. */
async function open(ctx, to = 'Relay') {
  const probe = ctx.facility.probe;
  const kit = new GateKit(ctx.server);
  await probe.teleport(before(GEOM, 8), O);
  await ticks(20);
  const dial = (await kit.dial(GATE, to)).text;
  const drawn = await kit.waitDrawn(probe, GEOM, 15000);
  return { dial, drawn };
}

async function shut(ctx, far = 'Relay') {
  const kit = new GateKit(ctx.server);
  await kit.force(GATE);
  await kit.force(far);
}

async function run(ctx, o) {
  const obs = ctx.observed;
  const fac = ctx.facility;
  const probe = fac.probe;
  const kit = new GateKit(ctx.server);
  ear(probe);
  if (o.case === 'timeout-activate') {
    await probe.teleport(atButton(), O);
    await ticks(20);
    const lights = watchLights(probe);
    const t0 = Date.now();
    await probe.press(GEOM.button);
    await ticks(30);
    obs.litAt1s = lights.litNow();
    await until(async () => /timed out and deactivated/.test(toldSince(probe, t0)), 8000);
    obs.told = toldSince(probe, t0);
    obs.tookMs = Date.now() - t0;
    await ticks(10);
    obs.litAfter = lights.litNow();
    lights.stop();
  } else if (o.case === 'use cooldown') {
    Object.assign(obs, await open(ctx));
    obs.first = await walkThrough(probe);
    await shut(ctx);
    Object.assign(obs, { again: await open(ctx) });
    const t0 = Date.now();
    obs.second = await walkThrough(probe);
    obs.told = toldSince(probe, t0);
    await shut(ctx);
  } else if (o.case === 'same world only') {
    Object.assign(obs, await open(ctx, 'Range'));
    const t0 = Date.now();
    const rangeArrival = { x: RANGE.cx + 0.5, y: RANGE.floorY, z: RANGE.openingAt + 2 };
    obs.walked = await walkThrough(probe, GEOM, rangeArrival);
    obs.walkedTo = probe.dimension;
    obs.told = toldSince(probe, t0);
    // Then a cart, Probe in it, rolled into the same opening.
    if (probe.dimension === O) Object.assign(obs, await rideCart(ctx));
    await probe.teleport(campus.TRANSIT.home, O);
    await shut(ctx, 'Range');
  } else if (o.case === 'cart to another world') {
    Object.assign(obs, await open(ctx, 'Range'));
    Object.assign(obs, await rideCart(ctx));
    await probe.teleport(campus.TRANSIT.home, O);
    await shut(ctx, 'Range');
  } else if (o.case === 'preview limits') {
    await probe.teleport(PREVIEW_AT, O);
    await ticks(10);
    const tryOne = async () => {
      const t0 = Date.now();
      probe.bot.chat('/wormhole gate build Standard');
      await until(async () => /Previewing|Too many preview blocks|Previews are off/.test(toldSince(probe, t0)), 3000);
      const said = toldSince(probe, t0);
      probe.bot.chat('/wormhole gate preview clear -all');
      await ticks(10);
      return said;
    };
    obs.byDefault = await tryOne();
    await ctx.config.set('gate-preview-max-blocks', '10', 's1');
    obs.atTen = await tryOne();
    await ctx.config.set('gate-preview-max-blocks', '0', 's1');
    obs.atNone = await tryOne();
  } else if (o.case === 'preview minutes') {
    await probe.teleport(PREVIEW_AT, O);
    await ticks(10);
    const t0 = Date.now();
    probe.bot.chat('/wormhole gate build Standard');
    await until(async () => /Previewing/.test(toldSince(probe, t0)), 3000);
    obs.said = toldSince(probe, t0);
    const shown = async () => (await ctx.server.run(`execute positioned ${PREVIEW_AT.x} 1 ${PREVIEW_AT.z} if entity @e[type=minecraft:block_display,distance=..12]`)).lines.some((l) => /Test passed/.test(l));
    await ticks(100);
    obs.at5s = await shown();
    // Expired at 60 s, taken down at the next sweep (every 100 ticks).
    obs.gone = await until(async () => !(await shown()), 80000, 20);
    obs.goneAfterMs = Date.now() - t0;
  } else if (o.case === 'iris animation') {
    const cells = GEOM.opening;
    const shutOnce = async () => {
      const rec = iris.recordCells(probe.bot, cells);
      const lever = await kit.toggleIris(probe, GEOM);
      await ticks(40);
      const steps = iris.stepsOf(rec.stop(), { to: IRIS_BLOCK });
      await kit.toggleIris(probe, GEOM);
      await ticks(40);
      return { lever, steps: steps.length };
    };
    obs.sweep = await shutOnce();
    await ctx.config.set('gate-iris-animation', 'instant', 's1');
    obs.instant = await shutOnce();
  } else if (o.case === 'dial spin') {
    const once = async () => {
      await probe.teleport(before(GEOM, 8), O);
      await ticks(20);
      const lights = watchLights(probe);
      const dial = (await kit.dial(GATE, 'Relay')).text;
      await kit.waitDrawn(probe, GEOM, 20000);
      const waves = lights.stop().filter((w) => w.order <= 7 && w.at !== null);
      await shut(ctx);
      await ticks(40);
      const span = waves.length > 1 ? (waves[waves.length - 1].at - waves[0].at) / (waves.length - 1) : null;
      return { dial, waves: waves.length, span };
    };
    obs.top = await once();
    await ctx.config.set('gate-dial-spin', 'none', 's1');
    obs.none = await once();
  } else if (o.case === 'arrival splash') {
    const once = async () => {
      await open(ctx);
      // Drawn at the eye block of whoever arrives: one up from the feet, two out from the opening.
      const a = relayArrival();
      const seen = { water: 0 };
      const on = (_old, b) => {
        if (b && b.name === 'water' && Math.abs(b.position.x + 0.5 - a.x) <= 1 && Math.abs(b.position.z + 0.5 - a.z) <= 1 && b.position.y === a.y + 1) seen.water++;
      };
      probe.bot.on('blockUpdate', on);
      const arrived = await walkThrough(probe);
      await ticks(30);
      probe.bot.off('blockUpdate', on);
      await shut(ctx);
      return { arrived, water: seen.water };
    };
    obs.byDefault = await once();
    await ctx.config.set('gate-arrival-splash-ticks', '0', 's1');
    obs.off = await once();
  } else if (o.case === 'sign colours') {
    obs.sign = await nameSign(ctx);
  } else if (o.case === 'sign colour bad') {
    obs.before = await ctx.config.get('sign-color-gate-name');
    // Through Config, so an accepted PINK is put back after the run like any setting a case changes.
    obs.said = await ctx.config.set('sign-color-gate-name', 'PINK', 's1').then((x) => `is now ${x}`, (e) => e.message);
    obs.after = await ctx.config.get('sign-color-gate-name');
    // The name sign written again (an owner change rewrites it), under PINK.
    obs.rewritten = (await kit.edit(GATE, 'owner', probe.name)).text;
    obs.sign = await nameSign(ctx);
  } else if (o.case === 'log level') {
    const press = async () => {
      await probe.teleport(atButton(), O);
      await ticks(10);
      const mark = logMark(ctx);
      await probe.press(GEOM.button);
      await ticks(20);
      await kit.force(GATE);
      return logSince(ctx, mark).filter((l) => /\[WormholeXTreme\] \[FINE\] PlayerInteract: Probe clicked potential activator/.test(l)).length;
    };
    obs.atInfo = await press();
    await ctx.config.set('log-level', 'FINE', 's1');
    obs.atFine = await press();
  }
}

/**
 * A sign's front text as `data get block ... front_text` prints it, line by line, each line its
 * text parts with the colour each is drawn in: [[{ text, color }]]. A line is a JSON string
 * before 1.21.5 and a compound from it; a part takes its parent's colour unless it has its own.
 */
function signLines(raw) {
  const at = (raw || '').indexOf('{');
  if (at < 0) return [];
  let nbt;
  try { nbt = text.parseSnbt(raw.slice(at)); } catch { return []; }
  const parts = (comp, color, out) => {
    if (typeof comp === 'string') { out.push({ text: comp, color }); return out; }
    if (!comp || typeof comp !== 'object') return out;
    const own = comp.color || color;
    if (comp.text !== undefined && comp.text !== '') out.push({ text: String(comp.text), color: own });
    for (const x of comp.extra || []) parts(x, own, out);
    return out;
  };
  return (nbt.messages || []).map((m) => {
    let comp = m;
    if (typeof m === 'string') { try { comp = JSON.parse(m); } catch { comp = m; } }
    return parts(comp, null, []);
  });
}

/** The front text of Sys's name sign (the :N block's face), as `data get block` gives it. */
async function nameSign(ctx) {
  const n = GEOM.blocks.find((b) => b.marks.includes('N'));
  const at = { x: n.x + GEOM.normal.x, y: n.y, z: n.z + GEOM.normal.z };
  return (await ctx.server.run(`data get block ${at.x} ${at.y} ${at.z} front_text`)).lines.join(' ');
}

function checks(obs, o) {
  const list = (o.case === 'preview limits' || o.case === 'preview minutes') ? [] : [...builtChecks(obs)];
  // The sign's front text as the server holds it: a component per line, its colour beside its text.
  const coloured = (text, colour) => signLines(obs.sign).some((parts) => parts.some((p) => p.text === text && p.color === colour));
  if (o.case === 'timeout-activate') {
    list.push(c('the DHD lit the gate: "Gate successfully activated."', () => /Gate successfully activated\./.test(obs.told || '')),
      c('its chevrons were lit a second later', () => obs.litAt1s > 0),
      c(`"Gate: ${GATE} timed out and deactivated." about three seconds after the press`, () => {
        if (new RegExp(`Gate: ${GATE} timed out and deactivated\\.`).test(obs.told || '') && obs.tookMs >= 2500 && obs.tookMs <= 6000) return true;
        throw new Error(`after ${obs.tookMs} ms: ${obs.told}`);
      }),
      c('and its chevrons went dark', () => obs.litAfter === 0));
  } else if (o.case === 'use cooldown') {
    list.push(c(`${GATE} dialled Relay and opened`, () => /Stargates connected/.test(obs.dial || '') && obs.drawn === true),
      c('Probe walked through to Relay', () => obs.first === true),
      c('the gate was opened again', () => obs.again && /Stargates connected/.test(obs.again.dial || '') && obs.again.drawn === true),
      c('and refused Probe: "You must wait longer before using a stargate."', () => /You must wait longer before using a stargate\./.test(obs.told || '')),
      c('with "Current Wait (in seconds): N", N 30 or under', () => {
        const m = /Current Wait \(in seconds\): (\d+)/.exec(obs.told || '');
        return m && Number(m[1]) > 0 && Number(m[1]) <= 30;
      }),
      c('and did not carry it', () => obs.second === false));
  } else if (o.case === 'same world only') {
    list.push(c(`${GATE} dialled the Range (a dial is not refused) and opened`, () => /Stargates connected/.test(obs.dial || '') && obs.drawn === true),
      c('walking in: "Cross-world travel is disabled on this server."', () => /Cross-world travel is disabled on this server\./.test(obs.told || '')),
      c('and Probe stayed in the overworld', () => obs.walked === false && obs.walkedTo === O),
      c('a cart, Probe in it, rolled to the opening', () => obs.reached === true),
      c('riding a cart in, Probe stayed in the overworld too', () => {
        if (obs.rodeTo === O) return true;
        throw new Error(`the cart took Probe to ${obs.rodeTo}`);
      }));
  } else if (o.case === 'cart to another world') {
    list.push(c(`${GATE} dialled the Range and opened`, () => /Stargates connected/.test(obs.dial || '') && obs.drawn === true),
      c('a cart, Probe in it, rolled to the opening', () => obs.reached === true),
      c('and carried Probe to the Range', () => obs.rodeTo === 'minecraft:the_nether'));
  } else if (o.case === 'preview limits') {
    list.push(c('by default Probe stands a preview: "Previewing Standard ..."', () => /Previewing Standard/.test(obs.byDefault || '')),
      c('at 10: "Too many preview blocks on the server. Clear one with /wormhole gate preview clear."', () => /Too many preview blocks on the server\. Clear one with \/wormhole gate preview clear\./.test(obs.atTen || '') && !/Previewing/.test(obs.atTen || '')),
      c('at 0: "Previews are off on this server."', () => /Previews are off on this server\./.test(obs.atNone || '') && !/Previewing/.test(obs.atNone || '')));
  } else if (o.case === 'preview minutes') {
    list.push(c('Probe stood a preview', () => /Previewing Standard/.test(obs.said || '')),
      c('it was still there after five seconds', () => obs.at5s === true),
      c('and gone a minute on (60 to 70 s), unasked', () => {
        if (obs.gone && obs.goneAfterMs >= 55000 && obs.goneAfterMs <= 75000) return true;
        throw new Error(`gone ${obs.gone} after ${obs.goneAfterMs} ms`);
      }));
  } else if (o.case === 'iris animation') {
    list.push(c('the default: the iris shut at its lever in several steps', () => obs.sweep && obs.sweep.lever === true && obs.sweep.steps > 1),
      c('instant: in one', () => {
        if (obs.instant && obs.instant.lever === true && obs.instant.steps === 1) return true;
        throw new Error(`${obs.instant && obs.instant.steps} steps`);
      }));
  } else if (o.case === 'dial spin') {
    list.push(c('both dials connected, each with its seven chevrons seen', () => [obs.top, obs.none].every((x) => x && /Stargates connected/.test(x.dial) && x.waves === 7)),
      c('with none, a chevron in 0.35 to 0.65 of the default\'s time (its rest on the top chevron gone)', () => {
        if (obs.top.span && obs.none.span && obs.none.span >= obs.top.span * 0.35 && obs.none.span <= obs.top.span * 0.65) return true;
        throw new Error(`${obs.top && Math.round(obs.top.span)} ms a chevron by default, ${obs.none && Math.round(obs.none.span)} with none`);
      }));
  } else if (o.case === 'arrival splash') {
    list.push(c('by default Probe arrived at Relay with water drawn over its eyes', () => obs.byDefault && obs.byDefault.arrived && obs.byDefault.water > 0),
      c('at 0 it arrived with none', () => obs.off && obs.off.arrived && obs.off.water === 0));
  } else if (o.case === 'sign colours') {
    const line = (what, text, colour) => c(`${what} in ${colour}: "${text}"`, () => {
      if (coloured(text, colour)) return true;
      throw new Error(obs.sign);
    });
    list.push(line('the name', `-${GATE}-`, 'red'), line('the network', 'N:SysNet', 'gold'), line('the owner', 'O:Probe', 'blue'),
      c('and its text glows', () => {
        const at = (obs.sign || '').indexOf('{');
        try { return at >= 0 && Number(text.parseSnbt(obs.sign.slice(at)).has_glowing_text) === 1; } catch { return false; }
      }));
  } else if (o.case === 'sign colour bad') {
    list.push(c(`the name sign written again shows the name in the default colour, dark_aqua`, () => {
      if (/Now owned by: Probe/.test(obs.rewritten || '') && coloured(`-${GATE}-`, 'dark_aqua')) return true;
      throw new Error(obs.sign);
    }));
    list.push(c('refused, and the setting unchanged', () => {
      if (!/is now PINK/.test(obs.said || '') && obs.after === obs.before) return true;
      throw new Error(`told: ${obs.said}; ${obs.before} -> ${obs.after}`);
    }));
  } else if (o.case === 'log level') {
    list.push(c('at INFO a DHD press logs no [FINE] line', () => obs.atInfo === 0),
      c('at FINE it logs "[WormholeXTreme] [FINE] PlayerInteract: Probe clicked potential activator ..."', () => obs.atFine > 0));
  }
  return list;
}

module.exports = { cases, needs, stage, run, checks, PREVIEW_AT };
