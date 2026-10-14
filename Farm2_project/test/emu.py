#!/usr/bin/env python3
"""Chạy game J2ME trong FreeJ2ME headless (Xvfb), bấm phím theo kịch bản, chụp màn.

  python3 run_emu.py --fj freej2me_plus.jar --jar game.jar --out shots/ \
      --steps "wait 10; shot boot; key w; wait 3; shot menu; key Return; wait 2; shot next"

Lệnh trong --steps (ngăn bằng ';'):
  wait <giây>        chờ
  shot <tên>         chụp màn hình game -> <out>/<tên>.png (đã cắt đúng vùng game)
  key <phím> [n]     bấm phím n lần (keysym X11: Return Up Down Left Right q w 0-9 ...)
Phím FreeJ2ME mặc định: q/w = soft trái/phải, Return = OK/fire, mũi tên, số 0-9.
In JSON: ảnh đã chụp + exception trong log. Lỗi MIDI "Sequencer ... is null" là do
container không có synth -> lỗi môi trường, không phải lỗi game (chọn tắt nhạc để né).
Cần: Xvfb, ImageMagick (import/convert), pip install python-xlib pillow.
"""
import argparse, json, os, signal, subprocess, time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--fj', required=True, help='freej2me_plus.jar')
    ap.add_argument('--jar', required=True)
    ap.add_argument('--out', default='shots')
    ap.add_argument('--steps', default='wait 10; shot boot')
    ap.add_argument('--size', default='240x320', help='độ phân giải màn game (xem MIDlet/ảnh nền)')
    ap.add_argument('--rawcrop', type=int, default=1)
    ap.add_argument('--keepraw', type=int, default=0)
    ap.add_argument('--ox', type=int, default=0)
    ap.add_argument('--oy', type=int, default=0)
    ap.add_argument('--home', default=None, help='HOME for the emulator (RMS lives under it)')
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    disp = ':%d' % (100 + os.getpid() % 400)
    xv = subprocess.Popen(['Xvfb', disp, '-screen', '0', a.size + 'x24'],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    env = dict(os.environ, DISPLAY=disp)
    env.pop('JAVA_TOOL_OPTIONS', None)
    if a.home:
        os.makedirs(a.home, exist_ok=True)
        env['HOME'] = os.path.abspath(a.home)
    logp = os.path.join(a.out, 'emu.log')
    log = open(logp, 'w')
    # Truyền sẵn ISO_8859_1 để FreeJ2ME không tự khởi động lại; jar truyền bằng đường dẫn thường.
    emu = subprocess.Popen(['java', '-Dfile.encoding=ISO_8859_1', '-jar', os.path.abspath(a.fj),
                            os.path.abspath(a.jar), '1'] + a.size.split('x') + ['1'],
                           env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)

    from Xlib import display as xd, X, XK
    from Xlib.ext import xtest
    from PIL import Image
    d = xd.Display(disp)
    shots = []

    def shot(name):
        raw = os.path.join(a.out, '.raw.png')
        subprocess.run(['import', '-display', disp, '-window', 'root', raw],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        im = Image.open(raw).convert('RGB')
        if a.rawcrop:
            W, H = [int(v) for v in a.size.split('x')]
            # locate the LCD: FreeJ2ME window at (0,0) with a menu bar; find first row below bar
            im.save(os.path.join(a.out, name + '_raw.png')) if a.keepraw else None
            f = os.path.join(a.out, name + '.png')
            im.crop((a.ox, a.oy, a.ox + W, a.oy + H)).save(f)
            os.remove(raw)
            shots.append(f)
            return
        # cắt bỏ nền đen Xvfb + thanh menu của FreeJ2ME: lấy bbox vùng khác đen
        bbox = im.point(lambda v: 255 if v > 8 else 0).convert('L').getbbox()
        if bbox:
            im = im.crop(bbox)
            # thanh menu "File Settings Debug" cao ~21px, nền xám sáng
            top = im.crop((0, 0, im.width, 1)).convert('L').getextrema()
            if top[0] > 180:
                im = im.crop((0, 21, im.width, im.height))
        f = os.path.join(a.out, name + '.png')
        im.save(f)
        os.remove(raw)
        shots.append(f)

    try:
        for step in [s.strip() for s in a.steps.split(';') if s.strip()]:
            p = step.split()
            if p[0] == 'wait':
                time.sleep(float(p[1]))
            elif p[0] == 'shot':
                shot(p[1])
            elif p[0] == 'hold':
                code = d.keysym_to_keycode(XK.string_to_keysym(p[1]))
                xtest.fake_input(d, X.KeyPress, code); d.sync(); time.sleep(float(p[2]))
                xtest.fake_input(d, X.KeyRelease, code); d.sync(); time.sleep(0.2)
            elif p[0] == 'key':
                code = d.keysym_to_keycode(XK.string_to_keysym(p[1]))
                for _ in range(int(p[2]) if len(p) > 2 else 1):
                    xtest.fake_input(d, X.KeyPress, code); d.sync(); time.sleep(0.12)
                    xtest.fake_input(d, X.KeyRelease, code); d.sync(); time.sleep(0.35)
    finally:
        alive = emu.poll() is None
        try:
            os.killpg(emu.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        xv.kill()
        log.close()

    lines = open(logp, errors='replace').read().splitlines()
    errs = [l for l in lines if ('Exception' in l or 'Error' in l) and not l.startswith('\tat ')]
    print(json.dumps({'shots': shots, 'emu_alive_at_end': alive,
                      'exceptions': sorted(set(errs))[:20], 'log': logp},
                     ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
