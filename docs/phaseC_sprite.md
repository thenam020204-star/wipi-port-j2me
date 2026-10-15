# Phase C — Ghép sprite (CIF) → chuỗi lời gọi `DM_*` (AND2011)

Nguồn sự thật: `binary.mod` AND2011. Địa chỉ = vaddr ELF (mã giả Ghidra có base `0x10000`, trừ đi khi đối chiếu).
Công cụ: `tools/sprite.py`. Kết quả selftest: `docs/phaseC_sprite_selftest.txt`.

Phương pháp: mô hình Python của **`SPR_drawSpriteNPC` @0xc00a0 (4128 byte)** sinh ra *danh sách lời gọi* đến
`DM_RestorePalette`, `DM_SetChagePalBlendEx`, `DM_DrawImg`, `DM_DrawImgEx`, `DM_DrawImgRotate`, `DM_DrawImgRotateEx`
(và `MenuOptions_particle`). Chuỗi này được so với chuỗi mà **chính hàm gốc** phát ra (móc điểm vào các hàm đó trong
emulator đối chứng), từng đối số một. Phần *pixel* của các `DM_*` được kiểm riêng (`dm_blit.py`, `dm_fx.py`).

## 1. Kiểm chứng
- **14.400 ca** với CIF ngẫu nhiên (cả mode 0x11 và mode 2), bộ set ảnh/cờ ngẫu nhiên, nạp bằng `SPR_loadSprite` gốc.
  **Độ phủ lệnh của hàm gốc: 1029/1032**; ba địa chỉ chưa chạm tới (`0xc107c`, `0xc1080`, `0xc1084`) là literal pool, không phải code.
  ⇒ mọi nhánh của hàm gốc đều được thực thi và đối chiếu.
- **55.107 ca vẽ** trên **toàn bộ 148 file `_CIF` thật**: mọi (anim, hướng, khung), 3 biến thể trạng thái (sạch; ngẫu nhiên ×2).
- **0 lệch** ở cả hai phần. Đối chiếu gồm cả số khung được chỉnh lại (`*frameptr`) và mảng set ảnh hàm gốc ghi lại (`spr+0x28`).
- Hạn chế (UNKNOWN, ghi rõ): `anim` ngoài `[0, nAnim)` (bản gốc đọc bộ nhớ rác); mỗi hướng của một anim trong test có cùng số khung
  (dữ liệu thật thoả điều kiện này vì selftest thật không lệch).

## 2. Điểm vào (CONFIRMED)
`SPR_drawSprite(frame, spr, anim, dir, frameptr, x, y, a7, a8)` @0xc11bc
= `SPR_drawSpriteNPC(frame, spr, anim, 2, 3, dir, frameptr, x, y, a7, a8, ctrl=-1)` (trước đó nếu `_lcdEff[10]==2` thì gọi `SPR_set_control_pal(spr,0,0,0,0xff,0x10)`).
`SPR_drawSpriteSK` @0xc10c0 giống nhưng truyền `ctrl` riêng, sau đó xoá cờ `spr+0x68` nếu `_lcdEff[10]==2`.
- `frameptr`: con trỏ tới **một byte** là số khung hiện tại; nếu `(s8)khung > maxFrame` hoặc `< 0` thì hàm đặt lại **0**.
- `dir < 0` (bit 7) ⇒ không vẽ. `dir == 3` (tham số 5) ⇒ dùng `dir = 2` (tham số 4) **và** bật *chế độ gương* `D`.
- `D` (chế độ gương) = `a7 == 1` **hoặc** `dir == 3`. Ý nghĩa quan sát được: dữ liệu lưu mặt hướng trái (dir 2); hướng phải (dir 3) là bản lật ngang.

## 3. Cấu trúc sprite dùng trong hàm vẽ (CONFIRMED)
| offset | kiểu | ý nghĩa |
|---|---|---|
| 0x28 | ptr[ ] | mảng con trỏ set ảnh **đang dùng** (mỗi set là mảng cấu trúc ảnh 32 byte) |
| 0x2c | ptr[ ] | mảng set ảnh gốc; với `ctrl == 2` (kiểu "SK") được sao sang 0x28, **trừ set 7** (xem §6) |
| 0x38 | s32 | **số set ảnh** = byte đầu của CIF (`SPR_loadNewSprite` @0xc1f4c) |
| 0x3c | u8 | số anim |
| 0x44/0x48 | ptr | bảng bản ghi anim (0x4c byte/anim) — hàm vẽ chép 0x44 → 0x48 mỗi lần vẽ |
| 0x4c | ptr | dữ liệu CIF thô |
| 0x60,0x62,0x64 | s16 | tham số palette điều khiển (`SPR_set_control_pal` @0xbfd94) |
| 0x66,0x67 | u8 | idem |
| 0x68 | u8 | cờ palette điều khiển đang bật |
| 0x6c | s8 | số khung (SPR_initFrame/nextFrame/repeatFrame) |
| 0x6d | u8 | nếu ≠ 0: ảnh của **set 2** dịch thêm +2 chỉ số |
| 0x6e/0x6f | u8 | bật pha trộn / alpha (`SPR_setBlend` @0xbff3c; 0x6f khởi tạo 0xff) |
| 0x70 | s8 | mode CIF (2 = hero, khác = 0x11) |
Cấu trúc ảnh (32 byte): `+2 s16 h`, `+4 s16 aux`, `+0x1c u8 cờ`. **`aux` chính là bề rộng truyền cho `DM_DrawImg`** (không phải `w`).

