'use strict';
// The Facility Logbook (design addendum, built in stage 5): a written book every player is given
// on joining, alongside the chat console. Page 1 is its contents, each entry turning to its page;
// then Your runs (the reader's), Bot runs, a page per wing (the wing and each of its chambers with
// a Go that moves the reader there), Transit (each route's last smoke test) and how to use the
// facility. A written book cannot change once given, so after every run the facility puts a
// fresh copy in the slot each holder keeps it in, never a second copy; `!book` or the console's
// Logbook button asks for one. A book gone from a player's inventory is not given back unasked,
// and none is ever dropped on the floor: a player with no free slot is told so instead.
//
// The book's three forms (NBT before 1.20.5, written_book_content, SNBT pages from 1.21.5) are
// lib/text.js bookItem's; a Go is `/trigger wx set <code>`, the console's own go and watch
// codes, so it needs no op.

const text = require('./text');
const campus = require('./campus');
const { triggerCommand, GO, ACTION_OPTION, ACTIONS, normaliseOptions } = require('./console');

const MARKER = 'wx_logbook';
const TITLE = 'Facility Logbook';
const RUNS_SHOWN = 8;
// A book page shows 14 lines of about 19 characters (114 pixels, most characters 6 wide); 18
// leaves room for capitals and bold.
const PAGE_LINES = 14;
const PAGE_WIDTH = 18;

/** How many lines a page's plain text takes, wrapped at word boundaries as a book wraps it. */
function rowsOf(plainText) {
  let rows = 0;
  const paragraphs = plainText.split('\n');
  if (paragraphs[paragraphs.length - 1] === '') paragraphs.pop();
  for (const p of paragraphs) {
    let row = 0;
    let n = 1;
    for (const word of p.split(' ')) {
      const w = word.length;
      if (row === 0) { row = w; } else if (row + 1 + w <= PAGE_WIDTH) { row += 1 + w; } else { n++; row = w; }
      while (row > PAGE_WIDTH) { n++; row -= PAGE_WIDTH; }
    }
    rows += n;
  }
  return rows;
}

/** The plain text of a list of text parts. */
function plainOf(parts) {
  return parts.map((x) => (typeof x === 'string' ? x : x.text || '')).join('');
}

/**
 * A section's pages: `head` (its title parts) then `items` (each a list of parts, ending in a new
 * line), as many to a page as fit; a page after the first starts with the head again.
 */
function paginate(head, items) {
  const pages = [];
  let page = [...head];
  let used = rowsOf(plainOf(head));
  let any = false;
  for (const item of items) {
    const rows = rowsOf(plainOf(item));
    if (any && used + rows > PAGE_LINES) {
      pages.push(page);
      page = [...head];
      used = rowsOf(plainOf(head));
    }
    page.push(...item);
    used += rows;
    any = true;
  }
  pages.push(page);
  return pages;
}

/** The slot argument `item replace entity` takes for an Inventory Slot number. */
function slotArg(n) {
  if (n >= 0 && n <= 8) return `hotbar.${n}`;
  if (n >= 9 && n <= 35) return `inventory.${n - 9}`;
  if (n === -106) return 'weapon.offhand';
  return null;
}

/** A run's one-line entry, with the detail in its hover text. */
function runLine(r) {
  const colour = { PASS: 'dark_green', FAIL: 'red', KNOWN: 'gold', REFUSED: 'dark_gray', STAGED: 'dark_aqua' }[r.shown] || 'black';
  const detail = [`${r.id.toUpperCase()} ${r.title} at ${r.time}`, r.settings, `${r.outcome}${r.reason ? `: ${r.reason}` : ''}`,
    r.known ? `known plugin failure: ${r.known}` : null].filter(Boolean).join('\n');
  return [{ text: `${r.time} `, color: 'dark_gray' }, { text: `${r.id.toUpperCase()} `, color: 'black', bold: true },
    { text: r.shown, color: colour, hover: detail }, '\n'];
}

class Logbook {
  /** `fac` is the Facility: its entries, console, transit and server. */
  constructor(fac) {
    this.fac = fac;
    this.runs = [];
    this.holders = new Set();
  }

  /**
   * Notes a finished run: the chamber, its settings, the outcome, and KNOWN where the matrix says
   * the plugin is known to fail that way on this version.
   */
  record(e, values, result, by = null) {
    const opts = normaliseOptions(e.chamber ? e.chamber.options : {});
    const settings = opts.map((o) => {
      const v = o.values.find((x) => x.value === values[o.name]) || o.values[0];
      return `${o.name} ${v.label}`;
    }).join(', ');
    const known = this.knownFor(e, values, result);
    const now = new Date();
    const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    this.runs.push({
      id: e.def.id, title: e.def.title, settings, outcome: result.outcome, reason: result.reason, known,
      shown: known ? 'KNOWN' : result.outcome, by, time,
    });
    if (this.runs.length > 500) this.runs.splice(0, this.runs.length - 500);
  }

