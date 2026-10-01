'use strict';
// The config console audit: `wormhole config` against every setting the plugin's source declares.

const settings = require('../../lib/settings');
const { v, c, said, configFile } = require('./common');

/** Bad values, one of each kind ParsedSetting refuses, and the words it refuses them with. */
const REFUSALS = [
  ['gate-sounds-enabled', 'loud', 'GATE_SOUNDS_ENABLED is true or false, not "loud".'],
  ['gate-sound-volume', 'quiet', 'GATE_SOUND_VOLUME is a number, not "quiet".'],
  ['ring-countdown-ticks', '1.5', 'RING_COUNTDOWN_TICKS is a number, not "1.5".'],
  ['gate-dial-spin', 'wobble', 'GATE_DIAL_SPIN is CHEVRON, TOP, LAP, FILL, PEGASUS, CHASE, UNIVERSE, OVERSHOOT or NONE, not "wobble".'],
  ['gate-iris-animation', 'wobble', 'GATE_IRIS_ANIMATION is sweep, spiral, rows, columns, instant, not "wobble".'],
  ['log-level', 'LOUD', 'LOG_LEVEL is SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL or OFF, not "LOUD".'],
  ['ring-default-access', 'open', 'RING_DEFAULT_ACCESS is PUBLIC or PRIVATE, not "open".'],
  ['ring-default-style', 'zigzag', 'RING_DEFAULT_STYLE is CONCURRENT or SEQUENTIAL, not "zigzag". Fast and slow work too.'],
  ['ring-default-material', 'stone', 'RING_DEFAULT_MATERIAL is the name of a slab, not "stone" -- a ring moves half a block at a time, which is what a slab can do.'],
  ['ring-default-light', 'diamond', 'RING_DEFAULT_LIGHT is the name of a block, not "diamond".'],
];

const cases = [
  v('every setting', 'each setting DefaultSettings declares answers `wormhole config <name>`, and the plugin lists no other'),
  v('both spellings', 'gate-sound-volume, GATE_SOUND_VOLUME and Gate-Sound-Volume are one setting; set by one, read by another'),
  v('search', '`wormhole config ring-sound` lists exactly the settings with RING_SOUND in their names, as ring_sound does'),
  v('bad values', 'a word for a switch, a word or a fraction for a number, and an unknown name for each fixed set: each refused in its own words, nothing changed'),
  v('unknown setting', '`wormhole config no-such-setting 1`: "No setting called no-such-setting.", and no match'),
  v('saved', 'a change is written to config.yml at once'),
];

async function run(ctx, o) {
  const obs = ctx.observed;
  const all = settings.load();
  obs.declared = all.length;
  if (o.case === 'every setting') {
    obs.answers = {};
    for (const s of all) {
      const lines = await said(ctx, `wormhole config ${s.name}`);
      obs.answers[s.name] = lines.find((l) => l.startsWith(`${s.key} = `)) || lines.join(' | ') || 'nothing';
    }
    // Listing everything: the first 30, then "...and N more".
    obs.listing = await said(ctx, 'wormhole config');
  } else if (o.case === 'both spellings') {
    obs.read = {};
    for (const n of ['gate-sound-volume', 'GATE_SOUND_VOLUME', 'Gate-Sound-Volume']) obs.read[n] = await ctx.config.get(n);
    obs.wrote = await ctx.config.set('gate-sound-volume', '0.7', 's1');
    obs.readBack = await ctx.config.get('GATE_SOUND_VOLUME');
  } else if (o.case === 'search') {
    obs.want = all.filter((s) => s.key.includes('RING_SOUND')).map((s) => s.key).sort();
    obs.kebab = await said(ctx, 'wormhole config ring-sound');
    obs.snake = await said(ctx, 'wormhole config ring_sound');
  } else if (o.case === 'bad values') {
    obs.refused = [];
    for (const [name, value, words] of REFUSALS) {
      const before = await ctx.config.get(name);
      const lines = await said(ctx, `wormhole config ${name} ${value}`);
      const after = await ctx.config.get(name);
      obs.refused.push({ name, value, words, lines, before, after });
    }
  } else if (o.case === 'unknown setting') {
    obs.lines = await said(ctx, 'wormhole config no-such-setting 1');
  } else if (o.case === 'saved') {
    obs.before = configFile(ctx);
    obs.wrote = await ctx.config.set('gate-sound-volume', '0.7', 's1');
    obs.after = configFile(ctx);
  }
}

