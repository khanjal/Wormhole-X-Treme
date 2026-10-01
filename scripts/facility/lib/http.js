'use strict';
// Plain local HTTP for the facility: Dynmap's web map, on this machine.

const http = require('http');

/** GETs a local URL's body as text, within `ms`; rejects on anything but 200. */
function httpText(url, ms = 10000) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (d) => { body += d; });
      res.on('end', () => (res.statusCode === 200 ? resolve(body) : reject(new Error(`HTTP ${res.statusCode}`))));
      res.on('error', reject);
    });
    req.on('error', reject);
    req.setTimeout(ms, () => req.destroy(new Error(`no answer in ${ms} ms`)));
  });
}

module.exports = { httpText };
