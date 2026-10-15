# Phase C — Hình học vẽ ảnh `DM_DrawImg` (AND2011)

Nguồn sự thật: `binary.mod` AND2011. Địa chỉ = vaddr ELF. Công cụ: `tools/dm_blit.py`.
Phương pháp: mô hình Python viết theo disassembly, rồi **so khung hình với chính `DM_DrawImg` gốc** chạy trong
emulator đối chứng (`tools/and_hle.py`). Mô hình chia hai lớp, mỗi lớp kiểm riêng rồi kiểm ghép.

## 1. Chữ ký và cấu trúc dữ liệu (CONFIRMED)
`DM_DrawImg(frame, x, y, w, h, img, srcx, srcy, flip, rot, anchor)` @0x4f760 (11 tham số; 5 tham số cuối đi qua stack).
- `(w, h)` = kích thước **vùng con của nguồn** lấy tại `(srcx, srcy)`. Nếu `w <= 0` hoặc `h <= 0` thì không vẽ gì (0x4f774..0x4f7a0).
- `OutR` (global @0xfa29c, 4 × s32) là hình chữ nhật cắt **(x0, y0, x1, y1)**, biên phải/dưới **không bao gồm**.
  Do `DM_SetCliptoGBM` @0x4d378 ghi (`stm {r0-r3}`); `DM_DrawImg` so `x >= OutR+8` (0x4f824), `y >= OutR+0xc`.
- Cấu trúc ảnh nạp (32 byte, do `DM_LoadMergeGbm565`): `+0 s16 w`, `+2 s16 h`, `+4 s16 aux`, `+6 s16 ncolors`,
  `+8` con trỏ thân ảnh gốc, `+0xc` con trỏ palette gốc, `+0x14` palette hiện hành, `+0x18` thân hiện hành.
  - `DM_DrawImg` **đặt lại `+0x18 ← +8`** mỗi lần vẽ (0x4f7d0..0x4f7d8); `DM_DrawScreen565` **đặt lại `+0x14 ← +0xc`** (0x3de9c..0x3deac).
    Hệ quả cho palette (xem `phaseC_pal.md`): đổi palette phải làm *ngay trước* lệnh vẽ, không giữ qua lần vẽ khác.
  - Stride hàng 8 bpp = `s16 w` của ảnh (không phải `aux`); 4 bpp = `(w+1)>>1`.
- Điểm trong suốt: màu sau khi tra palette == `SKIP_COL` (0xF81F), như `gbm.py`.

## 2. Neo (`DM_CalcAnchorGbm` @0x4d29c, CONFIRMED)
Áp **trước** lật/xoay, trên `w`, `h` chưa xoay; mọi phép tính s32, dịch số học (`asr`):
| bit anchor | tác dụng |
|---|---|
| 0x10 | `x -= w>>1` (căn giữa ngang) |
| 0x20 | `x -= w` (căn phải; bị 0x10 che nếu cùng đặt) |
| 0x02 | `y -= h>>1` (căn giữa dọc) |
| 0x04 | `y -= h` (căn dưới; bị 0x02 che) |
Với `rot` = 90/270 (giá trị **thô**, kể cả khi `flip = 3` bỏ qua `rot`), phần căn giữa dùng kích thước đã xoay
(`x -= h>>1`, `y -= w>>1`) nhưng phần 0x20/0x04 vẫn dùng `w`/`h` chưa xoay (QUIRK của bản gốc, đã chép nguyên).
Các bit khác (0x01, 0x08) không có tác dụng.

## 3. Phép biến đổi (CONFIRMED bằng thực nghiệm, 9 mode của `DM_DrawScreen565`)
Vùng nguồn được **lật trước, xoay sau** (xoay cùng chiều kim đồng hồ):
| flip | rot | mode truyền xuống | đường cắt (path) |
|---|---|---|---|
| 0 | 0 / khác | 0 | A |
| 0 | 90 | 4 | B |
| 0 | 180 | 5 | A |
| 0 | 270 | 6 | C |
| 1 (lật ngang) | 0 | 1 | A |
| 1 | 180 | 2 | A |
| 1 | 90 | 7 | B |
| 1 | 270 | 8 | C |
| 2 (lật dọc) | 0 | 2 | A |
| 2 | 180 | 1 | A |
| 2 | 90 | 8 | C |
| 2 | 270 | 7 | B |
| 3 | bất kỳ | 3 (quay 180°, **bỏ qua `rot`**) | A |
`rot` ngoài {0, 90, 180, 270} hoạt động như 0. Với rot 90/270 hộp đích có kích thước `h × w`.

## 4. Hai lớp của mô hình
- **Lớp 1 — `plan()`**: chuyển `(x, y, w, h, srcx, srcy, flip, rot, anchor, OutR)` thành `(X, Y, W, H, SX, SY, mode)`,
  là các tham số truyền cho `DM_DrawScreen565` (con trỏ `DM_DrawScreen` @0xfa28c). Gồm cắt biên theo `OutR`, và điều chỉnh
  offset nguồn. Chép tay từ disassembly 0x4f7dc..0x4fd10 (ba đường A 0x4f81c / B 0x4f938 / C 0x4faec).
