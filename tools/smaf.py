#!/usr/bin/env python3
"""SMAF (.mmf) reader and converter for the game's SND/*_MMF files: score tracks -> Standard MIDI File,
audio tracks (Yamaha 4-bit ADPCM) -> WAV.  Pure Python, no third-party code.

Container (big-endian):  'MMMD' u32 size, chunks { 4-byte id, u32 size, data }, trailing CRC16.
  CNTI  contents info          OPDA  optional data
  MTR<n> score track   : u8 formatType (2 = Mobile Standard, uncompressed), u8 sequenceType, u8 durationTimeBase,
                         u8 gateTimeTimeBase, 16 x u8 channel status, then sub-chunks Mspi / Mtsu (setup) / Mtsq (sequence) / Mtsp
  ATR<n> audio track   : u8 formatType, u8 sequenceType, u8 params (bit7 stereo, bits6-4 format, bits3-0 rate code),
                         u8 reserved, u8 durationTimeBase, u8 gateTimeTimeBase, then sub-chunks AspI / Atsq / Awa<k>
  time base codes: 0:1ms 1:2ms 2:4ms 3:5ms 0x10:10ms 0x11:20ms 0x12:40ms 0x13:50ms
  audio rate codes: 0:4000 1:8000 2:11025 3:22050 4:44100 Hz (format 1 = Yamaha ADPCM, low nibble first - checked
                    against ffmpeg's decoder and against the game's own OGG renditions, see docs/phaseC_audio.md)

Mtsq sequence (Mobile Standard, CONFIRMED by parsing every file to its exact end):
  event = duration (MIDI variable length, 7 bits + continuation) , status byte :
    0x8n note, gate(vlq)                 -> channel's last velocity (init 64)  0xAn d1 d2 : reserved (skipped)
    0x9n note, velocity, gate(vlq)       0xBn control, value               0xCn program      0xDn d1 : reserved
    0xEn lsb, msb pitch bend             0xF0 len(vlq) bytes : sysex       0xFF 0x00 : nop    0xFF 0x2F 0x00 : end
  A note carries its own gate time (no note-off); duration is the delay since the previous event.

MIDI output: type 0, one track, 1000 pulses per quarter at 60 bpm => 1 tick = 1 ms.  The synth voice numbers of the MA-3
chip are written as GM programs unchanged (timbre WILL differ from the original FM synthesis; see docs).

Usage: python3 tools/smaf.py info <file.mmf>
       python3 tools/smaf.py tomidi <in.mmf> <out.mid>
       python3 tools/smaf.py towav  <in.mmf> <out.wav>
       python3 tools/smaf.py convert <res dir> <out dir>   (BGM_xx.mid + EFF_xx.wav for every SND/*_MMF, prints sha256; output stays in work/)
       python3 tools/smaf.py selftest <res dir>      (parses all SND/*_MMF, checks exact consumption, ADPCM vs ffmpeg if present)
"""
import os
import struct
import sys

TIMEBASE_MS = {0: 1, 1: 2, 2: 4, 3: 5, 0x10: 10, 0x11: 20, 0x12: 40, 0x13: 50}
AUDIO_RATE = {0: 4000, 1: 8000, 2: 11025, 3: 22050, 4: 44100}


def chunks(b, p, end):
    out = []
    while p + 8 <= end:
        cid = bytes(b[p:p + 4])
        size = struct.unpack('>I', b[p + 4:p + 8])[0]
        out.append((cid, p + 8, size))
        p += 8 + size
    if p != end:
        raise ValueError('chunk sizes do not add up (%d != %d)' % (p, end))
    return out


def vlq(b, p):
    v = 0
    while True:
        c = b[p]; p += 1
        v = (v << 7) | (c & 0x7f)
        if not c & 0x80:
            return v, p


class Track:
    pass


