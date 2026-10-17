/*
 * SMAF (MMF) -> Standard MIDI File / WAV.
 * Line-by-line port of hl4.core.Smaf (HL4_project_2026-09-27j, hl4j/src/core/hl4/core/Smaf.java), which follows dlunch's smaf-player mapping.
 * Java semantics are kept: signed bytes masked with & 0xFF, integer division truncates toward zero, and any out-of-range read aborts the
 * conversion (Java's ArrayIndexOutOfBounds inside convert()'s catch) instead of reading undefined.
 */
(function (root) {
  'use strict';

  function OOB() { var e = new Error('out of range'); e.oob = true; return e; }
  function u8(d, i) { if (i < 0 || i >= d.length) throw OOB(); return d[i]; }
  function s8(d, i) { return (u8(d, i) << 24) >> 24; }
  function be32(d, o) { return ((u8(d, o) << 24) | (u8(d, o + 1) << 16) | (u8(d, o + 2) << 8) | u8(d, o + 3)) | 0; }
  function be16(d, o) { return (u8(d, o) << 8) | u8(d, o + 1); }
  function idiv(a, b) { if (b === 0) throw new Error('/ by zero'); return (a / b) | 0; }
  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  function timebase(raw) {
    switch (raw) {
      case 0: return 1; case 1: return 2; case 2: return 4; case 3: return 5;
      case 0x10: return 10; case 0x11: return 20; case 0x12: return 40; case 0x13: return 50;
      default: return 1;
    }
  }

  var MAX_CH = 64;
  var MELODY_ORDER = [0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15];
  var SHORT_EXPR = [0x00, 0x00, 0x1f, 0x27, 0x2f, 0x37, 0x3f, 0x47, 0x4f, 0x57, 0x5f, 0x67, 0x6f, 0x77, 0x7f];
  var SHORT_MOD = [0x00, 0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38, 0x40, 0x48, 0x50, 0x60, 0x70, 0x7f];
  var SCALE = [230, 230, 230, 230, 307, 409, 512, 614];

  function Conv() {
    this.events = [];
    this.seqCounter = 0;
    this.endTime = 0;
    this.pcm16 = null;
    this.pcmRate = 0;
    this.formatType = 0;
    this.channelTypes = new Int32Array(MAX_CH);
    this.programs = new Int32Array(MAX_CH);
    this.volumes = new Int32Array(MAX_CH);
    this.expressions = new Int32Array(MAX_CH);
    this.velocities = new Int32Array(MAX_CH);
    this.bankMsb = new Int32Array(MAX_CH);
    this.bankLsb = new Int32Array(MAX_CH);
    this.forcedRhythm = new Array(MAX_CH);
    this.realMap = new Int32Array(MAX_CH);
    this.handyOffset = 0;
    this.pos = 0;
  }

  Conv.prototype.add = function (time, order, msg) {
    this.events.push({ time: time, order: order, seq: this.seqCounter++, msg: msg });
  };
  Conv.prototype.add3 = function (time, order, a, b, c) { this.add(time, order, [a & 0xff, b & 0xff, c & 0xff]); };

  Conv.prototype.resetToneMap = function () {
    for (var i = 0; i < MAX_CH; i++) {
      this.channelTypes[i] = 2; this.programs[i] = 0; this.volumes[i] = 100; this.expressions[i] = 127;
      this.velocities[i] = 64; this.bankMsb[i] = 0; this.bankLsb[i] = 0; this.forcedRhythm[i] = false; this.realMap[i] = -1;
    }
  };

  Conv.prototype.scoreTrack = function (d, o, len) {
    var end = o + len;
    this.formatType = u8(d, o);
    var tbD = timebase(u8(d, o + 2)), tbG = timebase(u8(d, o + 3));
    var p = o + 4;
    this.resetToneMap();
    var handy = this.formatType === 0;
    var i;
    if (handy) {
      var st = be16(d, p);
      p += 2;
      for (i = 0; i < 4; i++) {
        var x = (st >> ((3 - i) * 4)) & 0xF;
        this.channelTypes[this.handyOffset + i] = (x & 3) === 3 ? 3 : 1;
        this.velocities[this.handyOffset + i] = 127;
      }
    } else {
      for (i = 0; i < 16; i++) {
        var t = u8(d, p + i) & 3;
        this.channelTypes[i] = t === 1 ? 1 : (t === 3 ? 3 : 2);
      }
      p += 16;
    }
    while (p + 8 <= end) {
      var len2 = be32(d, p + 4);
      var body = p + 8;
      if (body + len2 > end) len2 = end - body;
      if (u8(d, p) === 0x4d && u8(d, p + 1) === 0x74 && u8(d, p + 2) === 0x73 && u8(d, p + 3) === 0x71) {   // "Mtsq"
        var seq, so, sl;
        if (this.formatType === 1) {
          seq = huffman(d, body, len2);
          so = 0;
          sl = seq === null ? 0 : seq.length;
        } else {
          seq = d; so = body; sl = len2;
        }
        if (seq !== null) {
          if (handy) this.sequenceHandy(seq, so, sl, tbD, tbG);
          else this.sequenceMobile(seq, so, sl, tbD, tbG);
        }
      }
      p = body + len2;
    }
    if (handy) this.handyOffset += 4;
  };

  Conv.prototype.pseudo = function (ch) { return this.formatType === 0 ? Math.min(ch, MAX_CH - 1) : (ch & 15); };

  Conv.prototype.isRhythm = function (ch) {
    ch = this.pseudo(ch);
    return this.channelTypes[ch] === 3 || this.forcedRhythm[ch] || this.bankMsb[ch] === 0x7d;
  };

  Conv.prototype.realChannel = function (ch) {
    ch = this.pseudo(ch);
    if (this.isRhythm(ch)) return 9;
    if (this.realMap[ch] >= 0) return this.realMap[ch];
    var used = new Array(16);
    for (var u = 0; u < 16; u++) used[u] = false;
    used[9] = true;
    for (var i = 0; i < MAX_CH; i++) if (this.realMap[i] >= 0) used[this.realMap[i]] = true;
    var r = ch & 15;
    for (i = 0; i < MELODY_ORDER.length; i++) {
      if (!used[MELODY_ORDER[i]]) { r = MELODY_ORDER[i]; break; }
    }
    this.realMap[ch] = r;
    return r;
  };

  Conv.prototype.mapProgram = function (ch, program) {
    ch = this.pseudo(ch);
    var msb = this.bankMsb[ch], lsb = this.bankLsb[ch], pr = program & 0x7F;
    if (msb === 0x7c && lsb === 0x01) {
      switch (pr) {
        case 0x22: return 81; case 0x70: return 30; case 0x46: return 84;
        case 0x21: return 33; case 0x6a: return 87; case 0x62: return 98;
      }
    }
    if (msb === 0x7d && lsb === 0 && pr === 2) return 0;
    return pr;
  };

  Conv.prototype.mapNote = function (ch, note) {
    var ci = this.pseudo(ch);
    if (this.formatType === 0) {
      if (this.isRhythm(ch)) return Math.min(this.programs[ci], 127);
      return clamp(note + 36, 0, 127);
    }
    note = clamp(note, 0, 127);
    if (!this.isRhythm(ch)) return note;
    switch (note) {
      case 0x12: return 45; case 0x1a: return 41; case 0x1f: return 47;
      case 0x4d: return 50; case 0x54: return 43; case 0x59: return 48;
    }
    return note;
  };

  function vlq(d, P) {
    var b = u8(d, P.p++);
    if ((b & 0x80) === 0) return b;
    var r = b & 0x7F;
    for (;;) {
      b = u8(d, P.p++);
      r = (r << 7) | (b & 0x7F);
      if ((b & 0x80) === 0) return r;
    }
  }

  function handyVlq(d, P) {
    var f = u8(d, P.p++);
    if ((f & 0x80) === 0) return f;
    var s = u8(d, P.p++);
    return (((f & 0x7F) + 1) << 7) | s;
  }

  Conv.prototype.note = function (time, ch, note, vel, gate) {
    var ci = this.pseudo(ch);
    var n = this.mapNote(ch, note);
    var v;
    if (this.formatType === 0 && this.channelTypes[ci] === 3) {
      v = clamp(idiv(this.volumes[ci] * this.expressions[ci], this.expressions[ci] < 64 ? 102 : 100), 1, 127);
    } else if (vel >= 0) {
      v = Math.min(vel, 127);
      this.velocities[ci] = v;
    } else v = this.velocities[ci];
    var rc = this.realChannel(ch);
    this.add3(time, 30, 0x90 | rc, n, Math.max(1, v));
    this.add3(time + gate, 20, 0x80 | rc, n, 0);
    if (time + gate > this.endTime) this.endTime = time + gate;
  };

  Conv.prototype.sequenceMobile = function (d, o, len, tbD, tbG) {
    var end = o + len;
    var P = { p: o };
    var now = 0;
    var octave = new Int32Array(MAX_CH);
    while (P.p < end) {
      var dur = vlq(d, P);
      now += dur * tbD;
      var st = u8(d, P.p++);
      var hi = st & 0xF0, ch = st & 0x0F, nt, gate, ci;
      if (hi === 0x80) {
        nt = u8(d, P.p++);
        gate = vlq(d, P);
        this.note(now, ch, nt + octave[ch] * 12, -1, gate * tbG);
      } else if (hi === 0x90) {
        nt = u8(d, P.p++);
        var vel = u8(d, P.p++);
        gate = vlq(d, P);
        this.note(now, ch, nt + octave[ch] * 12, vel, gate * tbG);
      } else if (hi === 0xA0) {
        P.p += 2;
      } else if (hi === 0xB0) {
        var ctl = u8(d, P.p++) & 0x7F, val = u8(d, P.p++) & 0x7F;
        ci = this.pseudo(ch);
        if (ctl === 0) this.bankMsb[ci] = val;
        else if (ctl === 32) this.bankLsb[ci] = val;
        else if (ctl === 7) this.volumes[ci] = val;
        this.add3(now, 5, 0xB0 | this.realChannel(ch), ctl, val);
      } else if (hi === 0xC0) {
        var pr = u8(d, P.p++) & 0x7F;
        ci = this.pseudo(ch);
        if (this.bankMsb[ci] === 0x7d) this.forcedRhythm[ci] = true;
        var mp = this.isRhythm(ch) ? 0 : this.mapProgram(ch, pr);
        this.programs[ci] = mp;
        this.add(now, 6, [(0xC0 | this.realChannel(ch)) & 0xff, mp & 0xff]);
      } else if (hi === 0xD0) {
        P.p += 1;
      } else if (hi === 0xE0) {
        var lsb = u8(d, P.p++) & 0x7F, msb = u8(d, P.p++) & 0x7F;
        this.add3(now, 5, 0xE0 | this.realChannel(ch), lsb, msb);
      } else if (st === 0xF0) {
        var l = vlq(d, P);
        P.p += l;                 // Yamaha specific exclusive: not forwarded
      } else if (st === 0xFF) {
        var b2 = u8(d, P.p++);
        if (b2 === 0x2F) { P.p++; break; }
      }
    }
    if (now > this.endTime) this.endTime = now;
  };

  Conv.prototype.sequenceHandy = function (d, o, len, tbD, tbG) {
    var end = o + len;
    var P = { p: o };
    var now = 0;
    var octave = new Int32Array(MAX_CH);
    while (P.p < end) {
      if (P.p + 4 <= end && u8(d, P.p) === 0 && u8(d, P.p + 1) === 0 && u8(d, P.p + 2) === 0 && u8(d, P.p + 3) === 0) break;
      var dur = handyVlq(d, P);
      now += dur * tbD;
      var st = u8(d, P.p++);
      var ch, ci, v;
      if (st !== 0 && st !== 0xFF) {
        ch = this.handyOffset + ((st >> 6) & 3);
        var oct = (st >> 4) & 3, voice = st & 15;
        var gate = handyVlq(d, P);
        this.note(now, ch, oct * 12 + voice + octave[Math.min(ch, MAX_CH - 1)] * 12, -1, gate * tbG);
      } else if (st === 0) {
        var nb = u8(d, P.p++);
        ch = this.handyOffset + ((nb >> 6) & 3);
        ci = this.pseudo(ch);
        var et = nb & 0x3F;
        if (et === 0) {
          P.p++;
        } else if (et >= 1 && et <= 0x0e) {
          this.expressions[ci] = SHORT_EXPR[et];
          if (!this.isRhythm(ch)) this.add3(now, 5, 0xB0 | this.realChannel(ch), 7, Math.min(127, idiv(this.volumes[ci] * this.expressions[ci], 127)));
        } else if (et >= 0x11 && et <= 0x1e) {
          v = Math.min(0x3fff, idiv((et - 0x10) * 16384, 16));
          this.add3(now, 5, 0xE0 | this.realChannel(ch), v & 0x7F, (v >> 7) & 0x7F);
        } else if (et >= 0x21 && et <= 0x2e) {
          this.add3(now, 5, 0xB0 | this.realChannel(ch), 1, SHORT_MOD[et - 0x20]);
        } else {
          var val = u8(d, P.p++);
          switch (et) {
            case 0x30:
              if (this.channelTypes[ci] === 3) {
                this.programs[ci] = Math.min(val, 127);
                this.add(now, 6, [0xC9, 0]);
              } else {
                if (this.bankMsb[ci] === 0x7d) this.forcedRhythm[ci] = true;
                var mp = this.isRhythm(ch) ? 0 : this.mapProgram(ch, val);
                this.programs[ci] = mp;
                this.add(now, 6, [(0xC0 | this.realChannel(ch)) & 0xff, mp & 0xff]);
              }
              break;
            case 0x31:
              if ((val & 0x80) !== 0) this.forcedRhythm[ci] = true;
              this.bankMsb[ci] = val & 0x7F;
              this.add3(now, 5, 0xB0 | this.realChannel(ch), 0, val & 0x7F);
              break;
            case 0x32:
              if (val <= 4) octave[Math.min(ch, MAX_CH - 1)] = val;
              else if (val >= 0x81 && val <= 0x84) octave[Math.min(ch, MAX_CH - 1)] = -(val - 0x80);
              break;
            case 0x33:
              this.add3(now, 5, 0xB0 | this.realChannel(ch), 1, val & 0x7F);
              break;
            case 0x34:
              v = clamp(8192 + (val - 128) * 64, 0, 16383);
              this.add3(now, 5, 0xE0 | this.realChannel(ch), v & 0x7F, (v >> 7) & 0x7F);
              break;
            case 0x36:
            case 0x3b:
              this.expressions[ci] = Math.min(val, 127);
              if (!this.isRhythm(ch)) this.add3(now, 5, 0xB0 | this.realChannel(ch), 7, Math.min(127, idiv(this.volumes[ci] * this.expressions[ci], 127)));
              break;
            case 0x37:
              this.volumes[ci] = Math.min(val, 127);
              if (this.isRhythm(ch)) this.add3(now, 5, 0xB9, 7, 100);
              else this.add3(now, 5, 0xB0 | this.realChannel(ch), 7, Math.min(127, idiv(this.volumes[ci] * this.expressions[ci], 127)));
              break;
            case 0x3a:
              this.add3(now, 5, 0xB0 | this.realChannel(ch), 10, val & 0x7F);
              break;
            default:
              break;
          }
        }
      } else {  // 0xFF
        var nb2 = u8(d, P.p++);
        if (nb2 === 0xF0) {
          while (P.p < end && u8(d, P.p) !== 0xF7) P.p++;
          P.p++;
        }
      }
    }
    if (now > this.endTime) this.endTime = now;
  };

  function bit(s, bp, limit) {
    if (bp.p >= limit) return -1;
    var b = (s8(s, bp.p >> 3) >> (7 - (bp.p & 7))) & 1;
    bp.p++;
    return b;
  }

  function readTree(s, bp, limit, left, right, avail) {
    var b = bit(s, bp, limit);
    if (b < 0) return -1;
    if (b === 1) {
      var node = avail.n++;
      if (avail.n > 511) return -1;
      left[node] = readTree(s, bp, limit, left, right, avail);
      right[node] = readTree(s, bp, limit, left, right, avail);
      if (left[node] < 0 || right[node] < 0) return -1;
      return node;
    }
    var v = 0;
    for (var i = 0; i < 8; i++) {
      var x = bit(s, bp, limit);
      if (x < 0) return -1;
      v = (v << 1) | x;
    }
    return v;
  }

  function huffman(src, off, len) {
    var decodedLen = be32(src, off);
    if (decodedLen < 0) throw new Error('NegativeArraySize');
    var bp = { p: (off + 4) * 8 };
    var limit = (off + len) * 8;
    var left = new Int32Array(511), right = new Int32Array(511);
    var avail = { n: 256 };
    var root = readTree(src, bp, limit, left, right, avail);
    if (root < 0) return null;
    var out = new Uint8Array(decodedLen);
    for (var i = 0; i < decodedLen; i++) {
      var node = root;
      while (node >= 256) {
        var b = bit(src, bp, limit);
        if (b < 0) return null;
        node = b === 1 ? right[node] : left[node];
      }
      out[i] = node & 0xff;
    }
    return out;
  }

  Conv.prototype.pcmTrack = function (d, o, len) {
    var end = o + len;
    var waveType = be16(d, o + 2);
    var fmt = (waveType >> 12) & 7;
    var rateCode = (waveType >> 8) & 15;
    var rate;
    switch (rateCode) {
      case 0: rate = 4000; break; case 1: rate = 8000; break; case 2: rate = 11025; break; case 3: rate = 22050; break;
      default: rate = 44100; break;
    }
    var p = o + 6;
    var firstWave = null, seqFirst = -1;
    var waves = new Array(64);
    while (p + 8 <= end) {
      var l = be32(d, p + 4);
      var body = p + 8;
      if (body + l > end) l = end - body;
      if (u8(d, p) === 0x41 && u8(d, p + 1) === 0x77 && u8(d, p + 2) === 0x61) {             // "Awa"
        var n = u8(d, p + 3) & 0x3F;
        if (l < 0) throw new Error('NegativeArraySize');
        if (body + l > d.length) throw OOB();
        var w = d.slice(body, body + l);
        waves[n] = w;
        if (firstWave === null) firstWave = w;
      } else if (u8(d, p) === 0x41 && u8(d, p + 1) === 0x74 && u8(d, p + 2) === 0x73 && u8(d, p + 3) === 0x71) {  // "Atsq"
        var Q = { p: body };
        var qe = body + l;
        while (Q.p < qe && seqFirst < 0) {
          vlq(d, Q);
          var fb = u8(d, Q.p++);
          if (fb === 0xFF) {
            var sb = u8(d, Q.p++);
            if (sb === 0xF0) Q.p += u8(d, Q.p) + 1;
          } else if (fb !== 0) {
            seqFirst = fb & 0x3F;
            vlq(d, Q);
          } else {
            var sb2 = u8(d, Q.p++) & 0x3F;
            if (sb2 === 0x34 || sb2 === 0x36 || sb2 === 0x3b || sb2 === 0x37 || sb2 === 0x3a) Q.p++;
          }
        }
      }
      p = body + l;
    }
    var wv = seqFirst >= 0 && waves[seqFirst] ? waves[seqFirst] : firstWave;
    if (wv === null) return;
    var pcm, i;
    if (fmt === 1) pcm = adpcm(wv);
    else {
      pcm = new Int16Array(wv.length);
      for (i = 0; i < wv.length; i++) pcm[i] = ((wv[i] << 24) >> 24) << 8;
    }
    var b = new Uint8Array(pcm.length * 2);
    for (i = 0; i < pcm.length; i++) { b[i * 2] = pcm[i] & 0xff; b[i * 2 + 1] = (pcm[i] >> 8) & 0xff; }
    this.pcm16 = b;
    this.pcmRate = rate;
  };

  // Yamaha (MA-3/YMU) ADPCM, identical to ffmpeg's adpcm_yamaha
  function adpcm(data) {
    var out = new Int16Array(data.length * 2);
    var pred = 0, step = 127, k = 0;
    for (var i = 0; i < data.length; i++) {
      var b = data[i];
      for (var nib = 0; nib < 2; nib++) {
        var s = nib === 0 ? (b & 15) : (b >> 4);     // low nibble first
        var delta = s & 7;
        var diff = idiv(step * (1 + (delta << 1)), 8);
        pred = clamp((s & 8) !== 0 ? pred - diff : pred + diff, -32768, 32767);
        step = clamp((step * SCALE[delta]) >> 8, 127, 24576);
        out[k++] = pred;
      }
    }
    var fade = Math.min(k, 40);
    for (var j = 0; j < fade; j++) out[k - 1 - j] = idiv(out[k - 1 - j] * j, fade);
    return out;
  }

  function wav(pcm, rate) {
    var o = new Uint8Array(44 + pcm.length);
    var dv = new DataView(o.buffer);
    function str(at, s) { for (var i = 0; i < 4; i++) o[at + i] = s.charCodeAt(i); }
    str(0, 'RIFF'); dv.setUint32(4, 36 + pcm.length, true); str(8, 'WAVE'); str(12, 'fmt ');
    dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true); dv.setUint32(24, rate, true);
    dv.setUint32(28, rate * 2, true); dv.setUint16(32, 2, true); dv.setUint16(34, 16, true); str(36, 'data');
    dv.setUint32(40, pcm.length, true);
    o.set(pcm, 44);
    return o;
  }

  function writeVlq(o, v) {
    if (v < 0) v = 0;
    var buf = v & 0x7F;
    while ((v >>= 7) > 0) { buf <<= 8; buf |= ((v & 0x7F) | 0x80); }
    for (;;) {
      o.push(buf & 0xFF);
      if ((buf & 0x80) !== 0) buf >>= 8; else break;
    }
  }

  // MIDI writer (format 0, 500 ticks per quarter at 500000 us -> 1 tick = 1 ms)
  Conv.prototype.toMidi = function () {
    var ev = this.events.slice();
    ev.sort(function (x, y) { return x.time - y.time || x.order - y.order || x.seq - y.seq; });
    var trk = [0, 0xFF, 0x51, 3, 0x07, 0xA1, 0x20];
    var last = 0;
    for (var i = 0; i < ev.length; i++) {
      var t = ev[i].time;
      writeVlq(trk, t - last);
      last = t;
      for (var j = 0; j < ev[i].msg.length; j++) trk.push(ev[i].msg[j]);
    }
    writeVlq(trk, Math.max(0, this.endTime - last));
    trk.push(0xFF, 0x2F, 0);
    var f = new Uint8Array(22 + trk.length);
    var hdr = [0x4d, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 500 >> 8, 500 & 0xff, 0x4d, 0x54, 0x72, 0x6b,
      (trk.length >>> 24) & 0xff, (trk.length >>> 16) & 0xff, (trk.length >>> 8) & 0xff, trk.length & 0xff];
    f.set(hdr, 0);
    f.set(trk, 22);
    return f;
  };

  /** convert(Uint8Array) -> { type: 'audio/midi' | 'audio/x-wav' | null, data: Uint8Array | null, durationMs } */
  function convert(data) {
    var c = { type: null, data: null, durationMs: 0 };
    try {
      if (!data || data.length < 8 || data[0] !== 0x4d || data[1] !== 0x4d || data[2] !== 0x4d || data[3] !== 0x44) return c;
      var s = new Conv();
      var size = be32(data, 4);
      var end = Math.min(data.length, 8 + size);
      var o = 8;
      while (o + 8 <= end) {
        var id0 = u8(data, o), id1 = u8(data, o + 1), id2 = u8(data, o + 2);
        var len = be32(data, o + 4);
        var body = o + 8;
        if (body + len > data.length) len = data.length - body;
        if (id0 === 0x4d && id1 === 0x54 && id2 === 0x52) s.scoreTrack(data, body, len);        // "MTR"
        else if (id0 === 0x41 && id1 === 0x54 && id2 === 0x52) s.pcmTrack(data, body, len);     // "ATR"
        o = body + len;
      }
      if (s.events.length > 0) {
        c.type = 'audio/midi';
        c.data = s.toMidi();
        c.durationMs = s.endTime;
      } else if (s.pcm16 !== null) {
        c.type = 'audio/x-wav';
        c.data = wav(s.pcm16, s.pcmRate);
        c.durationMs = Math.floor((s.pcm16.length / 2) * 1000 / Math.max(1, s.pcmRate));
      }
    } catch (e) {
      c.type = null;
      c.data = null;
      c.error = String(e && e.message || e);
    }
    return c;
  }

  var api = { convert: convert };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Smaf = api;
})(this);
