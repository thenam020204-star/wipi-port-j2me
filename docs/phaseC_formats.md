# Phase C — Định dạng tài nguyên (AND2011)

Nguồn sự thật: AND2011 `binary.mod` + `res/` trong `jarfile.mp3`. Địa chỉ = vaddr ELF.
Phương pháp kiểm chứng: **kiểm thử vi sai**. Cài đặt Python viết theo disassembly, rồi so byte-for-byte hoặc pixel-for-pixel
với chính hàm gốc chạy trong emulator đối chứng (`tools/and_emu.py`, Unicorn — chỉ là công cụ, không phải runtime sản phẩm).

| Định dạng | Công cụ | Kiểm chứng | Kết quả |
|---|---|---|---|
| Mã hoá tài nguyên/save (DES biến thể) | `tools/hl4des.py` | `selftest`: 408 file nhóm DES, giải mã + mã hoá, so với `MX_desDecrypt`/`MX_desEncrypt` gốc | **0 lệch** (`work/cache/des_selftest.txt`) |
| Ảnh GBM (container + 4/8 bpp) | `tools/gbm.py` | `selftest`: 452 file, 4067 ảnh vẽ qua `DM_LoadMergeGbm565` + `DM_DrawImg` gốc; thêm 2 ảnh lớn (`tdf/TITLE_BM` 320×320, `ITM/_ITM_ICN` 533×13) | **0 lệch** |
| Font HNF + ghép âm tiết Hàn 8×4×4 | `tools/hf_font.py` | `selftest`: 2350 âm tiết KS X 1001 + 82 ký tự ASCII, mỗi ký tự vẽ qua `HF_DrawText` gốc | **0 lệch** (`docs/phaseC_font_selftest.txt`) |
| Text MrgText (`s8 count`, `{u16be len, bytes}`) | `tools/mrgtext.py` | 6 file, so với `MrgText_load` gốc | **0 lệch** |
| Text LinkedText (`u16 size, u16 count, u16 off[]`) | `tools/linkedtext.py` | 12 file, mọi chỉ số, so với `getLinkedText` gốc | **0 lệch** |
| Chỉ mục sprite CIF | `tools/cif.py` | 148 file, so bảng offset khung với `SPR_loadSprite` gốc | **0 lệch** (`docs/phaseC_cif_selftest.txt`) |
| Hình học `DM_DrawImg` | `tools/dm_blit.py` | `selftest`: so cả khung 240×400, 1.776.085 ca; kế hoạch cắt 907.137 ca | **0 lệch** (`docs/phaseC_blit.md`) |
| Phân loại DES/thô | `tools/and_resmap.py` → `docs/resmap_and2011.tsv` | theo nơi gọi `UM_LoadResDes` / `UM_LoadRes` | CONFIRMED |

## 1. Nạp tài nguyên (CONFIRMED)
- `UM_LoadRes(path, &size)` @0xca200 → `CUSTOM_knlGetResourceID` → `tempLocalGetExactFilename` → `MC_knlGetResourceID` → `MC_knlGetResource`. Không giải mã.
- `UM_LoadResDes` @0xca16c giống hệt, rồi gọi thêm `MX_desDecrypt(buf, size)`.
- LZMA (`LzmaDecode`, `MX_fxGetLzmaFile`) là code chết. Không tài nguyên nào nén.

### Nhóm mã hoá DES (CONFIRMED, 24 nơi gọi `UM_LoadResDes`)
`E/_BSDAT_%d`, `E/_ESDAT_%d`, `FR/_FR_{BA,PL,EN,SU,SK}`, `HDAT/_H_S%03d`, `HDAT/_H_{SA,BH,BS,SS}`, `ITM/DAT/*`,
`NPC/_QUEST_%d_DAT`, `NPC/NPCUI_ARMSSHOP_DAT_%d`, `NPC/_NPCUI_{COMBINE,SHOP}_DAT_%d`, `NPC/PROBABILITY_DAT`, `MAP/SC/{e,n}%04d_scn`.
`NpcUiGuardianShop_dataLoad` và `Item_RepaySet` nhận đường dẫn qua tham số, nên chưa gắn được file cụ thể (INFERRED: `NPC/NPCUI_GUARDIANSHOP_DAT`, `ITM/DAT/_ITM_REPAY_%d`).
Mọi thứ khác (ảnh, sprite, map, font, text, AI, palette, âm thanh) nạp **thô**.

