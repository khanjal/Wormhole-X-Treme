'use strict';
// What a bot hears: every sound packet its client is sent, by name, with where it came from,
// its volume and pitch, and when. A bot cannot hear, so the Systems checks count these: a
// setting that silences a sound is judged by its packets stopping, against a run that had them.

/** The sound's name without its namespace: "block.conduit.activate". */
function bare(name) {
  return String(name || '').replace(/^minecraft:/, '');
}

/**
 * Starts recording what `bot` hears. `stop()` ends it and returns
 * [{ name, x, y, z, volume, pitch, t }] in the order heard (`t` in ms from `t0`, its start).
 */
function record(bot) {
  const t0 = Date.now();
  const heard = [];
  const on = (name, pos, volume, pitch) => {
    heard.push({ name: bare(name), x: pos.x, y: pos.y, z: pos.z, volume, pitch, t: Date.now() - t0 });
  };
  bot.on('soundEffectHeard', on);
  return {
    heard,
    t0,
    stop() {
      bot.off('soundEffectHeard', on);
      return heard;
    },
  };
}

/** The sounds in `heard` named `name` (bare or namespaced). */
function named(heard, name) {
  const want = bare(name);
  return heard.filter((s) => s.name === want);
}

/** How many of each sound `heard` has, as "name x3, other x1", for a check's detail. */
function tally(heard) {
  const counts = new Map();
  for (const s of heard) counts.set(s.name, (counts.get(s.name) || 0) + 1);
  return [...counts.entries()].map(([n, c]) => `${n} x${c}`).join(', ') || 'nothing';
}

module.exports = { record, named, tally, bare };
