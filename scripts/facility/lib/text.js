'use strict';
// THE version switch for text components. Everything the facility shows as text (chat menus,
// text displays, bossbar names) is written as a version-neutral spec and serialised here.
//
// Two changes at 1.21.5, both checked against real servers by spike.js:
//  - event keys: clickEvent {action, value} / hoverEvent {action, contents} became
//    click_event {action, command|url|...} / hover_event {action, value};
//  - entity NBT: a text display's `text` was a JSON string; it is now an SNBT component.
//    Sending the new form to 1.20.4 is not an error: the display silently reads "".
//
// A spec is a string, an array of specs, or
//   { text, color, bold, italic, underlined, click: { run } | { suggest } | { page }, hover: spec, extra: [spec] }
// (`page` turns a written book's page: change_page, whose target is `page` from 1.21.5 and a
// string `value` before.)

const { atLeast } = require('./version');

const SNAKE_CASE_EVENTS = '1.21.5';

function modern(version) {
  return atLeast(version, SNAKE_CASE_EVENTS);
}

const STYLE_KEYS = ['color', 'bold', 'italic', 'underlined', 'strikethrough', 'obfuscated', 'font', 'insertion'];

/** The spec as a plain component object with this version's key names. */
function toComponent(version, spec) {
  if (typeof spec === 'string') return { text: spec };
  if (Array.isArray(spec)) return { text: '', extra: spec.map((s) => toComponent(version, s)) };
  const out = { text: spec.text === undefined ? '' : String(spec.text) };
  for (const key of STYLE_KEYS) if (spec[key] !== undefined) out[key] = spec[key];
  if (spec.click && spec.click.page !== undefined) {
    if (modern(version)) out.click_event = { action: 'change_page', page: Number(spec.click.page) };
    else out.clickEvent = { action: 'change_page', value: String(spec.click.page) };
  } else if (spec.click) {
    const [action, target] = spec.click.run !== undefined
      ? ['run_command', spec.click.run]
      : ['suggest_command', spec.click.suggest];
    if (target === undefined) throw new Error(`click needs run, suggest or page: ${JSON.stringify(spec.click)}`);
    if (modern(version)) out.click_event = { action, command: target };
    else out.clickEvent = { action, value: target };
  }
  if (spec.hover !== undefined) {
    const shown = toComponent(version, spec.hover);
    if (modern(version)) out.hover_event = { action: 'show_text', value: shown };
    else out.hoverEvent = { action: 'show_text', contents: shown };
  }
  if (spec.extra) out.extra = spec.extra.map((s) => toComponent(version, s));
  return out;
}

const BARE_KEY = /^[A-Za-z0-9_.+-]+$/;

/** Serialises a plain value as SNBT: bare keys where allowed, strings double-quoted. */
function toSnbt(value) {
  if (Array.isArray(value)) return `[${value.map(toSnbt).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    const body = Object.entries(value)
      .map(([k, v]) => `${BARE_KEY.test(k) ? k : JSON.stringify(k)}:${toSnbt(v)}`)
      .join(',');
    return `{${body}}`;
  }
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') return String(value);
  return JSON.stringify(String(value));
}

/** An SNBT single-quoted string holding `s`. */
function quoteSingle(s) {
  return `'${s.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
}

/**
 * A written book as an item argument (for `item replace`), marked with `marker` so it can be found
 * again. Three forms: NBT with its pages as JSON strings before 1.20.5; the written_book_content
 * component with JSON-string pages from 1.20.5; SNBT component pages (snake_case click and hover
 * keys) from 1.21.5. The marker is a root tag before 1.20.5 and custom_data from it.
 */
function bookItem(version, { title, author, pages, marker = null }) {
  const comps = pages.map((p) => toComponent(version, p));
  const asJson = comps.map((c) => quoteSingle(JSON.stringify(c))).join(',');
  if (!atLeast(version, '1.20.5')) {
    return `minecraft:written_book{title:${JSON.stringify(title)},author:${JSON.stringify(author)},pages:[${asJson}]${marker ? `,${marker}:1b` : ''}}`;
  }
  // 1.20.5 to 1.21.4 take JSON-string pages here: unverified, as no tested version is in that range.
  const list = modern(version) ? comps.map(toSnbt).join(',') : asJson;
  const data = marker ? `,minecraft:custom_data={${marker}:1b}` : '';
  return `minecraft:written_book[minecraft:written_book_content={title:${JSON.stringify(title)},author:${JSON.stringify(author)},pages:[${list}]}${data}]`;
}

/** The component argument for tellraw, bossbar add/set name, title: JSON before 1.21.5, SNBT from it. */
function command(version, spec) {
  const c = toComponent(version, spec);
  return modern(version) ? toSnbt(c) : JSON.stringify(c);
}

/** The value for a text display's `text` NBT: a quoted JSON string before 1.21.5, a component from it. */
function displayNbt(version, spec) {
  const c = toComponent(version, spec);
  return modern(version) ? toSnbt(c) : quoteSingle(JSON.stringify(c));
}

/** A whole `summon text_display` command at x y z with the given tags and extra NBT. */
function summonDisplay(version, { x, y, z, tags = [], spec, nbt = {} }) {
  const extra = Object.entries(nbt).map(([k, v]) => `,${k}:${v}`).join('');
  return `summon minecraft:text_display ${x} ${y} ${z} {Tags:${toSnbt(tags)},text:${displayNbt(version, spec)}${extra}}`;
}

