#!/usr/bin/env python3
"""mk_images.py <jar_dir> <overlay_dir>: vá ảnh có chữ Hàn của Chroisen -> overlay/image/*.dat

Chữ Việt vẽ bằng font NAM.P f12 (kro/vi/font/f12.bin) qua bộ pixeltext (kro/img/pixeltext, của Tool_Viet_Hoa_NAM_P).
Mỗi ảnh = PNG bảng màu (tRNS 0/255); ảnh sau vá được lượng tử về đúng các màu đã dùng (không dither).
Nhãn ngắn hơn ô: khớp đúng ô gốc (hàng/cột giữ nguyên); logo Softmaru (logo.dat[3]) thay bằng logo NAMP (logo0.png).
"""
import sys, os, io, struct
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
os.environ.setdefault('PIXELTEXT_FONTS', os.path.join(HERE, 'fonts'))
from PIL import Image
import pixeltext.font as F
F.SIZES = F.SIZES + (12,); F.FONTS[12] = 'f12.bin'
import pixeltext.metrics as M
import pixeltext.paint as P
import datpack

BLACK = (0, 0, 0, 255)


def load(b):
    return Image.open(io.BytesIO(b)).convert('RGBA')


def save_png(im):
    """RGBA (alpha 0/255) -> palette PNG, exact colours, index 0 = transparent when present"""
    px = im.load(); w, h = im.size
    cols = {}
    trans = False
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 128: trans = True
    pal = [(0, 0, 0)] if trans else []
    idx = {}
    data = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 128 and trans: data.append(0); continue
            k = (r, g, b)
            if k not in idx:
                idx[k] = len(pal); pal.append(k)
            data.append(idx[k])
    if len(pal) > 256: raise SystemExit('quá 256 màu: %d' % len(pal))
    out = Image.new('P', (w, h))
    flat = []
    for c in pal: flat += list(c)
    out.putpalette(flat + [0] * (768 - len(flat)))
    out.putdata(data)
    bio = io.BytesIO()
    out.save(bio, 'PNG', transparency=0) if trans else out.save(bio, 'PNG')
    return bio.getvalue()


def lum(c): return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def sample(im, box):
    """(fill, outline) of a label cell: the commonest bright colour / the commonest dark colour"""
    x, y, w, h = box
    cnt = {}
    for yy in range(y, min(y + h, im.height)):
        for xx in range(x, min(x + w, im.width)):
            p = im.getpixel((xx, yy))
            if p[3] < 128: continue
            cnt[p[:3]] = cnt.get(p[:3], 0) + 1
    br = [(n, c) for c, n in cnt.items() if lum(c) >= 120]
    dk = [(n, c) for c, n in cnt.items() if lum(c) < 70]
    fill = max(br)[1] if br else (255, 255, 255)
    outline = max(dk)[1] if dk else None
    return fill, outline


def fits(text, w, size=12, gap=0):
    return M.measure(text, size=size, mode='tight', gap=gap)['w'] <= w


def put(im, box, cands, fill=None, outline='auto', align='center', gap=0, report=None, name=''):
    """clear `box` to transparent and write the first candidate text that fits the width"""
    x, y, w, h = box
    f0, o0 = sample(im, box)
    fill = fill or f0
    if outline == 'auto': outline = o0
    text = None
    for t in cands:
        if fits(t, w - (2 if outline else 0), gap=gap): text = t; break
    if text is None:
        text = cands[-1]
        if report is not None: report.append('%s: "%s" rộng %d > %d' % (name, text, M.measure(text, size=12, mode='tight', gap=gap)['w'], w))
    pxl = im.load()
    for yy in range(y, min(y + h, im.height)):
        for xx in range(x, min(x + w, im.width)):
            pxl[xx, yy] = (0, 0, 0, 0)
    P.draw(im, box, text, size=12, mode='tight', gap=gap, align=align, valign='center',
           fill=tuple(fill) + (255,), outline=(tuple(outline) + (255,)) if outline else None, clip=True)
    return text


