package hl4.core;

import java.util.Vector;

/**
 * Host drawn, full screen replacements of some game windows (Vietnamese build).
 *
 * The game keeps running its own window logic (keys, cursor, values); this class only watches the
 * texts the game draws during a frame (TextEngine.Sink) and, when it recognises a window, paints a
 * new full screen layout over the finished frame, re-using the game's values and parts of its
 * picture (portrait, highlight state).
 */
public class UiOverlay implements TextEngine.Sink {
    static final class T {
        int x, y, align;
        String s;
    }

    private final VnFont font;
    private final int fh;
    private final Vector texts = new Vector();
    private final Vector pool = new Vector();
    public boolean enabled = true;
    /** One line debug banner (save diagnostics), shown for noteFrames frames. */
    public String note;
    public int noteFrames;

    public void note(String s) {
        note = s;
        noteFrames = 27 * 5;
    }

    public UiOverlay(VnFont font) {
        this.font = font;
        this.fh = font.height;
    }

    public void text(int gfx, int x, int y, int maxW, int f, int align, int color, String s) {
        T t = pool.isEmpty() ? new T() : (T) pool.lastElement();
        if (!pool.isEmpty()) pool.removeElementAt(pool.size() - 1);
        t.x = x;
        t.y = y;
        t.align = align;
        t.s = s;
        texts.addElement(t);
    }

    /** Called with the finished frame just before it is shown. */
    public void compose(int[] frame, int w, int h) {
        try {
            if (enabled && w == 240 && h == 320) {
                if (find("Status", 100, 105, 80, 86) != null) status(frame);
            }
            if (note != null && noteFrames > 0 && w == 240) {
                noteFrames--;
                fb = frame;
                fill(0, 0, 240, fh + 2, 0xFF000000);
                text(note, 2, 1, 0xFFFFFF40, 0);
            }
        } finally {
            for (int i = 0; i < texts.size(); i++) pool.addElement(texts.elementAt(i));
            texts.removeAllElements();
        }
    }

    // ------------------------------------------------------------------
    // captured text helpers

    private T find(String s, int x0, int x1, int y0, int y1) {
        for (int i = 0; i < texts.size(); i++) {
            T t = (T) texts.elementAt(i);
            if (t.x >= x0 && t.x <= x1 && t.y >= y0 && t.y <= y1 && clean(t.s).equals(s)) return t;
        }
        return null;
    }

    /** First text inside the box (control codes removed), or "". */
    private String at(int x0, int x1, int y0, int y1) {
        for (int i = 0; i < texts.size(); i++) {
            T t = (T) texts.elementAt(i);
            if (t.x >= x0 && t.x <= x1 && t.y >= y0 && t.y <= y1) return clean(t.s);
        }
        return "";
    }

