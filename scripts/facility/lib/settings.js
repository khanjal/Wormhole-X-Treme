'use strict';
// Every setting the plugin has, read from its source (src/main/java/.../DefaultSettings.java):
// name, enum name, default and the config.yml group it is written under. The Systems audit runs
// against this list, so a setting added to the plugin is a setting the audit asks about.

const fs = require('fs');
const path = require('path');

const SOURCE = path.join(__dirname, '..', '..', '..', 'src', 'main', 'java', 'com', 'wormhole_xtreme', 'wormhole', 'config', 'DefaultSettings.java');

/** "GATE_SOUND_VOLUME" -> "gate-sound-volume". */
function kebab(key) {
  return key.toLowerCase().replace(/_/g, '-');
}

/** [{ key, name, value, group }] in the order config.yml writes them. */
function load(file = SOURCE) {
  const src = fs.readFileSync(file, 'utf8');
  const out = [];
  let group = null;
  for (const line of src.split(/\r?\n/)) {
    const g = /^\s*group\("([^"]+)"/.exec(line);
    if (g) group = g[1];
    const s = /new Setting\(ConfigKeys\.([A-Z0-9_]+),\s*("(?:[^"\\]|\\.)*"|[^,]+),/.exec(line);
    if (s) out.push({ key: s[1], name: kebab(s[1]), value: s[2].startsWith('"') ? JSON.parse(s[2]) : s[2].trim(), group });
  }
  return out;
}

module.exports = { load, kebab, SOURCE };
