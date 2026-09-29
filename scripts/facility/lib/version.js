'use strict';
// Minecraft version comparison across both numbering schemes (1.21.11 and 26.1.2 compare as
// numbers part by part, which puts every 26.x after every 1.x).

function parts(v) {
  return String(v).split('.').map((p) => Number.parseInt(p, 10) || 0);
}

function compare(a, b) {
  const x = parts(a);
  const y = parts(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d !== 0) return d;
  }
  return 0;
}

function atLeast(version, floor) {
  return compare(version, floor) >= 0;
}

module.exports = { compare, atLeast };
