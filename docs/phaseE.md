# Phase E — Logic game (Java ME) kiểm chứng từng tick với bản gốc

Công cụ chính: **bộ chạy tham chiếu toàn game** `tools/ref_game.py` (ARM/Unicorn + HLE import WIPI-C; chỉ để kiểm chứng, không phải runtime của sản phẩm)
và phép so **từng tick** (`tools/e_trace.py` ↔ `hl4.test.TraceTest`): mỗi tick ghi FNV-1a của vùng game 240×320 + số lần gọi `UM_Random`.
Lệch đầu tiên chỉ ra đúng tick/hàm sai; `tools/e_diff.py` vẽ ảnh gốc / ảnh Java / ảnh sai khác. Golden của từng hàm lấy **trực tiếp từ hàm gốc**
(`tools/e_golden.py`, `tools/e_test.py`, kết quả `docs/phaseE_selftest.txt`).

## 1. Phát hiện kiến trúc quan trọng (CONFIRMED bằng chạy bản gốc)
1. **AND2011 mặc định là game ngang 320×240**: `OptionInit` @0xbd624 đặt `_menuOptions+4 = 1` ⇒ `touch_setUiChange` (0x35d50) chọn vùng game 320×240 xoay lên khung 240×400
   (`nTouchRotate = 1`, `DM_CopyFrameBufRotate`). Binary vẫn giữ nhánh dọc (`+4 == 0`: 240×(frame_h−keysize)). Port nhắm **240×320 dọc** (tham số đã duyệt)
   ⇒ tham chiếu ép `+4 = 0` sau `OptionInit` (hook tại lệnh `bx lr` cuối). `docs/phaseB_display.md` ghi giả định dọc nhưng bỏ sót mặc định này — **đã sửa ở đây**.
2. **Lớp "Touch" không chỉ là cảm ứng**: `TF_setFrame(id)` chọn một animation của 4 CIF (`TOUCH/CIF/*.cif`) vẽ quanh tâm màn hình, kèm bảng "extradata"
   (`TOUCH/EXD/*.exd`) mà game dùng để đặt chữ, nút, `menuX/menuY`. Nền hộp thoại/menu của AND2011 nằm ở đây ⇒ phải port (`hl4.game.Touch`).
   Chỉ bỏ: vùng bấm (`touch_Addrect`, mã vùng) và bàn phím ảo (`touch_drawVirtualPad`, vẽ dưới hàng 320).
3. Trước `TitleFrame_Initialize` các tùy chọn `_menuOptions` đều **0** (không phải mặc định): ảnh hưởng `MenuOptions_particle()` (alpha hiệu ứng) và `fps[]`.
4. Nhịp khung: sau mỗi lần vẽ game hẹn lại sau `fps[_menuOptions[2]]` ms (`fps = 90,80,70,60,50,40,30,20,10,1`; mặc định tuỳ chọn 6 ⇒ 30 ms; màn tải 10 ms).
   Tốc độ thật phụ thuộc máy ⇒ chu kỳ chuẩn của bản J2ME là quyết định Phase F (đề xuất 30 ms + thời gian xử lý).

## 2. Cấu hình tham chiếu (ghi ở đầu mỗi trace, `ref_game.IMPL`)
Đồng hồ ảo (gốc 2011-04-20 12:00 KST, +50 ms/tick), `PHONEMODEL="HL4-REF"`, `TIMEZONE="GMT+09:00"`, FS trong RAM, audio no-op, chiều cao game ép 320 ngay từ đầu
(bản gốc dùng 400 ở màn logo), phím hiển thị bàn phím ảo bị chặn, `MC_grpFillRect/DrawRect/DrawLine` do HLE thực hiện (ngữ nghĩa INFERRED: FillRect phủ w×h;
DrawRect viền khoảng bao gồm x..x+w, y..y+h — game truyền w−1,h−1 và ghép với fill w−2; DrawLine Bresenham gồm cả hai đầu; chỉ cắt theo bộ đệm khung).

## 3. Đã port và đã khớp bản gốc
| Module | Kiểm chứng |
|---|---|
| `Rng` (UM_Random/Rand/RandRatio) | số lần gọi + hệ quả hình ảnh khớp trong trace |
| `Hf` (HF_DrawTextAnchorH2Color, GetWidth2, GetByteLenPrintable, màu escape, jamo đặc biệt) | 6000 ca vẽ + 3000 ca đo rộng khớp 0 lệch (`HfTest`); GetByteLenPrintable qua trace |
| `Prim`/`Gfx` (FillRect, DrawRect, DrawLine, FillRectAlpha, CopyOffScreen) | qua trace (FillRectAlpha, DrawLine chưa có golden riêng) |
| `Touch` (TF_setFrame/Push/Pop, extradata, Touch_draw) | trace |
| `LoadFrame`, `LogoFrame`, `TitleFrame` (hộp đồng ý), `MapFx` (fade + hiệu ứng rơi) | trace A: 300 tick (logo → trang thông báo → logo → tải → title → hộp đồng ý), hash + RNG trùng từng tick |
| `GameStart`, `Scene`, `Maps` (nạp scene/map/object), `Npcs`, `Chars`, `HeroProc` (event mode), `Cam`, `Event`/`Evt`/`It` (VM script: lệnh 0–9, 0xf, 0x20, 0x23, 0x24, 0x2b, 0x2d–0x31, 0x37) | trace C: tick 501–699, hash khung + RNG + back-buffer |
| Chuỗi vẽ `MainGameFrame_drawGame`: `MapDraw` (back buffer cuộn theo camera), `MapObjDraw`, `ObjDraw`, `HeroDraw`, `EventDraw` (dải đen, tint, SKIP, fade skip) | trace C: tick 501–699 |
| `Dialog` (ScriptWindow open/close, TalkBox reset/tick, thứ tự vẽ 0x510, khung `drawGMenuBackFrame_main`, tên "=%s ", mặt `NPC/FACE_n`, chữ chạy `HF_GetNextLen`) | trace C: tick 607–699 (hộp thoại đầu) |

## 4. Khác biệt có chủ đích so với bản gốc (Phase E)
- Vùng game 240×320 ngay từ màn logo (bản gốc 240×400 trước title). Chỉ đổi vị trí dọc của màn logo/thông báo.
- Chuỗi "TOUCH THE SCREEN" ở màn title giữ nguyên (bản keypad nên đổi nhãn — quyết định Phase F).
- Không có vùng bấm / pointer / bàn phím ảo. Âm thanh chưa gắn (lời gọi `Sound.*` giữ chỗ).
