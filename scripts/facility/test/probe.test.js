'use strict';
// lib/probe.js's action-bar hearing without a server: a fake client stands in for the protocol
// stream and the same ChatMessage path Mineflayer's system_chat handler uses reads the result.
// Run with `npm test --prefix scripts/facility`.

const test = require('node:test');
const assert = require('node:assert');
const { EventEmitter } = require('events');
const registry = require('prismarine-registry')('1.20.4');
const ChatMessage = require('prismarine-chat')(registry);
const { hearActionBars } = require('../lib/probe');

/** A client wired like Mineflayer's chat plugin: systemChat becomes a 'message' on the bot. */
function fakeBot() {
  const client = new EventEmitter();
  const bot = new EventEmitter();
  const heard = [];
  client.on('systemChat', (d) => bot.emit('message', ChatMessage.fromNotch(d.formattedMessage), d.positionId === 2 ? 'game_info' : 'system'));
  bot.on('message', (m, position) => heard.push([m.toString(), position]));
  hearActionBars(client);
  return { client, heard };
}

test('an action_bar packet is heard as a game_info line, as a JSON string or as an NBT component', () => {
  const { client, heard } = fakeBot();
  client.emit('action_bar', { text: JSON.stringify({ text: 'Ring: 3' }) });
  client.emit('action_bar', { text: { type: 'compound', name: '', value: { text: { type: 'string', value: 'Ring: 2' } } } });
  assert.deepStrictEqual(heard, [['Ring: 3', 'game_info'], ['Ring: 2', 'game_info']]);
});
