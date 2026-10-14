# -*- coding: utf-8 -*-
"""Turning masks into pixels on a PIL image."""
import collections
from PIL import Image
from .font import Font, SIZES
from .metrics import Mask, layout, measure, wrap, fit


# ------------------------------------------------------------------- colours
def parse_color(c, default=(255, 255, 255, 255)):
    if c is None: return None
    if isinstance(c, (tuple, list)):
        t = tuple(int(v) for v in c)
        return t if len(t) == 4 else t + (255,)
    s = str(c).strip()
    if s.startswith('#'): s = s[1:]
    if len(s) == 3: s = ''.join(ch * 2 for ch in s)
    if len(s) == 6: s += 'ff'
    if len(s) != 8: raise ValueError("bad colour %r" % c)
    return tuple(int(s[i:i+2], 16) for i in (0, 2, 4, 6))


def gradient(stops, h):
    """stops = [c0, c1] or [(pos0,c0),(pos1,c1),...]  ->  callable(y)->rgba"""
    if callable(stops): return stops
    if isinstance(stops, dict): return lambda y: stops.get(y, (255, 255, 255, 255))
    pts = []
    if stops and not isinstance(stops[0], (tuple, list)) or (
            stops and isinstance(stops[0], (tuple, list)) and len(stops[0]) in (3, 4)):
        n = len(stops)
        pts = [((i / (n - 1) if n > 1 else 0.0), parse_color(c)) for i, c in enumerate(stops)]
    else:
        pts = [(float(p), parse_color(c)) for p, c in stops]
    def f(y):
        t = (y / (h - 1)) if h > 1 else 0.0
        for i in range(len(pts) - 1):
            p0, c0 = pts[i]; p1, c1 = pts[i + 1]
            if p0 <= t <= p1:
                k = 0 if p1 == p0 else (t - p0) / (p1 - p0)
                return tuple(int(round(c0[j] + (c1[j] - c0[j]) * k)) for j in range(4))
        return pts[-1][1]
    return f


def _fillfn(fill, h):
    if fill is None: return lambda y: (255, 255, 255, 255)
    if callable(fill): return fill
    if isinstance(fill, dict): return lambda y: fill.get(y, (255, 255, 255, 255))
    if isinstance(fill, (list,)) and len(fill) > 1 and not isinstance(fill[0], int):
        return gradient(fill, h)
    c = parse_color(fill)
    return lambda y: c


# --------------------------------------------------------------- render text
def render(text, size=13, mode='metric', gap=1, cell=None, align='left',
           line_spacing=0, scale=1, fill='#ffffff', outline=None, outline_diag=False,
           shadow=None, shadow_offset=(1, 1), bg=None, pad=0, trim=False):
    """Render `text` to a new RGBA PIL image sized to the text (plus `pad`)."""
    m = layout(text, size, mode, gap, cell, align, line_spacing, scale)
    if trim: m = m.trimmed()
    ox = oy = pad
    if outline or shadow: ox += 1; oy += 1
    W = m.w + 2 * ox + (abs(shadow_offset[0]) if shadow else 0)
    H = m.h + 2 * oy + (abs(shadow_offset[1]) if shadow else 0)
    img = Image.new('RGBA', (max(W, 1), max(H, 1)), parse_color(bg) if bg else (0, 0, 0, 0))
    _blit(img, m, (ox, oy), fill, outline, outline_diag, shadow, shadow_offset)
    return img


def _blit(img, mask, at, fill, outline, outline_diag, shadow, shadow_offset):
    px = img.load()
    W, H = img.size
    ox, oy = at
    f = _fillfn(fill, mask.h)
    def put(x, y, c):
        if 0 <= x < W and 0 <= y < H and c is not None: px[x, y] = c
    if shadow:
        sc = parse_color(shadow)
        for (x, y) in mask.pixels: put(x + ox + shadow_offset[0], y + oy + shadow_offset[1], sc)
    if outline:
        oc = parse_color(outline)
        for (x, y) in mask.outline(outline_diag): put(x + ox, y + oy, oc)
    for (x, y) in mask.pixels: put(x + ox, y + oy, f(y))
    return img