## 2. Mã hoá DES biến thể (CONFIRMED, `tools/hl4des.py`)
- Khoá: 8 ký tự đầu của `"J@IWO8N7L0E7E"` (@0xd9830; `SaveDataInit` → `MX_desInit`, `strncpy(…, 8)` @0x2add8).
- Lịch khoá chuẩn: PC1, các bước xoay `{1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1}`, PC2 → `KEY4REAL[16][48]`. Bản Python khớp `KEY4REAL` của hàm gốc.
- IP, IP⁻¹, E, P, PC1, PC2 giống FIPS 46-3. **S-box khác FIPS đúng một ô**: S1 hàng 3 cột 10 = **2** (FIPS = 3), byte 0xe0d46.
- ECB theo khối 8 byte; `len % 8` byte cuối giữ nguyên, không padding.
- Mã hoá (`mode 1`, khoá xuôi): `IP` → `(a, b) = (trái, phải)` → 16 vòng `a, b = b, a ^ f(b, K)` → `swap32bit` → xuất `IP⁻¹(a‖b)`.
- Giải mã (`mode 0`, khoá đảo): `(a, b) = (phải, trái)` → 16 vòng như trên → **không** `swap32bit` (nhánh 0x27f78 → 0x27e1c) → xuất `IP⁻¹(b‖a)`.
- `decrypt(encrypt(x)) == x` (đã kiểm). Thư viện DES chuẩn **không** giải được (vì ô S-box lệch).
- Trạng thái `KEY4ENCRYPT`: lần gọi encrypt/decrypt đầu tiên đổi chiều sẽ đảo thứ tự khoá con (hoán đổi hàng i ↔ 15−i), rồi ghi cờ. Hành vi quan sát được không phụ thuộc thứ tự gọi.

## 3. Ảnh GBM (CONFIRMED, `tools/gbm.py`)
- Container ảnh gộp (`getMergedGbmData` @0xd2af0): `u32le count`, rồi `count × {u32le size, body[size]}`. Có 452 file như vậy.
- Body (`DM_LoadMergeGbm565` @0x51234): `u8 type`, `u16le w`, `u16le h`, `u16le aux`, `u16le ncolors`, `ncolors × u16le RGB565`, rồi điểm ảnh.
  - `type 0x0c`: 8 bpp, stride = `w` byte.
  - `type 0x0b`: 4 bpp, stride = `(w+1)/2` byte (hàng lẻ được đệm), **nibble cao = pixel trái** (@0x3df94).
  - Thống kê: 3824 ảnh 4 bpp, 245 ảnh 8 bpp; không có kiểu khác.
- Trong suốt: so **màu sau khi tra palette** với `SKIP_COL`. `DM_Initialize` gán `SKIP_COL = GetPixelFromRGB(255, 0, 255) = 0xF81F`. Không dựa vào chỉ số palette.
- `aux`: các bộ blit 565 không dùng trường này (UNKNOWN; thường bằng `w` hoặc `w − 1`).
- Bộ blit: `DM_DrawImg` (clip theo `OutR`, lật 0..3, xoay 0/90/180/270, neo) → `DM_DrawScreen565` / `DM_DrawScreen565Ex` / `DM_DrawRotate565`
  (con trỏ do `DM_Initialize` gán). Phần lật/xoay/Ex/blend chưa kiểm vi sai (việc tiếp theo).
- Nạp đơn (`DM_LoadResGbm565` @0x53d54) có cùng layout body nhưng không có container.

## 4. Font HNF (CONFIRMED, `tools/hf_font.py`)
- Header: `'HNF'`, `u8 kind` (0 Hàn / 1 Latin), `u8 rowbits` (16 ⇒ 2 byte/hàng, 8 ⇒ 1), `u8 h`; font Hàn thêm `u8 8, 4, 4` (số bộ).
- `_FT_KOR10X11`: 160 phụ âm đầu (8 bộ × 20) + 88 nguyên âm (4 × 22) + 112 phụ âm cuối (4 × 28), mỗi glyph 22 byte.
  Mỗi hàng 2 byte: **byte 1 = 8 pixel trái, byte 0 = 8 pixel phải**, MSB = pixel trái nhất.
