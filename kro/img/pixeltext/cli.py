# -*- coding: utf-8 -*-
"""Command line front end - so an agent can use the toolkit without writing code.

  python3 -m pixeltext show     --text "Trò chơi mới" --size 13
  python3 -m pixeltext measure  --text "Trò chơi mới"
  python3 -m pixeltext check    --text "Trò chơi mới ★"
  python3 -m pixeltext fit      --text "Trò chơi mới" --box 64x16
  python3 -m pixeltext wrap     --text "..." --width 200 --size 13s
(--size: 18, 15, 13 = co chan; 15s, 13s = khong chan; 15c, 13c = chan chon loc; all = tat ca)
  python3 -m pixeltext draw     --text "..." --size 13 --scale 3 --out a.png
  python3 -m pixeltext compare  --text "..." --out cmp.png
  python3 -m pixeltext sheet    --size 13 --out sheet.png
  python3 -m pixeltext patch    --image ui.png --box 6,4,70,17 --text "..." --out ui2.png
"""
import argparse, json, sys
from .font import Font, SIZES, ALL, FONTS


from . import metrics as M
from . import paint as R
MODES = M.MODES


def _size(v):
    return v if v == 'all' else (int(v) if v.isdigit() else v)


def _box(v):
    v = v.replace('x', ',')
    p = [int(x) for x in v.split(',')]
    return tuple(p)


