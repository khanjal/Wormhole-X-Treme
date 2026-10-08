'use strict';
// The gallery's quantum mirrors (lib/gallery.js, family 'mirror'): a banner on the studio's
// backdrop wall, and the window it opens onto another room. A mirror is a wall banner; near it the
// plugin sends the viewer the room it opens onto, drawn as blocks behind the wall with the banner
// and the wall's 1 x 2 opening taken away, so the viewer shows it. The rooms are the campus's own
// mirrors, in the Nether (Range) and the End (Annex), whose captures were taken when they were made.
//
//   still, idle    the banner on the wall, before anybody has chosen where it opens
//   still, open    a right-click later: the room it opens onto, seen through the wall
//   reel, choose   from just before the right-click until the room has been drawn
//
// One mirror is made for the whole run and taken down at its end (`finish`): its own room is
// captured when it is made, which takes seconds, and a scene only changes which mirror it opens
// onto (`mirror set -start`). Probe is sent out of the mirror's range first, so that each scene
// starts with nothing chosen.

const mirrors = require('./mirrors');
const trip = require('./ringtrip');
const { standAt } = require('./studio');

const MIRROR = { name: 'Gallery', dim: 'minecraft:overworld', x: 0, y: 1, z: 375, facing: 'south', floorY: 0 };
/** The rooms it opens onto: the campus's mirrors in other worlds. */
const ROOMS = [{ to: 'Range', slug: 'nether' }, { to: 'Annex', slug: 'end' }];
/** Far enough from the mirror (its range is 16) for the choice to be forgotten. */
const AWAY = { x: 0.5, y: 0, z: 430.5 };

/** Every mirror scene: the banner, and each room as a still and a reel. */
function scenes() {
  const out = [{ name: 'mirror-idle', family: 'mirror', kind: 'still', state: 'idle', mirror: { to: null } }];
  for (const r of ROOMS) {
    out.push({ name: `mirror-${r.slug}-open`, family: 'mirror', kind: 'still', state: 'open', mirror: { to: r.to } });
  }
  for (const r of ROOMS) {
    out.push({ name: `mirror-${r.slug}`, family: 'mirror', kind: 'reel', action: 'choose', trim: 500, mirror: { to: r.to } });
  }
  return out;
}

/**
 * Where Probe stands to look at the banner: a couple of blocks out, so the 1 x 2 opening is a good
 * part of the picture, looking at the banner's middle, which is where Probe faces to click it (a
 * different aim would make the picture jump as the click begins).
 */
function camera(m = MIRROR) {
  return standAt({ x: m.x + 0.5, y: 1.6, z: m.z + 0.5 + 2 }, { x: m.x + 0.5, y: m.y + 0.6, z: m.z + 0.5 });
}

/** The state shared by the run: whether the mirror stands (and whether its banner is up, which it can be without), what Probe has been told. */
function shared(ctx) {
  if (!ctx.mirrorState) ctx.mirrorState = { made: false, heard: [], kit: new mirrors.MirrorKit(ctx.srv) };
  return ctx.mirrorState;
}

async function prepare(ctx, s) {
  const st = shared(ctx);
  const m = MIRROR;
  if (!st.made) {
    // Noted before it goes up: a setup that fails after it must still take it down at the end.
    st.banner = true;
    await st.kit.banner(m, 'red');
    const said = await st.kit.create(m.name, m);
    const { names } = await st.kit.list();
    if (!names.includes(m.name)) throw new Error(`the mirror was not made: ${said || 'no answer'}`);
    await st.kit.captured(m.name);
    trip.listen(ctx.probe, st.heard);
    st.made = true;
  }
  // Out of range, so that the choice of the last scene is forgotten and this one starts afresh.
  await ctx.probe.teleport(AWAY);
  // The plugin forgets a choice on its next proximity sweep (a second at 20 TPS), and a slow server takes longer.
  await ctx.sleep(2500);
  if (s.mirror.to) {
    const said = await st.kit.set(m.name, '-start', s.mirror.to);
    if (!new RegExp(`right-click on .* opens onto .*${s.mirror.to}`).test(said)) throw new Error(`mirror set -start ${s.mirror.to}: ${said || 'no answer'}`);
  }
  return { camera: camera(m), kit: st.kit, m, heard: st.heard };
}

/**
 * Right-clicks the banner until it opens onto the room the scene names (its start, so the first
 * click), and waits until the room has been drawn behind the wall and has stopped arriving.
 */
async function act(ctx, s, prep) {
  const { m, heard } = prep;
  const seen = mirrors.watchWindow(ctx.probe, m);
  try {
    // Facing it first, as a player does: the plugin shows its name above the hotbar to somebody looking at it.
    await ctx.probe.face({ x: m.x + 0.5, y: m.y + 0.6, z: m.z + 0.5 });
    ctx.mark();
    const chose = await mirrors.chooseUntil(ctx.probe, m, s.mirror.to, heard, { max: 4 });
    if (!chose.reached) throw new Error(`the mirror did not open onto ${s.mirror.to}: ${chose.hints.join(' / ')}`);
    const give = Date.now() + 60000 * ctx.factor;
    let lastCount = -1;
    let lastChange = Date.now();
    while (Date.now() < give) {
      if (seen.count !== lastCount) {
        lastCount = seen.count;
        lastChange = Date.now();
      }
      if (seen.bannerAir && seen.count > 200 && Date.now() - lastChange > 1500 * ctx.factor) return;
      await ctx.sleep(Math.max(5, Math.min(100, 25 * ctx.factor)));
    }
    throw new Error(`the room was not drawn: ${seen.count} blocks behind the wall, banner ${seen.bannerAir ? 'gone' : 'still there'}`);
  } finally {
    seen.stop();
  }
}

/** Takes the mirror and its banner away at the end of the run. */
async function finish(ctx) {
  const st = ctx.mirrorState;
  if (!st || !(st.made || st.banner)) return;
  await st.kit.remove(MIRROR.name).catch(() => {});
  await ctx.srv.run(`execute in ${MIRROR.dim} run setblock ${MIRROR.x} ${MIRROR.y} ${MIRROR.z} minecraft:air`);
  st.made = false;
  st.banner = false;
}

module.exports = { scenes, prepare, act, finish, camera, MIRROR, ROOMS, AWAY };
