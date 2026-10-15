#!/usr/bin/env python3
"""Sprite composition of AND2011: CIF frame parts -> sequence of DM_* calls, verified at the call
boundary against the ORIGINAL SPR_drawSpriteNPC 0xc00a0 (size 4128) executed in the reference emulator.

What is verified here (CONFIRMED by selftest): *which* DM_RestorePalette / DM_SetChagePalBlendEx /
DM_DrawImg / DM_DrawImgEx / DM_DrawImgRotate / DM_DrawImgRotateEx calls the original makes, in order, with
every argument.  What the DM_* calls do to pixels is verified separately (dm_blit.py, dm_fx.py).

Entry points (CONFIRMED, 0xc11bc / 0xc10c0):
  SPR_drawSprite(frame, spr, anim, dir, frameptr, x, y, a7, a8)
      = SPR_drawSpriteNPC(frame, spr, anim, 2, 3, dir, frameptr, x, y, a7, a8, ctrl = -1)
  SPR_drawSpriteSK(...)  same with ctrl = its last argument (+ _lcdEff[10]==2 palette hack)
`frameptr` points to a byte holding the current frame number; it is reset to 0 when > max frame.

Sprite struct fields used (offset: meaning):  0x28 image-set pointer array, 0x2c original array,
  0x38 set count, 0x3c u8 nAnim, 0x44/0x48 anim records (0x4c bytes each), 0x4c CIF data, 0x60/0x62/0x64 s16 and
  0x66/0x67 u8 control-palette parameters (SPR_set_control_pal), 0x68 control-palette flag, 0x6c frame number,
  0x6d u8 'set-2 image offset' flag, 0x6e u8 blend enable, 0x6f u8 blend alpha, 0x70 s8 CIF mode (2 = hero).
Image struct (32 bytes): +2 s16 height, +4 s16 draw width ('aux'), +0x1c u8 flag.

Part (CIF) encoding, mode != 2 (4 / 9 bytes) and mode == 2 (5 / 10 bytes):
  b0: bit7 long part | bits6..5 class (0 -> 0x10, 1 -> 8, 2/3 -> 4) | bits4..0 image-set index
      (b0 == 0x7f: empty part, only skipped)
  b1: image index inside the set (s8 * 32 = struct offset; +2 if set == 2 and spr[0x6d])
  mode != 2: b2 s8 x, b3 s8 y.   mode == 2: x = s12((b2 << 4) | (b3 >> 4)), y = s12(((b3 & 15) << 8) | b4)
  long part adds 5 bytes: scaleX/10, scaleY/10, angle/2, alpha (s8, -1 = none), (effect << 2) | class2
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def s8(v):
    v &= 0xff
    return v - 256 if v & 0x80 else v


def s16(v):
    v &= 0xffff
    return v - 65536 if v & 0x8000 else v


CLS = {0: 0x10, 1: 8}          # anything else -> 4


class Env:
    """Everything SPR_drawSpriteNPC reads, as plain Python values (a snapshot of the emulated memory)."""

    def __init__(self):
        self.data = b''               # CIF bytes (spr+0x4c)
        self.mode = 0x11              # s8 spr+0x70
        self.n_anim = 0               # u8 spr+0x3c
        self.anims = []               # cif.parse()['anims']: anims[a][d] = [(frame_start_offset, parts), ...]
        self.n_sets = 0               # spr+0x38
        self.sets = [0] * 32          # spr+0x28 array (current)
        self.orig = [0] * 32          # spr+0x2c array
        self.ctrl_pal = (0, 0, 0, 0, 0)   # s16 0x60, s16 0x62, s16 0x64, u8 0x67, u8 0x66
        self.f68 = self.f6d = self.f6e = self.f6f = 0
        self.lcd = [0] * 12           # _lcdEff bytes
        self.extra_effect = 0         # _imgSKextraEffect
        self.particle = 1             # MenuOptions_particle() result
        self.img = {}                 # image pointer -> (h, aux, flag1c)


def max_frame(env, anim, d):
    """SPR_getMaxFrame 0xbff54 -> s8 (number of frames of direction-slot `d` minus one)."""
    d = s8(d)
    if d == 3:
        k = 2
    elif d > 4:
        k = 0 if d == 6 else d - 5
    else:
        k = d
    dirs = env.anims[anim]
    if len(dirs) <= 2:
        k = 0
    nf = len(dirs[k]) if 0 <= k < len(dirs) else 0      # rec bytes past nDir are zero (calloc'ed record)
    return s8(nf - 1)


def draw_npc(env, frame, anim, p4, p5, p6, frame_no, X, Y, p10, p11, p12):
    """Pure model of SPR_drawSpriteNPC.  Returns (events, new_frame_no).  Events are tuples whose first item
    is the DM_* name; images are identified by their struct pointer (set base + s8(index) * 32)."""
    ev = []
    if p6 & 0x80:
        return ev, frame_no
    ctrl = s8(p12) if p12 != 0xffffffff else -1
    p11u = p11 & 0xffffffff
    eff_dir = (p4 & 0xff) if p6 == p5 else p6
    sets = list(env.sets)
    # ---- 0xc0124..0xc01b0: image-set array refresh for ctrl == 2 (SK skins)
    n = env.n_sets
    i = 0
    while i < n:
        o = env.orig[i]
        if o == 0 or ctrl != 2:
            sets[i] = o
        elif i != 7:
            sets[i] = o
        else:
            if env.extra_effect:
                sets[7] = env.extra_effect
            i = 8
            if n < 9:
                break
            continue
        i += 1
    env.sets = sets
    # ---- frame number clamp
    if not (0 <= anim < env.n_anim):
        raise ValueError('anim out of range: the original reads garbage record memory (UNKNOWN); callers must pass 0 <= anim < nAnim')
    mf = max_frame(env, anim, eff_dir)
    if s8(frame_no) > mf or s8(frame_no) < 0:
        frame_no = 0
    dirs = env.anims[anim]
    d = s8(eff_dir)
    if d >= len(dirs) or d < 0:
        d = 0                                   # pointer slot 0 (rec+0xc+4*d == 0 -> index 0)
    frames = dirs[d]
    if s8(frame_no) >= len(frames):
        return ev, frame_no
    start, parts = frames[s8(frame_no)]
    nparts = s8(env.data[start])
    if nparts <= 0:
        return ev, frame_no
    data = env.data
    fp = start + 1
    flip = rot = 0                              # local_40 / local_44 persist across parts
    short_sz = 5 if env.mode == 2 else 4
    D = (p10 == 1) or (p6 == p5)
    for _ in range(nparts):
        b0 = data[fp]
        if b0 == 0x7f:
            fp += short_sz
            continue
        cls = CLS.get((b0 >> 5) & 3, 4)
        set_idx = b0 & 0x1f
        img_i = data[fp + 1]
        if set_idx == 2 and env.f6d:
            img_i = (img_i + 2) & 0xff
        if env.mode == 2:
            b2, b3, b4 = data[fp + 2], data[fp + 3], data[fp + 4]
            x = s16(((b2 << 4) | (b3 >> 4) | (0xf000 if b2 & 0x80 else 0)) & 0xffff)
            y = s16((b4 | ((b3 & 0xf) << 8) | (0xf000 if b3 & 8 else 0)) & 0xffff)
        else:
            x, y = s8(data[fp + 2]), s8(data[fp + 3])
        fp += short_sz
        if not (b0 & 0x80):
            base = sets[set_idx]
            if base == 0:
                continue
            img = base + (s8(img_i) << 5)
            if not D:
                rot = 0
                flip = {0x10: 0, 8: 1, 4: 2}[cls]
            else:
                if cls == 8:
                    rot = 0; flip = 0
                elif cls == 0x10:
                    aux = s16(env.img[img][1])
                    flip = 1
                    if aux & 1:
                        rot = 0
                    else:
                        rot = 0
                        x = s16(x + 1)
                else:
                    rot = 0xb4; flip = 0
                x = s16(-x)
            use_ex = rot != 0
            _palette_short(env, ev, img, set_idx, p11u)
            if env.f68 == 0:
                ev.append(('DM_RestorePalette', img))
            else:
                _ctrl_pal(env, ev, img, set_idx, long_part=False)
            if env.f6e:
                use_ex = True
            h, aux, _ = env.img[img]
            if not use_ex:
                ev.append(('DM_DrawImg', frame, X + x, Y + y, s16(aux), s16(h), img, 0, 0, flip, 0, 0x12))
            else:
                ev.append(('DM_DrawImgEx', frame, X + x, Y + y, s16(aux), s16(h), img, 0, 0, flip, rot,
                           env.f6f, 0, 0x12))
        else:
            sx10, sy10, ang = data[fp], data[fp + 1], data[fp + 2]
            if env.lcd[9] == 1 and env.f68 == 1:
                b4_, alpha, eff = data[fp + 4], -1, 0
            else:
                b4_ = data[fp + 4]
                alpha = s8(data[fp + 3])
                eff = b4_ >> 2
                if eff != 0:
                    ev.append(('MenuOptions_particle',))
                    if env.particle == 0:
                        alpha = -1
            fp += 5
            cls2 = CLS.get(b4_ & 3, 4)
            if ctrl == 0 and set_idx == 7:
                continue
            base = sets[set_idx]
            if base == 0:
                continue
            ang2 = ang * 2
            img = base + (s8(img_i) << 5)
            if not D:
                flip = {0x10: 0, 8: 1, 4: 2}[cls2]
                rot = ang2
            else:
                aux = s16(env.img[img][1])
                if cls2 == 8:
                    flip = 0
                elif cls2 == 0x10:
                    if sx10 * 10 == 100 and ((aux ^ 1) & 1):
                        x = s16(x + 1)
                    flip = 1
                else:
                    flip = 0
                if ang == 0x87:                          # ang*2 == 0x10e  (0xc0b14)
                    rot = 0x10e if cls2 == 4 else 0x5a
                elif ang == 0x2d:                        # ang*2 == 0x5a   (0xc0e1c): note the opposite test
                    rot = 0x5a if cls2 == 4 else 0x10e
                else:
                    if ang == 0x5a:
                        rot = 0xb4 if cls2 != 4 else 0
                    elif ang == 0:
                        rot = ang2
                        if cls2 == 4:
                            rot = 0xb4
                x = s16(-x)
            # palette handling
            if env.lcd[9] != 0:
                if env.img[img][2] == 1:
                    ev.append(('DM_RestorePalette', img))
                if env.mode == 2 and set_idx not in (5, 7):
                    ev.append(('DM_SetChagePalBlendEx', img, s16(env.lcd[0] | env.lcd[1] << 8), s16(env.lcd[2] | env.lcd[3] << 8),
                               s16(env.lcd[4] | env.lcd[5] << 8), env.lcd[7], env.lcd[6]))
            else:
                if p11u == 0xffffffff or p11u == set_idx:
                    ev.append(('DM_RestorePalette', img))
            if env.f68 == 0:
                ev.append(('DM_RestorePalette', img))
            else:
                if env.img[img][2] == 1:
                    ev.append(('DM_RestorePalette', img))
                if set_idx not in (5, 7):
                    c = env.ctrl_pal
                    ev.append(('DM_SetChagePalBlendEx', img, c[0], c[1], c[2], c[3], c[4]))
            if env.f6e:
                alpha = s8(env.f6f)
            sx_, sy_ = sx10 * 10, sy10 * 10
            h, aux, _ = env.img[img]
            common = (frame, X + x, Y + y, s16(aux), s16(h), img, 0, 0, flip, rot)
            if sy_ != 100 or sx_ != 100:
                if alpha != -1 or eff != 0:
                    ev.append(('DM_DrawImgRotateEx',) + common + (sx_, sy_, eff, alpha & 0xff, 0x12))
                else:
                    ev.append(('DM_DrawImgRotate',) + common + (sx_, sy_, 0x12))
            else:
                if alpha != -1 or eff != 0:
                    ev.append(('DM_DrawImgEx',) + common + (alpha & 0xff, eff, 0x12))
                else:
                    ev.append(('DM_DrawImg',) + common + (0x12,))
    return ev, frame_no


def _palette_short(env, ev, img, set_idx, p11u):
    """0xc04.. palette block of a short part (identical for p11 == -1 and p11 != -1)."""
    if env.lcd[9] == 0:
        ev.append(('DM_RestorePalette', img))
    else:
        if env.img[img][2] == 1:
            ev.append(('DM_RestorePalette', img))
        if set_idx not in (5, 7):
            ev.append(('DM_SetChagePalBlendEx', img, s16(env.lcd[0] | env.lcd[1] << 8), s16(env.lcd[2] | env.lcd[3] << 8),
                       s16(env.lcd[4] | env.lcd[5] << 8), env.lcd[7], env.lcd[6]))


def _ctrl_pal(env, ev, img, set_idx, long_part):
    if env.img[img][2] == 1:
        ev.append(('DM_RestorePalette', img))
    if set_idx not in (5, 7):
        c = env.ctrl_pal
        ev.append(('DM_SetChagePalBlendEx', img, c[0], c[1], c[2], c[3], c[4]))


# ------------------------------------------------------------------------------------ self-test
import random


class _Rig:
    ARITY = {'DM_RestorePalette': 1, 'DM_SetChagePalBlendEx': 6, 'DM_DrawImg': 11, 'DM_DrawImgEx': 13,
             'DM_DrawImgRotate': 13, 'DM_DrawImgRotateEx': 15, 'MenuOptions_particle': 0}

    def __init__(self, binp, resdir, tmpdir):
        from and_hle import Harness
        from unicorn import UC_HOOK_CODE
        from unicorn.arm_const import UC_ARM_REG_SP, UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0, UC_ARM_REG_R1, \
            UC_ARM_REG_R2, UC_ARM_REG_R3
        self.regs = (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3)
        self.R = (UC_ARM_REG_SP, UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0)
        self.real_res, self.tmp = resdir, tmpdir
        os.makedirs(os.path.join(tmpdir, 'SYN'), exist_ok=True)
        self.h = h = Harness(binp, tmpdir).boot_dm()
        h.imports['MC_knlAlloc'] = lambda e, a: e.alloc(a[0])
        h.imports['MC_knlGetFreeMemory'] = lambda e, a: 0x4000000        # INFERRED platform call (closeGbmMerger)
        self.log = []
        self.particle = 1
        for n in self.ARITY:
            a = h.elf.fbyname[n][0]
            h.uc.hook_add(UC_HOOK_CODE, self._mk(n), begin=a, end=a)
        self.fa, self.fsz, _ = h.elf.fbyname['SPR_drawSpriteNPC']
        self.cov = set()
        h.uc.hook_add(UC_HOOK_CODE, lambda uc, ad, sz, _: self.cov.add(ad), begin=self.fa, end=self.fa + self.fsz)
        self.lcd, self.extra = h.objaddr('_lcdEff'), h.objaddr('_imgSKextraEffect')
        self.pool = []

    @staticmethod
    def _s32(v):
        return v - (1 << 32) if v & 0x80000000 else v

    def _mk(self, name):
        SP, LR, PC, R0 = self.R

        def cb(uc, addr, size, _):
            sp = uc.reg_read(SP)
            vals = [self._s32(uc.reg_read(r)) for r in self.regs] + \
                [self._s32(v) for v in struct.unpack('<12I', bytes(uc.mem_read(sp, 48)))]
            if name == 'DM_RestorePalette':
                a = (vals[0] & 0xffffffff,)
            elif name == 'DM_SetChagePalBlendEx':
                a = (vals[0] & 0xffffffff,) + tuple(vals[1:6])
            else:
                a = tuple(vals[:self.ARITY[name]])
                if name.startswith('DM_Draw'):
                    a = a[:5] + (a[5] & 0xffffffff,) + a[6:]
            self.log.append((name,) + a)
            uc.reg_write(R0, self.particle if name == 'MenuOptions_particle' else 0)
            uc.reg_write(PC, uc.reg_read(LR))
        return cb

    def make_set(self, rng, env):
        h = self.h
        if len(self.pool) >= 24:
            mid, imgs = rng.choice(self.pool)
            env.img.update(imgs)
            return mid
        mid = h.alloc(256 * 32 + 64) + 128 * 32
        imgs = {}
        for i in range(-128, 128):
            p = mid + i * 32
            hh, aux, fl = rng.randint(1, 60), rng.randint(1, 60), rng.choice([0, 0, 1])
            h.write(p + 2, struct.pack('<h', hh)); h.write(p + 4, struct.pack('<h', aux)); h.write(p + 0x1c, bytes([fl]))
            imgs[p] = (hh, aux, fl)
        env.img.update(imgs)
        self.pool.append((mid, imgs))
        return mid

    def load(self, rng, data, mode, resdir, relpath):
        """Load `data` (a CIF) with the ORIGINAL SPR_loadSprite and build the matching Env with random image sets / flags."""
        import cif
        h = self.h
        h.resdir = resdir
        spr = h.alloc(0x80); path = h.alloc(len(relpath) + 4); h.write(path, b'/' + relpath.encode() + b'\0')
        h.call('SPR_loadSprite', spr, path, mode)
        m = cif.parse(data, mode)
        assert m['end'] == len(data)
        env = Env(); env.data = data; env.mode = mode; env.anims = m['anims']; env.n_anim = len(m['anims'])
        nset = rng.randint(1, 32)
        env.n_sets = nset
        for i in range(32):
            if i < nset and rng.random() < 0.8:
                env.sets[i] = self.make_set(rng, env)
                env.orig[i] = env.sets[i] if rng.random() < 0.8 else self.make_set(rng, env)
            elif i < nset and rng.random() < 0.5:
                env.orig[i] = self.make_set(rng, env)
        arr, arr2 = h.alloc(128), h.alloc(128)
        h.write(arr, b''.join(struct.pack('<I', v) for v in env.sets)); h.write(arr2, b''.join(struct.pack('<I', v) for v in env.orig))
        h.write(spr + 0x28, struct.pack('<I', arr)); h.write(spr + 0x2c, struct.pack('<I', arr2))
        h.write(spr + 0x38, struct.pack('<I', nset))
        self.spr, self.env, self.arr = spr, env, arr
        return spr, env

    def randomize_state(self, rng):
        h, env, spr = self.h, self.env, self.spr
        c = (rng.randrange(-300, 300), rng.randrange(-300, 300), rng.randrange(-300, 300), rng.randrange(256), rng.randrange(256))
        env.ctrl_pal = c
        h.write(spr + 0x60, struct.pack('<hhh', c[0], c[1], c[2])); h.write(spr + 0x67, bytes([c[3]])); h.write(spr + 0x66, bytes([c[4]]))
        env.f68, env.f6d, env.f6e, env.f6f = rng.choice([0, 0, 1, 1, 2]), rng.choice([0, 1]), rng.choice([0, 0, 1]), rng.randrange(256)
        for off, v in ((0x68, env.f68), (0x6d, env.f6d), (0x6e, env.f6e), (0x6f, env.f6f)):
            h.write(spr + off, bytes([v]))
        env.lcd = [rng.randrange(256) for _ in range(12)]
        env.lcd[9] = rng.choice([0, 1, 1, 2])
        h.write(self.lcd, bytes(env.lcd))
        env.extra_effect = rng.choice([0, 0, self.make_set(rng, env)])
        h.write(self.extra, struct.pack('<I', env.extra_effect))
        env.particle = self.particle = rng.choice([0, 1])

    def call(self, rng, anim, p4, p5, p6, fno, X, Y, p10, p11, p12):
        """Run original and model; return (ok, detail)."""
        h, env, spr = self.h, self.env, self.spr
        fb = h.alloc(8)
        h.write(fb, bytes([fno]))
        self.log.clear()
        h.call('SPR_drawSpriteNPC', 0x1234, spr, anim, p4, p5, p6, fb, X, Y, p10, p11, p12)
        got, gfno = list(self.log), h.read(fb, 1)[0]
        ev, mfno = draw_npc(env, 0x1234, anim, p4, p5, p6, fno, X, Y, p10, p11, p12)
        msets = list(struct.unpack('<32I', h.read(self.arr, 128)))
        ok = got == [tuple(e) for e in ev] and gfno == mfno and msets == env.sets
        return ok, (got, ev, gfno, mfno)


def _gen_cif(rng, mode):
    out = bytearray([0, 0])
    nan = rng.randint(1, 3); out[1] = nan
    shortsz = 5 if mode == 2 else 4
    for _ in range(nan):
        nd = rng.choice([1, 2, 3, 4, 5]); out.append(nd)
        nfr = rng.randint(1, 3)
        for _ in range(nd):
            out.append(nfr)
            for _ in range(nfr):
                npart = rng.randint(1, 6); out.append(npart)
                for _ in range(npart):
                    if rng.random() < 0.05:
                        out += bytes([0x7f]) + bytes(rng.randrange(256) for _ in range(shortsz - 1)); continue
                    islong = rng.random() < 0.5
                    b0 = (0x80 if islong else 0) | (rng.randint(0, 3) << 5) | rng.choice([0, 1, 2, 3, 4, 5, 6, 7, 8, 0, 1, 2, 5, 7, 31])
                    part = bytearray([b0, rng.randrange(256) if rng.random() < 0.3 else rng.randint(0, 5)])
                    part += bytes(rng.randrange(256) for _ in range(shortsz - 2))
                    if islong:
                        part += bytes([rng.choice([10, 10, 10, 5, 20, 0, rng.randrange(256)]), rng.choice([10, 10, 10, 5, 20, rng.randrange(256)]),
                                       rng.choice([0, 0, 0x2d, 0x5a, 0x87, rng.randrange(256)]), rng.choice([0xff, 0xff, rng.randrange(256)]),
                                       rng.choice([rng.randrange(256), rng.randrange(4), (rng.randrange(1, 64) << 2) | rng.randrange(4)])])
                    out += part
    return bytes(out)


def _rand_call(rng, env):
    return (rng.randrange(env.n_anim), rng.choice([2, 2, 0, 1]), rng.choice([3, 3, 1]), rng.choice([0, 1, 2, 3, 3, 4, 5, 6, 7, 0x81]),
            rng.choice([0, 1, 2, 3, 4, 127, 128, 200, 255]), rng.randint(-50, 300), rng.randint(-50, 300),
            rng.choice([0, 0, 1, 2]), rng.choice([0xffffffff, 0xffffffff, 0, 1, 2, 5, 7, 8]), rng.choice([0xffffffff, 0xffffffff, 0, 2, 5]))


def selftest(binp, resdir, seeds=range(1, 13), sprites=30, calls=40):
    import tempfile
    import cif as cifmod
    tmp = tempfile.mkdtemp(prefix='hl4sprite_')
    rig = _Rig(binp, resdir, tmp)
    n = bad = 0
    # 1) random CIFs (all code paths of the original are reached; coverage is printed)
    for sd in seeds:
        rng = random.Random(sd)
        for si in range(sprites):
            mode = rng.choice([0x11, 0x11, 2])
            data = _gen_cif(rng, mode)
            rel = 'SYN/_S%d_%d_CIF' % (sd, si)
            open(os.path.join(tmp, rel), 'wb').write(data)
            rig.load(rng, data, mode, tmp, rel)
            rig.randomize_state(rng)
            for _ in range(calls):
                ok, d = rig.call(rng, *_rand_call(rng, rig.env))
                n += 1
                if not ok:
                    bad += 1
                    if bad <= 5:
                        print('DIFF', sd, si, 'got', d[0][:3], 'exp', d[1][:3], d[2], d[3])
    allins = set(range(rig.fa, rig.fa + rig.fsz, 4))
    unc = sorted(allins - rig.cov)
    print('random-CIF cases', n, 'mismatches', bad, 'instruction coverage', len(rig.cov & allins), '/', len(allins),
          '(uncovered are literal-pool words:', [hex(a) for a in unc], ')')
    # 2) every real CIF: every (anim, dir slot, frame) with several parameter variants
    files = sorted(os.path.join(r, f) for r, _, fs in os.walk(resdir) for f in fs if f.endswith('_CIF'))
    rng = random.Random(777)
    n2 = bad2 = 0
    for p in files:
        rel = os.path.relpath(p, resdir).replace(os.sep, '/')
        data = open(p, 'rb').read()
        mode = None
        for cand in (0x11, 2):
            try:
                if cifmod.parse(data, cand)['end'] == len(data):
                    mode = cand; break
            except Exception:
                pass
        assert mode is not None, rel
        rig.load(rng, data, mode, resdir, rel)
        for variant in range(3):
            rig.randomize_state(rng)
            if variant == 0:
                rig.env.lcd[9] = 0; rig.env.f68 = rig.env.f6e = 0
                rig.h.write(rig.lcd, bytes(rig.env.lcd)); rig.h.write(rig.spr + 0x68, b'\0'); rig.h.write(rig.spr + 0x6e, b'\0')
            for a, dirs in enumerate(rig.env.anims):
                for d in range(len(dirs) + 1):
                    for f in range(len(dirs[min(d, len(dirs) - 1)])):
                        p6 = d if d < len(dirs) else 3
                        ok, dd = rig.call(rng, a, 2, 3, p6, f, rng.randint(0, 240), rng.randint(0, 320), rng.choice([0, 1]),
                                          0xffffffff, rng.choice([0xffffffff, 0xffffffff, 2]))
                        n2 += 1
                        if not ok:
                            bad2 += 1
                            if bad2 <= 5:
                                print('REAL DIFF', rel, a, d, f, 'got', dd[0][:2], 'exp', dd[1][:2])
    print('real-CIF files', len(files), 'draw cases', n2, 'mismatches', bad2)
    import shutil
    shutil.rmtree(tmp, ignore_errors=True)
    return bad + bad2


if __name__ == '__main__':
    if sys.argv[1] == 'selftest':
        sys.exit(1 if selftest(sys.argv[2], sys.argv[3]) else 0)