def main(argv=None):
    ap = argparse.ArgumentParser(prog='pixeltext', description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)

    def common(p, with_style=False):
        p.add_argument('--text', required=True)
        p.add_argument('--size', default='13')
        p.add_argument('--mode', default='metric', choices=MODES)
        p.add_argument('--gap', type=int, default=1)
        p.add_argument('--cell', type=int, default=None)
        p.add_argument('--scale', type=int, default=1)
        p.add_argument('--align', default='left', choices=['left', 'center', 'right'])
        p.add_argument('--line-spacing', type=int, default=0)
        if with_style:
            p.add_argument('--fill', default='#ffffff')
            p.add_argument('--gradient', default=None, help='c1,c2[,c3] top->bottom')
            p.add_argument('--outline', default=None)
            p.add_argument('--outline-diag', action='store_true')
            p.add_argument('--shadow', default=None)
            p.add_argument('--shadow-offset', default='1,1')
            p.add_argument('--bg', default=None)
            p.add_argument('--pad', type=int, default=0)
            p.add_argument('--trim', action='store_true')
            p.add_argument('--out', required=True)

    p = sub.add_parser('show', help='ASCII-art preview (no image needed)'); common(p)
    p.add_argument('--on', default='#'); p.add_argument('--off', default='.')

    p = sub.add_parser('measure', help='pixel size of the text'); common(p)
    p = sub.add_parser('check', help='report characters the font cannot draw')
    p.add_argument('--text', required=True); p.add_argument('--size', default='all')

    p = sub.add_parser('audit', help='report every ambiguous / duplicate glyph in a font')
    p.add_argument('--size', default='all')

    p = sub.add_parser('fit', help='pick the biggest size/mode that fits a box')
    p.add_argument('--text', required=True)
    p.add_argument('--box', required=True, type=_box, help='WxH or x,y,W,H')
    p.add_argument('--sizes', default='13s,13c,13,15s,15c,15,18')
    p.add_argument('--scales', default='1')
    p.add_argument('--no-wrap', action='store_true')
    p.add_argument('--pad', type=int, default=0)

    p = sub.add_parser('wrap', help='insert newlines to fit a pixel width'); common(p)
    p.add_argument('--width', type=int, required=True)

    p = sub.add_parser('draw', help='render text to a PNG'); common(p, True)

    p = sub.add_parser('compare', help='same text in every size')
    p.add_argument('--text', required=True); p.add_argument('--out', required=True)
    p.add_argument('--scale', type=int, default=3)
    p.add_argument('--fg', default='#ffffff'); p.add_argument('--bg', default='#101018')
    p.add_argument('--mode', default='metric', choices=MODES)

    p = sub.add_parser('sheet', help='charset contact sheet')
    p.add_argument('--size', default='13'); p.add_argument('--out', required=True)
    p.add_argument('--cols', type=int, default=16); p.add_argument('--scale', type=int, default=3)

    p = sub.add_parser('patch', help='replace the text inside a box of an existing PNG')
    p.add_argument('--image', required=True); p.add_argument('--out', required=True)
    p.add_argument('--box', required=True, type=_box)
    p.add_argument('--text', required=True)
    p.add_argument('--size', default='13')
    p.add_argument('--mode', default='metric', choices=MODES)
    p.add_argument('--gap', type=int, default=1)
    p.add_argument('--scale', type=int, default=1)
    p.add_argument('--align', default='center'); p.add_argument('--valign', default='center')
    p.add_argument('--dx', type=int, default=0); p.add_argument('--dy', type=int, default=0)
    p.add_argument('--fill-rule', default='bright', choices=['bright', 'mode', 'flat'])
    p.add_argument('--fill', default=None); p.add_argument('--outline', default=None)
    p.add_argument('--clear', default=None)
    p.add_argument('--auto', action='store_true', help='auto pick size/mode to fit the box')

    a = ap.parse_args(argv)

    if a.cmd == 'check':
        sizes = ALL if a.size == 'all' else [_size(a.size)]
        out, amb = {}, {}
        for s in sizes:
            f = Font.load(s)
            out['%spx' % s] = ''.join(f.missing(a.text))
            amb['%spx' % s] = [{'char': c, 'looks_like': o} for c, o in f.ambiguous_in(a.text)]
        print(json.dumps({'text': a.text, 'missing': out, 'ambiguous': amb},
                         ensure_ascii=False, indent=1))
        return 0 if not any(out.values()) and not any(amb.values()) else 1

    if a.cmd == 'audit':
        sizes = ALL if a.size == 'all' else [_size(a.size)]
        rep = {}
        for s in sizes:
            f = Font.load(s)
            amb = f.ambiguous()
            groups = sorted({''.join(sorted([c] + v)) for c, v in amb.items()})
            rep['%spx' % s] = {'height': f.height, 'cap': f.cap_height,
                               'x_height': f.x_height, 'baseline': f.baseline,
                               'chars': len(f.glyphs),
                               'ambiguous_groups': groups,
                               'ambiguous_count': len(amb)}
        print(json.dumps(rep, ensure_ascii=False, indent=1))
        return 0

    if a.cmd == 'fit':
        plan = M.fit(a.text, a.box,
                     sizes=[_size(x) for x in a.sizes.split(',')],
                     scales=[int(x) for x in a.scales.split(',')],
                     allow_wrap=not a.no_wrap, pad=a.pad)
        print(json.dumps(plan, ensure_ascii=False))
        return 0 if plan else 1

    if a.cmd == 'compare':
        R.compare(a.text, scale=a.scale, fg=a.fg, bg=a.bg, mode=a.mode).save(a.out)
        print(a.out); return 0

    if a.cmd == 'sheet':
        R.sheet(_size(a.size), cols=a.cols, scale=a.scale).save(a.out)
        print(a.out); return 0

    if a.cmd == 'patch':
        from PIL import Image
        img = Image.open(a.image).convert('RGBA')
        kw = dict(size=_size(a.size), mode=a.mode, gap=a.gap, scale=a.scale,
                  align=a.align, valign=a.valign, dx=a.dx, dy=a.dy)
        if a.auto:
            plan = M.fit(a.text, a.box, pad=1)
            if plan is None:
                print(json.dumps({'error': 'nothing fits', 'box': a.box})); return 1
            kw.update(size=plan['size'], mode=plan['mode'], gap=plan['gap'],
                      scale=plan['scale'])
            a.text = plan['text']
        if a.fill: kw['fill'] = a.fill
        if a.outline: kw['outline'] = a.outline
        if a.clear: kw['clear'] = a.clear
        info = R.patch(img, tuple(a.box), a.text,
                       keep_style=not (a.fill and a.outline and a.clear),
                       fill_rule=a.fill_rule, **kw)
        img.save(a.out)
        print(json.dumps(info, ensure_ascii=False)); return 0

    sizes = ALL if a.size == 'all' else [_size(a.size)]

    if a.cmd == 'measure':
        out = {}
        for s in sizes:
            m = M.measure(a.text, s, a.mode, a.gap, a.cell, a.scale, a.line_spacing)
            out['%spx' % s] = {k: m[k] for k in ('w', 'h', 'lines', 'line_widths')}
        print(json.dumps({'text': a.text, 'mode': a.mode, 'scale': a.scale,
                          'sizes': out}, ensure_ascii=False))
        return 0

    if a.cmd == 'wrap':
        print(M.wrap(a.text, a.width, sizes[0], a.mode, a.gap, a.cell))
        return 0

    if a.cmd == 'show':
        for s in sizes:
            f = Font.load(s)
            miss = f.missing(a.text)
            print("--- %spx  (height=%d cap=%d)%s" %
                  (s, f.height, f.cap_height, "  MISSING: " + ''.join(miss) if miss else ""))
            if miss: continue
            print(R.ascii_art(a.text, s, a.mode, a.gap, a.cell, a.scale, a.on, a.off))
        return 0

    if a.cmd == 'draw':
        fill = a.fill
        if a.gradient:
            fill = [c.strip() for c in a.gradient.split(',')]
        so = tuple(int(x) for x in a.shadow_offset.split(','))
        img = R.render(a.text, sizes[0], a.mode, a.gap, a.cell, a.align,
                       a.line_spacing, a.scale, fill, a.outline, a.outline_diag,
                       a.shadow, so, a.bg, a.pad, a.trim)
        img.save(a.out)
        print(json.dumps({'out': a.out, 'w': img.width, 'h': img.height}))
        return 0
    return 1


if __name__ == '__main__':
    sys.exit(main())
