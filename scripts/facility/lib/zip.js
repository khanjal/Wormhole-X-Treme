'use strict';
// A small zip writer and reader for design exports (lib/designmode.js), so neither the designer
// nor the maintainer needs a zip tool. Deflate or store, no Zip64: a zip of more than 65535
// entries or 4 GB is refused before it is written, and one using Zip64 is refused when read.
// A zip being read is untrusted: it is read a piece at a time, each entry inflated to no more
// than the size it declares (and never more than MAX_ENTRY), and no name may leave its folder.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const LIMIT = 0xffffffff;
const MAX_ENTRIES = 0xffff;
const MAX_ENTRY = 1024 * 1024 * 1024;

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
 * Whether a name is safe to unpack: relative, forward slashes only, no drive letter, no `..` or
 * empty segment, no control character.
 */
function safeName(name) {
  if (!name || name.length > 512 || /[\\\x00-\x1f]/.test(name) || name.startsWith('/') || /^[A-Za-z]:/.test(name)) return false;
  const parts = name.replace(/\/$/, '').split('/');
  return parts.every((p) => p && p !== '.' && p !== '..');
}

/**
 * Writes a zip at `file` from `entries`: [{ name, from (a file path) | data (a Buffer) }]. Names
 * use forward slashes. Returns { files, bytes }.
 */