  /** The known-failure note of the matrix cell a run matches, or null. */
  knownFor(e, values, result) {
    if (result.outcome !== 'FAIL') return null;
    const { MATRIX, defaultsOf, expectation } = require('../matrix');
    for (const cell of MATRIX[e.def.id] || []) {
      const full = { ...defaultsOf(e.chamber), ...cell.values };
      const same = Object.keys(full).every((k) => full[k] === values[k]);
      const want = expectation(cell, this.fac.version, this.fac.fixed || []);
      if (same && cell.known && want === `FAIL:${result.reason}`) return cell.known;
    }
    return null;
  }

  /** The book's pages for a reader, as text specs, and where each section starts. */
  pages(player) {
    const wings = campus.WINGS;
    const entries = this.fac.entries || [];
    const sections = [];
    const add = (name, list) => { sections.push({ name, pages: list }); };
    const allMine = this.runs.filter((r) => r.by === player);
    const allBots = this.runs.filter((r) => !r.by);
    const mine = allMine.slice(-RUNS_SHOWN).reverse();
    const bots = allBots.slice(-RUNS_SHOWN).reverse();
    // Each list counts every run it has had, the latest first.
    add('Your runs', paginate([{ text: `YOUR RUNS · ${allMine.length}\n\n`, bold: true }],
      mine.length ? mine.map(runLine) : [[{ text: 'None yet. Say ! for the console, or press a chamber\'s Run.', color: 'dark_gray' }]]));
    add('Bot runs', paginate([{ text: `BOT RUNS · ${allBots.length}\n\n`, bold: true }],
      bots.length ? bots.map(runLine) : [[{ text: 'None yet.', color: 'dark_gray' }]]));
    for (const [i, w] of wings.entries()) {
      const head = [{ text: `${w.title.toUpperCase()}\n`, bold: true }];
      const items = [[{ text: '[Go]', color: 'dark_green', click: { run: triggerCommand(GO + i) }, hover: `take me to ${w.title}` }, { text: ` ${w.nick}\n\n`, color: 'dark_gray' }]];
      for (const e of entries.filter((x) => x.def.wing === w.id || (w.id === 'ops' && x.def.wing === 'systems'))) {
        const s = this.fac.status ? this.fac.status[e.def.id] : null;
        items.push([{ text: '[Go] ', color: 'dark_green', click: { run: triggerCommand(e.number * 10000 + ACTION_OPTION * 100 + ACTIONS.indexOf('watch')) }, hover: `to ${e.def.title}'s gallery seat` },
          { text: `${e.def.id.toUpperCase()} ${e.def.title}`, color: 'black' },
          { text: ` ${s ? s.state : e.chamber ? 'idle' : `stage ${e.def.stage}`}\n`, color: 'dark_gray' }]);
      }
      add(w.title, paginate(head, items));
    }
    const routes = this.fac.transit && this.fac.transit.routes ? this.fac.transit.routes() : [];
    add('Transit', paginate([{ text: 'TRANSIT\n', bold: true }, { text: 'each route\'s last smoke test\n\n', color: 'dark_gray' }],
      routes.map((r) => {
        const s = this.fac.transit.status[r.id];
        return [{ text: `${r.id}: `, color: 'black' }, { text: s ? `${s.ok ? 'PASS' : 'FAIL'} ${s.time}` : 'not yet', color: s ? (s.ok ? 'dark_green' : 'red') : 'dark_gray', hover: s ? s.detail : 'walked by the self-test' }, '\n'];
      })));
    const prose = (title, paragraphs) => paginate([{ text: `${title}\n\n`, bold: true }],
      paragraphs.flatMap((p) => p.split(/(?<=[.:]) /).map((sentence, k, all) => [{ text: `${sentence}${k < all.length - 1 ? ' ' : '\n\n'}`, color: 'black' }])));
    add('How to use it', [
      ...prose('THE CONSOLE', ['Say ! in chat, or click [Console] on the welcome line.',
        'Every word is a click: a wing tab, a chamber, an option, then Run, Stage, Reset, Watch or Again.',
        'Typed: !g1, !g1 shape Grand, !run g1, !reset g1, !go range, !book.']),
      ...prose('GETTING ABOUT', ['Each wing is reached by what it tests: the gate north in the atrium, the ring pad east, the beam pad west, the mirror south.',
        'The tp plates under the balcony always work. A Go in this book moves you.',
        'This book is replaced after every run. !book gives you one if you have none.']),
    ]);
    // Contents first (as many pages as it takes): each entry turns to its section's first page.
    const contents = (first) => {
      let page = first;
      return paginate([{ text: `${TITLE.toUpperCase()}\n\n`, bold: true }], sections.map((s) => {
        const at = page;
        page += s.pages.length;
        return [{ text: s.name, color: 'dark_blue', underlined: true, click: { page: at }, hover: `page ${at}` }, { text: ` ${at}\n`, color: 'dark_gray' }];
      }));
    };
    let toc = contents(2);
    if (toc.length > 1) toc = contents(1 + toc.length);
    return { pages: [...toc, ...sections.flatMap((s) => s.pages)], sections };
  }