def glow_cell(im, box, text, fill, glow, bg=BLACK, report=None, name=''):
    """opaque cell: text with a soft coloured glow (as the original title menu art)"""
    x, y, w, h = box
    cands = [text]
    m = M.layout(text, 12, 'tight', 0, None, 'left', 0, 1)
    tw = M.measure(text, size=12, mode='tight', gap=0)['w']
    if report is not None and tw > w - 6: report.append('%s: "%s" rộng %d > %d' % (name, text, tw, w - 6))
    pxl = im.load()
    for yy in range(y, y + h):
        for xx in range(x, x + w): pxl[xx, yy] = bg
    bx0, by0, bx1, by1 = m.ink_bbox()
    ox = x + (w - (bx1 - bx0 + 1)) // 2 - bx0
    oy = y + (h - (by1 - by0 + 1)) // 2 - by0
    ink = {(px + ox, py + oy) for (px, py) in m.pixels}
    def blend(p, c, a):
        if 0 <= p[0] < im.width and 0 <= p[1] < im.height:
            o = pxl[p[0], p[1]]
            pxl[p[0], p[1]] = tuple(int(o[i] + (c[i] - o[i]) * a) for i in range(3)) + (255,)
    layers = []
    cur = set(ink)
    for r, a in ((3, 0.25), (2, 0.5), (1, 0.8)):
        pass
    ring = {}
    for (px, py) in ink:
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                d = max(abs(dx), abs(dy))
                q = (px + dx, py + dy)
                if q in ink: continue
                if q not in ring or d < ring[q]: ring[q] = d
    for q, d in ring.items(): blend(q, glow, {1: 0.9, 2: 0.55, 3: 0.28}[d])
    for p in ink: blend(p, fill, 1.0)


def pack_dat(jar, ov, name, repl):
    ents = datpack.read(os.path.join(jar, 'image', name))
    for i, b in repl.items(): ents[i] = b
    os.makedirs(os.path.join(ov, 'image'), exist_ok=True)
    datpack.write(os.path.join(ov, 'image', name), ents)


def rows(n, h): return [(0, i * h, None, h) for i in range(n)]