# ------------------------------------------------------------ draw into image
def draw(img, box, text, size=13, mode='metric', gap=1, cell=None,
         align='center', valign='center', dx=0, dy=0, line_spacing=0, scale=1,
         fill='#ffffff', outline=None, outline_diag=False, shadow=None,
         shadow_offset=(1, 1), clear=None, trim_v=True, clip=True):
    """Draw text inside `box`=(x,y,w,h) of an existing RGBA image.  In place."""
    x0, y0, w, h = box
    if clear is not None:
        c = parse_color(clear)
        p = img.load()
        for y in range(h):
            for x in range(w):
                if 0 <= x0 + x < img.width and 0 <= y0 + y < img.height:
                    p[x0 + x, y0 + y] = c
    m = layout(text, size, mode, gap, cell,
               'left' if align == 'left' else ('right' if align == 'right' else 'center'),
               line_spacing, scale)
    bx0, by0, bx1, by1 = m.ink_bbox()
    tw = m.w
    th = (by1 - by0 + 1) if by1 >= by0 else 0
    pad = 1 if (outline or shadow) else 0
    ax = {'left': pad, 'center': (w - tw) // 2, 'right': w - tw - pad}[align] + dx
    if trim_v:
        ay = {'top': pad, 'center': (h - th) // 2, 'bottom': h - th - pad}[valign] - by0 + dy
    else:
        ay = {'top': pad, 'center': (h - m.h) // 2, 'bottom': h - m.h - pad}[valign] + dy
    sub = img if not clip else img
    _blit_clipped(img, m, (x0 + ax, y0 + ay), fill, outline, outline_diag,
                  shadow, shadow_offset, (x0, y0, w, h) if clip else None)
    return dict(w=tw, h=th, x=x0 + ax, y=y0 + ay, overflow_x=max(0, tw + 2 * pad - w),
                overflow_y=max(0, th + 2 * pad - h))


def _blit_clipped(img, mask, at, fill, outline, outline_diag, shadow, shadow_offset, cliprect):
    px = img.load(); W, H = img.size; ox, oy = at
    f = _fillfn(fill, mask.h)
    if cliprect: cx, cy, cw, ch = cliprect
    def put(x, y, c):
        if c is None: return
        if cliprect and not (cx <= x < cx + cw and cy <= y < cy + ch): return
        if 0 <= x < W and 0 <= y < H: px[x, y] = c
    if shadow:
        sc = parse_color(shadow)
        for (x, y) in mask.pixels: put(x + ox + shadow_offset[0], y + oy + shadow_offset[1], sc)
    if outline:
        oc = parse_color(outline)
        for (x, y) in mask.outline(outline_diag): put(x + ox, y + oy, oc)
    for (x, y) in mask.pixels: put(x + ox, y + oy, f(y))


# ------------------------------------------------------- sample colours back
def sample_style(img, box, bg=None, fill_rule='bright'):
    """Read (bg, per-row fill, outline) out of an existing text tile.

    Lets you drop new text into artwork and keep the original palette,
    gradient and outline.  fill_rule: 'bright' | 'mode' | 'flat'.
    """
    x0, y0, w, h = box
    q = img.load()
    bgc = parse_color(bg) if bg is not None else q[x0, y0]
    cols = collections.Counter(q[x0 + x, y0 + y] for x in range(w) for y in range(h))
    glyph = [c for c in cols if c != bgc and c[3] != 0]
    if not glyph: return bgc, {}, None
    lum = lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
    outc = min(glyph, key=lum)
    rows = {}
    if fill_rule == 'flat':
        cnt = collections.Counter(c for c in cols.elements() if c != bgc and c[3] != 0)
        if outc in cnt and len(cnt) > 1: del cnt[outc]
        flat = cnt.most_common(1)[0][0]
        rows = {y: flat for y in range(h)}
    else:
        for y in range(h):
            row = [q[x0 + x, y0 + y] for x in range(w)
                   if q[x0 + x, y0 + y] != bgc and q[x0 + x, y0 + y][3] != 0]
            if not row: continue
            if fill_rule == 'mode':
                cnt = collections.Counter(row)
                if outc in cnt and len(cnt) > 1: del cnt[outc]
                rows[y] = cnt.most_common(1)[0][0]
            else:
                rows[y] = max(row, key=lum)
    if rows:
        ks = sorted(rows)
        near = lambda y: rows[min(ks, key=lambda k: abs(k - y))]
        rows = {y: rows.get(y) or near(y) for y in range(h)}
    return bgc, rows, outc


def patch(img, box, text, keep_style=True, fill_rule='bright', **kw):
    """Replace the text inside `box` of `img`, reusing the original colours."""
    if keep_style:
        bgc, rows, outc = sample_style(img, box, kw.pop('bg', None), fill_rule)
        kw.setdefault('fill', (lambda y: rows.get(y, (255, 255, 255, 255))))
        kw.setdefault('outline', outc)
        kw.setdefault('clear', bgc)
    return draw(img, box, text, **kw)


# -------------------------------------------------------------------- previews
BLOCK = ' .:#'

def ascii_art(text, size=13, mode='metric', gap=1, cell=None, scale=1,
              on='#', off='.', border=True):
    """Text rendering as ASCII art - lets a model check the result without an image."""
    m = layout(text, size, mode, gap, cell, 'left', 0, scale)
    lines = []
    for y in range(m.h):
        lines.append(''.join(on if (x, y) in m.pixels else off for x in range(m.w)))
    if border:
        bar = '+' + '-' * m.w + '+'
        lines = [bar] + ['|' + l + '|' for l in lines] + [bar]
    return '\n'.join(lines)


def sheet(size=13, cols=16, scale=3, fg='#ffffff', bg='#101018', label=True):
    """Full charset contact sheet for one font."""
    f = Font.load(size)
    chars = [c for c in f.chars if c != ' ']
    cw = max(g.advance for g in f.glyphs.values()) + 2
    ch = f.height + 2
    rows = (len(chars) + cols - 1) // cols
    img = Image.new('RGBA', (cw * cols * scale, ch * rows * scale), parse_color(bg))
    for i, c in enumerate(chars):
        m = layout(c, f, 'metric')
        sub = m.scaled(scale)
        _blit(img, sub, ((i % cols) * cw * scale + scale, (i // cols) * ch * scale + scale),
              fg, None, False, None, (0, 0))
    return img


def compare(text, sizes=SIZES, scale=3, fg='#ffffff', bg='#101018',
            mode='metric', gap=1, labels=True, pad=6):
    """One image showing the same text in every size, for eyeballing."""
    from PIL import ImageDraw
    parts = []
    for s in sizes:
        f = Font.load(s)
        m = layout(text, f, mode, gap).scaled(scale)
        img = Image.new('RGBA', (m.w + 2 * pad, m.h + 2 * pad), (0, 0, 0, 0))
        _blit(img, m, (pad, pad), fg, None, False, None, (0, 0))
        parts.append((s, f, img))
    lw = 92 if labels else 0
    W = lw + max(p[2].width for p in parts) + pad * 2
    H = sum(p[2].height + pad for p in parts) + pad
    out = Image.new('RGBA', (W, H), parse_color(bg))
    d = ImageDraw.Draw(out)
    y = pad
    for s, f, img in parts:
        if labels:
            d.text((6, y + 2), "%spx" % s, fill=(255, 214, 110, 255))
            d.text((6, y + 14), "h=%d cap=%d" % (f.height, f.cap_height), fill=(150, 190, 230, 255))
        out.alpha_composite(img, (lw, y))
        y += img.height + pad
    return out