    static String clean(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("`{}&|$^<'\"".indexOf(c) >= 0) continue;
            if (c == ';') c = ' ';
            if (c == '=') c = ' ';
            sb.append(c);
        }
        return sb.toString().trim();
    }

    static int num(String s) {
        int v = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                v = v * 10 + (c - '0');
                any = true;
            } else if (any) break;
        }
        return v;
    }

    /** "a/b" -> {a, b} */
    static int[] frac(String s) {
        int k = s.indexOf('/');
        if (k < 0) return new int[]{num(s), num(s)};
        return new int[]{num(s.substring(0, k)), num(s.substring(k + 1))};
    }

    // ------------------------------------------------------------------
    // drawing

    static final int BG = 0xFF101418, PANEL = 0xFF1C2228, LINE = 0xFF5A6470, LINE_HI = 0xFFB8C8A0,
            GOLD = 0xFFE8C860, TXT = 0xFFE8E8E8, DIM = 0xFFA0A8B0, TITLE = 0xFF2C3A20,
            HPC = 0xFFD83030, SPC = 0xFF3070D8, EXPC = 0xFF30B8A8, BARBG = 0xFF30343A, SEL = 0xFFF0E040;

    private int[] fb;

    private void fill(int x, int y, int w, int h, int c) {
        int x1 = Math.max(0, x), y1 = Math.max(0, y), x2 = Math.min(240, x + w), y2 = Math.min(320, y + h);
        for (int yy = y1; yy < y2; yy++) {
            int o = yy * 240;
            for (int xx = x1; xx < x2; xx++) fb[o + xx] = c;
        }
    }

    private void rect(int x, int y, int w, int h, int c) {
        fill(x, y, w, 1, c);
        fill(x, y + h - 1, w, 1, c);
        fill(x, y, 1, h, c);
        fill(x + w - 1, y, 1, h, c);
    }

    private void panel(int x, int y, int w, int h) {
        fill(x, y, w, h, PANEL);
        rect(x, y, w, h, LINE);
    }

    int textWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int a = font.advance(s.charAt(i));
            w += a < 0 ? (s.charAt(i) == ' ' ? 3 : 6) : a;
        }
        return w;
    }

    /** align: 0 left, 1 centre, 2 right. y = top of the text line. */
    private void text(String s, int x, int y, int color, int align) {
        if (align == 1) x -= textWidth(s) / 2;
        else if (align == 2) x -= textWidth(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int[] p = font.pixels(c);
            int a = font.advance(c);
            if (p != null) {
                for (int k = 0; k < p.length; k++) {
                    int px = x + (p[k] & 0xFF), py = y + (p[k] >> 8);
                    if (px >= 0 && px < 240 && py >= 0 && py < 320) fb[py * 240 + px] = color;
                }
            }
            x += a < 0 ? (c == ' ' ? 3 : 6) : a;
        }
    }

    private void bar(int x, int y, int w, int h, int v, int max, int color) {
        fill(x, y, w, h, BARBG);
        int f = max <= 0 ? 0 : (int) ((long) w * Math.min(v, max) / max);
        fill(x, y, f, h, color);
        fill(x, y, f, 1, (color & 0xFEFEFE) >> 1 | 0xFF808080);
        rect(x - 1, y - 1, w + 2, h + 2, LINE);
    }

    /** Copy a region of the game picture, scaled up by s (nearest). */
    private void blit(int[] src, int sx, int sy, int sw, int sh, int dx, int dy, int s) {
        for (int y = 0; y < sh * s; y++) {
            int ty = dy + y;
            if (ty < 0 || ty >= 320) continue;
            int so = (sy + y / s) * 240 + sx;
            for (int x = 0; x < sw * s; x++) {
                int tx = dx + x;
                if (tx >= 0 && tx < 240) fb[ty * 240 + tx] = src[so + x / s];
            }
        }
    }

    private boolean edge(int[] src, int x0, int x1, int y) {
        return selected(src, x0, y, x1, y);
    }

    private boolean selected(int[] src, int x0, int y0, int x1, int y1) {
        // the game marks the chosen stat box with a light (yellow/white) frame
        for (int x = x0; x <= x1; x++) {
            int c = src[y0 * 240 + x];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (r > 200 && g > 200 && b < 140) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // character status

    private int[] src = new int[240 * 320];

    private void status(int[] frame) {
        System.arraycopy(frame, 0, src, 0, src.length);
        fb = frame;
        String name = at(40, 135, 98, 108);
        String race = at(40, 135, 116, 128);
        String cls = at(40, 135, 130, 142);
        int level = num(at(170, 239, 104, 112));
        int[] hp = frac(at(60, 135, 152, 160));
        int[] sp = frac(at(60, 135, 164, 172));
        int exp = num(at(60, 135, 179, 186));
        int next = num(at(60, 135, 188, 195));
        String[] lab = {"Tấn công", "Phòng thủ", "Kháng phép", "Kháng", "Chính xác", "Đỡ đòn", "Né tránh", "Chí mạng"};
        int[] ys = {123, 132, 141, 150, 159, 168, 177, 186};
        String[] val = new String[8];
        for (int i = 0; i < 8; i++) val[i] = at(170, 239, ys[i] - 2, ys[i] + 2);
        String pts = at(40, 100, 221, 228);
        String str = at(125, 155, 208, 215), agi = at(180, 215, 208, 215);
        String vit = at(125, 155, 221, 228), inte = at(180, 215, 221, 228);
        // selection frame: yellow left/right edges of the value boxes (STR/VIT x 128, AGI/INT x 180)
        boolean sStr = edge(src, 127, 129, 215), sAgi = edge(src, 179, 181, 215);
        boolean sVit = edge(src, 127, 129, 229), sInt = edge(src, 179, 181, 229);

        fill(0, 0, 240, 320, BG);
        // title bar
        fill(0, 0, 240, 20, TITLE);
        fill(0, 20, 240, 1, LINE_HI);
        text("1", 6, 2, DIM, 0);
        text("3", 234, 2, DIM, 2);
        text("Nhân vật", 120, 2, GOLD, 1);
        // name / class / level / exp
        panel(2, 23, 236, 46);
        text(name, 8, 25, TXT, 0);
        text(cls, 232, 25, GOLD, 2);
        text(race, 8, 40, DIM, 0);
        text("LV " + level, 232, 40, TXT, 2);
        text("EXP", 8, 54, DIM, 0);
        bar(34, 58, 110, 6, exp, next > 0 ? next : 1, EXPC);
        text(exp + "/" + next, 232, 54, TXT, 2);
        // portrait
        panel(2, 72, 96, 122);
        blit(src, 33, 100, 44, 54, 6, 76, 2);
        // hp / sp and combat values
        panel(101, 72, 137, 122);
        text("HP", 106, 74, DIM, 0);
        text(hp[0] + "/" + hp[1], 233, 74, TXT, 2);
        bar(106, 90, 126, 4, hp[0], hp[1], HPC);
        text("SP", 106, 95, DIM, 0);
        text(sp[0] + "/" + sp[1], 233, 95, TXT, 2);
        bar(106, 111, 126, 4, sp[0], sp[1], SPC);
        for (int i = 0; i < 4; i++) {
            text(lab[i], 106, 118 + i * 15, DIM, 0);
            text(val[i], 233, 118 + i * 15, TXT, 2);
        }
        panel(2, 197, 236, 64);
        for (int i = 4; i < 8; i++) {
            int col = (i - 4) % 2, row = (i - 4) / 2;
            text(lab[i], 8 + col * 118, 200 + row * 15, DIM, 0);
            text(val[i], 114 + col * 118, 200 + row * 15, TXT, 2);
        }
        fill(8, 232, 224, 1, LINE);
        text("Điểm", 8, 238, DIM, 0);
        text(pts, 114, 238, GOLD, 2);
        // base stats
        panel(2, 264, 236, 38);
        statBox(4, 267, "STR", str, sStr);
        statBox(62, 267, "AGI", agi, sAgi);
        statBox(120, 267, "VIT", vit, sVit);
        statBox(178, 267, "INT", inte, sInt);
        // soft keys
        fill(0, 304, 240, 16, TITLE);
        fill(0, 303, 240, 1, LINE_HI);
        text("Trở về", 4, 304, TXT, 0);
        text("Chọn", 236, 304, TXT, 2);
        text("1/3 đổi trang", 120, 304, DIM, 1);
    }

    private void statBox(int x, int y, String label, String v, boolean sel) {
        fill(x, y, 56, 32, sel ? 0xFF3A3A18 : 0xFF14181C);
        rect(x, y, 56, 32, sel ? SEL : LINE);
        if (sel) rect(x + 1, y + 1, 54, 30, SEL);
        text(label, x + 28, y + 1, sel ? SEL : DIM, 1);
        text(v, x + 28, y + 15, TXT, 1);
    }
}
