# Phase B — Màn hình / viewport (AND2011)

Nguồn: `docs/phaseB_display_dump.txt` (tái lập: `python3 tools/and_display.py <AND2011 binary.mod> docs/phaseB_display_dump.txt`).
Mọi địa chỉ là vaddr ELF của AND2011 `binary.mod`.

## Kết luận
**Vùng game nội bộ là 240×320 khi khung hình rộng 240 và cao ≥ 400. Phần cao dư là bàn phím ảo cảm ứng.**
Tham số đích 240×320 đã duyệt **giữ nguyên**.

| # | Sự kiện | Nhãn | Địa chỉ |
|---|---|---|---|
| 1 | Kích thước khung lấy lúc chạy: `wipic_get_frame_width/height` → struct info `{stride=2w, bpp=2, w, h, ptr}` (RGB565) | CONFIRMED | `DM_GET_FRAME_BUFFER_INFO` 0x50514–0x5054c |
| 2 | Lưu `_lcdG+0x64 = frame_w`, `_lcdG+0x68 = frame_h` (copy struct vào `_lcdG+0x5c`); `_lcdG+0x54/0x58` = w/h vùng game; tạo offscreen buffer theo w/h đó | CONFIRMED | `DM_Initialize` 0x5603c–0x56070 |
| 3 | `m_TouchDirectKeySize = frame_h − 320` nếu `frame_h > 400`, ngược lại `= 80` | CONFIRMED | `TitleFrame_Initialize` 0xc8bfc–0xc8c48 |
| 4 | Dọc (`_menuOptions+4 == 0`): `game_h = frame_h − m_TouchDirectKeySize`, `game_w = frame_w` | CONFIRMED | `touch_setUiChange` 0x35c58–0x35c6c |
| 5 | Ngang/xoay (`_menuOptions+4 != 0`): `game_w = frame_h − keysize`, `game_h = frame_w` | CONFIRMED (gán) / INFERRED (ý nghĩa "xoay") | `touch_setUiChange` 0x35d64–0x35d78 |
| 6 | Bố cục dùng `_lcdG+0x54/0x58` (35/30 hàm: Map, MMenu, PopupBox, MainGameFrame_draw*, Event_Draw_*, TitleFrame_Render…), không cố định hằng | CONFIRMED | quét toàn bộ hàm bằng `andelf.annotate` |
| 7 | Chế độ QCIF: `MainFrame_isQcif` ⇔ `game_w ≤ 176` | CONFIRMED | 0x88cb4–0x88cc0 |
| 8 | Định dạng điểm ảnh RGB565 (mask 0xf800/0x7e0/0x1f; `CUSTOM_grpGetDisplayInfo` đổi R/B nếu máy báo BGR) | CONFIRMED | `DM_Initialize` 0x56108–0x56120, `CUSTOM_grpGetDisplayInfo` 0x27a74–0x27aa4 |
| 9 | Mỗi tick: vẽ vào offscreen `_lcdG+0x10`, `touch_drawVirtualPad`, `CUSTOM_grpFlushLcd` | CONFIRMED | `timerCB` 0xa79e4–0xa7a34 |

## Hệ quả cho bản J2ME
- Mô phỏng cấu hình nền tảng `frame = 240×400` (keysize = 80) ⇒ `game = 240×320`, chế độ dọc. Hiển thị **chỉ** vùng game 240×320
  trên Canvas; không vẽ bàn phím ảo (`touch_drawVirtualPad`).
- Không đặt `frame_h = 320`: theo #3/#4 game sẽ ra 240×240 — sai so với bản gốc.
- Chưa biết (UNKNOWN): frame thực của máy LG-LU9400/Optimus khi chạy app này (cần để tái hiện y hệt vùng pad). Điều này không ảnh hưởng vùng game.
