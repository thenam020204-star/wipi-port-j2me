# Phase C — Palette hoán đổi `H4/PAL/_H_###_PAL` (AND2011)

Nguồn sự thật: `binary.mod` AND2011. Công cụ: `tools/pal.py`. Kết quả: `docs/phaseC_pal_selftest.txt`.
Kiểm chứng: mô hình Python so với **`DM_ChangePalette` @0xbc1f4 gốc** (emulator đối chứng) trên ảnh thật và ảnh tổng hợp.

## 1. Bố cục file (CONFIRMED)
`u8 header`, rồi các cặp `{u32le old, u32le new}`, mỗi màu dạng `0x00RRGGBB` (byte: B, G, R, 0). 196 file (`_H_000_PAL` … `_H_195_PAL`).
- `header` = số cặp (đúng với cả 196 file; **bộ nạp không dùng** — nó lặp `pos = 1, 9, 17…` cho đến khi `pos ≥ kích thước file`); không file nào có cặp dang dở.
- `UM_ByteToInt32` @0xd8ea4 đọc little-endian.

## 2. Luật áp dụng (CONFIRMED)
`DM_ChangePalette(img, data, start=0, end=size)`: đặt `img+0x18 ← img+8` và `img+0x14 ← img+0xc`, rồi với mỗi cặp:
- `old565 = (old>>8 & 0xf800) | (old & 0xff)>>3 | (old>>5 & 0x7c0)` — xanh lục chỉ giữ 5 bit cao (g5 << 6);
- `new565 = ((new>>16 & 0xf8) << 8) | (new & 0xff)>>3 | ((new>>8 & 0xf8) << 3)` — cùng quy ước;
- tìm **mục đầu tiên** `i` (0..ncolors−1) của palette **thân ảnh** (`body+9`) bằng `old565`; nếu có: ghi `new565` vào cả palette thân *và* palette làm việc tại `i`. Không tìm thấy hoặc `ncolors == 0` thì bỏ qua cặp.
- Palette thân bị sửa **tại chỗ**, nên thay đổi bền vững qua `DM_RestorePalette` (hàm này chép thân → làm việc).

## 3. Cách dùng trong game (CONFIRMED)
- `changePalette(img, n)` @0xbcaf8: nếu `n ≥ 0` nạp `"/H4/PAL/_H_%03d_PAL"` rồi gọi `DM_ChangePalette`.
- `setPalImages(path, imgs, count)` @0xbcb8c (**hàm chết**, bỏ qua): áp một file cho `count` cấu trúc ảnh liên tiếp.
- `DM_LoadResGbmMergeBlending(cell, img, palIndex, slot)` @0x54750 gọi `changePalette(img, palIndex)` khi `palIndex ≤ 254` (xem `phaseC_fx.md` §5). `palIndex` do hàm nạp ảnh sprite truyền (hero: biến thể màu; INFERRED).

## 4. Kiểm chứng
196 file × 6 ảnh hero thật + 196 × 2 ảnh tổng hợp có sẵn các màu `old` (để việc thay thật sự xảy ra): **1.568 ca, 4.849 mục palette thật sự bị đổi, 0 lệch** (so palette thân, palette làm việc, `+0x14`, `+0x18`).

## 5. Chưa xác định
- Ánh xạ "số palette ↔ trang bị/lớp nhân vật/quái" (ai truyền `palIndex`): để Phase E (cần luồng nạp hero/quái).

## 6. Ghi chú cho bản Java ME
- Áp hoán đổi lúc nạp ảnh: duyệt `ncolors` ≤ 256 cho mỗi cặp (≤ 20 cặp): rẻ, không cấp phát thêm.
- Giữ nguyên quy ước g5 khi chuyển màu cũ → 565 (bit thấp của xanh lục luôn bằng 0 khi so khớp).
