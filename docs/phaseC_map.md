# Phase C — Map (`MAP/M/_MAP_M_###`), tileset (`TILE/`) và ảnh đối tượng (`OBJ/`) (AND2011)

Nguồn sự thật: `binary.mod` AND2011. Công cụ: `tools/mapdata.py`. Kết quả: `docs/phaseC_map_selftest.txt`.
Phương pháp: bộ phân tích Python viết theo `Map_SetMapData` @0x9b434 / `Map_addMapObject` @0x9b2e8 / `Map_objIdxCreate`, rồi so với **chính
`Map_SetMapData` gốc** chạy trong emulator đối chứng: các hàm tạo đối tượng game được thay bằng stub ghi lại tham số
(`SprObject_create`, `MAPOBJ_createAndCopy`, `Map_getSprBody`, `MONSTER_Clone`…), còn dữ liệu đổ vào `_map` (kích thước, hai lớp, chuỗi…) được đọc lại từ bộ nhớ.

## 1. Nạp (CONFIRMED)
`Map_LoadMap` @0x9bc88 → `sprintf("/MAP/M/_MAP_M_%03d", _map.mapIdx)` → `Map_LoadMapData` @0x9bc30 (`UM_LoadRes`, **không mã hoá**) → `Map_SetMapData`.
97 file, `W ≤ 60`, `H ≤ 50`.

## 2. Bố cục file (CONFIRMED — cả 97 file tiêu thụ đúng bằng kích thước file)
| trường | kiểu | ghi vào / ý nghĩa |
|---|---|---|
| nAB | u8 | `_map[0x270]` = số tileset (1..3) |
| nAB × {a, b} | u8, u8 | `_map[0x271+i] = a` (**chỉ số tileset** → `TILE/_TILE_%03d`, 0xFF = không dùng), `_map[0x274+i] = b` (luôn 0xFF trong dữ liệu; UNKNOWN) |
| 2 × chuỗi | s8 len + len byte | tên khu vực và tên vùng con, **EUC-KR** (vd. `수레바퀴섬` / `필드1`); `len ≤ 0` = không có chuỗi. Bản gốc chép `len+1` byte vào `_map+0x278/0x27c` (bản sao thứ hai ở `+0x280/0x284`) |
| unk0 | u8 | bị bỏ qua (UNKNOWN; luôn 0 trong dữ liệu) |
| W, H | u8, u8 | `_map[0x7a]`, `_map[0x7b]` (số ô); kích thước pixel `W·16` (`_map+0x84`), `H·16` (`_map+0x88`); kích thước ô 16×16 do `_map[0x7e]`, `_map[0x7f]` |
| nObjIdx + danh sách | u8 + n × u8 | id ảnh đối tượng (`Map_objIdxCreate`); **giá trị ≥ 0x80 coi như 0 và không tiêu thụ byte nào** (0xab5b0 `iVar8 < 0`) |
| nSpr + danh sách | u8 + n × u8 | id sprite-object (`SprObject_create(id, Map_Time_Return(_map[0x68]))`); cùng quy tắc ≥ 0x80 |
| lớp A | W·H byte | → `*(_map+676)` |
| lớp B | W·H byte | → `*(_map+704)`; `_map[0x350] = lớpB[0] & 8` |
| 4 × (count + bản ghi) | u8 (**không dấu**) + count × 8 byte | danh sách đối tượng 0..3 = `moreBottom`, `bottom`, `giữa` (`_objList`), `top`. **count là u8 0..255** (mã giả Ghidra ghi `char` — sai; disassembly 0x9bb98 `ands r0, r3, #0xff`; map có count = 151) |
| nSummon + danh sách | u8 + n × u8 | loại quái triệu hồi (`< 3` → chỉ số vào bảng 3 × s16 `_map+0x48` → `MONSTER_addBody`) |
| nMon + mục | u8 + … | quái đặt sẵn, xem §4 |

Bản ghi đối tượng (8 byte): `{u8 b0, u8 b1, u8 b2, u8 b3, s16le x, s16le y}`.
`b0` bit 7 = sprite-object (`Map_getSprBody(b3, danhSách)` rồi `SprObject_copy(s8(b0), x, y, danhSách)`), ngược lại đối tượng tĩnh (`MAPOBJ_createAndCopy`).
Toạ độ x,y là pixel; `MAPOBJ_create` cộng `+8`, `+16` (giữa ô). Các trường `b1,b2` chưa rõ ý nghĩa (UNKNOWN).

## 3. Hai lớp ô (INFERRED từ `Map_Draw_Tile` @0x98b68 và các hàm thuộc tính; chưa kiểm vi sai — sẽ kiểm khi dựng bộ vẽ map ở Phase E)
- **Lớp A** (`byte A`): `A & 0x1f` = chỉ số ô trong dải tile (ô 16×16 xếp dọc nên tối đa 32 ô/tileset); `A & 0x20` = chọn hàm vẽ
  (`0` → `DM_DrawImageClipMirror` với mã lật, `1` → `DM_DrawEffectClip` với `rot = 0x5a`); `A & 0xc0` = mã lật `0x10 / 4 / 8 / 0xc`
  cho `(bit7,bit6) = 00 / 01 / 10 / 11`.
