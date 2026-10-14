package wipi.host;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Vector;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Bo dieu khien font bitmap cho J2ME (CLDC 1.0, MIDP 2.0).
 * Co chu 18 / 15 / 13 px, 3 bien the:
 *   SERIF     co chan        (f18.bin f15.bin f13.bin) - net cua f18, rong co dinh 7
 *   SANS      khong chan     (f15s.bin f13s.bin)       - rong theo net, gon hon
 *   SELECTIVE chan chon loc  (f15c.bin f13c.bin)       - nhu SANS, rieng I i j l J 1 4 giu chan
 * 18px chi co ban co chan; SANS / SELECTIVE o 18 -> dung f18.
 *
 * Dinh dang file .bin (DataInputStream):
 *   UTF charset | byte height | byte width[n] | bit atlas (hang-truoc, LSB-first)
 * Atlas la mot dai ngang: glyph i nam o cot offset[i]..offset[i]+width[i]-1.
 *
 * Cach dung:
 *   BitmapFont.setDefault(BitmapFont.SIZE_13, BitmapFont.SELECTIVE);   // hoac SANS, SERIF; setDefault(SIZE_13) = co chan
 *   BitmapFont f = BitmapFont.current();
 *   g.setColor(0xFFFFFF);
 *   f.drawString(g, "Xin chào", x, y, Graphics.TOP | Graphics.LEFT);
 *   f.drawWrapped(g, text, x, y, 160, 2, Graphics.LEFT);
 */
public final class BitmapFont {

    public static final int SIZE_18 = 18;
    public static final int SIZE_15 = 15;
    public static final int SIZE_13 = 13;

    /** Bien the: co chan (nhu f18) / khong chan (gon hon) / chan chon loc. */
    public static final int SERIF = 0;
    public static final int SANS = 1;
    public static final int SELECTIVE = 2;

    /** Cac co chu co san; file: f<co><hau to>.bin, hau to theo bien the: "" / "s" / "c". */
    private static final int[] SIZES = { SIZE_18, SIZE_15, SIZE_13 };
    private static final String[] SUFFIX = { "", "s", "c" };
    /** Co nao co ban SANS / SELECTIVE. */
    private static final boolean[] HAS_STYLES = { false, true, true };

    /** Thu muc chua cac file font trong jar. */
    private static String resDir = "/font/";

    private static final BitmapFont[] fonts = new BitmapFont[SIZES.length * 3];
    private static BitmapFont current;

    private static final int CACHE = 4;

    // ------------------------------------------------------------ du lieu font
    private final int height;
    private final int baseline;       // hang day cua chu (tinh tu 0)
    private final char[] chars;       // da sap xep tang dan
    private final short[] sortedIdx;  // chars[k] -> chi so glyph
    private final short[] asciiIdx;   // tra nhanh cho ky tu < 128
    private final byte[] widths;
    private final int[] offsets;
    private final int atlasW;
    private final byte[] bits;
    private int spaceW;
    private int letterSpacing;

    // ------------------------------------------------------------ cache atlas theo mau
    private final int[] cacheCol = new int[CACHE];
    private final Image[] cacheImg = new Image[CACHE];
    private int cacheN;
    private boolean atlasOk = true;

    // ============================================================ quan ly co chu

    /** Doi thu muc chua file font (mac dinh "/font/"). Goi truoc lan nap dau tien. */
    public static void setResourceDir(String dir) {
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        resDir = dir;
    }

    /** Lay font co chan theo co chu (18, 15, 13). Xem get(int, int). */
    public static BitmapFont get(int size) {
        return get(size, SERIF);
    }

    /**
     * Lay font theo co chu (18, 15, 13) va bien the (SERIF / SANS / SELECTIVE); nap lan dau khi can.
     * Co khac -> co gan nhat (>=17 -> 18, 14..16 -> 15, <=13 -> 13). Bien the chua co -> co chan.
     * Tra ve null neu loi.
     */
    public static synchronized BitmapFont get(int size, int style) {
        int i = slot(size, style);
        if (fonts[i] == null) {
            fonts[i] = load(resDir + "f" + SIZES[i % SIZES.length] + SUFFIX[i / SIZES.length] + ".bin");
        }
        return fonts[i];
    }

    /** Chon co chu mac dinh (co chan) cho current(). */
    public static void setDefault(int size) {
        setDefault(size, SERIF);
    }

