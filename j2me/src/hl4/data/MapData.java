package hl4.data;

import hl4.sys.Bytes;

/**
 * MAP/M/_MAP_M_### (docs/phaseC_map.md; verified against the original Map_SetMapData, 97/97 maps).  Raw parse only: the game-object
 * factories and the monster tables are Phase E.  All counts are UNSIGNED bytes except where noted; list counts and ids >= 0x80
 * behave as 0 and consume nothing (original quirk @0xab5b0).
 */
public final class MapData {
    public int[] tilesetA;       // _map[0x271+i]  (0xFF = unused)
    public int[] tilesetB;       // _map[0x274+i]  (UNKNOWN, always 0xFF in the data)
    public byte[] name0;         // EUC-KR, area name   (len byte <= 0 -> empty)
    public byte[] name1;         // EUC-KR, sub-area name
    public int unk0;
    public int w;                // tiles (one tile = 16 px)
    public int h;
    public int[] objImg;         // object image ids (Map_objIdxCreate)
    public int[] sprIds;         // sprite-object ids
    public byte[] layerA;        // w*h
    public byte[] layerB;        // w*h
    public int[][] objs;         // 4 lists (moreBottom, bottom, middle, top); 6 ints per record: b0, b1, b2, b3, x(s16), y(s16)
    public int[] summon;         // monster-type indices
    public int[] mons;           // 3 ints per entry: type, x, y
    public int end;              // bytes consumed

    public static MapData parse(byte[] b) {
        MapData d = new MapData();
        int p = 0;
        int n = b[p++] & 0xff;
        d.tilesetA = new int[n];
        d.tilesetB = new int[n];
        for (int i = 0; i < n; i++) {
            d.tilesetA[i] = b[p++] & 0xff;
            d.tilesetB[i] = b[p++] & 0xff;
        }
        d.name0 = str(b, p);
        p += strLen(b, p);
        d.name1 = str(b, p);
        p += strLen(b, p);
        d.unk0 = b[p] & 0xff;
        d.w = b[p + 1] & 0xff;
        d.h = b[p + 2] & 0xff;
        int cnt = b[p + 3] & 0xff;
        p += 4;
        if (cnt != 0 && cnt < 0x80) {
            d.objImg = ubytes(b, p, cnt);
            p += cnt;
        } else {
            d.objImg = new int[0];
        }
        cnt = b[p++] & 0xff;
        if (cnt != 0 && cnt < 0x80) {
            d.sprIds = ubytes(b, p, cnt);
            p += cnt;
        } else {
            d.sprIds = new int[0];
        }
        int tiles = d.w * d.h;
        d.layerA = new byte[tiles];
        System.arraycopy(b, p, d.layerA, 0, tiles);
        p += tiles;
        d.layerB = new byte[tiles];
        System.arraycopy(b, p, d.layerB, 0, tiles);
        p += tiles;
        d.objs = new int[4][];
        for (int l = 0; l < 4; l++) {
            int c = b[p++] & 0xff;
            int[] r = new int[6 * c];
            for (int i = 0; i < c; i++) {
                r[6 * i] = b[p] & 0xff;
                r[6 * i + 1] = b[p + 1] & 0xff;
                r[6 * i + 2] = b[p + 2] & 0xff;
                r[6 * i + 3] = b[p + 3] & 0xff;
                r[6 * i + 4] = Bytes.s16(b, p + 4);
                r[6 * i + 5] = Bytes.s16(b, p + 6);
                p += 8;
            }
            d.objs[l] = r;
        }
        int c = b[p++] & 0xff;
        if (c > 0 && c < 0x80) {
            d.summon = ubytes(b, p, c);
            p += c;
        } else {
            d.summon = new int[0];
        }
        c = b[p++] & 0xff;
        int k = (c > 0 && c < 0x80) ? c : 0;
        d.mons = new int[3 * k];
        for (int i = 0; i < 3 * k; i++) {
            d.mons[i] = b[p + i] & 0xff;
        }
        p += 3 * k;
        d.end = p;
        return d;
    }

    private static int strLen(byte[] b, int p) {
        int l = b[p];
        return l > 0 ? 1 + l : 1;
    }

    private static byte[] str(byte[] b, int p) {
        int l = b[p];
        if (l <= 0) {
            return new byte[0];
        }
        byte[] s = new byte[l];
        System.arraycopy(b, p + 1, s, 0, l);
        return s;
    }

    private static int[] ubytes(byte[] b, int p, int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = b[p + i] & 0xff;
        }
        return r;
    }

    /**
     * Monster placements exactly as Map_SetMapData walks the list (stream semantics, no boss rules): an entry whose type byte (s8)
     * is >= 3 consumes ONE byte and spawns nothing (QUIRK: its x/y bytes are re-read as new entries); otherwise 3 bytes
     * (type, x, y) spawn MONSTER_Clone(body, i, s8(x)*16+8, (y+1)*16).  Returns 3 ints per spawn: i, px, py.
     */
    public int[] monsterSpawns(byte[] b) {
        int n = mons.length / 3;
        int p = end - 3 * n;
        int[] out = new int[3 * (n + 1)];
        int cnt = 0;
        int i = 0;
        while (i != n) {
            int t = p < b.length ? b[p] : 0;
            if (t >= 3) {
                i++;
                p++;
                continue;
            }
            int x = p + 1 < b.length ? b[p + 1] : 0;       // s8
            int y = p + 2 < b.length ? b[p + 2] & 0xff : 0;
            out[3 * cnt] = i;
            out[3 * cnt + 1] = x * 16 + 8;
            out[3 * cnt + 2] = (y + 1) * 16;
            cnt++;
            p += 3;
            i++;
        }
        int[] r = new int[3 * cnt];
        System.arraycopy(out, 0, r, 0, 3 * cnt);
        return r;
    }
}
