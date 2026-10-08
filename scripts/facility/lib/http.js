'use strict';
// Plain local HTTP for the facility: the web maps (Dynmap, BlueMap, squaremap, Pl3xMap), on this
// machine.

const http = require('http');
const zlib = require('zlib');

/**
 * GETs a local URL's body as text, within `ms`; rejects on anything but 200. A gzipped body is
 * unpacked: BlueMap serves its files as it stores them, compressed or not.
 */
function httpText(url, ms = 10000) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, (res) => {
      const parts = [];
      res.on('data', (d) => { parts.push(d); });
      res.on('end', () => {
        if (res.statusCode !== 200) { reject(new Error(`HTTP ${res.statusCode}`)); return; }
        try {
          const raw = Buffer.concat(parts);
          resolve((/gzip/i.test(res.headers['content-encoding'] || '') ? zlib.gunzipSync(raw) : raw).toString('utf8'));
        } catch (e) {
          reject(e);
        }
      });
      res.on('error', reject);
    });
    req.on('error', reject);
    req.setTimeout(ms, () => req.destroy(new Error(`no answer in ${ms} ms`)));
  });
}

module.exports = { httpText };