## 4. Bố cục dữ liệu CIF (CONFIRMED)
`s8 n0` (= số set), `n0` byte (bộ nạp **bỏ qua**, UNKNOWN), `u8 nAnim`, rồi mỗi anim `s8 nDir`, mỗi hướng `u8 nKhung`
(nKhung = 0 ⇒ con trỏ hướng bằng NULL ⇒ vẽ dùng hướng 0), mỗi khung `s8 nParts` + các mảnh.

Mảnh (mode 0x11: 4 byte, +5 nếu dài; mode 2: 5 byte, +5 nếu dài):
| byte | ý nghĩa |
|---|---|
| b0 | bit7 = mảnh dài; bit6..5 = *lớp* (0 → 0x10, 1 → 8, 2/3 → 4); bit4..0 = chỉ số set ảnh (0..31). `b0 == 0x7f` = mảnh rỗng (chỉ bỏ qua) |
| b1 | chỉ số ảnh trong set, **s8** (địa chỉ ảnh = `set + (s8)b1 * 32`); nếu set = 2 và `spr[0x6d]` thì `(b1+2)&0xff` |
| mode 0x11: b2,b3 | x, y — **s8** |
| mode 2: b2,b3,b4 | x = s12(`b2<<4 \| b3>>4`), y = s12(`(b3&15)<<8 \| b4`) |
| (dài) +0 | tỉ lệ X = `byte × 10` (%) |
| (dài) +1 | tỉ lệ Y = `byte × 10` (%) |
| (dài) +2 | góc = `byte × 2` (độ; hàm gốc truyền thẳng, không rút gọn mod 360) |
| (dài) +3 | alpha s8 (−1 = không dùng) |
| (dài) +4 | `bit1..0` = lớp thứ hai (0 → 0x10, 1 → 8, khác → 4); `bit7..2` = chỉ số hiệu ứng |
Set có con trỏ = 0 ⇒ mảnh bị bỏ qua. Mảnh dài với `ctrl == 0` và set 7 bị bỏ qua. Nếu có chỉ số hiệu ứng ≠ 0 mà
`MenuOptions_particle() == 0` (người chơi tắt hiệu ứng) thì alpha bị ép về −1.

## 5. Luật vẽ (CONFIRMED, mô hình `draw_npc`)
Toạ độ cuối = `(X + x, Y + y)` (cộng s16), anchor luôn `0x12` (căn giữa), nguồn `(0,0)`, rộng = `aux`, cao = `h`.
Biến cục bộ `flip` và `rot` (`local_40`, `local_44` trong mã giả) **được giữ nguyên từ mảnh trước** khi một nhánh không gán lại chúng
(chỉ xảy ra ở mảnh dài chế độ gương với góc "lạ"); mô hình giữ đúng hành vi này.

**Mảnh ngắn**
- Không gương (`D = 0`): `rot = 0`; `flip` theo lớp: 0x10 → 0, 8 → 1, 4 → 2.
- Gương (`D = 1`): lớp 8 → flip 0; lớp 0x10 → flip 1 (nếu `aux` chẵn thì `x += 1` bù lệch nửa pixel); lớp 4 → `rot = 180°`, flip 0; rồi `x = −x`.
- Palette (theo thứ tự): xem §5.1. Cuối cùng: nếu không cần `Ex` thì `DM_DrawImg(frame, X+x, Y+y, aux, h, img, 0, 0, flip, 0, 0x12)`;
  ngược lại `DM_DrawImgEx(..., flip, rot, spr[0x6f], 0, 0x12)`. "Cần `Ex`" = `rot ≠ 0` hoặc `spr[0x6e] ≠ 0`.