    /** Chon co chu + bien the mac dinh cho current(). */
    public static void setDefault(int size, int style) {
        BitmapFont f = get(size, style);
        if (f != null) {
            current = f;
        }
    }

    /** Font dang dung (mac dinh 15px). */
    public static BitmapFont current() {
        if (current == null) {
            current = get(SIZE_15);
        }
        return current;
    }

    /** Giai phong font khong dung (moi bien the) de tiet kiem bo nho. */
    public static synchronized void unload(int size) {
        for (int v = 0; v < SUFFIX.length; v++) {
            int i = slot(size, v);
            if (fonts[i] != null && fonts[i] == current) current = null;
            fonts[i] = null;
        }
    }

    private static int slot(int size, int style) {
        int k = size >= 17 ? 0 : size >= 14 ? 1 : 2;   // chi so trong SIZES
        if (style < 0 || style >= SUFFIX.length || !HAS_STYLES[k]) style = SERIF;
        return style * SIZES.length + k;
    }

    /** Nap font tu resource trong jar. */
    public static BitmapFont load(String path) {
        InputStream in = null;
        try {
            in = "".getClass().getResourceAsStream(path);
            if (in == null) return null;
            return new BitmapFont(new DataInputStream(in));
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception e) { }
            }
        }
    }

    /** Doc font tu mot luong bat ky (dinh dang .bin o tren). */
    public BitmapFont(DataInputStream in) throws java.io.IOException {
        String cs = in.readUTF();
        int n = cs.length();
        height = in.readUnsignedByte();
        widths = new byte[n];
        in.readFully(widths);
        offsets = new int[n];
        int w = 0;
        for (int i = 0; i < n; i++) {
            offsets[i] = w;
            w += widths[i];
        }
        atlasW = w;
        bits = new byte[(atlasW * height + 7) >> 3];
        in.readFully(bits);

        // bang tra ky tu
        asciiIdx = new short[128];
        for (int i = 0; i < 128; i++) asciiIdx[i] = -1;
        chars = new char[n];
        sortedIdx = new short[n];
        for (int i = 0; i < n; i++) {
            char c = cs.charAt(i);
            if (c < 128) asciiIdx[c] = (short) i;
            // chen co thu tu (n nho, chi chay mot lan)
            int k = i;
            while (k > 0 && chars[k - 1] > c) {
                chars[k] = chars[k - 1];
                sortedIdx[k] = sortedIdx[k - 1];
                k--;
            }
            chars[k] = c;
            sortedIdx[k] = (short) i;
        }
        int sp = indexOf(' ');
        spaceW = sp >= 0 ? widths[sp] : 4;
        baseline = findBaseline();
    }

    // ============================================================ thong so

    public int getHeight() { return height; }

    /** Hang day chu (y + getBaseline() = duong ke chan chu). */
    public int getBaseline() { return baseline; }

    /** Khoang cach them giua cac ky tu (mac dinh 0, co the am). */
    public void setLetterSpacing(int px) { letterSpacing = px; }

    public int getLetterSpacing() { return letterSpacing; }

    /** Font co ky tu nay khong. */
    public boolean hasChar(char c) { return indexOf(c) >= 0; }

    public int charWidth(char c) {
        int i = indexOf(c);
        return (i >= 0 ? widths[i] : spaceW) + letterSpacing;
    }

    public int stringWidth(String s) {
        return s == null ? 0 : substringWidth(s, 0, s.length());
    }

    public int substringWidth(String s, int start, int len) {
        int w = 0;
        for (int i = start, e = start + len; i < e; i++) {
            w += charWidth(s.charAt(i));
        }
        return w;
    }

    // ============================================================ ve chu

    /** Ve 1 ky tu tai (x, y) goc tren-trai; tra ve do rong da tien. */
    public int drawChar(Graphics g, char c, int x, int y) {
        int i = indexOf(c);
        if (i < 0) {
            return spaceW + letterSpacing;
        }
        Image img = atlas(g.getColor() & 0xFFFFFF);
        if (img != null) {
            g.drawRegion(img, offsets[i], 0, widths[i], height, 0, x, y, Graphics.TOP | Graphics.LEFT);
        } else {
            drawGlyphRGB(g, i, x, y);
        }
        return widths[i] + letterSpacing;
    }

    /**
     * Ve chuoi voi anchor giong Graphics.drawString:
     * LEFT/HCENTER/RIGHT ket hop TOP/VCENTER/BASELINE/BOTTOM. anchor = 0 tuong duong TOP|LEFT.
     * Ho tro '\n' (xuong dong, can le theo anchor ngang).
     */
    public void drawString(Graphics g, String s, int x, int y, int anchor) {
        if (s == null || s.length() == 0) return;
        if (s.indexOf('\n') >= 0) {
            String[] lines = split(s, '\n');
            int lh = height + 1;
            int total = lines.length * lh - 1;
            int yy = alignY(y, anchor, total);
            int h = anchor & (Graphics.LEFT | Graphics.HCENTER | Graphics.RIGHT);
            for (int i = 0; i < lines.length; i++) {
                drawLine(g, lines[i], x, yy, h);
                yy += lh;
            }
            return;
        }
        drawLine(g, s, x, alignY(y, anchor, height), anchor);
    }

    /** Ve chuoi voi mau chu va vien 1px xung quanh (hop cho chu tren nen anh). */
    public void drawOutlined(Graphics g, String s, int x, int y, int anchor, int color, int outline) {
        g.setColor(outline);
        drawString(g, s, x - 1, y, anchor);
        drawString(g, s, x + 1, y, anchor);
        drawString(g, s, x, y - 1, anchor);
        drawString(g, s, x, y + 1, anchor);
        g.setColor(color);
        drawString(g, s, x, y, anchor);
    }

    /** Ve chuoi voi bong do lech (1, 1). */
    public void drawShadow(Graphics g, String s, int x, int y, int anchor, int color, int shadow) {
        g.setColor(shadow);
        drawString(g, s, x + 1, y + 1, anchor);
        g.setColor(color);
        drawString(g, s, x, y, anchor);
    }

    /**
     * Tu dong ngat dong theo do rong maxW va ve tu (x, y) tro xuong.
     * hAnchor: Graphics.LEFT / HCENTER / RIGHT. Tra ve tong chieu cao da ve.
     */
    public int drawWrapped(Graphics g, String s, int x, int y, int maxW, int lineGap, int hAnchor) {
        String[] lines = wrap(s, maxW);
        int lh = height + lineGap;
        for (int i = 0; i < lines.length; i++) {
            drawLine(g, lines[i], x, y + i * lh, hAnchor);
        }
        return lines.length == 0 ? 0 : lines.length * lh - lineGap;
    }

    /** Ngat chuoi thanh cac dong khong vuot qua maxW (ngat o dau cach, cat tu qua dai). */
    public String[] wrap(String s, int maxW) {
        Vector out = new Vector();
        if (s != null) {
            String[] paras = split(s, '\n');
            for (int p = 0; p < paras.length; p++) {
                wrapPara(paras[p], maxW, out);
            }
        }
        String[] r = new String[out.size()];
        out.copyInto(r);
        return r;
    }

    /** So dong sau khi ngat (de tinh chieu cao khung hoi thoai). */
    public int lineCount(String s, int maxW) {
        return wrap(s, maxW).length;
    }

    // ============================================================ noi bo

    private int indexOf(char c) {
        if (c < 128) return asciiIdx[c];
        int lo = 0, hi = chars.length - 1;
        while (lo <= hi) {
            int m = (lo + hi) >>> 1;
            char v = chars[m];
            if (v < c) lo = m + 1;
            else if (v > c) hi = m - 1;
            else return sortedIdx[m];
        }
        return -1;
    }

    private boolean pixel(int x, int y) {
        int k = y * atlasW + x;
        return (bits[k >> 3] & (1 << (k & 7))) != 0;
    }

    private int findBaseline() {
        int i = indexOf('x');
        if (i < 0) i = indexOf('A');
        if (i < 0) return height - 1;
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < widths[i]; x++) {
                if (pixel(offsets[i] + x, y)) return y;
            }
        }
        return height - 1;
    }

    private int alignY(int y, int anchor, int h) {
        if ((anchor & Graphics.BOTTOM) != 0) return y - h;
        if ((anchor & Graphics.BASELINE) != 0) return y - baseline;
        if ((anchor & Graphics.VCENTER) != 0) return y - (h >> 1);
        return y;
    }

    private void drawLine(Graphics g, String s, int x, int y, int anchor) {
        if ((anchor & (Graphics.HCENTER | Graphics.RIGHT)) != 0) {
            int w = stringWidth(s);
            if ((anchor & Graphics.RIGHT) != 0) x -= w;
            else x -= w >> 1;
        }
        Image img = atlas(g.getColor() & 0xFFFFFF);
        int n = s.length();
        for (int k = 0; k < n; k++) {
            int i = indexOf(s.charAt(k));
            if (i < 0) {
                x += spaceW + letterSpacing;
                continue;
            }
            if (img != null) {
                g.drawRegion(img, offsets[i], 0, widths[i], height, 0, x, y, Graphics.TOP | Graphics.LEFT);
            } else {
                drawGlyphRGB(g, i, x, y);
            }
            x += widths[i] + letterSpacing;
        }
    }

    /** Anh atlas theo mau (LRU CACHE mau). Tra null neu may khong du bo nho. */
    private Image atlas(int rgb) {
        for (int i = 0; i < cacheN; i++) {
            if (cacheCol[i] == rgb) {
                Image img = cacheImg[i];
                if (i > 0) {
                    System.arraycopy(cacheCol, 0, cacheCol, 1, i);
                    System.arraycopy(cacheImg, 0, cacheImg, 1, i);
                    cacheCol[0] = rgb;
                    cacheImg[0] = img;
                }
                return img;
            }
        }
        if (!atlasOk) return null;
        Image img;
        try {
            int[] px = new int[atlasW * height];
            int argb = 0xFF000000 | rgb;
            int k = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < atlasW; x++, k++) {
                    if ((bits[k >> 3] & (1 << (k & 7))) != 0) px[k] = argb;
                }
            }
            img = Image.createRGBImage(px, atlasW, height, true);
        } catch (Throwable t) {
            atlasOk = false;
            return null;
        }
        int keep = cacheN < CACHE ? cacheN : CACHE - 1;
        System.arraycopy(cacheCol, 0, cacheCol, 1, keep);
        System.arraycopy(cacheImg, 0, cacheImg, 1, keep);
        cacheCol[0] = rgb;
        cacheImg[0] = img;
        if (cacheN < CACHE) cacheN++;
        return img;
    }

    /** Xoa cache anh (goi khi doi nhieu mau hoac thieu bo nho). */
    public void clearCache() {
        for (int i = 0; i < CACHE; i++) cacheImg[i] = null;
        cacheN = 0;
        atlasOk = true;
    }

    /** Du phong khi khong tao duoc atlas: ve tung glyph bang drawRGB. */
    private void drawGlyphRGB(Graphics g, int i, int x, int y) {
        int w = widths[i];
        int off = offsets[i];
        int[] px = new int[w * height];
        int argb = 0xFF000000 | g.getColor();
        for (int yy = 0; yy < height; yy++) {
            for (int xx = 0; xx < w; xx++) {
                if (pixel(off + xx, yy)) px[yy * w + xx] = argb;
            }
        }
        g.drawRGB(px, 0, w, x, y, w, height, true);
    }

    private void wrapPara(String s, int maxW, Vector out) {
        int n = s.length();
        int start = 0;
        while (start < n) {
            // bo dau cach dau dong (tru dong dau)
            if (start > 0) {
                while (start < n && s.charAt(start) == ' ') start++;
                if (start >= n) break;
            }
            int w = 0;
            int lastSpace = -1;
            int i = start;
            while (i < n) {
                char c = s.charAt(i);
                int cw = charWidth(c);
                if (w + cw > maxW && i > start) break;
                if (c == ' ') lastSpace = i;
                w += cw;
                i++;
            }
            if (i >= n) {
                out.addElement(s.substring(start));
                return;
            }
            int end = lastSpace > start ? lastSpace : i;
            out.addElement(s.substring(start, end));
            start = end;
        }
        if (n == 0) out.addElement("");
    }

    private static String[] split(String s, char sep) {
        Vector v = new Vector();
        int p = 0;
        int q;
        while ((q = s.indexOf(sep, p)) >= 0) {
            v.addElement(s.substring(p, q));
            p = q + 1;
        }
        v.addElement(s.substring(p));
        String[] r = new String[v.size()];
        v.copyInto(r);
        return r;
    }
}
