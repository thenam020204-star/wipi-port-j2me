# Phase C — Thuộc tính sprite `H4/EXD/_H_###_EXD` (hộp va chạm / tấn công) (AND2011)

Nguồn sự thật: `binary.mod` AND2011. Công cụ: `tools/exd.py`. Kết quả: `docs/phaseC_exd_selftest.txt`.
Bộ nạp gốc: `SPR_loadAttackProperty` @0xc17e8 (quái/boss) và `SPR_loadExtra` @0xc1ee0 → cùng một thân hàm (hero, `HERO_spriteLoad`).
Phương pháp: mô phỏng trình nạp theo đúng thứ tự thao tác trên bộ nhớ (bao gồm cả các bước suy ra nhóm lật), rồi **so từng byte** cấu trúc heap
do hàm gốc dựng (bảng `spr+0x54`, bảng chỉ số `spr+0x58`, 10 số nguyên tiêu đề `spr+0..0x24`, mọi ô 0x44 byte).

## 1. Bố cục file (CONFIRMED)
```
u8  nRec                                 -> spr+0x5c
nRec x bản ghi, mỗi bản ghi bắt đầu bằng  u8 b0, s8 b1
```
- **Bản ghi tiêu đề** (`b0 == 0 && b1 == 1`): 2 byte chưa rõ (UNKNOWN), `u8 nItems`, rồi `nItems × { u8 type; … }`:
  `type 0`: `s16 a, s16 b` → `spr[0], spr[1]` (INFERRED: điểm neo phía trên đầu, y ≈ −35…−72, dùng cho thanh máu/tên);
  `type 1`: `4 × s16` → `spr[2..5]` (INFERRED: hộp thân x, y, w, h);
  `type 2`: `4 × s16` → `spr[6..9]` (INFERRED: hộp thứ hai). Bản ghi tiêu đề chiếm một ô của bảng nhưng để trống.
- **Bản ghi chuyển động**: `b0` = id chuyển động (`spr+0x58[b0] = chỉ số bản ghi`, bảng 400 byte), `b1` = số mục; mỗi mục `{ u8 id, s8 nA, s8 nB }` rồi `nA` nhóm × `nB` ô:
  - ô = `{ u8 type; payload }`: `type 3, 13` → `4 × s16`; `type 4, 5, 6, 9, 10, 12` → `2 × s16` (hai số còn lại = −1); `type 7, 8, 11` → `1 × s16` (ba số còn lại = −1); loại khác không có payload (số = 0).
  - Mỗi ô là một **cell 0x44 byte**: `+1` = `type` của nhóm đọc cuối cùng, `+4 + 0x10·g` = 4 số nguyên của nhóm `g` (g = 0..3).
  - Nhóm suy ra sau mỗi lần đọc (kết quả cuối cùng là cái cuối thực hiện):
    `nA == 1`: nhóm 1,2,3 ← nhóm 0 · `nA == 2`: nhóm 2 ← biến đổi(nhóm 1), nhóm 3 giữ 0 · `nA ≥ 3`: nhóm 3 ← biến đổi(nhóm 2) (với `nA == 4` dữ liệu đọc cho nhóm 3 bị ghi đè).
    Biến đổi theo `type` hiện tại của cell: `{3,5,6,13}`: `(|x| − w, y, w, h)`; `4`: `(−x, y, −1, −1)`; khác: sao chép.
  - Ý nghĩa của các `type` (hộp đánh, hộp nhận đòn, điểm bắn…): INFERRED/UNKNOWN — thuộc Phase E (cần đối chiếu với `HERO_*`/`MONSTER_*`).

## 2. Kiểm chứng
117 file `_EXD` (tổng 22.795 byte) nạp bằng `SPR_loadAttackProperty` gốc: **0 lệch trên toàn bộ cấu trúc** (10 số tiêu đề, `nRec`, bảng chỉ số, 1.321 cell × 0x44 byte, các `id/nB`).
Hai điểm bất thường của dữ liệu (hành vi bản gốc, giữ nguyên):
- `_H_099_EXD`, `_H_102_EXD` **thiếu 1 byte cuối** (10 byte; bản ghi tiêu đề cần 11): bản gốc đọc vượt hết bộ đệm 1 byte. Giá trị byte đó trên máy thật UNKNOWN (emulator: 0). Hệ quả: với hai file này `spr[0] = 0x0200` và `spr[1] = 0x00f2 | (byte_lạ << 8)` (dữ liệu bất thường so với 115 file còn lại, nơi `spr[0] = 0` và `spr[1]` âm; INFERRED: tệp bị cắt/hỏng, bản gốc vẫn chạy).
- `_H_084_EXD` (51 byte thừa) và `_H_095_EXD` (4 byte thừa): bản gốc **không bao giờ đọc** phần cuối file (khai báo `nRec = 3` nhưng dữ liệu có thêm bản ghi).

## 3. Ghi chú cho bản Java ME
- Cấu trúc nhỏ: ≤ 3,6 KB mỗi file; cell 0x44 byte có thể thu về `short[4×4]` + `type` (nhóm 3 và các nhóm dẫn xuất tính khi nạp).
- Dùng `int` có dấu 16 bit cho mọi toạ độ (đọc bằng `s16`, little-endian); `-1` là giá trị "không có".
