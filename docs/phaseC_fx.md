# Phase C — Pha màu, trong suốt, co giãn và palette (`DM_*`, AND2011)

Nguồn sự thật: `binary.mod` AND2011. Địa chỉ = vaddr ELF. Công cụ: `tools/dm_fx.py`. Kết quả: `docs/phaseC_fx_selftest.txt`.
Tất cả đều là nhánh **RGB565** (`_lcdG[0x92] == 1`; `DM_Initialize` gán `SKIP_COL = 0xF81F` và các con trỏ `DM_DrawScreen*` bản 565).
Nhánh 666 (`_lcdG[0x92] == 2`) có trong mã nhưng không dùng trên máy đích (INFERRED từ Phase B: khung 16 bpp) — không port.

Phương pháp như các phần trước: mô hình Python viết theo disassembly/mã giả, rồi so với hàm gốc trong emulator đối chứng
(nền ngẫu nhiên không đồng nhất để phép trộn thật sự được thử).

## 1. Bảng trộn `BlendingTable32Bit` (CONFIRMED)
`DM_Initialize` gọi `DM_SetBlendingTable565(modes, 16)` (@0x562d4) với mã mode `0..15`; bảng 16 × 32 × 32 byte tại `0xf6284`,
`T[mode][a][b]` với `a` = kênh nguồn (0..31), `b` = kênh đích. Công thức từng mode (`na = 31 − a`; kết quả bị chặn `[0,31]`):
| mode | công thức | | mode | công thức |
|---|---|---|---|---|
| 0 | `a` (thay thế) | | 8 | `a + b − 31` |
| 1 | `min(a,b)` | | 9 | `2a + b − 31` |
| 2 | `max(a,b)` | | 10 | `b > 16 ? 31 − (na·(31−b) >> 4) : a·b >> 4` |
| 3 | `a·b / 31` | | 11 | `a ≥ 17 ? 31 − (na·(31−b) >> 4) : a·b >> 4` |
| 4 | `31 − na·(31−b) / 31` | | 12 | `b` kẹp vào `[2a−31, 2a]` rồi vào `[0,31]` |
| 5 | `a == 31 ? 31 : b·31 / na` | | 13 | `a + b ≥ 31 ? 31 : 0` |
| 6 | `31 − (31−b)·31 / (a+1)` | | 14 | `|a − b|` |
| 7 | `a + b` | | 15 | `a + b − (a·b >> 4)` |
Chia là chia nguyên C (cắt về 0). Selftest: bảng dựng từ mô hình **bằng từng byte** bảng của hàm gốc.
Chỉ số effect > 16 trỏ ra ngoài bảng (đọc đối tượng `.bss` khác) — UNDEFINED; trong dữ liệu CIF thật effect ∈ 0..17 (effect 17 xuất hiện 4/79.162 mảnh dài).

## 2. `DM_DrawImgEx` → `DM_DrawScreen565Ex` (CONFIRMED)
`DM_DrawImgEx(frame, x, y, w, h, img, srcx, srcy, flip, rot, alpha, effect, anchor)` @0x4ee60. Gọi `DM_DrawScreen565Ex(fb, X, Y, W, H, fbw, fbh, img, SX, SY, aux, h, mode, alpha, effect)` @0x3fdec. Không vẽ gì nếu `alpha == 0`.
- **Kế hoạch cắt biên = `dm_blit.plan(..., ex=True)`**, trùng `DM_DrawImg` *trừ* `flip == 3`: ở đây `rot` **không** bị bỏ qua mà cộng gộp (quay 180° rồi quay `rot`):
  `rot 0 → mode 3`, `90 → 6`, `180 → 0`, `270 → 4`, khác → `0`. Kiểm: **960.498 ca** lưới dày, 0 lệch.
- **Quy tắc từng pixel** (kênh 5 bit; xanh lục dùng quy ước g5 = `(g6 & 0x7e0) >> 6`, tức *bỏ bit thấp nhất của cả nguồn lẫn đích*):
  - nguồn == `SKIP_COL` → không đổi;
  - `effect == 0x10`: `R = G = B = (6·rs + 4·gs + 2·bs) >> 4` (xám, bỏ qua đích);
  - ngược lại `R = T[eff][rs][rd]`, `G5 = T[eff][gs][gd]`, `B = T[eff][bs][bd]`;
  - `alpha < 255`: `ra = (alpha>>3)+1`, `ga = ((alpha&0xf8)>>2)+1`, `ba = alpha>>3` (**QUIRK: kênh xanh dương không cộng 1**),
    `R = (R·ra + rd·(32−ra)) >> 5`, `G6 = (2·G5·ga + 2·gd·(64−ga)) >> 6`, `B = (B·ba + bd·(32−ba)) >> 5`;
  - ghi `R<<11 | (G6<<5)&0x7e0 | B&0x1f`.