- **Lớp 2 — `raster()`**: `DM_DrawScreen565` @0x3de54 vẽ vùng nguồn `(SX, SY, W, H)` đã biến đổi theo `mode` tại `(X, Y)`.
  Không cắt biên ở lớp này (phụ thuộc hoàn toàn lớp 1).

### Lỗi (bug) của bản gốc đã chép nguyên — hành vi quan sát được
Khi ảnh bị cắt ở mép, một số đường **không cộng offset nguồn** hoặc cộng sai:
- Đường B (mode 4 = xoay 90 không lật) bị cắt ở **mép trên**: `SX` không tăng (0x4f9c0) → hiện phần đầu của ảnh thay vì phần bị cắt.
- Đường A, các mode lật (1, 2, 3, 5) bị cắt cả hai mép cùng lúc: `SX`/`SY` không bù phần bị cắt phía kia.
Java phải tái hiện nguyên các quirk này nếu muốn khớp từng pixel (xem `plan()`).

### Hành vi không xác định (UB) của bản gốc — **KHÁC BIỆT CÓ CHỦ ĐÍCH**
Khi ảnh chạm đúng mép vùng cắt (`x + w == x0` hoặc `y + h == y0`), `plan()` cho `W = 0` hoặc `H = 0`. Bản gốc vẫn gọi
`DM_DrawScreen565`; nhánh 4 bpp ghi qua một ô stack **chưa khởi tạo** (`strhne r3,[sb]` @0x3f150, `sb` nạp từ `[sp+0x6c]`)
nên có thể ghi rác hoặc **sập** (trong emulator: `UC_ERR_WRITE_UNMAPPED`); với 8 bpp không vẽ gì.
→ Bản Java: **không vẽ gì** khi `W <= 0` hoặc `H <= 0`. Tác động quan sát được: thiếu vài pixel rác ở trường hợp hiếm, an toàn hơn.
Selftest đếm riêng các ca này và không so sánh.

## 5. Kiểm chứng
Xem `docs/phaseC_blit_selftest.txt` (stdout đúng của lần chạy cuối).
- Lớp 1 (so tham số truyền xuống bằng cách móc điểm vào `DM_DrawScreen565`, không vẽ pixel): **907.137 ca, 0 lệch** —
  17 tổ hợp (flip, rot) × w,h ∈ [1,7] × x,y ∈ [−9,23] với clip (4,6,14,17), `srcx=3`, `srcy=5`.
- Lớp 2 (gọi thẳng `DM_DrawScreen565`): **21.600 ca, 0 lệch, 0 sập** — 9 mode × mọi vùng con trong ảnh, ảnh tổng hợp 8 bpp
  (5×3, 6×4) và 4 bpp (5×3, 6×4, 7×5), tức SX chẵn/lẻ.
- Ghép (`DM_DrawImg` thật so với `draw()`), so **toàn bộ khung 240×400 từng byte**: **1.776.085 ca, 0 lệch, 0 sập**, thêm
  108.875 ca độ rộng/cao bằng 0 (UB, không so sánh). Phạm vi: 18 ảnh (6 tổng hợp 4/8 bpp + 12 ảnh thật của các container khác nhau),
  vùng con toàn ảnh và vùng con lệch (1,1,w−2,h−1), 5 vùng cắt (toàn khung, hộp nhỏ, hộp 2×2, góc, mép dưới-phải), 17 tổ hợp
  (flip, rot) gồm cả `rot` ngoài tập {0,90,180,270}, 11 giá trị anchor, các vị trí quanh mép cắt (âm, sát mép, chạm mép, trong khung).
  Cùng lần chạy: `+0x18 ← +8` đúng.

## 6. Chưa xác định / ngoài phạm vi
- UNKNOWN: kết quả khi vùng con `(srcx, srcy, w, h)` vượt ra ngoài ảnh (bản gốc không kiểm; chỉ test vùng con nằm trong ảnh).
- UNKNOWN: khi `OutR` lớn hơn khung đệm. Game đặt `OutR` từ `DM_SetCliptoGBM` với kích thước khung (lệnh gọi: `DM_Initialize`,
  `timerCB`, các `*_Render`), nên giả định `OutR` ⊆ khung.
- Chưa làm: biểu đồ tần suất `(flip, rot, anchor)` tại các nơi gọi; ghi nhận để Phase E đối chiếu.
- Các biến thể `DM_DrawImgEx`, `DM_DrawImgRotate(Ex)`, blend: xem `phaseC_fx.md`.

## 7. Ghi chú cho bản Java ME
- Một lần vẽ không cấp phát: `plan()` chỉ là số nguyên; `raster()` duyệt từng pixel, không tạo đối tượng.
- Tất cả toán học là `int` (s32); `asr` ↔ `>>` của Java; không có float.
- Nên giữ bộ đệm khung RGB565 `short[]`/`int[]` 240×400 (192 KB) và vẽ vào đó, rồi đẩy lên `Canvas` bằng `Graphics.drawRGB`/`Image`.