function writeZip(file, entries) {
  if (entries.length > MAX_ENTRIES) throw new Error(`${file} would hold ${entries.length} entries; this writer does at most ${MAX_ENTRIES}`);
  for (const e of entries) if (!safeName(e.name)) throw new Error(`${e.name} is not a name a zip should hold`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const fd = fs.openSync(file, 'w');
  const central = [];
  let centralSize = 0;
  let offset = 0;
  const write = (buf) => { fs.writeSync(fd, buf); offset += buf.length; };
  let ok = false;
  try {
    for (const e of entries) {
      const data = e.data || fs.readFileSync(e.from);
      const name = Buffer.from(e.name, 'utf8');
      const deflated = zlib.deflateRawSync(data, { level: 6 });
      const store = deflated.length >= data.length;
      const body = store ? data : deflated;
      const crc = crc32(data);
      const { time, date } = dosTime(e.mtime || new Date());
      // This entry, every central record so far and its own, and the end record, all under 4 GB.
      if (data.length >= LIMIT || offset + 30 + name.length + body.length + centralSize + 46 + name.length + 22 > LIMIT) {
        throw new Error(`${file} would be over 4 GB, which this writer does not do`);
      }
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
      centralSize += 46 + name.length;
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
    ok = true;
  } finally {
    fs.closeSync(fd);
    if (!ok) fs.rmSync(file, { force: true });
  }
  return { files: central.length, bytes: offset };
}

function readAt(fd, pos, len) {
  const buf = Buffer.alloc(len);
  let got = 0;
  while (got < len) {
    const n = fs.readSync(fd, buf, got, len - got, pos + got);
    if (n === 0) throw new Error('the zip ends early');
    got += n;
  }
  return buf;
}

/**
 * Reads a zip's directory: { fd, entries: [{ name, method, crc, size, raw, at }], close() }, the
 * file held open until close(). Zip64, an entry over MAX_ENTRY and a broken directory are refused.
 */
function openZip(file) {
  const fd = fs.openSync(file, 'r');
  try {
    const length = fs.fstatSync(fd).size;
    const tailLen = Math.min(length, 22 + 0xffff);
    const tail = readAt(fd, length - tailLen, tailLen);
    let e = tail.length - 22;
    while (e >= 0 && tail.readUInt32LE(e) !== 0x06054b50) e--;
    if (e < 0) throw new Error(`${file} is not a zip`);
    if (e >= 20 && tail.readUInt32LE(e - 20) === 0x07064b50) throw new Error(`${file} is a Zip64 zip, which is not read here`);
    const count = tail.readUInt16LE(e + 10);
    const dirSize = tail.readUInt32LE(e + 12);
    const dirAt = tail.readUInt32LE(e + 16);
    if (count === 0xffff || dirSize === LIMIT || dirAt === LIMIT) throw new Error(`${file} is a Zip64 zip, which is not read here`);
    if (dirAt + dirSize > length) throw new Error(`${file}: its directory runs past its end`);
    const dir = readAt(fd, dirAt, dirSize);
    const entries = [];
    let p = 0;
    for (let i = 0; i < count; i++) {
      if (p + 46 > dir.length || dir.readUInt32LE(p) !== 0x02014b50) throw new Error(`${file}: a broken central directory`);
      const nameLen = dir.readUInt16LE(p + 28);
      const ent = {
        method: dir.readUInt16LE(p + 10), crc: dir.readUInt32LE(p + 16), size: dir.readUInt32LE(p + 20), raw: dir.readUInt32LE(p + 24),
        at: dir.readUInt32LE(p + 42), name: dir.toString('utf8', p + 46, p + 46 + nameLen),
      };
      p += 46 + nameLen + dir.readUInt16LE(p + 30) + dir.readUInt16LE(p + 32);
      if (ent.size === LIMIT || ent.raw === LIMIT || ent.at === LIMIT) throw new Error(`${file}: an entry uses Zip64, which is not read here`);
      if (ent.raw > MAX_ENTRY) throw new Error(`${file}: an entry says it unpacks to ${ent.raw} bytes, over the ${MAX_ENTRY} allowed`);
      entries.push(ent);
    }
    return { fd, entries, length, close: () => fs.closeSync(fd) };
  } catch (err) {
    fs.closeSync(fd);
    throw err;
  }
}

/** One entry's bytes: read from the open zip, inflated to at most its declared size, checked. */
function readEntry(z, ent) {
  if (ent.at + 30 + ent.size > z.length) throw new Error(`${ent.name}: runs past the end of the zip`);
  const head = readAt(z.fd, ent.at, 30);
  if (head.readUInt32LE(0) !== 0x04034b50) throw new Error(`${ent.name}: no local header where the directory says`);
  const start = ent.at + 30 + head.readUInt16LE(26) + head.readUInt16LE(28);
  if (start + ent.size > z.length) throw new Error(`${ent.name}: runs past the end of the zip`);
  const body = readAt(z.fd, start, ent.size);
  let data;
  if (ent.method === 0) data = body;
  else if (ent.method === 8) {
    try {
      data = zlib.inflateRawSync(body, { maxOutputLength: Math.max(ent.raw, 1) });
    } catch (e) {
      throw new Error(`${ent.name}: does not unpack to the ${ent.raw} bytes it declares (${e.code || e.message})`);
    }
  } else throw new Error(`${ent.name}: compression method ${ent.method} is not read here`);
  if (data.length !== ent.raw) throw new Error(`${ent.name}: unpacks to ${data.length} bytes, not the ${ent.raw} it declares`);
  if (crc32(data) !== ent.crc) throw new Error(`${ent.name}: its checksum does not match`);
  return data;
}

/** The entries of a zip: [{ name, method, read() -> Buffer }], each read() opening it again. */
function readZip(file) {
  const z = openZip(file);
  z.close();
  return z.entries.map((ent) => ({
    name: ent.name,
    method: ent.method,
    read() {
      const again = openZip(file);
      try { return readEntry(again, again.entries.find((x) => x.at === ent.at)); } finally { again.close(); }
    },
  }));
}

/**
 * Unpacks a zip into `dir`, the entries `want(name)` accepts (all by default). Every name must be
 * a plain relative path, or nothing is written. Returns the names written.
 */
function extractZip(file, dir, want = () => true) {
  const root = path.resolve(dir);
  const z = openZip(file);
  try {
    const bad = z.entries.find((e) => !safeName(e.name));
    if (bad) throw new Error(`${file}: the entry "${bad.name.replace(/[\x00-\x1f]/g, '?')}" is not a plain relative name`);
    const names = [];
    for (const e of z.entries) {
      if (e.name.endsWith('/') || !want(e.name)) continue;
      const to = path.resolve(root, e.name);
      if (!to.startsWith(root + path.sep)) throw new Error(`${file}: ${e.name} would land outside ${root}`);
      const data = readEntry(z, e);
      fs.mkdirSync(path.dirname(to), { recursive: true });
      fs.writeFileSync(to, data);
      names.push(e.name);
    }
    return names;
  } finally {
    z.close();
  }
}

module.exports = { crc32, filesUnder, safeName, writeZip, readZip, extractZip, openZip, MAX_ENTRY };