- Kiểm: 2.933 ca toàn khung 240×400 với nền ngẫu nhiên, 4/8 bpp, các lật/xoay, alpha đặc biệt (0, 7, 8, 9, 127, 128, 247, 248, 254, 255…), effect 0..16: 0 lệch.

## 3. Co giãn `DM_DrawImgRotate` / `DM_DrawImgRotateEx` (CONFIRMED)
Thực chất **không xoay góc tuỳ ý**: góc chỉ nhận 0/90/180/270 (giá trị khác = 0) kết hợp `flip`; phần "xoay" là **co giãn lân cận gần nhất**.
`DM_DrawImgRotate(frame, x, y, w, h, img, sx, sy, flip, rot, scaleX%, scaleY%, anchor)` @0x4f68c → `DM_DrawRotate565` @0x49d20;
`...Ex(…, scaleY%, effect, alpha, anchor)` @0x4f598 → `DM_DrawRotate565Ex` @0x4a308 (không làm gì nếu `alpha == 0`).
- Bảng mode: `flip,rot → mode` giống `DM_DrawImg` (flip 3 → mode 3 **bất kể rot**, khác `DrawImgEx`).
- Kích thước đích: `dw = max(1, w·scaleX/100)`, `dh = max(1, h·scaleY/100)` (chia C). Bước cố định 10 bit: `stepX = (w<<10)/dw`, `stepY = (h<<10)/dh`;
  chỉ số nguồn của pixel thứ `i` là `(i·(step+1)) >> 10` (**mỗi bước cộng `step+1`**, không phải `step`).
- Mode ≥ 4 hoán đổi hộp đích (rộng = `dh`, cao = `dw`); neo dùng `DM_CalcAnchor` @0x4ce4c (căn giữa `x −= w>>1`, phải `x −= w`, dọc tương tự) với kích thước *đã co giãn* (hoán đổi nếu mode ≥ 4).
- Cắt biên **từng pixel** theo `OutR` (không có kế hoạch cắt như `DM_DrawImg`).
- 4 bpp: chỉ số nguồn lấy nibble theo **chẵn/lẻ của cột tương đối** (QUIRK: nếu `srcx` lẻ thì chọn nibble sai so với pixel thật; sprite luôn dùng `srcx = 0`).
- `Ex` dùng đúng công thức pixel của §2 (`ba = alpha>>3`).
- Kiểm: 2.500 ca toàn khung (cả hai hàm, tỉ lệ 1..300 %, 4/8 bpp, mọi flip/rot, neo, hộp cắt, vùng con): 0 lệch.

## 4. Palette (CONFIRMED)
- `DM_RestorePalette(img)` @0x3d898: nếu `img != 0` và cờ `img+0x1c ≠ 0`: `+0x18 ← +8`, `+0x14 ← +0xc`, chép lại palette thân (`body+9`, `ncolors` mục u16) vào palette làm việc, xoá cờ.
- `DM_SetChagePalBlendEx(img, p2, p3, p4, mode, alpha)` @0x4e66c: bỏ qua nếu cờ == 1; ngược lại đổi palette **làm việc** (`+0xc`) rồi đặt cờ = 1:
  - `mode == 0x15` (HSB): nếu `p2=p3=p4=0` thì không làm gì (cờ không đổi). Mỗi màu ≠ `SKIP_COL`: RGB888 = `((c>>11)<<3, (c>>3)&0xf8, (c&0x1f)<<3)` → HSB; `H += p2` (±360); độ bão hoà: `p3>0: S += (100−S)·p3/100`, `p3<0: S += S·p3/100`; HSB → RGB;
    độ sáng: `p4>0: ch += (255−ch)·p4/100`, `p4<0: ch += ch·p4/100`; ghi lại 565 (kết quả == `SKIP_COL` thì thay bằng `0x821`).
  - `mode == 0x14`: pha tuyến tính với màu `tint` (chia C cho 100, kể cả alpha > 100 cho tử số âm).
  - `mode 0..15` và `0x10` (xám): tra `T[mode][tint][màu]` (ở các mode không phải HSB, `p2`,`p3`,`p4` là R,G,B của màu phủ: `tint = (p4>>3) | (p2>>3)<<11 | (p3>>2)<<5` nếu một trong ba ≠ 0, ngược lại chính màu đó), rồi trộn alpha như §2 nhưng **cả R và B dùng `ra = (alpha>>3)+1`**; `alpha == 255` giữ nguyên, riêng mode 0 trả về màu `tint`.
  - `DM_ConvertRGBtoHSB` @0x4dc6c, `DM_ConvertHSBtoRGB` @0x4ddf4: toàn số nguyên. **"Float" duy nhất** của `ConvertHSBtoRGB` là phép so sánh `(double)S == 0.0` (`__aeabi_i2d` + `__aeabi_dcmpeq`), tức `S == 0` — **không cần float trong Java**. Kiểm 40.000 ca: 0 lệch.