def parse(b):
    """-> dict(score=[ScoreTrack...], audio=[AudioTrack...], cnti=bytes)"""
    if b[:4] != b'MMMD':
        raise ValueError('not SMAF')
    total = struct.unpack('>I', b[4:8])[0]
    end = 8 + total
    if end != len(b):                         # the size field counts the trailing CRC16
        raise ValueError('size %d does not match file %d' % (end, len(b)))
    end -= 2
    d = {'score': [], 'audio': [], 'cnti': b''}
    for cid, o, s in chunks(b, 8, end):
        if cid == b'CNTI':
            d['cnti'] = bytes(b[o:o + s])
        elif cid[:3] == b'MTR':
            t = Track()
            t.number = cid[3]
            t.fmt, t.seqtype, t.dur_tb, t.gate_tb = b[o:o + 4]
            if t.fmt != 2:
                raise ValueError('unsupported format type %d (only Mobile Standard uncompressed is used by this game)' % t.fmt)
            t.status = list(b[o + 4:o + 20])
            t.dur_ms, t.gate_ms = TIMEBASE_MS[t.dur_tb], TIMEBASE_MS[t.gate_tb]
            t.sub = {c: bytes(b[oo:oo + ss]) for c, oo, ss in chunks(b, o + 20, o + s)}
            t.events = parse_sequence(t.sub[b'Mtsq'])
            d['score'].append(t)
        elif cid[:3] == b'ATR':
            t = Track()
            t.number = cid[3]
            t.fmt, t.seqtype, params, _, t.dur_tb, t.gate_tb = b[o:o + 6]
            t.stereo = bool(params & 0x80)
            t.wave_format = (params >> 4) & 7
            t.rate = AUDIO_RATE[params & 0x0f]
            t.sub = {}
            for c, oo, ss in chunks(b, o + 6, o + s):
                t.sub[c] = bytes(b[oo:oo + ss])
            d['audio'].append(t)
    return d


def parse_sequence(q):
    """-> list of (delta_duration, kind, args)"""
    ev = []
    p = 0
    while p < len(q):
        dur, p = vlq(q, p)
        st = q[p]; p += 1
        hi, ch = st & 0xf0, st & 0x0f
        if hi == 0x80:
            note = q[p]; p += 1
            gate, p = vlq(q, p)
            ev.append((dur, 'note', (ch, note, None, gate)))          # None: reuse the channel's last velocity
        elif hi == 0x90:
            note, vel = q[p], q[p + 1]; p += 2
            gate, p = vlq(q, p)
            ev.append((dur, 'note', (ch, note, vel, gate)))
        elif hi == 0xa0:
            p += 2
            ev.append((dur, 'reserved', (st,)))
        elif hi == 0xb0:
            ev.append((dur, 'cc', (ch, q[p], q[p + 1]))); p += 2
        elif hi == 0xc0:
            ev.append((dur, 'pc', (ch, q[p]))); p += 1
        elif hi == 0xd0:
            p += 1
            ev.append((dur, 'reserved', (st,)))
        elif hi == 0xe0:
            ev.append((dur, 'bend', (ch, q[p], q[p + 1]))); p += 2
        elif st == 0xf0:
            n, p = vlq(q, p)
            ev.append((dur, 'sysex', (bytes(q[p:p + n]),))); p += n
        elif st == 0xff:
            sub = q[p]; p += 1
            if sub == 0x00:
                ev.append((dur, 'nop', ()))
            elif sub == 0x2f:
                p += 1
                ev.append((dur, 'end', ()))
            else:
                raise ValueError('unknown 0xFF %02x' % sub)
        else:
            raise ValueError('unknown status %02x at %d' % (st, p - 1))
    return ev


# ------------------------------------------------------------------------------------------------ MIDI writer
def _vlq(v):
    out = [v & 0x7f]
    v >>= 7
    while v:
        out.append(0x80 | (v & 0x7f)); v >>= 7
    return bytes(reversed(out))


PASS_CC = (1, 6, 7, 10, 11, 38, 64, 71, 74, 100, 101, 120, 121, 123, 126, 127)     # controls kept (same set as the reference tool)
DRUM_BANK = 125                       # SMAF bank-select value (CC0) of the rhythm kit; melodic channels use 124


