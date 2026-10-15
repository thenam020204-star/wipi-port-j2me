#!/usr/bin/env python3
"""GBM image container + decoder for AND2011 resources, with a differential self-test that
draws every image through the ORIGINAL DM_LoadMergeGbm565 + DM_DrawImg (DM_DrawScreen565)
in the reference emulator and compares pixel by pixel.

Container (CONFIRMED, getMergedGbmData 0xd2af0): u32le count, then count x {u32le size, body}.
Body (CONFIRMED, DM_LoadMergeGbm565 0x51234): u8 type, u16le w (+0), u16le h (+2),
u16le aux (+4), u16le ncolors (+6), ncolors x u16le RGB565 palette, pixel data.
Pixel layout / transparency: established by the self-test below (see NOTES / docs/phaseC_formats.md).
Usage:
  python3 tools/gbm.py list <file>
  python3 tools/gbm.py selftest <binary.mod> <res dir> [maxfiles]
  python3 tools/gbm.py png <file> <outdir>          # needs nothing but zlib (writes PNG by hand)
"""
import os
import struct
import sys
import zlib

TRANSPARENT = 0xF81F


def parse_container(b):
    n = struct.unpack_from('<I', b, 0)[0]
    off, out = 4, []
    for _ in range(n):
        s = struct.unpack_from('<I', b, off)[0]
        out.append(b[off + 4:off + 4 + s])
        off += 4 + s
    if off != len(b):
        raise ValueError('container size mismatch %d != %d' % (off, len(b)))
    return out


def is_container(b):
    try:
        return len(b) >= 8 and 0 < struct.unpack_from('<I', b, 0)[0] < 4096 and bool(parse_container(b))
    except Exception:
        return False


def header(body):
    t = body[0]
    w, h, aux, nc = struct.unpack_from('<4H', body, 1)
    pal = list(struct.unpack_from('<%dH' % nc, body, 9))
    return t, w, h, aux, nc, pal, body[9 + 2 * nc:]


def decode(body):
    """-> (w, h, list of rows of RGB565 or None for transparent)."""
    t, w, h, aux, nc, pal, px = header(body)
    rows = []
    if t == 0x0c:
        for y in range(h):
            rows.append([pal[px[y * w + x]] for x in range(w)])
    elif t == 0x0b:
        stride = (w + 1) >> 1                 # bytes per row, odd widths padded (DM_DrawScreen565 0x3df94)
        for y in range(h):
            row = []
            for x in range(w):
                v = px[y * stride + (x >> 1)]
                row.append(pal[(v >> 4) if (x & 1) == 0 else (v & 0xf)])   # high nibble = left pixel
            rows.append(row)
    else:
        raise ValueError('type %#x' % t)
    return w, h, [[None if c == TRANSPARENT else c for c in r] for r in rows]


