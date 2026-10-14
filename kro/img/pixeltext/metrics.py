# -*- coding: utf-8 -*-
"""Turning a string into a set of ink pixels: spacing, wrapping, fitting."""
from .font import Font, SIZES, height_of, style_rank

# spacing modes ---------------------------------------------------------------
#   'metric' : use each glyph's own advance width (what the font designer drew)
#   'tight'  : pack by real ink width + `gap` px  (fits small UI boxes)
#   'fixed'  : every glyph occupies `cell` px, centred
MODES = ('metric', 'tight', 'fixed')


class Mask(object):
    """A rendered block of text as a set of (x, y) ink pixels."""
    __slots__ = ('pixels', 'w', 'h', 'lines', 'font', 'scale', 'line_boxes')

    def __init__(self, pixels, w, h, lines, font, scale=1, line_boxes=None):
        self.pixels = pixels
        self.w, self.h = w, h
        self.lines = lines
        self.font = font
        self.scale = scale
        self.line_boxes = line_boxes or []

    def __repr__(self):
        return "<Mask %dx%d lines=%d px=%d>" % (self.w, self.h, len(self.lines), len(self.pixels))

    # ---------------------------------------------------------------- helpers
    def ink_bbox(self):
        if not self.pixels: return (0, 0, -1, -1)
        xs = [p[0] for p in self.pixels]; ys = [p[1] for p in self.pixels]
        return (min(xs), min(ys), max(xs), max(ys))

    def trimmed(self):
        """same mask with the empty border rows/cols removed"""
        x0, y0, x1, y1 = self.ink_bbox()
        if x1 < x0: return Mask(set(), 0, 0, self.lines, self.font, self.scale)
        px = {(x - x0, y - y0) for (x, y) in self.pixels}
        return Mask(px, x1 - x0 + 1, y1 - y0 + 1, self.lines, self.font, self.scale)

    def outline(self, diagonal=False):
        d = [(-1, 0), (1, 0), (0, -1), (0, 1)]
        if diagonal: d += [(-1, -1), (1, -1), (-1, 1), (1, 1)]
        out = set()
        for (x, y) in self.pixels:
            for dx, dy in d:
                p = (x + dx, y + dy)
                if p not in self.pixels: out.add(p)
        return out

    def scaled(self, k):
        if k == 1: return self
        px = {(x * k + dx, y * k + dy) for (x, y) in self.pixels
              for dx in range(k) for dy in range(k)}
        return Mask(px, self.w * k, self.h * k, self.lines, self.font, self.scale * k,
                    [(a * k, b * k, c * k, d * k) for (a, b, c, d) in self.line_boxes])


# ----------------------------------------------------------------- measuring
def advances(font, text, mode='metric', gap=1, cell=None):
    """-> list of (glyph, x_offset_of_ink, cell_width) for one line"""
    out, x = [], 0
    for ch in text:
        g = font.glyphs.get(ch)
        if g is None:
            raise KeyError("font %s has no glyph for %r" % (font.name, ch))
        if mode == 'metric':
            out.append((g, x + 0, g.advance)); x += g.advance
        elif mode == 'tight':
            if g.blank:
                out.append((g, x, font.space)); x += font.space
            else:
                out.append((g, x - g.x0, g.ink_w + gap)); x += g.ink_w + gap
        elif mode == 'fixed':
            c = cell or font.height // 2
            off = (c - g.ink_w) // 2 - g.x0 if not g.blank else 0
            out.append((g, x + off, c)); x += c
        else:
            raise ValueError("mode must be one of %s" % (MODES,))
    if out and mode == 'tight' and not out[-1][0].blank:
        x -= gap
    return out, x


def line_width(font, text, mode='metric', gap=1, cell=None):
    return advances(font, text, mode, gap, cell)[1]