def to_midi(d, master_volume=True):
    """Score track(s) -> SMF type 0 bytes (1 tick = 1 ms).
    Rules (each checked event-by-event against an independent converter, docs/phaseC_audio.md):
      * a note = note-on at tick, note-off at tick + gate * gateTimeBase; gate 0 -> dropped;
      * 0x8n notes use the channel's last velocity (initially 64); 0x9n notes set it;
      * bank select (CC0/CC32) is not GM: dropped; a channel whose CC0 was 125 is a rhythm channel: program forced to 0;
      * controls in PASS_CC pass through, other controls are dropped; program changes and pitch bends pass through;
      * Yamaha SMAF master-volume sysex (43 79 06 7F 00 vv) -> GM master volume (INFERRED equivalence)."""
    events = []                                   # (tick, order, bytes)
    for tr in d['score']:
        tick = 0
        vel = [64] * 16
        rhythm = [False] * 16
        for dur, kind, a in tr.events:
            tick += dur * tr.dur_ms
            if kind == 'note':
                ch, note, v, gate = a
                if v is not None:
                    vel[ch] = v
                if gate == 0:
                    continue
                events.append((tick, 1, bytes([0x90 | ch, note, vel[ch]])))
                events.append((tick + gate * tr.gate_ms, 0, bytes([0x80 | ch, note, 0])))
            elif kind == 'cc':
                ch, c, v = a
                if c == 0:
                    rhythm[ch] = (v == DRUM_BANK)
                elif c in PASS_CC:
                    events.append((tick, 2, bytes([0xb0 | ch, c, v])))
            elif kind == 'pc':
                events.append((tick, 3, bytes([0xc0 | a[0], 0 if rhythm[a[0]] else a[1]])))
            elif kind == 'bend':
                events.append((tick, 2, bytes([0xe0 | a[0], a[1], a[2]])))
            elif kind == 'sysex' and master_volume:
                sx = a[0]
                if len(sx) == 7 and sx[:5] == bytes([0x43, 0x79, 0x06, 0x7f, 0x00]) and sx[6] == 0xf7:
                    events.append((tick, 2, bytes([0xf0, 0x07, 0x7f, 0x7f, 0x04, 0x01, 0x00, sx[5] & 0x7f, 0xf7])))
    events.sort(key=lambda e: (e[0], e[1]))
    trk = bytearray()
    trk += _vlq(0) + b'\xff\x51\x03' + (1000000).to_bytes(3, 'big')            # 60 bpm
    last = 0
    for tick, _, msg in events:
        trk += _vlq(tick - last) + msg
        last = tick
    trk += _vlq(0) + b'\xff\x2f\x00'
    return b'MThd' + struct.pack('>IHHH', 6, 0, 1, 1000) + b'MTrk' + struct.pack('>I', len(trk)) + bytes(trk)


# ------------------------------------------------------------------------------------------------ audio
_DIFF = (1, 3, 5, 7, 9, 11, 13, 15, -1, -3, -5, -7, -9, -11, -13, -15)
_SCALE = (230, 230, 230, 230, 307, 409, 512, 614, 230, 230, 230, 230, 307, 409, 512, 614)


def yamaha_adpcm(data):
    """Yamaha 4-bit ADPCM (YM2608 style), low nibble first -> list of int16."""
    pred, step = 0, 127
    out = []
    for byte in data:
        for nib in (byte & 15, byte >> 4):
            pred += (step * _DIFF[nib]) // 8 if (step * _DIFF[nib]) >= 0 else -((-step * _DIFF[nib]) // 8)
            pred = max(-32768, min(32767, pred))
            step = max(127, min(24576, (step * _SCALE[nib]) >> 8))
            out.append(pred)
    return out


def to_wav(d, track=0):
    t = d['audio'][track]
    wa = [v for k, v in t.sub.items() if k[:3] == b'Awa'][0]
    if t.wave_format != 1:
        raise ValueError('wave format %d not handled' % t.wave_format)
    pcm = yamaha_adpcm(wa)
    data = struct.pack('<%dh' % len(pcm), *pcm)
    return (b'RIFF' + struct.pack('<I', 36 + len(data)) + b'WAVEfmt ' + struct.pack('<IHHIIHH', 16, 1, 1, t.rate, t.rate * 2, 2, 16)
            + b'data' + struct.pack('<I', len(data)) + data)


def _midi_ok(mid):
    """Sanity: header, one track, every note-on has a later/equal note-off, ticks non-decreasing, ends with End-of-Track."""
    if mid[:4] != b'MThd' or mid[14:18] != b'MTrk':
        return False
    n = struct.unpack('>I', mid[18:22])[0]
    q, end, run = 22, 22 + n, None
    on = {}
    tick = 0
    while q < end:
        v = 0
        while True:
            c = mid[q]; q += 1; v = (v << 7) | (c & 0x7f)
            if not c & 0x80:
                break
        tick += v
        st = mid[q]
        if st == 0xff:
            q += 3 + mid[q + 2]
            continue
        if st == 0xf0:
            q += 1
            l = 0
            while True:
                c = mid[q]; q += 1; l = (l << 7) | (c & 0x7f)
                if not c & 0x80:
                    break
            q += l
            continue
        run = st
        q += 1
        hi = st & 0xf0
        d1 = mid[q]; q += 1
        d2 = None
        if hi not in (0xc0, 0xd0):
            d2 = mid[q]; q += 1
        if hi == 0x90:
            on[(st & 15, d1)] = on.get((st & 15, d1), 0) + 1
        elif hi == 0x80:
            k = (st & 15, d1)
            if on.get(k, 0) <= 0:
                return False
            on[k] -= 1
    return all(v == 0 for v in on.values()) and mid[-3:] == b'\xff\x2f\x00'