**Mảnh dài** (`sx = 10·b+0`, `sy = 10·b+1`, `ang = b+2`, `α = b+3`, `eff = (b+4)>>2`, `cls2 = b+4 & 3`)
- Không gương: `flip` theo `cls2` (0x10→0, 8→1, 4→2), `rot = 2·ang`.
- Gương: `flip` theo `cls2` (0x10 → 1 [và `x += 1` nếu `sx == 100` và `aux` chẵn], 8 → 0, 4 → 0);
  `rot` theo góc (đã nhân 2): `ang==0x87` → `cls2==4 ? 270 : 90`; `ang==0x2d` → `cls2==4 ? 90 : 270` (**điều kiện ngược nhau** — Ghidra
  gộp nhầm hai nhánh này, disassembly 0xc0b14 / 0xc0e1c mới đúng); `ang==0x5a` → `cls2==4 ? 0 : 180`; `ang==0` → `cls2==4 ? 180 : 0`;
  góc khác → **giữ nguyên `rot` cũ**; rồi `x = −x`.
- Cuối: nếu `spr[0x6e]` thì `α = spr[0x6f]` (s8). Nếu `sx ≠ 100` hoặc `sy ≠ 100`: có (α ≠ −1 hoặc eff ≠ 0) → `DM_DrawImgRotateEx(..., flip, rot, sx, sy, eff, α&0xff, 0x12)`,
  không thì `DM_DrawImgRotate(..., flip, rot, sx, sy, 0x12)`. Nếu cả hai = 100: `DM_DrawImgEx(..., flip, rot, α&0xff, eff, 0x12)` hoặc `DM_DrawImg(..., flip, rot, 0x12)`.
  **α luôn truyền dạng byte không dấu** (−1 → 255).

### 5.1 Thao tác palette trước mỗi lần vẽ (CONFIRMED)
`E = _lcdEff` (bảng 12 byte toàn cục: s16 [0..1],[2..3],[4..5], u8 [6],[7], [9] = bật, [10] = chế độ SK), `a8` = tham số 11.
- Mảnh ngắn: nếu `E[9]==0` → `Restore(img)`; ngược lại nếu `img[0x1c]==1` → `Restore`, và nếu set ∉ {5,7} → `SetChagePalBlendEx(img, s16 E0, s16 E2, s16 E4, E7, E6)`.
  Sau đó: nếu `spr[0x68]==0` → `Restore(img)`, ngược lại (cờ `img[0x1c]==1` → `Restore`; set ∉ {5,7} → `SetChagePalBlendEx(img, spr60, spr62, spr64, spr67, spr66)`).
- Mảnh dài: nếu `E[9]≠0`: `Restore` nếu `img[0x1c]==1`; `SetChagePalBlendEx(E…)` nếu `spr[0x70]==2` và set ∉ {5,7}.
  Nếu `E[9]==0`: `Restore` khi `a8 == −1` hoặc `a8 == set`. Rồi cùng khối `spr[0x68]` như trên.
Hệ quả quan trọng: mỗi lần `DM_DrawImg` đều đặt lại con trỏ thân ảnh (`+0x18←+8`) và palette (`+0x14←+0xc`) — xem `phaseC_blit.md` §1.

## 6. Set đặc biệt và `ctrl`
- `ctrl == 2` (đường `SPR_drawSpriteSK`): khi làm mới mảng set, set 7 **không** được chép từ bản gốc; nếu `_imgSKextraEffect ≠ 0` thì
  `sets[7] = _imgSKextraEffect` và vòng lặp tiếp tục từ chỉ số 8 (0xc0188..0xc01b0). INFERRED: set 7 là lớp "hiệu ứng da/trang bị", set 5 và 7 loại khỏi pha palette.
- `a8` (tham số 11) ≠ −1 chỉ ảnh hưởng nhánh `E[9]==0` của mảnh dài (§5.1). UNKNOWN ngữ nghĩa trò chơi (INFERRED: "chỉ khôi phục palette cho một set").

## 7. UNKNOWN / còn lại
- `n0` byte sau byte đầu CIF: bộ nạp bỏ qua; chưa thấy hàm nào đọc chúng. UNKNOWN (có thể dữ liệu công cụ tạo CIF).
- Tên ngữ nghĩa các cờ `spr+0x6d` (+2 chỉ số set 2: INFERRED "biến thể ảnh" như đổi vũ khí), `spr+0x6e/0x6f` (blend), `_lcdEff`.
- Tác động pixel của `DM_DrawImgEx` / `RotateEx` / `SetChagePalBlendEx`: xem `dm_fx` (chưa làm).
- Hạn chế kiểm thử ở §1.

## 8. Ghi chú cho bản Java ME
- Hàm vẽ **không cấp phát** (chỉ số nguyên, con trỏ); nên giữ mô hình tương tự: sinh lời gọi vẽ trực tiếp, không tạo đối tượng mảnh.
- Giữ nguyên số học: `x,y` cộng theo s16 rồi cộng vào `X,Y` (int); `alpha` cắt `& 0xff`; `rot = 2·ang` có thể tới 510.
- `flip`/`rot` là trạng thái *giữa các mảnh* của cùng một lần gọi — đặt cục bộ trong hàm, khởi tạo 0.
