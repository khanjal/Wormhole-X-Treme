'use strict';
// The transit routes (lib/campus.js ROUTES): made once a session as fixtures, and walked by a
// non-op (Probe2, adventure mode) as a standing smoke test. Each route is the plugin feature it
// reaches: a gate dialled from a console button or the DHD, a public ring pair, a beam to a
// public destination. They are permanent: chamber resets leave them alone, and ring-trip
// cleanups spare the transit pair (Facility.keepRings).

const campus = require('./campus');
const text = require('./text');
const { GateKit } = require('./gatekit');
const { RingKit, SWAP_TICKS, CYCLE_TICKS } = require('./rings');
const trip = require('./ringtrip');
const { ticks } = require('./probe');

const R = campus.ROUTES;
const O = campus.OVERWORLD;
const SHUT_SECONDS = 38; // timeout-shutdown's default

function geometry(name) {
  const g = R.gates[name];
  return new GateKit(null).place(g.shape, g.facing, g);
}

/** Where a beam destination puts you: the pad's block centre, facing its yaw. */
function beamArrival(p) {
  return { x: p.x + 0.5, y: p.y, z: p.z + 0.5, yaw: p.yaw };
}

/** Minecraft's yaw (0 south, 90 west) from Mineflayer's (radians, 0 north, counter-clockwise). */
function mcYaw(probe) {
  const deg = (probe.bot.entity.yaw * 180) / Math.PI;
  return (((180 - deg) % 360) + 360) % 360;
}

function yawOff(a, b) {
  const d = Math.abs(a - b) % 360;
  return d > 180 ? 360 - d : d;
}

function flat(p, q) {
  return Math.hypot(p.x - q.x, p.z - q.z);
}

/** Is a gate's opening drawn (open) to this probe's client? */
function drawn(probe, geom) {
  const { Vec3 } = require('vec3');
  const mid = geom.opening[Math.floor(geom.opening.length / 2)];
  const b = probe.bot.blockAt(new Vec3(mid.x, mid.y, mid.z));
  return Boolean(b) && !['air', 'cave_air'].includes(b.name);
}

class Transit {
  constructor(fac) {
    this.fac = fac;
    this.status = {}; // route id -> { ok, detail, time }
    this.ringId = null;
    this.ringArrivals = null;
  }

  // ---- fixtures ------------------------------------------------------------------------------

  /** Builds the gates, the ring pair and the beam destinations; returns a line for the report. */
  async build() {
    const srv = this.fac.srv;
    const probe = this.fac.probe;
    const kit = new GateKit(srv);
    for (const name of Object.keys(R.gates)) {
      if (await kit.exists(name)) await kit.remove(name);
      await kit.build(name, geometry(name), { dim: R.gates[name].dim, floorY: R.gates[name].floorY });
    }

    // The ring pair: anything a kept world still holds is taken down first, then laid, paired,
    // and each end named standing in it (a name belongs to one end), stepping straight out so
    // the countdown that standing in starts is cancelled.
    const rk = new RingKit(srv, probe);
    for (const id of (await rk.list()).ids) await rk.remove(id);
    const [a, b] = R.rings.ends;
    for (const e of [a, b]) {
      await rk.clear(R.rings.pattern, e);
      await rk.lay(R.rings.pattern, e, R.rings.slab);
    }
    const pair = await rk.build(a, b);
    if (pair.error) throw new Error(`transit ring pair: ${pair.error}`);
    this.ringId = pair.id;
    this.ringArrivals = pair.arrivals;
    this.fac.keepRings = new Set([pair.id]);
    this.named = [];
    for (const [i, e] of [a, b].entries()) {
      await probe.teleport(pair.arrivals[i]);
      this.named.push(await rk.ask(`/wormhole ring edit name ${e.name}`, { until: /This ring is now|Stand in/ }));
      await probe.teleport(campus.TRANSIT.home);
    }
    if (!this.named.every((n, i) => n.includes(`This ring is now ${[a, b][i].name}.`))) {
      throw new Error(`naming the ring ends: ${this.named.join(' / ')}`);
    }

    // Beam destinations: `beam admin set` saves where the player stands, facing as they face.
    for (const p of R.beams) {
      await probe.teleport(beamArrival(p));
      const said = await rk.ask(`/wormhole beam admin set ${p.name}`, { until: /set to your current location|error/ });
      if (!/set to your current location/.test(said)) throw new Error(`beam destination ${p.name}: ${said}`);
    }
    await probe.teleport(campus.TRANSIT.home);
    return `gates ${Object.keys(R.gates).join(', ')}; ring pair ${pair.id} (${a.name} ⇄ ${b.name}); beam ${R.beams.map((p) => p.name).join(', ')}`;
  }