def convert_all(resdir, outdir):
    import hashlib
    os.makedirs(outdir, exist_ok=True)
    snd = os.path.join(resdir, 'SND')
    for f in sorted(os.listdir(snd)):
        if not f.endswith('_MMF'):
            continue
        d = parse(open(os.path.join(snd, f), 'rb').read())
        if d['score']:
            name, data = f[:-4] + '.mid', to_midi(d)
        else:
            name, data = f[:-4] + '.wav', to_wav(d)
        open(os.path.join(outdir, name), 'wb').write(data)
        print(name, len(data), hashlib.sha256(data).hexdigest()[:16])


# ------------------------------------------------------------------------------------------------ self-test
def selftest(resdir):
    import shutil
    import subprocess
    import tempfile
    snd = os.path.join(resdir, 'SND')
    files = sorted(f for f in os.listdir(snd) if f.endswith('_MMF'))
    bad = 0
    n_score = n_audio = n_notes = 0
    ff = shutil.which('ffmpeg')
    tmp = tempfile.mkdtemp(prefix='smaf_')
    adp_ok = adp_n = 0
    for f in files:
        b = open(os.path.join(snd, f), 'rb').read()
        d = parse(b)
        if d['score']:
            n_score += 1
            for t in d['score']:
                if not t.events or t.events[-1][1] != 'end':
                    bad += 1; print('NO END EVENT', f)
                n_notes += sum(1 for e in t.events if e[1] == 'note')
            mid = to_midi(d)
            if not _midi_ok(mid):
                bad += 1; print('MIDI structure check failed', f)
        if d['audio']:
            n_audio += 1
            wav = to_wav(d)
            if ff:
                p = os.path.join(tmp, f + '.wav')
                q = os.path.join(tmp, f + '.ff.wav')
                open(p, 'wb').write(wav)
                subprocess.run([ff, '-v', 'error', '-y', '-i', os.path.join(snd, f), '-f', 'wav', q], check=True)
                a, c = open(p, 'rb').read()[44:], open(q, 'rb').read()
                c = c[c.find(b'data') + 8:]
                adp_n += 1
                if a[:len(c)] == c[:len(a)] and abs(len(a) - len(c)) <= 4:
                    adp_ok += 1
                else:
                    bad += 1; print('ADPCM differs from ffmpeg', f, len(a), len(c))
    print('MMF files', len(files), 'score', n_score, 'audio', n_audio, 'notes', n_notes,
          'ADPCM decode == ffmpeg (%s):' % ('available' if ff else 'ffmpeg missing'), adp_ok, '/', adp_n, 'problems', bad)
    shutil.rmtree(tmp, ignore_errors=True)
    return bad


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'selftest':
        sys.exit(1 if selftest(sys.argv[2]) else 0)
    if cmd == 'convert':
        convert_all(sys.argv[2], sys.argv[3])
        sys.exit(0)
    b = open(sys.argv[2], 'rb').read()
    d = parse(b)
    if cmd == 'info':
        for t in d['score']:
            kinds = {}
            for e in t.events:
                kinds[e[1]] = kinds.get(e[1], 0) + 1
            print('score track', t.number, 'fmt', t.fmt, 'dur', t.dur_ms, 'ms gate', t.gate_ms, 'ms status', set(t.status), 'sub', {k: len(v) for k, v in t.sub.items()}, kinds)
        for t in d['audio']:
            print('audio track', t.number, 'rate', t.rate, 'format', t.wave_format, 'stereo', t.stereo, {k: len(v) for k, v in t.sub.items()})
    elif cmd == 'tomidi':
        open(sys.argv[3], 'wb').write(to_midi(d))
    elif cmd == 'towav':
        open(sys.argv[3], 'wb').write(to_wav(d))
