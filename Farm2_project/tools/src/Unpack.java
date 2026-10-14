import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Unpacks every image packed inside the game's resource containers into a
 * plain PNG file, so the art can be edited without any tool.
 *
 * Containers: LBMP bundles (Image/**.dat, *.lbm) and PNG bundles (Image/**.dat,
 * Data/NPC/*.dat, Data/Pet/*.pet). Chunk i of "Dir/Name.ext" becomes
 * "Dir/Name/i.png" (i = ordinal of the image inside the container).
 *
 * In the container the chunk bytes are replaced by a reference of the SAME
 * length ("@PNG" u8 len, path, zero padding), so every offset, size table and
 * file length the game computes (fixed offsets, length / recordSize, Size.dat)
 * is unchanged; Image.createImage(byte[],off,len) sees the reference and loads
 * the PNG. Containers keep all their non-image bytes (names, sizes, records).
 *
 * LBMP is decoded with exactly the algorithm of the runtime shim
 * (org.kwis.msp.lcdui.Image.decodeLbmp); every written PNG is read back and
 * compared pixel by pixel (embedded PNGs are copied byte-exact).
 *
 * usage: Unpack <resourceDir> <report.txt>
 */
public class Unpack {
    static PrintStream rep;
    static int nLbmp, nPng, nFiles;
    static long pngBytes, srcBytes;

    public static void main(String[] a) throws Exception {
        File root = new File(a[0]);
        rep = new PrintStream(new FileOutputStream(a[1]), true, "UTF-8");
        List<File> fs = new ArrayList<File>();
        collect(new File(root, "Image"), fs);
        collect(new File(root, "Data"), fs);
        for (File f : fs) {
            unpack(root, f);
        }
        rep.println("files " + nFiles + " lbmp " + nLbmp + " png " + nPng + " srcBytes " + srcBytes + " pngBytes "
                + pngBytes);
        System.out.println("unpack: " + nFiles + " containers, " + nLbmp + " LBMP + " + nPng + " PNG chunks -> "
                + (nLbmp + nPng) + " frames -> " + nFiles + " sheets (" + pngBytes + " bytes)");
        rep.close();
    }

    static void collect(File d, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null) {
            return;
        }
        Arrays.sort(fs);
        for (File f : fs) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (!f.getName().endsWith(".png") && !f.getName().endsWith("Size.dat")) {
                out.add(f);
            }
        }
    }

    static int le32(byte[] d, int o) {
        return (d[o] & 0xff) | ((d[o + 1] & 0xff) << 8) | ((d[o + 2] & 0xff) << 16) | ((d[o + 3] & 0xff) << 24);
    }

    static int be32(byte[] d, int o) {
        return ((d[o] & 0xff) << 24) | ((d[o + 1] & 0xff) << 16) | ((d[o + 2] & 0xff) << 8) | (d[o + 3] & 0xff);
    }

    static final byte[] SIG = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 };

    static boolean at(byte[] d, int p, byte[] m) {
        if (p + m.length > d.length) {
            return false;
        }
        for (int i = 0; i < m.length; i++) {
            if (d[p + i] != m[i]) {
                return false;
            }
        }
        return true;
    }

    /** LBMP chunk length (header + pixel planes + optional mask plane), -1 if not plausible. */
    static int lbmpLen(byte[] d, int p) {
        if (p + 24 > d.length || d[p] != 'L' || d[p + 1] != 'B' || d[p + 2] != 'M' || d[p + 3] != 'P') {
            return -1;
        }
        int type = le32(d, p + 4), w = le32(d, p + 8), h = le32(d, p + 12), size = le32(d, p + 16),
                mask = le32(d, p + 20);
        if ((type != 2 && type != 8 && type != 16) || w <= 0 || h <= 0 || w > 1024 || h > 1024) {
            return -1;
        }
        int data = type == 2 ? size * 2 : size;
        int len = 24 + data + (mask == 1 ? ((h + 7) >> 3) * w : 0);
        return p + len <= d.length ? len : -1;
    }

    /** PNG stream length up to and including IEND, -1 if not a complete PNG. */
    static int pngLen(byte[] d, int p) {
        if (!at(d, p, SIG)) {
            return -1;
        }
        int q = p + 8;
        while (q + 12 <= d.length) {
            int n = be32(d, q);
            boolean iend = d[q + 4] == 'I' && d[q + 5] == 'E' && d[q + 6] == 'N' && d[q + 7] == 'D';
            q += 12 + n;
            if (iend) {
                return q <= d.length ? q - p : -1;
            }
        }
        return -1;
    }

    static void unpack(File root, File f) throws Exception {
        byte[] d = Files.readAllBytes(f.toPath());
        String rel = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        String base = rel.substring(0, rel.lastIndexOf('.'));
        List<Frame> frames = new ArrayList<Frame>();
        byte[] out = d.clone();
        for (int p = 0; p < d.length;) {
            int l = lbmpLen(d, p);
            boolean png = false;
            if (l < 0) {
                l = pngLen(d, p);
                png = l > 0;
            }
            if (l < 0) {
                p++;
                continue;
            }
            Frame fr = new Frame();
            fr.off = p;
            fr.len = l;
            if (png) {
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(Arrays.copyOfRange(d, p, p + l)));
                fr.w = bi.getWidth();
                fr.h = bi.getHeight();
                fr.px = new int[fr.w * fr.h];
                bi.getRGB(0, 0, fr.w, fr.h, fr.px, 0, fr.w);
                fr.info = "png";
                nPng++;
            } else {
                int[] wh = new int[3];
                fr.px = decodeLbmp(d, p, l, wh);
                fr.w = wh[0];
                fr.h = wh[1];
                fr.info = "lbmp" + le32(d, p + 4) + (wh[2] != 0 ? "+mask" : "");
                nLbmp++;
            }
            frames.add(fr);
            p += l;
        }
        if (frames.isEmpty()) {
            return;
        }
        nFiles++;
        sheet(root, rel, base, frames, d, out);
        Files.write(f.toPath(), out);
    }

    static class Frame {
        int off, len, w, h, x, y;
        int[] px;
        String info;
    }

    static final int GAP = 1, MAXW = 256;

    /**
     * Lays the frames of one container out on a sheet, in order, left to
     * right then top to bottom (uniform grid when all frames share a size),
     * writes Name.png + Name.txt (frame rectangles) and the references.
     */
    static void sheet(File root, String rel, String base, List<Frame> fs, byte[] d, byte[] out) throws Exception {
        int maxw = 0, maxh = 0;
        boolean uniform = true;
        for (Frame f : fs) {
            maxw = Math.max(maxw, f.w);
            maxh = Math.max(maxh, f.h);
            uniform &= f.w == fs.get(0).w && f.h == fs.get(0).h;
        }
        int limit = Math.max(MAXW, maxw);
        int x = 0, y = 0, rowh = 0, sw = 0;
        if (uniform) {
            int cols = Math.max(1, Math.min(fs.size(), (limit + GAP) / (maxw + GAP)));
            for (int i = 0; i < fs.size(); i++) {
                fs.get(i).x = (i % cols) * (maxw + GAP);
                fs.get(i).y = (i / cols) * (maxh + GAP);
            }
        } else {
            for (Frame f : fs) {
                if (x > 0 && x + f.w > limit) {
                    x = 0;
                    y += rowh + GAP;
                    rowh = 0;
                }
                f.x = x;
                f.y = y;
                x += f.w + GAP;
                rowh = Math.max(rowh, f.h);
            }
        }
        int sh = 0;
        for (Frame f : fs) {
            sw = Math.max(sw, f.x + f.w);
            sh = Math.max(sh, f.y + f.h);
        }
        int[] px = new int[sw * sh];
        boolean alpha = false;
        for (Frame f : fs) {
            for (int j = 0; j < f.h; j++) {
                for (int i = 0; i < f.w; i++) {
                    int c = f.px[j * f.w + i];
                    if ((c >>> 24) != 0xff) {
                        alpha = true;
                    }
                    px[(f.y + j) * sw + f.x + i] = c;
                }
            }
        }
        alpha |= fs.size() > 1; // gaps are transparent
        String png = base + ".png", txt = base + ".txt";
        byte[] img = encode(px, sw, sh, alpha);
        // read back, pixel-exact check of every frame
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(img));
        for (Frame f : fs) {
            int[] back = new int[f.w * f.h];
            bi.getRGB(f.x, f.y, f.w, f.h, back, 0, f.w);
            for (int i = 0; i < back.length; i++) {
                int a = f.px[i], b = back[i];
                if ((a >>> 24) == 0 ? (b >>> 24) != 0 : a != b) {
                    throw new RuntimeException("PNG mismatch " + png + " frame @" + f.off + " px " + i);
                }
            }
        }
        File o = new File(root, png);
        if (o.exists()) {
            throw new RuntimeException("name clash " + png);
        }
        Files.write(o.toPath(), img);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(png).append(": frames of ").append(rel).append('\n');
        sb.append("# frame x y w h   (edit the PNG; keep or update these rectangles)\n");
        byte[] pb = png.getBytes("US-ASCII");
        for (int i = 0; i < fs.size(); i++) {
            Frame f = fs.get(i);
            sb.append(i).append(' ').append(f.x).append(' ').append(f.y).append(' ').append(f.w).append(' ')
                    .append(f.h).append('\n');
            // reference: "@PNG" u8 len path u16 frame (big endian), zero padding; same length as the chunk
            if (7 + pb.length > f.len || pb.length > 255) {
                throw new RuntimeException("chunk too small for reference: " + png + " #" + i + " len " + f.len);
            }
            Arrays.fill(out, f.off, f.off + f.len, (byte) 0);
            out[f.off] = '@';
            out[f.off + 1] = 'P';
            out[f.off + 2] = 'N';
            out[f.off + 3] = 'G';
            out[f.off + 4] = (byte) pb.length;
            System.arraycopy(pb, 0, out, f.off + 5, pb.length);
            out[f.off + 5 + pb.length] = (byte) (i >> 8);
            out[f.off + 6 + pb.length] = (byte) i;
            rep.println(rel + " @" + f.off + "+" + f.len + " -> " + png + " #" + i + " " + f.x + "," + f.y + " "
                    + f.w + "x" + f.h + " (" + f.info + ")");
            srcBytes += f.len;
        }
        Files.write(new File(root, txt).toPath(), sb.toString().getBytes("US-ASCII"));
        pngBytes += img.length;
    }

    /** Same pixel math as org.kwis.msp.lcdui.Image.decodeLbmp; wh = {w, h, hasAlpha}. */
    static int[] decodeLbmp(byte[] d, int off, int len, int[] wh) {
        int type = le32(d, off + 4);
        int w = le32(d, off + 8);
        int h = le32(d, off + 12);
        int size = le32(d, off + 16);
        int mask = le32(d, off + 20);
        int p = off + 24;
        int plane = ((h + 7) >> 3) * w;
        int n = w * h;
        int[] out = new int[n];
        if (type == 16) {
            for (int i = 0; i < n; i++) {
                int c = (d[p + 2 * i] & 0xff) | ((d[p + 2 * i + 1] & 0xff) << 8);
                int r = (c >> 11) & 31, g = (c >> 5) & 63, b = c & 31;
                out[i] = 0xff000000 | (((r << 3) | (r >> 2)) << 16) | (((g << 2) | (g >> 4)) << 8) | ((b << 3) | (b >> 2));
            }
        } else if (type == 8) {
            for (int i = 0; i < n; i++) {
                int c = d[p + i] & 0xff;
                int r = (c >> 5) & 7, g = (c >> 2) & 7, b = c & 3;
                out[i] = 0xff000000 | ((r * 255 / 7) << 16) | ((g * 255 / 7) << 8) | (b * 85);
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int po = p + (y >> 3) * w + x;
                    int bit = y & 7;
                    int hi = (d[po] >> bit) & 1;
                    int lo = (d[po + size] >> bit) & 1;
                    int gray = 255 - (hi * 2 + lo) * 85;
                    out[y * w + x] = 0xff000000 | (gray << 16) | (gray << 8) | gray;
                }
            }
        }
        boolean alpha = false;
        if (mask == 1) {
            int m = p + (type == 2 ? size * 2 : size);
            if (m + plane <= off + len) {
                alpha = true;
                for (int y = 0; y < h; y++) {
                    int row = m + (y >> 3) * w;
                    int bit = 1 << (y & 7);
                    for (int x = 0; x < w; x++) {
                        if ((d[row + x] & bit) != 0) {
                            out[y * w + x] = 0;
                        }
                    }
                }
            }
        }
        wh[0] = w;
        wh[1] = h;
        wh[2] = alpha ? 1 : 0;
        return out;
    }

    /** Smallest of: indexed PNG (<= 256 colours, transparent index) or RGB/ARGB PNG. */
    static byte[] encode(int[] px, int w, int h, boolean alpha) throws Exception {
        Map<Integer, Integer> pal = new LinkedHashMap<Integer, Integer>();
        boolean hasT = false;
        for (int c : px) {
            if ((c >>> 24) == 0) {
                hasT = true;
            } else if (!pal.containsKey(c) && pal.size() <= 256) {
                pal.put(c, pal.size() + (0));
            }
        }
        byte[] best = null;
        int colors = pal.size() + (hasT ? 1 : 0);
        if (colors <= 256) {
            int n = Math.max(colors, 2);
            int bits = n <= 2 ? 1 : n <= 4 ? 2 : n <= 16 ? 4 : 8;
            byte[] r = new byte[n], g = new byte[n], b = new byte[n];
            int t = hasT ? 0 : -1;
            int k = hasT ? 1 : 0;
            Map<Integer, Integer> idx = new LinkedHashMap<Integer, Integer>();
            for (int c : pal.keySet()) {
                r[k] = (byte) (c >> 16);
                g[k] = (byte) (c >> 8);
                b[k] = (byte) c;
                idx.put(c, k++);
            }
            IndexColorModel cm = t >= 0 ? new IndexColorModel(bits, n, r, g, b, t) : new IndexColorModel(bits, n, r, g, b);
            BufferedImage bi = new BufferedImage(w, h, bits == 8 ? BufferedImage.TYPE_BYTE_INDEXED
                    : BufferedImage.TYPE_BYTE_BINARY, cm);
            java.awt.image.WritableRaster wr = bi.getRaster();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = px[y * w + x];
                    wr.setSample(x, y, 0, (c >>> 24) == 0 ? t : idx.get(c));
                }
            }
            best = write(bi);
        }
        BufferedImage bi = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, w, h, px, 0, w);
        byte[] full = write(bi);
        return best != null && best.length <= full.length ? best : full;
    }

    static byte[] write(BufferedImage bi) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bo);
        return bo.toByteArray();
    }
}