- Kiểm: 4.000 ca palette (ảnh/tham số ngẫu nhiên, kể cả cờ ban đầu 2) + khôi phục: 0 lệch.

## 5. Nạp ảnh có pha màu theo giờ (CONFIRMED)
Hai hàm áp dụng sau khi nạp ảnh; `slot` = lớp thời gian trong ngày (`_map[0x78]`, `Map_Time_Return`), `-1` = không đổi.
Bảng màu phủ (mode 10 trừ ghi chú): `slot 0: 0xabd8`, `1: 0xc591`, `2: 0xbd2c`, `3: 0x45ff`, `4: 0xffff`, `5: 0xfc2d`.
- `DM_LoadResGbmBlending(path, img, slot)` @0x53e68 (**TILE, OBJ**): `R = ((T+r)<<10)&0xf800`, `B = ((T+b)>>1)&0x1f`, `G6 = ((T_g·62 + g5·66)>>1)&0x7e0`
  (tức trung bình với màu gốc; trọng số lệch của kênh xanh lục). Slot khác 0..5 và khác −1: tint 0, mode 0. Palette thân và palette làm việc **giống hệt nhau** sau khi nạp.
- `DM_LoadResGbmMergeBlending(cell, img, palIndex, slot)` @0x54750 (**sprite, qua `loadSprImages`**):
  1. nếu `palIndex` (u16) ≤ 254 thì `changePalette(img, palIndex)` (xem `phaseC_pal.md`); 255/−1 bỏ qua;
  2. `slot 0..5`: tint ở trên, mode 10, alpha 127; `slot 6`: tint `0xf800`, mode 4, alpha 200 (UNKNOWN ý nghĩa: INFERRED hiệu ứng "đỏ" khi bị đánh); `slot −1`: không tint;
     công thức = §4 với `ra` cho R và B (`alpha ≠ 255`), rồi chép palette làm việc vào palette thân (trừ slot −1);
  3. slot ≥ 7: UNDEFINED (alpha chưa khởi tạo) — mô hình từ chối.
- Kiểm: 2.493 ca (278 file TILE/OBJ thật × slot −1..7) và 1.920 ca (30 ảnh sprite thật × 8 palette × 8 slot): 0 lệch.

## 6. Chưa xác định / để sau
- `DM_DrawMosaic*`, `DM_FillRectBlendingRGB`, `DM_DrawRectAlphaRGB`, `DM_DrawLineAlphaRGB`, `DM_DrawRoundRectRGB`, `DM_DrawArcRGB`, `DM_DrawEffect(Clip)`, `make_Lenz_array` (float): chưa kiểm — sẽ làm khi phân tích UI/hiệu ứng ở Phase E, theo các nơi gọi thật.
- Ý nghĩa gameplay của các `slot` thời gian và effect 17: Phase E.

## 7. Ghi chú cho bản Java ME
- Mọi phép trộn là số nguyên 16 bit; bảng 16 KB dựng một lần lúc khởi động (không cần lưu).
- Không dùng float: `HSB` là số nguyên; chia C ↔ `/` của Java (cắt về 0) — đúng ngữ nghĩa.
- Hàm vẽ không cấp phát; ghi trực tiếp vào bộ đệm khung.
