'use strict';
// A small zip writer and reader for design exports (lib/designmode.js), so neither the designer
// nor the maintainer needs a zip tool: deflate or store, no zip64 (an export over 4 GB is
// refused), one file in memory at a time.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  if (typeof zlib.crc32 === 'function') return zlib.crc32(buf) >>> 0;
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function dosTime(d) {
  const time = (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2);
  const date = ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate();
  return { time, date };
}

/** Every file under `dir`, as paths relative to it with forward slashes; `skip(rel)` leaves one out. */
function filesUnder(dir, skip = () => false, rel = '') {
  const out = [];
  for (const e of fs.readdirSync(path.join(dir, rel), { withFileTypes: true })) {
    const r = rel ? `${rel}/${e.name}` : e.name;
    if (skip(r)) continue;
    if (e.isDirectory()) out.push(...filesUnder(dir, skip, r));
    else if (e.isFile()) out.push(r);
  }
  return out;
}

/**
 * Writes a zip at `file` from `entries`: [{ name, from (a file path) | data (a Buffer) }]. Names
 * use forward slashes. Returns { files, bytes }.
 */
function writeZip(file, entries) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const fd = fs.openSync(file, 'w');
  const central = [];
  let offset = 0;
  const write = (buf) => { fs.writeSync(fd, buf); offset += buf.length; };
  try {
    for (const e of entries) {
      const data = e.data || fs.readFileSync(e.from);
      const name = Buffer.from(e.name.replace(/\\/g, '/'), 'utf8');
      const deflated = zlib.deflateRawSync(data, { level: 6 });
      const store = deflated.length >= data.length;
      const body = store ? data : deflated;
      const crc = crc32(data);
      const { time, date } = dosTime(e.mtime || new Date());
      if (offset + body.length + 30 + name.length > 0xfffffffe) throw new Error(`${file} would be over 4 GB, which this writer does not do`);
      const local = Buffer.alloc(30);
      local.writeUInt32LE(0x04034b50, 0);
      local.writeUInt16LE(20, 4);
      local.writeUInt16LE(0x0800, 6); // UTF-8 names
      local.writeUInt16LE(store ? 0 : 8, 8);
      local.writeUInt16LE(time, 10);
      local.writeUInt16LE(date, 12);
      local.writeUInt32LE(crc, 14);
      local.writeUInt32LE(body.length, 18);
      local.writeUInt32LE(data.length, 22);
      local.writeUInt16LE(name.length, 26);
      local.writeUInt16LE(0, 28);
      const at = offset;
      write(local);
      write(name);
      write(body);
      central.push({ name, crc, size: body.length, raw: data.length, method: store ? 0 : 8, time, date, at });
    }
    const start = offset;
    for (const c of central) {
      const h = Buffer.alloc(46);
      h.writeUInt32LE(0x02014b50, 0);
      h.writeUInt16LE(20, 4);
      h.writeUInt16LE(20, 6);
      h.writeUInt16LE(0x0800, 8);
      h.writeUInt16LE(c.method, 10);
      h.writeUInt16LE(c.time, 12);
      h.writeUInt16LE(c.date, 14);
      h.writeUInt32LE(c.crc, 16);
      h.writeUInt32LE(c.size, 20);
      h.writeUInt32LE(c.raw, 24);
      h.writeUInt16LE(c.name.length, 28);
      h.writeUInt32LE(c.at, 42);
      write(h);
      write(c.name);
    }
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(central.length, 8);
    end.writeUInt16LE(central.length, 10);
    end.writeUInt32LE(offset - start, 12);
    end.writeUInt32LE(start, 16);
    write(end);
  } finally {
    fs.closeSync(fd);
  }
  return { files: central.length, bytes: offset };
}

/** The entries of a zip: [{ name, read() -> Buffer }]. */
function readZip(file) {
  const buf = fs.readFileSync(file);
  let e = buf.length - 22;
  while (e >= 0 && buf.readUInt32LE(e) !== 0x06054b50) e--;
  if (e < 0) throw new Error(`${file} is not a zip`);
  const count = buf.readUInt16LE(e + 10);
  let p = buf.readUInt32LE(e + 16);
  const out = [];
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error(`${file}: a broken central directory`);
    const method = buf.readUInt16LE(p + 10);
    const crc = buf.readUInt32LE(p + 16);
    const size = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const at = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
    p += 46 + nameLen + extraLen + commentLen;
    out.push({
      name,
      read() {
        const start = at + 30 + buf.readUInt16LE(at + 26) + buf.readUInt16LE(at + 28);
        const body = buf.subarray(start, start + size);
        let data;
        if (method === 0) data = Buffer.from(body);
        else if (method === 8) data = zlib.inflateRawSync(body);
        else throw new Error(`${name}: compression method ${method} is not read here`);
        if (crc32(data) !== crc) throw new Error(`${name}: its checksum does not match`);
        return data;
      },
    });
  }
  return out;
}

/** Unpacks a zip into `dir`; refuses a name that would land outside it. Returns the names. */
function extractZip(file, dir) {
  const root = path.resolve(dir);
  const names = [];
  for (const e of readZip(file)) {
    if (e.name.endsWith('/')) continue;
    const to = path.resolve(root, e.name);
    if (!to.startsWith(root + path.sep)) throw new Error(`${file}: ${e.name} would land outside ${root}`);
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.writeFileSync(to, e.read());
    names.push(e.name);
  }
  return names;
}

module.exports = { crc32, filesUnder, writeZip, readZip, extractZip };