def main(jar, ov):
    rep = []
    # ---------------- title.dat
    ents = datpack.read(os.path.join(jar, 'image', 'title.dat'))
    t = {}
    # [5] ranking headers 46x144: 6 rows of 24
    im = load(ents[5])
    labels = ['Hạng', 'Số', 'Nghề', 'Cấp', 'EXP', 'Hạng tôi']
    for i, s in enumerate(labels):
        glow_cell(im, (0, i * 24, 46, 24), s, (190, 235, 255), (30, 110, 210), report=rep, name='title5.%d' % i)
    t[5] = save_png(im)
    # [6] menu strip 74x336: 21 cells of 16 (white on black)
    im = load(ents[6])
    menu = [['Trợ giúp'], ['Hộp quà nhận', 'Hộp quà'], ['Bắt đầu'], ['Cài đặt'], ['Xếp hạng/Quà', 'Hạng/Quà'], ['Thoát game'], ['Chơi mới'], ['Chơi tiếp'],
            ['Phím điều khiển', 'Phím điều khiển'[:0] + 'Điều khiển'], ['Cốt truyện'], ['Thông tin game'], ['Liên hệ'], ['Hạng hầm ngục', 'Hạng dungeon'],
            ['Xóa dữ liệu'], ['Phí thông tin'], ['Cửa hàng Cash'], ['Khôi phục dữ liệu', 'Khôi phục'], ['Lưu dữ liệu'], ['Chọn nhân vật'],
            ['Quản lý dữ liệu', 'Quản lý DL'], ['Tặng vật phẩm']]
    for i, c in enumerate(menu):
        box = (0, i * 16, 74, 16)
        px = im.load()
        for yy in range(box[1], box[1] + 16):
            for xx in range(74): px[xx, yy] = BLACK
        done = False
        for size in ('13c', 12):
            for text in c:
                if M.measure(text, size=size, mode='tight', gap=1)['w'] + 1 <= 74:
                    for dx in (0,):
                        tmp = Image.new('RGBA', (74, 16), (0, 0, 0, 0))
                        P.draw(tmp, (0, 0, 74, 16), text, size=size, mode='tight', gap=1, align='center', valign='center',
                               dx=dx - 0, fill=(255, 255, 255, 255), clip=True)
                        tp = tmp.load()
                        for yy in range(16):
                            for xx in range(74):
                                if tp[xx, yy][3] > 0: px[xx, box[1] + yy] = (255, 255, 255, 255)
                    done = True; break
            if done: break
        if not done: rep.append('title6.%d: không vừa' % i)
    t[6] = save_png(im)
    # [11] class buttons 61x155: 5 cells of 31
    im = load(ents[11])
    cl = [('Slasher', (255, 235, 220), (255, 110, 30)), ('Assassin', (255, 225, 255), (190, 60, 220)), ('Knight', (225, 255, 225), (30, 190, 50)),
          ('Magician', (225, 235, 255), (60, 90, 255)), ('Chọn', (255, 240, 220), (255, 130, 30))]
    for i, (s, f, g) in enumerate(cl):
        glow_cell(im, (0, i * 31, 61, 31), s, f, g, report=rep, name='title11.%d' % i)
    t[11] = save_png(im)
    pack_dat(jar, ov, 'title.dat', t)
    # ---------------- logo.dat: Softmaru -> NAMP (full screen picture, drawn centred by the game)
    logo = Image.open(os.path.join(HERE, '..', '..', 'logo0.png')).convert('RGBA')
    pack_dat(jar, ov, 'logo.dat', {3: quant(logo)})
    # ---------------- uiinner.dat
    ents = datpack.read(os.path.join(jar, 'image', 'uiinner.dat'))
    u = {}
    im = load(ents[7])   # quest labels 50x39 : 3 rows of 13
    for i, c in enumerate([['Chính tuyến', 'NV chính', 'Chính'], ['Phụ tuyến', 'NV phụ', 'Phụ'], ['Điểm chỉ số', 'Điểm CS']]):
        put(im, (0, i * 13, 50, 13), c, report=rep, name='ui7.%d' % i)
    u[7] = save_png(im)
    im = load(ents[9])   # class filter 27x78 : 4 rows of 13?, then 공용, 재료 (see runs)
    ys = [0, 13, 26, 39, 52, 65]
    for i, c in enumerate([['SLA'], ['ASN'], ['KNT'], ['MAG'], ['Mọi'], ['NL']]):
        put(im, (0, ys[i], 27, 13), c, report=rep, name='ui9.%d' % i)
    u[9] = save_png(im)
    im = load(ents[16])  # 24x48: 4 rows of 12
    for i, c in enumerate([['Nghề'], ['Đổi'], ['Tỉnh'], ['SP:']]):
        put(im, (0, i * 12, 24, 12), c, outline='auto', align='left', report=rep, name='ui16.%d' % i)
    u[16] = save_png(im)
    im = load(ents[19])  # tabs 234x13: 6 slots of 39
    for i, c in enumerate([['Nhân vật', 'Hồ sơ'], ['Túi'], ['Trang bị', 'Đồ'], ['Kỹ năng'], ['Nhiệm vụ', 'Quest'], ['Hệ thống', 'Menu']]):
        put(im, (i * 39, 0, 39, 13), c, report=rep, name='ui19.%d' % i)
    u[19] = save_png(im)
    im = load(ents[20])  # actions 45x168: 14 rows of 12
    acts = [['Dùng'], ['Mặc'], ['Chi tiết'], ['Vứt'], ['Gán phím'], ['Đầu tư'], ['Mua'], ['Bán'], ['Xác nhận'], ['Hủy'], ['Dung hợp'], ['Tải về'], ['Tặng'], ['Tháo']]
    for i, c in enumerate(acts):
        put(im, (0, i * 12, 45, 12), c, report=rep, name='ui20.%d' % i)
    u[20] = save_png(im)
    im = load(ents[22])  # stats 19x70: 5 rows of 14
    for i, c in enumerate([['Sức'], ['Ma'], ['Mẫn'], ['Thể'], ['May']]):
        put(im, (0, i * 14, 19, 14), c, report=rep, name='ui22.%d' % i)
    u[22] = save_png(im)
    im = load(ents[23])  # derived stats 33x70: 5 rows of 14
    for i, c in enumerate([['Công'], ['Phép'], ['Thủ'], ['Crit'], ['Né']]):
        put(im, (0, i * 14, 33, 14), c, report=rep, name='ui23.%d' % i)
    u[23] = save_png(im)
    im = load(ents[29])  # reincarnation 18x13
    put(im, (0, 0, 18, 13), ['TS'], report=rep, name='ui29')
    u[29] = save_png(im)
    im = load(ents[25])  # 24x9: '#:Bỏ'
    put(im, (0, 0, 24, 9), ['#:Bỏ'], report=rep, name='ui25')
    u[25] = save_png(im)
    im = load(ents[26])  # bag hints 48x52: 4 rows of 13
    for i, c in enumerate([['#:Phá túi'], ['*:Xếp túi'], ['*:Hiệu ứng bộ', '*:Hiệu ứng'], ['#:Đóng #:Mở', '#:Đóng']]):
        put(im, (0, i * 13, 48, 13), c, align='left', report=rep, name='ui26.%d' % i)
    u[26] = save_png(im)
    pack_dat(jar, ov, 'uiinner.dat', u)
    for r in rep: print('CẢNH BÁO ảnh:', r)
    print('ảnh: title 3, logo 1, uiinner', len(u))


def quant(logo):
    """the NAMP logo (opaque) -> palette PNG <= 256 colours"""
    q = logo.convert('RGB').quantize(colors=250, method=Image.MEDIANCUT, dither=Image.NONE)
    bio = io.BytesIO(); q.save(bio, 'PNG', optimize=True)
    return bio.getvalue()


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