  // ---- routes --------------------------------------------------------------------------------

  /** The routes in walking order; `quick` ones are the --quick profile's (one per feature). */
  routes() {
    return [
      { id: 'gate Ops→Hall', quick: true, walk: (p2, heard) => this.gateByConsole(p2, heard) },
      { id: 'gate Hall→Ops', walk: (p2, heard) => this.gateByDhd(p2, heard) },
      { id: 'ring Ops→Lab', quick: true, walk: (p2, heard) => this.ring(p2, heard, 0) },
      { id: 'ring Lab→Ops', walk: (p2, heard) => this.ring(p2, heard, 1) },
      { id: 'beam Atrium→BeamLab', quick: true, walk: (p2, heard) => this.beamByButton(p2, heard) },
      { id: 'beam BeamLab→Atrium', walk: (p2, heard) => this.beamTyped(p2, heard) },
    ];
  }

  /** Walks every route (or the quick ones) as Probe2; returns [{ id, ok, detail }]. */
  async walk({ quick = false } = {}) {
    const p2 = await this.fac.second();
    const heard = [];
    trip.listen(p2, heard);
    const out = [];
    for (const r of this.routes().filter((x) => !quick || x.quick)) {
      const t0 = Date.now();
      let res;
      try { res = await r.walk(p2, heard); } catch (e) { res = { ok: false, detail: `threw: ${e.message}` }; }
      const detail = `${res.detail}, ${((Date.now() - t0) / 1000).toFixed(1)} s`;
      this.status[r.id] = { ok: res.ok, detail, time: clock() };
      out.push({ id: r.id, ok: res.ok, detail });
      await this.fac.refreshOpsWall().catch(() => {});
    }
    await p2.teleport(campus.TRANSIT.home).catch(() => {});
    return out;
  }

  /** Waits until a probe is put down within `r` of a point (any dimension check included). */
  async landed(probe, at, ms, dim = O, r = 1.5) {
    return trip.until(async () => probe.dimension === dim && flat(probe.position, at) < r && Math.abs(probe.position.y - at.y) < 1.5, ms);
  }

  /** Ops to Hall: the console's `Hall` button (the console form of `gate dial`), then walk in. */
  async gateByConsole(p2, heard) {
    const ops = geometry('Ops');
    const hall = geometry('Hall');
    const c = R.gates.Ops.console;
    const bx = c.x + c.dial.indexOf('Hall');
    await p2.teleport({ x: bx + 0.5, y: 0, z: c.z + 2.5, yaw: 180 });
    await p2.press({ x: bx, y: 1, z: c.z });
    const open = await trip.until(async () => drawn(p2, ops), 10000);
    if (!open) return { ok: false, detail: 'the Hall button did not open the Ops gate (its opening was never drawn)' };
    await ticks(25);
    const t0 = Date.now();
    const arrival = hall.arrival;
    await p2.teleport({ x: 0.5, y: 0, z: R.gates.Ops.runway.z0 + 0.5, yaw: 180 });
    await p2.walkTo({ x: 0.5, z: ops.opening[0].z - 1.5 }, { within: 0.4, ms: 8000, until: () => flat(p2.position, arrival) < 2 }).catch(() => {});
    const there = await this.landed(p2, arrival, 5000);
    await p2.settle();
    const facing = yawOff(mcYaw(p2), 0);
    if (!there) return { ok: false, detail: `Probe2 did not come out at Hall (at ${fmt(p2.position)})` };
    if (facing > 15) return { ok: false, detail: `came out at Hall facing ${mcYaw(p2).toFixed(0)}°, not south` };
    // Both ends shut again within timeout-shutdown, seen from Hall.
    const shut = await trip.until(async () => !drawn(p2, hall) && !drawn(p2, ops), (SHUT_SECONDS + 5) * 1000 - (Date.now() - t0), 10);
    if (!shut) return { ok: false, detail: `the gates were still open ${SHUT_SECONDS + 5} s after the trip` };
    return { ok: true, detail: `at Hall within ${flat(p2.position, arrival).toFixed(2)}, facing south; both gates shut ${((Date.now() - t0) / 1000).toFixed(0)} s after` };
  }