def png(w, h, rows, path):
    raw = b''
    for r in rows:
        raw += b'\0'
        for c in r:
            if c is None:
                raw += b'\0\0\0\0'
            else:
                raw += bytes([((c >> 11) & 31) * 255 // 31, ((c >> 5) & 63) * 255 // 63, (c & 31) * 255 // 31, 255])
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


# ---------------------------------------------------------------- differential self-test
FB_W, FB_H = 240, 400
BG = 0x1234


def _emu(binp):
    """Emulator with the ORIGINAL DM_Initialize executed against a tiny frame-buffer HLE,
    so SKIP_COL, DM_DrawScreen*, DM_LoadMergeGbm... hold their real runtime values."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from and_emu import Emu, std_imports
    emu = std_imports(Emu(binp))
    frames = {}

    def new_frame(w, h):
        ptr = emu.alloc(w * h * 2)
        hnd = emu.alloc(0x40)          # opaque handle
        frames[hnd] = (ptr, w, h)
        return hnd
    screen = new_frame(FB_W, FB_H)
    emu.fb = frames[screen][0]
    emu.screen = screen
    emu.frames = frames
    emu.imports['MC_grpGetScreenFrameBuffer'] = lambda e, a: screen
    emu.imports['MC_grpCreateOffScreenFrameBuffer'] = lambda e, a: new_frame(a[0], a[1])
    emu.imports['MC_grpInitContext'] = lambda e, a: 0
    emu.imports['MC_grpGetDisplayInfo'] = lambda e, a: 0
    # WIPI-C pixel conversion for a 16-bit RGB565 frame (INFERRED platform semantics, test-only)
    emu.imports['MC_grpGetPixelFromRGB'] = lambda e, a: ((a[0] >> 3) << 11) | ((a[1] >> 2) << 5) | (a[2] >> 3)
    emu.imports['wipic_get_frame_pointer'] = lambda e, a: frames.get(a[0], frames[screen])[0]
    emu.imports['wipic_get_frame_width'] = lambda e, a: frames.get(a[0], frames[screen])[1]
    emu.imports['wipic_get_frame_height'] = lambda e, a: frames.get(a[0], frames[screen])[2]
    # platform drawing calls made during DM_Initialize (context setup / clear): no effect on the
    # globals this harness needs, so they are no-ops here (test harness only)
    for n in ('MC_grpSetContext', 'MC_grpFillRect', 'MC_grpFlushLcd', 'MC_grpDrawRect', 'MC_grpDrawLine'):
        emu.imports.setdefault(n, lambda e, a: 0)
    emu.call('DM_Initialize')
    obj = {o[2]: o[0] for o in emu.elf.objs}
    emu.skip_col = emu.u32(obj['SKIP_COL'])
    emu.write(obj['OutR'], struct.pack('<4i', 0, 0, FB_W, FB_H))   # clip rect x0,y0,x1,y1 = full test frame
    return emu


def draw_original(emu, body, x=0, y=0):
    emu.write(emu.fb, struct.pack('<H', BG) * (FB_W * FB_H))
    src = emu.alloc(len(body)); emu.write(src, body)
    cell = emu.alloc(4); emu.write(cell, struct.pack('<I', src))
    img = emu.alloc(0x20)
    emu.call('DM_LoadMergeGbm565', cell, img)
    w, h = struct.unpack('<hh', emu.read(img, 4))
    # DM_DrawImg(frame, x, y, w, h, img, srcx, srcy, flip, rot, anchor)
    emu.call('DM_DrawImg', emu.screen, x, y, w, h, img, 0, 0, 0, 0, 0)
    raw = emu.read(emu.fb, FB_W * FB_H * 2)
    return w, h, raw


def selftest(binp, resdir, maxfiles=None):
    emu = _emu(binp)
    files = []
    for root, _, fs in os.walk(resdir):
        for fn in fs:
            p = os.path.join(root, fn)
            b = open(p, 'rb').read()
            if is_container(b):
                files.append((p, b))
    files.sort()
    if maxfiles:
        files = files[:maxfiles]
    n_img = n_bad = 0
    for p, b in files:
        for k, body in enumerate(parse_container(b)):
            if len(body) < 9:
                continue
            w, h, rows = decode(body)
            if w > FB_W or h > FB_H:
                continue  # larger than the test frame; covered by clipping tests later
            ew, eh, raw = draw_original(emu, body)
            fbp = struct.unpack('<%dH' % (FB_W * FB_H), raw)
            ok = (ew, eh) == (w, h)
            for yy in range(h):
                if not ok:
                    break
                for xx in range(w):
                    exp = BG if rows[yy][xx] is None else rows[yy][xx]
                    if fbp[yy * FB_W + xx] != exp:
                        ok = False
                        break
            # nothing outside the image rectangle may change
            if ok:
                for yy in range(FB_H):
                    row = fbp[yy * FB_W:(yy + 1) * FB_W]
                    if yy >= h and any(v != BG for v in row) or any(v != BG for v in row[w:]):
                        ok = False
                        break
            n_img += 1
            if not ok:
                n_bad += 1
                if n_bad <= 10:
                    print('MISMATCH', os.path.relpath(p, resdir), k, header(body)[:5])
    print('containers', len(files), 'images compared', n_img, 'mismatches', n_bad)
    return n_bad


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'list':
        for k, body in enumerate(parse_container(open(sys.argv[2], 'rb').read())):
            print(k, [hex(v) if i == 0 else v for i, v in enumerate(header(body)[:5])], len(body))
    elif cmd == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else None) else 0)
    elif cmd == 'png':
        os.makedirs(sys.argv[3], exist_ok=True)
        base = os.path.basename(sys.argv[2])
        for k, body in enumerate(parse_container(open(sys.argv[2], 'rb').read())):
            w, h, rows = decode(body)
            png(w, h, rows, os.path.join(sys.argv[3], '%s_%03d.png' % (base, k)))