- **Lớp B** (`byte B`): `(B & 0xc) >> 2` = **tileset** dùng cho ô (phải `< nAB`); `B >> 4` = **thuộc tính ô** (`Map_Get_TIleProperty` @0xa1438):
  đi được khi thuộc tính ∈ {0, 7, 8, 9, 10} (`Map_canMoveTile` @0xa13a4); thuộc tính 1 = `Map_cannotMoveType1`; còn lại chặn. `B & 3`: chưa rõ (UNKNOWN).
  Thống kê 97 map: lớp B chỉ nhận 29 giá trị (đều là bội của 4); lớp A có 174 giá trị phân biệt.

## 4. Danh sách quái đặt sẵn (CONFIRMED hành vi, gồm lỗi của bản gốc)
`nMon` lần lặp; mỗi lần đọc `t = s8(byte)`:
- `t ≥ 3`: tiêu thụ **một** byte và không sinh quái (bản ghi bị vô hiệu — nhưng dữ liệu lưu mỗi bản ghi 3 byte, nên hai byte `x`,`y` còn lại bị **đọc tiếp như bản ghi mới** và cũng đếm vào `nMon`);
- `t < 3`: tiêu thụ 3 byte `(t, x, y)` và sinh `MONSTER_Clone(body, i, s8(x)·16 + 8, (y+1)·16, 0)` với `i` là chỉ số lần lặp.
Hệ quả quan sát được (QUIRK giữ nguyên): 79/97 map có bản ghi `t ≥ 3` (164/930 mục); khi `x` hoặc `y` của bản ghi vô hiệu `< 3` thì sinh thêm một con quái thừa ở toạ độ lạ,
và các bản ghi vô hiệu nằm *giữa* danh sách làm danh sách bị cắt cụt (vì mỗi byte bỏ qua tốn một lần lặp).
Có thêm luật boss/loại quái `'7'`/`':'` (cờ `_map[0x358]`, `_map[0x376]`) phụ thuộc bảng quái — chưa mô hình hoá (Phase E). Selftest dùng quái giả không phải boss.

## 5. Tileset `TILE/_TILE_###` và ảnh đối tượng `OBJ/` (CONFIRMED định dạng)
- Cả hai là **GBM đơn** (không phải container): `u8 type, u16 w, u16 h, u16 aux, u16 ncolors, palette RGB565, điểm ảnh` (`gbm.header`).
- Tile: 8 bpp, `w = 16`, `h = 16·số ô` (6..27 ô). Nạp: `Map_Load_Tile_image` @0xaa8b0 → `"/TILE/_TILE_%03d"` cho từng tileset khai báo (`DM_LoadResGbmBlending`, slot = `_map[0x78]`).
- Đối tượng: `"/OBJ/%03d/_OBJ_%03d"` với `(id/100, id)` (`Map_OBJIMG`), 4 hoặc 8 bpp; nạp bằng `DM_LoadResGbmBlending` cùng slot thời gian (`Map_Load_Object_Image_Base` @0xaae70).
- Pha màu theo giờ trong ngày: xem `phaseC_fx.md` §5 (đã kiểm vi sai trên 278 file TILE/OBJ thật).
- Phép tính vị trí vẽ ô và cuộn bản đồ (`Map_Draw_Buffer_Area_Copy`, `Map_Mapmove`, camera `Map_Position`): thuộc Phase E.

## 6. Kiểm chứng
Xem `docs/phaseC_map_selftest.txt`: **97/97 map khớp** trên mọi trường được kiểm — số tileset và cặp `(a,b)`, hai chuỗi, `W/H`, hai lớp nguyên vẹn,
cờ `_map[0x350]`, dãy id đối tượng/sprite, 16.358 bản ghi đối tượng (`Map_getSprBody(b3, danhSách)` đúng thứ tự; đối tượng tĩnh đi qua đúng 8 trường ở cùng độ lệch trong cấu trúc sao chép), 930 mục quái (kết quả `MONSTER_Clone`).

## 7. UNKNOWN / để sau
- `b` trong cặp `(a, b)`, `unk0`, `b1`/`b2` của bản ghi đối tượng, bit thấp của lớp B.
- Bảng quái 3 mục `_map+0x48` (từ đâu: kịch bản cảnh `_scn`/`Map_Initialize`) và luật boss — Phase E.
- Vẽ ô: xem §3.

## 8. Ghi chú cho bản Java ME
- Một map = hai `byte[W*H]` ≤ 3000 byte mỗi lớp (60×50), cùng vài danh sách nhỏ: nhẹ so với heap 2 MB.
- Ảnh tileset ≤ 7 KB nén (8 bpp 16×432 = 6,9 KB pixel); tất cả tile của một map nạp sẵn (≤ 3 tileset).
- Đọc đúng kiểu dữ liệu: **count u8 không dấu**, `x` của quái là `s8`, toạ độ đối tượng `s16`.