- Ghép âm tiết:
  - `code = HANINFO[(lead−0xB0)·94 + (trail−0xA1)]` (u16 big-endian, Johab);
  - `cho = HANCODE[(code>>10)&31]`, `jung = HANCODE[31 + ((code>>5)&31)]`, `jong = HANCODE[62 + (code&31)]`;
  - các bộ glyph: `choSet = ChoInfo[22·hasJong + jung]`, `jungSet = JungInfo[20·hasJong + cho]`, `jongSet = JongInfo[jung]`;
  - ghép = copy glyph phụ âm đầu, rồi OR glyph nguyên âm (nếu `jung ≠ 0`) và glyph phụ âm cuối (nếu có).
- Latin: `_FT_ENGL5X11` (font lớn, h = 11) và `_FT_ENGS7X7` (font nhỏ, h = 8), 95 glyph `' '..'~'`, 1 byte/hàng, MSB trái.
  Độ rộng = 5 px (`\` = 7 px ở font lớn; `\` vẽ glyph ô 4).
- Glyph được vẽ với gốc tại đúng `(x, y)` truyền vào `HF_DrawText`.
- Chưa kiểm vi sai: font nhỏ `ENGS7X7`, jamo rời `0xA4xx`, các mã đặc biệt `0xA3BF`/`0xA5xx`, mã màu, xuống dòng `;`,
  khoảng trắng 4 px, neo/căn giữa (`HF_DrawTextAnchorH2Color`) → làm cùng engine chữ ở Phase E.

## 5. Text (CONFIRMED)
- **MrgText** (`MrgText_load` @0x9e5c4): `s8 count`, rồi `count × {u16be len, byte[len]}`. Không có ký tự kết thúc.
  Có 6 file: `ITM/_ITM_SD0..2`, `tdf/_tdf_OPT`, `tdf/_tdf_SMENU`, `tdf/_tdf_TIP`.
- **LinkedText** (`getLinkedText` @0x9e514): `u16le size(=len file)`, `u16le count`, `u16le offset[count]` (tuyệt đối), chuỗi kết thúc NUL.
  Có 12 file: `GMenu/_GMENU_TXT`, `_GMENU_SUB_TXT`, `_NPCUI_TXT`, `NPC/_EVENT_POP_TXT`, `TOUCH/txm02_t_*`, `tdf/_tdf_{FREE,FREE_LGT,HELP,MENU,UseInfo}_TXT`.
- Còn lại: `NPC/NAME_DAT` (`Event_LoadTextData`) có định dạng khác, chưa phân tích.

## 6. Sprite CIF (CONFIRMED cấu trúc, `tools/cif.py`)
- `s8 n0`, `n0` byte (UNKNOWN), `u8 nAnim`. Mỗi anim: `s8 nDir`; mỗi hướng: `u8 nFrames`; mỗi khung: `s8 nParts` + các mảnh.
- Kích thước mảnh phụ thuộc **mode do hàm nạp truyền vào**:
  - mode 2 (`HERO_spriteLoad` @0x7e564): 10 byte nếu `b0 & 0x80`, ngược lại 5 byte;
  - mode khác (0x11, ví dụ `loadSimpleSprite`): 9 hoặc 4 byte.
- Bản gốc lưu, cho mỗi (anim, hướng), một mảng offset tới byte `nParts` của từng khung.
- Ý nghĩa các trường trong mảnh (toạ độ, chỉ số ảnh, lật…) nằm ở `SPR_drawSpriteNPC` (4128 byte): **chưa phân tích**, việc tiếp theo.

## 7. Còn lại (chưa phân tích)
`_PAL` (196), `_CIF` (148), `_EXD` (117), `_scn` (345, DES), `_DAT` (26), `_TXT` (8), `MAP/M/_MAP_M_%03d`, `TILE/`, `OBJ/`, `FT/` (font),
`E/AI%03d` (AI script), `SND/*_MMF` (SMAF) → MIDI/WAV.