  /** Hall to Ops: the DHD button, then `/dial Ops` as the player, then walk in. */
  async gateByDhd(p2, heard) {
    const ops = geometry('Ops');
    const hall = geometry('Hall');
    const k = hall.button;
    const t0 = Date.now();
    await p2.teleport({ x: k.x + 0.5, y: 0, z: k.z + 1.5, yaw: 180 });
    await p2.press(k);
    const asked = await trip.until(async () => trip.said(heard, /Type '\/dial <gatename>/, t0), 5000);
    if (!asked) return { ok: false, detail: `pressing the DHD did not ask for /dial (told: ${recent(heard, t0)})` };
    p2.bot.chat('/dial Ops');
    const connected = await trip.until(async () => trip.said(heard, /Stargates connected\./, t0), 5000);
    if (!connected) return { ok: false, detail: `/dial Ops was not connected (told: ${recent(heard, t0)})` };
    if (!(await trip.until(async () => drawn(p2, hall), 10000))) return { ok: false, detail: 'the Hall opening was never drawn' };
    await ticks(25);
    const arrival = ops.arrival;
    await p2.teleport({ x: 0.5, y: 0, z: R.gates.Hall.runway.z0 + 0.5, yaw: 180 });
    await p2.walkTo({ x: 0.5, z: hall.opening[0].z - 1.5 }, { within: 0.4, ms: 8000, until: () => flat(p2.position, arrival) < 2 }).catch(() => {});
    if (!(await this.landed(p2, arrival, 5000))) return { ok: false, detail: `Probe2 did not come out at Ops (at ${fmt(p2.position)})` };
    return { ok: true, detail: `at Ops within ${flat(p2.position, arrival).toFixed(2)}` };
  }

  /** One way through the transit ring pair: from end `from` to the other. */
  async ring(p2, heard, from) {
    const ends = R.rings.ends;
    const to = 1 - from;
    const arrival = this.ringArrivals[to];
    // After a trip the pair recharges for 30 s from the end of its cycle, and said for how long
    // when it was stepped into: wait that out first.
    const last = [...heard].reverse().find((m) => /Rings recharging\. Ready in \d+ seconds?/.test(m.text));
    if (last) {
      const readyAt = last.at + Number(/Ready in (\d+)/.exec(last.text)[1]) * 1000 + 1500;
      while (Date.now() < readyAt) await ticks(10);
    }
    const t0 = Date.now();
    await trip.stepIn(p2, ends[from], O);
    const arrived = await trip.arrival(p2, arrival, 15000);
    const countdown = trip.said(heard, /Transport in \d+ seconds?/, t0);
    const named = await trip.until(async () => trip.said(heard, new RegExp(`Arrived at ${ends[to].name}\\.`), t0), 2000);
    if (!arrived.ok) return { ok: false, detail: `Probe2 did not come out at ${ends[to].name} (at ${fmt(p2.position)}; told: ${recent(heard, t0)})` };
    if (flat(arrived.at, arrival) >= 1) return { ok: false, detail: `came out ${flat(arrived.at, arrival).toFixed(2)} from the ${ends[to].name} arrival` };
    if (!countdown) return { ok: false, detail: 'no countdown was shown' };
    if (!named) return { ok: false, detail: `not told "Arrived at ${ends[to].name}." (told: ${recent(heard, t0)})` };
    if (from === 1) return { ok: true, detail: `at ${ends[to].name} within ${flat(arrived.at, arrival).toFixed(2)}, announced` };
    // Straight back in: it is recharging, and says so.
    // (after the cycle has finished round it: stepping in before then is "already in use").
    await ticks(CYCLE_TICKS - SWAP_TICKS);
    const back = Date.now();
    await trip.stepIn(p2, ends[to], O);
    const recharging = await trip.until(async () => trip.said(heard, /Rings recharging\. Ready in \d+ seconds?/, back), 3000);
    if (!recharging) return { ok: false, detail: 'stepping straight back in did not say the rings are recharging' };
    return { ok: true, detail: `at ${ends[to].name} within ${flat(arrived.at, arrival).toFixed(2)}, announced; recharging on the way back` };
  }

  /** Atrium to BeamLab: the pad's button prompts Probe2, whose click runs `beam to` as it. */
  async beamByButton(p2, heard) {
    const [from, to] = R.beams;
    const b = from.button;
    const t0 = Date.now();
    await p2.teleport({ x: b.x + 0.5, y: 0, z: b.z + 1.5, yaw: 180 });
    const prompts = [];
    const onMsg = (m) => prompts.push(m);
    p2.bot.on('message', onMsg);
    try {
      await p2.press({ x: b.x, y: 1, z: b.z });
      await trip.until(async () => prompts.some((m) => text.clickCommands(m.json).length), 3000);
    } finally {
      p2.bot.off('message', onMsg);
    }
    const click = prompts.map((m) => text.clickCommands(m.json)).flat().find((c) => /beam to/.test(c.command));
    if (!click) return { ok: false, detail: 'the button sent no beam prompt' };
    p2.bot.chat(click.command);
    return this.beamLanding(p2, heard, to, t0, `button, then the click "${click.command}"`);
  }

  /** BeamLab to Atrium, typed: `/wormhole beam to Atrium`. */
  async beamTyped(p2, heard) {
    const [to] = R.beams;
    const t0 = Date.now();
    p2.bot.chat(`/wormhole beam to ${to.name}`);
    return this.beamLanding(p2, heard, to, t0, 'typed');
  }

  async beamLanding(p2, heard, to, t0, how) {
    const at = beamArrival(to);
    const there = await this.landed(p2, at, 8000, O, 1);
    await trip.until(async () => trip.said(heard, new RegExp(`Beamed to ${to.name}\\.`), t0), 3000);
    await p2.settle();
    const told = trip.said(heard, new RegExp(`Beaming to ${to.name}\\.\\.\\.`), t0) && trip.said(heard, new RegExp(`Beamed to ${to.name}\\.`), t0);
    if (!there) return { ok: false, detail: `${how}: Probe2 did not land at ${to.name} (at ${fmt(p2.position)}; told: ${recent(heard, t0)})` };
    const off = yawOff(mcYaw(p2), to.yaw);
    if (off > 5) return { ok: false, detail: `${how}: landed facing ${mcYaw(p2).toFixed(0)}°, not ${to.yaw}°` };
    if (!told) return { ok: false, detail: `${how}: landed, but not told "Beaming to ${to.name}..." and "Beamed to ${to.name}."` };
    return { ok: true, detail: `${how}: at ${to.name} within ${flat(p2.position, at).toFixed(2)}, facing ${mcYaw(p2).toFixed(0)}°` };
  }

  /** What the Ops wall shows under TRANSIT: one line per route. */
  wallLines() {
    const lines = ['\n', { text: 'TRANSIT', color: 'white', bold: true }];
    for (const r of this.routes()) {
      const s = this.status[r.id];
      lines.push('\n', { text: `${r.id} · `, color: 'white' },
        { text: s ? `${s.ok ? 'PASS' : 'FAIL'} · ${s.time}` : 'not walked yet', color: !s ? 'gray' : s.ok ? 'green' : 'red' });
    }
    return lines;
  }
}

function fmt(p) {
  return `${p.x.toFixed(1)} ${p.y.toFixed(1)} ${p.z.toFixed(1)}`;
}

function recent(heard, since) {
  return heard.filter((m) => m.at >= since).map((m) => m.text).slice(-4).join(' | ') || 'nothing';
}

function clock() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

module.exports = { Transit, geometry, beamArrival, mcYaw };