// ---- reading back -------------------------------------------------------------------------

/** A small SNBT reader: compounds, lists, quoted and bare strings, numbers with suffixes. */
function parseSnbt(src) {
  let i = 0;
  const ws = () => { while (i < src.length && /\s/.test(src[i])) i++; };
  const fail = (what) => { throw new Error(`SNBT: expected ${what} at ${i} in ${src}`); };
  function quoted() {
    const q = src[i++];
    let s = '';
    while (i < src.length && src[i] !== q) {
      if (src[i] === '\\') {
        i++;
        const c = src[i++];
        s += { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f' }[c] || c;
      } else s += src[i++];
    }
    if (src[i] !== q) fail(`closing ${q}`);
    i++;
    return s;
  }
  function bare() {
    const m = /^[A-Za-z0-9_.+-]+/.exec(src.slice(i));
    if (!m) fail('a value');
    i += m[0].length;
    const t = m[0];
    if (t === 'true') return true;
    if (t === 'false') return false;
    const num = /^([+-]?\d*\.?\d+(?:[eE][+-]?\d+)?)[bBsSlLfFdD]?$/.exec(t);
    return num ? Number(num[1]) : t;
  }
  function value() {
    ws();
    const c = src[i];
    if (c === '{') {
      i++;
      const o = {};
      ws();
      if (src[i] === '}') { i++; return o; }
      for (;;) {
        ws();
        const k = src[i] === '"' || src[i] === "'" ? quoted() : bare();
        ws();
        if (src[i++] !== ':') fail(':');
        o[String(k)] = value();
        ws();
        if (src[i] === ',') { i++; continue; }
        if (src[i++] === '}') return o;
        fail(', or }');
      }
    }
    if (c === '[') {
      i++;
      if (/^[BIL];/.test(src.slice(i, i + 2))) i += 2;
      const a = [];
      ws();
      if (src[i] === ']') { i++; return a; }
      for (;;) {
        a.push(value());
        ws();
        if (src[i] === ',') { i++; continue; }
        if (src[i++] === ']') return a;
        fail(', or ]');
      }
    }
    if (c === '"' || c === "'") return quoted();
    return bare();
  }
  const v = value();
  ws();
  if (i !== src.length) fail('end of input');
  return v;
}

/**
 * The component a text display holds, from the value `data get entity <e> text` printed
 * (what follows "has the following entity data: "). Before 1.21.5 that is a quoted JSON
 * string; from 1.21.5 it is the component itself, or a bare string for plain text.
 */
function readDisplayText(version, printed) {
  const v = parseSnbt(printed.trim());
  if (!modern(version)) {
    if (typeof v !== 'string') throw new Error(`expected a JSON string before 1.21.5, got ${printed}`);
    return v === '' ? { text: '' } : normalise(JSON.parse(v));
  }
  return normalise(v);
}

/** Plain strings as { text }, lists as their first element plus extra. */
function normalise(c) {
  if (typeof c === 'string') return { text: c };
  if (Array.isArray(c)) {
    const [first, ...rest] = c.map(normalise);
    return rest.length ? { ...first, extra: [...(first.extra || []), ...rest] } : first;
  }
  const out = { ...c };
  // SNBT prints a boolean style as a byte (bold: 1b) from 1.21.5.
  for (const key of BOOLEAN_STYLES) if (typeof out[key] === 'number') out[key] = out[key] !== 0;
  if (out.extra) out.extra = out.extra.map(normalise);
  return out;
}

const BOOLEAN_STYLES = ['bold', 'italic', 'underlined', 'strikethrough', 'obfuscated'];

/** The concatenated plain text of a component. */
function plain(c) {
  const n = normalise(c);
  return `${n.text || ''}${(n.extra || []).map(plain).join('')}`;
}

/** Deep equality of two components, ignoring key order. */
function sameComponent(a, b) {
  const canon = (x) => {
    if (Array.isArray(x)) return x.map(canon);
    if (x && typeof x === 'object') return Object.fromEntries(Object.keys(x).sort().map((k) => [k, canon(x[k])]));
    return x;
  };
  return JSON.stringify(canon(normalise(a))) === JSON.stringify(canon(normalise(b)));
}

/** Every object in a component tree, for finding a click event wherever the server put it. */
function* walk(node) {
  if (Array.isArray(node)) for (const n of node) yield* walk(n);
  else if (node && typeof node === 'object') {
    yield node;
    for (const v of Object.values(node)) yield* walk(v);
  }
}

/**
 * The click events in a received component (either era's keys), with the text they sit on:
 * [{ key, command, text }]. What a client would run if the player clicked that word.
 */
function clickCommands(component) {
  const found = [];
  for (const o of walk(component)) {
    if (o.clickEvent) found.push({ key: 'clickEvent', command: o.clickEvent.value, text: o.text });
    if (o.click_event) found.push({ key: 'click_event', command: o.click_event.command, text: o.text });
  }
  return found;
}

module.exports = {
  SNAKE_CASE_EVENTS, toComponent, command, displayNbt, summonDisplay, quoteSingle, bookItem,
  toSnbt, parseSnbt, readDisplayText, plain, sameComponent, clickCommands,
};