  /** The Inventory Slot the player keeps the Logbook in, and every slot in use: { book, used }. */
  async slots(player) {
    const r = await this.fac.srv.run(`data get entity ${player} Inventory`);
    const line = r.lines.find((l) => l.includes('has the following entity data:'));
    if (!line) return { book: null, used: new Set(), books: 0 };
    let items = [];
    try { items = text.parseSnbt(line.slice(line.indexOf('has the following entity data:') + 30).trim()); } catch { items = []; }
    const used = new Set(items.map((i) => Number(i.Slot)));
    const books = items.filter((i) => JSON.stringify(i).includes(MARKER));
    return { book: books.length ? Number(books[0].Slot) : null, used, books: books.length };
  }

  /**
   * Gives a player the Logbook: in the slot they keep it in, or (`fresh`) the first free hotbar
   * slot from the right, then the inventory; never on the floor. Returns the slot, or null.
   */
  async give(player, { fresh = true } = {}) {
    const { book, used } = await this.slots(player);
    let slot = book;
    if (slot === null) {
      if (!fresh) return null;
      slot = [8, 7, 6, 5, 4, 3, 2, 1, 0, ...Array.from({ length: 27 }, (_, i) => 9 + i)].find((n) => !used.has(n));
      if (slot === undefined) {
        await this.fac.console.tell(player, [{ text: 'No room for the Logbook: free a slot and say !book.', color: 'gold' }]);
        return null;
      }
    }
    const { pages } = this.pages(player);
    const item = text.bookItem(this.fac.version, { title: TITLE, author: 'Probe', pages, marker: MARKER });
    const r = await this.fac.srv.run(`item replace entity ${player} ${slotArg(slot)} with ${item}`);
    if (r.errors.length) throw new Error(`the Logbook for ${player}: ${r.errors.join(' ')}`);
    this.holders.add(player);
    return slot;
  }

  /** After a run: every holder still online and still holding one gets a fresh copy in its slot. */
  async refresh() {
    for (const player of [...this.holders]) {
      const on = await this.fac.srv.run(`execute if entity @a[name=${player}]`);
      if (!on.lines.some((l) => /Test passed/.test(l))) { this.holders.delete(player); continue; }
      await this.give(player, { fresh: false }).catch((e) => this.fac.log(`  logbook ${player}: ${e.message}`));
    }
  }
}

/**
 * The pages of a written book as a client holds it (Mineflayer's item), as plain component
 * objects: NBT pages of JSON strings before 1.20.5, the written_book_content component after.
 */
function clientPages(item) {
  const nbt = require('prismarine-nbt');
  if (!item) return [];
  if (item.nbt) {
    const simple = nbt.simplify(item.nbt);
    return (simple.pages || []).map((p) => { try { return JSON.parse(p); } catch { return { text: p }; } });
  }
  const c = (item.components || []).find((x) => x.type === 'written_book_content');
  if (!c) return [];
  // A list of mixed strings and compounds arrives with each string as { "": text }.
  const tidy = (x) => {
    if (Array.isArray(x)) return x.map(tidy);
    if (x && typeof x === 'object') {
      const keys = Object.keys(x);
      if (keys.length === 1 && keys[0] === '') return { text: x[''] };
      return Object.fromEntries(keys.map((k) => [k, tidy(x[k])]));
    }
    return x;
  };
  return c.data.pages.map((p) => tidy(p.content && typeof p.content === 'object' && p.content.type ? nbt.simplify(p.content) : p.content));
}

/** Every click in a component: [{ text, action, target }] (target: the page or the command). */
function clicksIn(component, out = []) {
  if (!component || typeof component !== 'object') return out;
  const ev = component.click_event || component.clickEvent;
  if (ev) out.push({ text: component.text || '', action: ev.action, target: String(ev.page !== undefined ? ev.page : ev.command !== undefined ? ev.command : ev.value) });
  for (const x of component.extra || []) clicksIn(x, out);
  return out;
}

module.exports = { Logbook, MARKER, TITLE, PAGE_LINES, clientPages, clicksIn, slotArg, rowsOf, paginate };
