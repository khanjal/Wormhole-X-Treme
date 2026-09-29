'use strict';
// The chat console: a tellraw menu whose every word runs `/trigger wx set <code>` as the
// player who clicked, and a typed `!` form of the same thing.
//
// Two things the design assumed and the spike showed otherwise (see docs/DEVELOPMENT.md):
//  - a server only sends score packets for an objective that sits in a display slot, so `wx`
//    is put in the sidebar of a team colour nobody is on: every client is sent the scores,
//    no client draws them;
//  - Mineflayer 4.39's `scoreUpdated` never fires from 1.20.3 (it tests a packet field that
//    no longer exists, and ignores reset_score), so the bot reads the raw packets.

const { EventEmitter } = require('events');
const text = require('./text');

const OBJECTIVE = 'wx';
// A team colour no one is put on: the display slot only exists so the server tracks `wx`.
const HIDDEN_SLOT = 'sidebar.team.dark_gray';

/** Codes are chamber * 1000 + option * 100 + value; this one table maps both ways. */
function encode({ chamber, option, value }) {
  if (option < 0 || option > 9 || value < 0 || value > 99) throw new Error(`code out of range: ${chamber}/${option}/${value}`);
  return chamber * 1000 + option * 100 + value;
}

function decode(code) {
  return { chamber: Math.floor(code / 1000), option: Math.floor((code % 1000) / 100), value: code % 100 };
}

/** Console commands that create the trigger objective and let everyone online use it. */
function setupCommands() {
  return [
    `scoreboard objectives add ${OBJECTIVE} trigger`,
    `scoreboard objectives setdisplay ${HIDDEN_SLOT} ${OBJECTIVE}`,
    `scoreboard players enable @a ${OBJECTIVE}`,
  ];
}

/** After a code is read: clear it and let the player trigger again. */
function rearmCommands(player) {
  return [
    `scoreboard players reset ${player} ${OBJECTIVE}`,
    `scoreboard players enable ${player} ${OBJECTIVE}`,
  ];
}

function triggerCommand(code) {
  return `/trigger ${OBJECTIVE} set ${code}`;
}

/**
 * A menu line: a title then one clickable word per choice; the current one is bracketed.
 * choices: [{ label, code, why, current }]
 */
function menu(version, title, choices) {
  const parts = [{ text: `${title} `, color: 'gray' }];
  for (const c of choices) {
    parts.push({
      text: c.current ? `[${c.label}]` : c.label,
      color: c.current ? 'gold' : 'aqua',
      click: { run: triggerCommand(c.code) },
      hover: c.why || `code ${c.code}`,
    }, ' ');
  }
  return `tellraw @a ${text.command(version, parts)}`;
}

/**
 * Listens on a Mineflayer bot for console input. Emits
 *   'code'  { player, code }  when a player's wx score is set;
 *   'typed' { player, line }  when a player says a line starting with `!`.
 */
function listen(bot) {
  const events = new EventEmitter();
  bot._client.on('scoreboard_score', (packet) => {
    if (packet.scoreName === OBJECTIVE) events.emit('code', { player: packet.itemName, code: packet.value });
  });
  bot.on('chat', (username, message) => {
    if (username !== bot.username && message.startsWith('!')) events.emit('typed', { player: username, line: message.slice(1).trim() });
  });
  return events;
}

module.exports = {
  OBJECTIVE, HIDDEN_SLOT, encode, decode, setupCommands, rearmCommands, triggerCommand, menu, listen,
};
