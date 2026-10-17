/*
 * Minimal SoundFont 2 renderer: parses an .sf2 and renders a Standard MIDI File offline to stereo Float32 PCM.
 * Implements: preset/instrument zone matching (global zones, key/velocity ranges), additive preset generators, sample
 * addressing offsets, loop modes 0/1/3, root key / tuning / scale tuning / pitch correction, pitch bend (+-2 semitones),
 * volume envelope (delay, attack, hold, decay, sustain, release, key-number scaling of hold/decay), initial attenuation,
 * pan (generator + CC10), CC7 volume, CC11 expression, bank select (CC0, channel 10 = bank 128), all-notes-off on CC120/123.
 * Not implemented (kept simple): modulators other than the default velocity/CC curves, filter, LFOs, modulation envelope,
 * chorus/reverb, exclusive classes.
 */
(function (root) {
  'use strict';

  // ---------------------------------------------------------------- SF2 parsing
  function str4(b, o) { return String.fromCharCode(b[o], b[o + 1], b[o + 2], b[o + 3]); }

  function parseSf2(buf) {
    var b = new Uint8Array(buf);
    var dv = new DataView(b.buffer, b.byteOffset, b.byteLength);
    if (str4(b, 0) !== 'RIFF' || str4(b, 8) !== 'sfbk') throw new Error('Không phải file SoundFont 2 (.sf2)');
    var smpl = null, pd = {};
    function walk(o, e, cb) {
      while (o + 8 <= e) {
        var id = str4(b, o), l = dv.getUint32(o + 4, true);
        cb(id, o + 8, l);
        o += 8 + l + (l & 1);
      }
    }
    walk(12, b.length, function (id, o, l) {
      if (id !== 'LIST') return;
      var t = str4(b, o);
      walk(o + 4, o + l, function (id2, o2, l2) {
        if (t === 'sdta' && id2 === 'smpl') smpl = new Int16Array(b.buffer.slice(b.byteOffset + o2, b.byteOffset + o2 + (l2 & ~1)));
        if (t === 'pdta') pd[id2] = { o: o2, l: l2 };
      });
    });
    if (!smpl || !pd.phdr || !pd.shdr) throw new Error('SoundFont thiếu dữ liệu mẫu hoặc preset');

    function recs(name, size, fn) {
      var r = [], c = pd[name];
      for (var i = 0; i + size <= c.l; i += size) r.push(fn(c.o + i));
      return r;
    }
    var phdr = recs('phdr', 38, function (o) { return { preset: dv.getUint16(o + 20, true), bank: dv.getUint16(o + 22, true), bag: dv.getUint16(o + 24, true) }; });
    var pbag = recs('pbag', 4, function (o) { return dv.getUint16(o, true); });
    var pgen = recs('pgen', 4, function (o) { return { op: dv.getUint16(o, true), s: dv.getInt16(o + 2, true), lo: b[o + 2], hi: b[o + 3] }; });
    var inst = recs('inst', 22, function (o) { return dv.getUint16(o + 20, true); });
    var ibag = recs('ibag', 4, function (o) { return dv.getUint16(o, true); });
    var igen = recs('igen', 4, function (o) { return { op: dv.getUint16(o, true), s: dv.getInt16(o + 2, true), lo: b[o + 2], hi: b[o + 3] }; });
    var shdr = recs('shdr', 46, function (o) {
      return { start: dv.getUint32(o + 20, true), end: dv.getUint32(o + 24, true), ls: dv.getUint32(o + 28, true), le: dv.getUint32(o + 32, true),
        rate: dv.getUint32(o + 36, true), key: b[o + 40], corr: dv.getInt8(o + 41), type: dv.getUint16(o + 44, true) };
    });

    // a zone = { gens: Int32Array-ish map op->value (undefined = absent), kl, kh, vl, vh }
    function zones(bags, gens, from, to) {
      var out = [];
      for (var z = from; z < to; z++) {
        var g = {}, kl = 0, kh = 127, vl = 0, vh = 127;
        for (var k = bags[z]; k < bags[z + 1]; k++) {
          var e = gens[k];
          if (e.op === 43) { kl = e.lo; kh = e.hi; } else if (e.op === 44) { vl = e.lo; vh = e.hi; } else g[e.op] = e.s;
        }
        out.push({ g: g, kl: kl, kh: kh, vl: vl, vh: vh });
      }
      return out;
    }
    var instruments = [];
    for (var i = 0; i + 1 < inst.length; i++) {
      var iz = zones(ibag, igen, inst[i], inst[i + 1]);
      var glob = null;
      if (iz.length && iz[0].g[53] === undefined) glob = iz.shift();
      instruments.push({ glob: glob, zones: iz.filter(function (z) { return z.g[53] !== undefined; }) });
    }
    var presets = {};
    for (var p = 0; p + 1 < phdr.length; p++) {
      var pz = zones(pbag, pgen, phdr[p].bag, phdr[p + 1].bag);
      var pg = null;
      if (pz.length && pz[0].g[41] === undefined) pg = pz.shift();
      presets[phdr[p].bank * 128 + phdr[p].preset] = { glob: pg, zones: pz.filter(function (z) { return z.g[41] !== undefined; }) };
    }
    return { smpl: smpl, shdr: shdr, instruments: instruments, presets: presets };
  }

  // generators that are not added from the preset level (SF2 8.5: index/range/sample-address generators)
  var NOT_ADDITIVE = { 0: 1, 1: 1, 2: 1, 3: 1, 4: 1, 12: 1, 41: 1, 43: 1, 44: 1, 45: 1, 46: 1, 47: 1, 50: 1, 53: 1, 54: 1, 57: 1, 58: 1 };
  var DEFAULTS = { 8: 13500, 21: -12000, 23: -12000, 25: -12000, 26: -12000, 27: -12000, 28: -12000, 30: -12000,
    33: -12000, 34: -12000, 35: -12000, 36: -12000, 38: -12000, 56: 100, 58: -1, 46: -1, 47: -1 };

  function gen(z, op) {
    if (z.local[op] !== undefined) return z.local[op];
    if (z.iglob && z.iglob[op] !== undefined) return z.iglob[op];
    return DEFAULTS[op] !== undefined ? DEFAULTS[op] : 0;
  }

  /** voices to start for (bank, program, key, vel): list of resolved generator tables */
  function findZones(sf, bank, program, key, vel) {
    var pr = sf.presets[bank * 128 + program] || sf.presets[(bank === 128 ? 128 : 0) * 128 + program] ||
      sf.presets[bank === 128 ? 128 * 128 : program] || sf.presets[0];
    if (!pr) return [];
    var out = [];
    pr.zones.forEach(function (pz) {
      if (key < pz.kl || key > pz.kh || vel < pz.vl || vel > pz.vh) return;
      var ins = sf.instruments[pz.g[41]];
      if (!ins) return;
      ins.zones.forEach(function (iz) {
        if (key < iz.kl || key > iz.kh || vel < iz.vl || vel > iz.vh) return;
        var t = { local: iz.g, iglob: ins.glob ? ins.glob.g : null };
        var r = {};
        for (var op = 0; op < 61; op++) {
          var v = gen(t, op);
          if (!NOT_ADDITIVE[op]) {
            var add = pz.g[op] !== undefined ? pz.g[op] : (pr.glob && pr.glob.g[op] !== undefined ? pr.glob.g[op] : 0);
            v += add;
          }
          r[op] = v;
        }
        out.push(r);
      });
    });
    return out;
  }

  // ---------------------------------------------------------------- MIDI parsing
  function parseMidi(bytes) {
    var b = bytes, p = 0;
    function u32() { var v = (b[p] << 24 | b[p + 1] << 16 | b[p + 2] << 8 | b[p + 3]) >>> 0; p += 4; return v; }
    function u16() { var v = b[p] << 8 | b[p + 1]; p += 2; return v; }
    function vlq() { var v = 0, c; do { c = b[p++]; v = (v << 7) | (c & 0x7f); } while (c & 0x80); return v; }
    if (String.fromCharCode(b[0], b[1], b[2], b[3]) !== 'MThd') throw new Error('Không phải MIDI');
    p = 4;
    var hl = u32(), fmt = u16(), ntr = u16(), div = u16();
    p = 8 + hl;
    if (div & 0x8000) throw new Error('MIDI SMPTE chưa hỗ trợ');
    var evs = [];
    for (var t = 0; t < ntr && p + 8 <= b.length; t++) {
      var id = String.fromCharCode(b[p], b[p + 1], b[p + 2], b[p + 3]); p += 4;
      var len = u32(), end = p + len, tick = 0, run = 0, seq = 0;
      if (id !== 'MTrk') { p = end; continue; }
      while (p < end) {
        tick += vlq();
        var st = b[p];
        if (st & 0x80) p++; else st = run;
        if (st === 0xff) {
          var ty = b[p++], l = vlq();
          if (ty === 0x51) evs.push({ tick: tick, tempo: (b[p] << 16) | (b[p + 1] << 8) | b[p + 2], seq: seq++, tr: t });
          p += l;
        } else if (st === 0xf0 || st === 0xf7) {
          p += vlq();
        } else {
          run = st;
          var hi = st & 0xf0, d1 = b[p++], d2 = (hi === 0xc0 || hi === 0xd0) ? 0 : b[p++];
          evs.push({ tick: tick, st: st, d1: d1, d2: d2, seq: seq++, tr: t });
        }
      }
      p = end;
    }
    evs.sort(function (x, y) { return x.tick - y.tick || x.tr - y.tr || x.seq - y.seq; });
    // ticks -> seconds through the tempo map
    var tempo = 500000, lastTick = 0, sec = 0, out = [], lastSec = 0;
    evs.forEach(function (e) {
      sec += (e.tick - lastTick) * tempo / 1e6 / div;
      lastTick = e.tick;
      if (e.tempo !== undefined) { tempo = e.tempo; return; }
      e.time = sec;
      out.push(e);
      lastSec = sec;
    });
    return { events: out, length: lastSec, format: fmt };
  }

  // ---------------------------------------------------------------- renderer
  function tc2s(tc) { return tc <= -12000 ? 0 : Math.pow(2, tc / 1200); }

  function render(sf, midiBytes, rate, onProgress) {
    var mid = parseMidi(midiBytes);
    var tailSec = 2;
    var total = Math.ceil((mid.length + tailSec) * rate);
    var L = new Float32Array(total), R = new Float32Array(total);
    var ch = [];
    for (var c = 0; c < 16; c++) ch.push({ prog: 0, bank: c === 9 ? 128 : 0, vol: 100, expr: 127, pan: 64, bend: 0 });
    var voices = [];
    var BLOCK = 64;

    function noteOn(c, key, vel) {
      var C = ch[c];
      var bank = c === 9 ? 128 : C.bank;
      findZones(sf, bank, C.prog, key, vel).forEach(function (g) {
        var s = sf.shdr[g[53]];
        if (!s) return;
        var start = s.start + g[0] + g[4] * 32768, end = s.end + g[1] + g[12] * 32768;
        var ls = s.ls + g[2] + g[45] * 32768, le = s.le + g[3] + g[50] * 32768;
        var rootKey = g[58] >= 0 ? g[58] : s.key;
        var cents = (key - rootKey) * g[56] + g[51] * 100 + g[52] + s.corr;
        var holdTc = g[35] + (60 - key) * g[39], decayTc = g[36] + (60 - key) * g[40];
        voices.push({
          c: c, key: key, vel: vel,
          pos: start, start: start, end: end, ls: ls, le: le, mode: g[54] & 3,
          step: Math.pow(2, cents / 1200) * s.rate / rate,
          atten: Math.max(0, g[48]) / 10,                               // dB
          pan: Math.max(-500, Math.min(500, g[17])) / 1000,
          delay: tc2s(g[33]), attack: tc2s(g[34]), hold: tc2s(holdTc), decay: tc2s(decayTc),
          sustainDb: Math.max(0, Math.min(1440, g[37])) / 10, release: Math.max(0.001, tc2s(g[38])),
          t: 0, released: false, relT: 0, relStartDb: 0, curDb: 0, dead: false
        });
      });
    }
    function noteOff(c, key) {
      voices.forEach(function (v) {
        if (v.c === c && v.key === key && !v.released) { v.released = true; v.relT = 0; v.relStartDb = v.curDb; }
      });
    }

    // envelope level in dB attenuation (0 = full) and linear attack factor at voice time t
    function envDb(v) {
      var t = v.t;
      if (t < v.delay) return 200;
      t -= v.delay;
      if (t < v.attack) return -20 * Math.log10(Math.max(1e-5, t / v.attack));
      t -= v.attack;
      if (t < v.hold) return 0;
      t -= v.hold;
      var db = v.decay > 0 ? 96 * t / v.decay : v.sustainDb;
      return Math.min(db, v.sustainDb);
    }

    var ei = 0, evs = mid.events;
    var frame = 0;
    var lastReport = 0, blocks = 0;
    while (frame < total) {
      var nowSec = frame / rate;
      while (ei < evs.length && evs[ei].time <= nowSec) {
        var e = evs[ei++], c2 = e.st & 15, hi = e.st & 0xf0, C2 = ch[c2];
        if (hi === 0x90 && e.d2 > 0) noteOn(c2, e.d1, e.d2);
        else if (hi === 0x80 || hi === 0x90) noteOff(c2, e.d1);
        else if (hi === 0xc0) C2.prog = e.d1;
        else if (hi === 0xe0) C2.bend = ((e.d2 << 7 | e.d1) - 8192) / 8192 * 200;   // cents, +-2 semitones
        else if (hi === 0xb0) {
          if (e.d1 === 0) C2.bank = e.d2 === 0 || c2 === 9 ? (c2 === 9 ? 128 : 0) : e.d2;
          else if (e.d1 === 7) C2.vol = e.d2;
          else if (e.d1 === 11) C2.expr = e.d2;
          else if (e.d1 === 10) C2.pan = e.d2;
          else if (e.d1 === 120 || e.d1 === 123) voices.forEach(function (v) { if (v.c === c2) { v.released = true; v.relT = 0; v.relStartDb = v.curDb; } });
        }
      }
      var n = Math.min(BLOCK, total - frame);
      if (ei < evs.length) {
        var nextF = Math.ceil(evs[ei].time * rate);
        if (nextF > frame && nextF - frame < n) n = nextF - frame;
      }
      for (var vi = 0; vi < voices.length; vi++) {
        var v = voices[vi];
        if (v.dead) continue;
        var C3 = ch[v.c];
        var db;
        if (!v.released) db = envDb(v);
        else {
          db = v.relStartDb + 96 * v.relT / v.release;
          v.relT += n / rate;
        }
        v.curDb = v.released ? db : envDb(v);
        if (db >= 96) { if (v.released) { v.dead = true; continue; } }
        var vg = (v.vel / 127) * (v.vel / 127) * (C3.vol / 127) * (C3.vol / 127) * (C3.expr / 127) * (C3.expr / 127);
        var gain = Math.pow(10, -(db + v.atten) / 20) * vg * 0.3;
        var pan = Math.max(-0.5, Math.min(0.5, v.pan + (C3.pan - 64) / 127));
        var gl = gain * Math.cos((pan + 0.5) * Math.PI / 2), gr = gain * Math.sin((pan + 0.5) * Math.PI / 2);
        var step = v.step * Math.pow(2, C3.bend / 1200);
        var smpl = sf.smpl, pos = v.pos, end = v.end, ls = v.ls, le = v.le;
        var loop = v.mode === 1 || (v.mode === 3 && !v.released);
        var g0 = v.prevGl === undefined ? gl : v.prevGl, g1 = v.prevGr === undefined ? gr : v.prevGr;
        var dgl = (gl - g0) / n, dgr = (gr - g1) / n;
        for (var k = 0; k < n; k++) {
          if (loop && le > ls && pos >= le) pos -= (le - ls);
          if (pos >= end - 1) { v.dead = true; break; }
          var ip = pos | 0, fr = pos - ip;
          var s0 = smpl[ip], s1 = smpl[ip + 1];
          var x = (s0 + (s1 - s0) * fr) / 32768;
          g0 += dgl; g1 += dgr;
          L[frame + k] += x * g0;
          R[frame + k] += x * g1;
          pos += step;
        }
        v.pos = pos;
        v.prevGl = gl; v.prevGr = gr;
        v.t += n / rate;
      }
      if ((++blocks & 15) === 0 && voices.length > 0) voices = voices.filter(function (v) { return !v.dead; });
      frame += n;
      if (onProgress && frame - lastReport > rate) { lastReport = frame; onProgress(frame / total); }
    }
    // trim trailing silence (keep 0.3 s), normalise only when clipping
    var last = total - 1;
    while (last > 0 && Math.abs(L[last]) < 1e-4 && Math.abs(R[last]) < 1e-4) last--;
    var len = Math.min(total, last + Math.floor(rate * 0.3));
    var peak = 0;
    for (var i = 0; i < len; i++) { var a = Math.abs(L[i]), bb = Math.abs(R[i]); if (a > peak) peak = a; if (bb > peak) peak = bb; }
    // normalise every rendering to a 0.9 peak (gain capped at x20 so near-silent files are not blown up)
    if (peak > 0) { var sc = Math.min(20, 0.9 / peak); for (i = 0; i < len; i++) { L[i] *= sc; R[i] *= sc; } }
    return { left: L.subarray(0, len), right: R.subarray(0, len), rate: rate, notes: evs.filter(function (e) { return (e.st & 0xf0) === 0x90 && e.d2 > 0; }).length, peak: peak };
  }

  var api = { parseSf2: parseSf2, parseMidi: parseMidi, render: render };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Sf2Synth = api;
})(this);