def measure(text, size=13, mode='metric', gap=1, cell=None, scale=1,
            line_spacing=0, trim=False):
    """-> dict(w, h, lines, font) without building any pixels."""
    f = size if isinstance(size, Font) else Font.load(size)
    lines = text.split('\n')
    ws = [line_width(f, ln, mode, gap, cell) for ln in lines]
    w = max(ws) if ws else 0
    h = len(lines) * f.height + (len(lines) - 1) * line_spacing
    if trim:
        m = layout(text, f, mode, gap, cell, line_spacing=line_spacing).trimmed()
        w, h = m.w, m.h
    return dict(w=w * scale, h=h * scale, lines=len(lines),
                line_widths=[x * scale for x in ws], font=f)


# ------------------------------------------------------------------- layout
def layout(text, size=13, mode='metric', gap=1, cell=None,
           align='left', line_spacing=0, scale=1):
    f = size if isinstance(size, Font) else Font.load(size)
    lines = text.split('\n')
    laid, widths = [], []
    for ln in lines:
        a, w = advances(f, ln, mode, gap, cell)
        laid.append(a); widths.append(w)
    W = max(widths) if widths else 0
    H = len(lines) * f.height + (len(lines) - 1) * line_spacing
    px, boxes = set(), []
    y = 0
    for a, w in zip(laid, widths):
        ox = {'left': 0, 'center': (W - w) // 2, 'right': W - w}[align]
        for g, gx, _ in a:
            if g.blank: continue
            for yy in range(f.height):
                row = g.bitmap[yy]
                for xx in range(g.advance):
                    if row[xx]: px.add((ox + gx + xx, y + yy))
        boxes.append((ox, y, w, f.height))
        y += f.height + line_spacing
    m = Mask(px, W, H, lines, f, 1, boxes)
    return m.scaled(scale) if scale > 1 else m


# ------------------------------------------------------------------ wrapping
def wrap(text, max_w, size=13, mode='metric', gap=1, cell=None):
    """greedy word wrap at `max_w` pixels; returns the text with \\n inserted"""
    f = size if isinstance(size, Font) else Font.load(size)
    out = []
    for para in text.split('\n'):
        cur = ''
        for word in para.split(' '):
            trial = word if not cur else cur + ' ' + word
            if line_width(f, trial, mode, gap, cell) <= max_w or not cur:
                cur = trial
            else:
                out.append(cur); cur = word
        out.append(cur)
    return '\n'.join(out)


# -------------------------------------------------------------------- fitting
def fit(text, box, sizes=SIZES, modes=('metric', 'tight'), scales=(1,),
        allow_wrap=True, gaps=(1, 0), line_spacing=0, pad=0):
    """Pick the biggest (size, mode, scale) whose rendering fits `box`.

    box = (w, h) or (x, y, w, h).  Returns a plan dict, or None if nothing fits.
    Preference order: bigger font > metric spacing > co chan > chan chon loc > khong chan > bigger scale.
    """
    if len(box) == 4: _, _, bw, bh = box
    else: bw, bh = box
    bw -= 2 * pad; bh -= 2 * pad
    best = None
    for scale in sorted(scales, reverse=True):
        for size in sorted(sizes, key=lambda s: (height_of(s), style_rank(s)), reverse=True):
            f = Font.load(size)
            for mode in modes:
                for gap in (gaps if mode == 'tight' else (1,)):
                    variants = [text]
                    if allow_wrap and (' ' in text or '\n' in text):
                        variants.append(wrap(text, bw // scale, f, mode, gap))
                    for txt in variants:
                        m = measure(txt, f, mode, gap, scale=scale, line_spacing=line_spacing)
                        if m['w'] <= bw and m['h'] <= bh:
                            cand = dict(size=size, mode=mode, scale=scale, text=txt,
                                        gap=gap, line_spacing=line_spacing,
                                        w=m['w'], h=m['h'], lines=m['lines'])
                            rank = (height_of(size), mode == 'metric', style_rank(size), scale, -m['lines'], -m['w'])
                            if best is None or rank > best[0]: best = (rank, cand)
    return best[1] if best else None