function checks(obs, o) {
  if (o.case === 'every setting') {
    const missing = () => Object.entries(obs.answers || {}).filter(([, a]) => !/^[A-Z0-9_]+ = /.test(a));
    const listed = () => {
      const m = (obs.listing || []).map((l) => /^\.\.\.and (\d+) more\./.exec(l)).find(Boolean);
      return m ? 30 + Number(m[1]) : (obs.listing || [])[0] ? obs.listing[0].split(', ').length : 0;
    };
    return [
      c(`each of the ${obs.declared} settings DefaultSettings declares answers \`wormhole config <name>\``, () => {
        if (obs.declared > 0 && missing().length === 0) return true;
        throw new Error(missing().slice(0, 3).map(([n, a]) => `${n}: ${a}`).join('; '));
      }),
      c(`and the plugin lists ${obs.declared} settings in all ("...and N more")`, () => {
        if (listed() === obs.declared) return true;
        throw new Error(`lists ${listed()}: ${(obs.listing || []).join(' | ').slice(0, 200)}`);
      }),
    ];
  }
  if (o.case === 'both spellings') {
    const r = () => Object.values(obs.read || {});
    return [
      c('gate-sound-volume, GATE_SOUND_VOLUME and Gate-Sound-Volume read the same value', () => r().length === 3 && r().every((x) => x === r()[0])),
      c('set as gate-sound-volume: "GATE_SOUND_VOLUME is now 0.7."', () => obs.wrote === '0.7'),
      c('and read back as GATE_SOUND_VOLUME: 0.7', () => obs.readBack === '0.7'),
    ];
  }
  if (o.case === 'search') {
    const names = (lines) => ((lines || [])[0] ? lines[0].split(', ') : []);
    return [
      c(`ring-sound lists exactly the ${(obs.want || []).length} settings with RING_SOUND in their names`, () => {
        if (obs.want.length > 1 && names(obs.kebab).join() === obs.want.join()) return true;
        throw new Error(`listed: ${(obs.kebab || []).join(' | ')}`);
      }),
      c('and ring_sound lists the same', () => names(obs.snake).join() === obs.want.join()),
      c('with how to read and change one', () => (obs.kebab || []).some((l) => l.startsWith('/wormhole config <name> shows one'))),
    ];
  }
  if (o.case === 'bad values') {
    return (obs.refused || []).map((x) => c(`${x.name} ${x.value}: "${x.words}", unchanged`, () => {
      if (x.lines.includes(x.words) && x.after === x.before) return true;
      throw new Error(`told: ${x.lines.join(' | ')}; ${x.before} -> ${x.after}`);
    }));
  }
  if (o.case === 'unknown setting') {
    return [
      c('"No setting called no-such-setting."', () => (obs.lines || []).includes('No setting called no-such-setting.')),
      c('and "No setting matches "no-such-setting"."', () => (obs.lines || []).includes('No setting matches "no-such-setting".')),
    ];
  }
  if (o.case === 'saved') {
    return [
      c('config.yml had another value before', () => /^gate-sound-volume: /m.test(obs.before || '') && !/^gate-sound-volume: 0\.7\s*$/m.test(obs.before || '')),
      c('"GATE_SOUND_VOLUME is now 0.7." and config.yml says gate-sound-volume: 0.7', () => obs.wrote === '0.7' && /^gate-sound-volume: 0\.7\s*$/m.test(obs.after || '')),
    ];
  }
  return [];
}

module.exports = { cases, run, checks, REFUSALS };
