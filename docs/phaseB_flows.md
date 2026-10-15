# Phase B — Luồng chính (AND2011)

Địa chỉ = vaddr ELF AND2011 `binary.mod`. Dump tái lập: `python3 tools/and_dump.py <binary.mod> - <hàm…>`.
Bảng hỗ trợ: `symbols_and2011.tsv` (2388 hàm), `callgraph_and2011.tsv` (6900 cạnh BL trực tiếp, 0 BL lệch đầu hàm),
`imports_and2011.tsv` (105 import), `fptrs_and2011.tsv` (77 slot con trỏ hàm trong data, 96 trong GOT), `objects_and2011.tsv` (633 biến).

## 1. Điểm vào nền tảng (CONFIRMED)
- `_export_table` → `init` @0x220c4: 3× `dlet_set_property`, rồi `wipic_event_handler(cfunc)` (tail-call 0x22124).
- `cfunc` (.data 0xe0bec) = `{startClet, pauseClet, resumeClet, destroyClet, paintClet, handleCletEvent}` — bảng callback Clet WIPI-C.
- `paintClet` @0x266bc rỗng (4 byte) — việc vẽ không qua paint của nền tảng, mà ở `timerCB`.

## 2. Khởi động (CONFIRMED)
`startClet` @0x31f34: `MC_miscBackLight`, `MC_mdaSetMuteState`, `CUSTOM_onStartClet` @0x26b78
(đọc `PHONEMODEL`; nếu `== "LG-LU9400"` thì bật `internalTimerNanoCheck`; `MC_knlDefTimer`, `MC_knlSetTimer`), rồi `clsidAuth` (xác thực LGT — ngoài phạm vi offline).
`onStartApp` → `DM_Initialize`, `DM_initLCDEffect`, `FR_Load`, `HF_Initialize`, `Item_dataPreLoad`, …, `MainFrame_Initialize` @0x890f0,
`MyTimer_changeDefaultFps(1)` @0x33764.

## 3. Game loop / timing (CONFIRMED)
- `MainFrame_ShowNotify` → `MyTimer_setTimer` @0xa7938: `CUSTOM_knlDefTimer(&_mTimer+0x18, timerCB)`, hẹn **50 ms** (0xa796c).
- `timerCB` @0xa7994 (mỗi lần = 1 khung):
  `MainFrame_KeyPressed` → `MainFrame_KeyReleased` → `MainFrame_TouchNotify` → `MainFrame_Process` → `MainFrame_Render` →
  `MainFrame_EndProcess` → `SM_PlayResEx` → (vẽ pad ảo) → `CUSTOM_grpFlushLcd` → `MyTimer_setTimerAfterPaint` (tail 0xa7a54).
- `MyTimer_setTimerAfterPaint` @0xa78dc: one-shot với độ trễ 64-bit `_mTimer+0x10` (đặt lại **sau** khi vẽ xong ⇒ chu kỳ = thời gian xử lý + trễ).
- Độ trễ: mặc định **1 ms** (`onStartApp` 0xa7764), `MyTimer_setFrameRateLoad` = 10 ms, `MyTimer_setFrameRateMenu` = 1 ms.
  `MyTimer_setFrameRateDefault` gọi `MyTimer_setFrameRate` — hàm rỗng (`bx lr`, 0xa7880) ⇒ không có tác dụng.
- ⇒ Logic chạy **theo khung**; tốc độ game phụ thuộc tốc độ máy (giống nhận xét về bản KTF trong project cũ).
  Nhịp chuẩn để tái hiện cần đo trên emulator đối chứng / thông tin `nFPS_*` (UNKNOWN). Đây là quyết định timing sẽ phải chốt ở Phase E/F.
- `CUSTOM_extSetTimer` @0x26e64: máy thường → `MC_knlSetTimer` trực tiếp; LU9400 → bảng 20 timer tự quản, hạn = now + delay − 20 ms, tối thiểu 5 ms.

## 4. Input (CONFIRMED phần phím)
`handleCletEvent` @0x32bb4 → `clsidAuthHandle` @0x32a18 (lúc đang xác thực thì nuốt phím số) → `onHandleEvent` @0x330d4
(hoặc `imeLocal_onHandleEvent` @0x2da9c khi byte `[GOT+0x24c]+0x3c ≠ 0` — INFERRED: trạng thái IME/nhập chữ).
- Mã sự kiện: 0x1f6 = phím nhấn → `mainFrame+0x30 = keycode`; 0x1f7 = phím nhả → `mainFrame+0x34`;
  0x5064/0x5065/0x5066 = cảm ứng nhấn/nhả/kéo (`touch_DirectEvent`, toạ độ `mainFrame+0x3c/0x40`). Bỏ qua mọi sự kiện khi `LoadFrame_isLoading`.
- Phím **được chốt** (latched) rồi xử lý ở tick kế tiếp (`timerCB` gọi `MainFrame_KeyPressed/Released` trước `Process`).
- `touch_keyChange` @0x33d48 đổi mã phím khi `nTouchRotate` (GOT 0xe09bc → 0xf13ec) `== 1` (0x332d8, 0x332fc).
- Việc tiếp theo: bảng keycode WIPI-C (`MH_KEY_*`) dùng trong `*_KeyPressed`; tách hẳn nhánh cảm ứng.

## 5. Frame (màn hình trạng thái) (INFERRED từ tên + call graph; xác minh tiếp)
Các frame `Logo/Title/MainMenu/Load/MainGame/GameMenu/TS(touch)` mỗi frame có bộ hàm
`_ShowNotify/_HideNotify/_Process/_Render/_KeyPressed/_KeyReleased/_EndProcess`, đăng ký qua con trỏ lấy từ GOT
(`fptrs_and2011.tsv`, phần `.got`). Menu con dùng bảng con trỏ: `gMenuPaint/gMenuKey` (8), `mMenuPaint/mMenuKey/mMenuProc` (7),
`NpcUiPaint/NpcUiKey/NpcUiProc` (11).

## 6. Render / audio / save / mạng — trạng thái
| Luồng | Neo | Trạng thái |
|---|---|---|
| Render | `DM_*` (93 hàm), offscreen `_lcdG+0x10`, `CUSTOM_grpFlushLcd` @0x2fd94 | neo xong, chi tiết ở Phase B tiếp |
| Audio | `SM_*`, `SM_PlayResEx`, `MC_mdaClip*`, `CUSTOM_mdaClipPutData[ByFile]`, `loadBGMSound` | chưa phân tích |
| Save | `MX_sd*`, `MC_fs*` (5 hàm open), tên `./Hero4Game ./Hero4Slot ./Hero4OptSave ./Hero4Sms`, mẫu `kickass` thật | chưa phân tích |
| Mạng/thanh toán | `MC_net*`, `_midas_*` (108), `clsidAuth*`, `NpcUiNet*` | ngoài phạm vi (offline); cần biết điểm cắt an toàn |

## 7. Float (CONFIRMED trên chính binary nguồn)
Chỉ 6 hàm gọi soft-float: `make_Lenz_array`, `make_Warp_array`, `_sin`, `_sqrt`, `MGHEO_sqrt`, `DM_ConvertHSBtoRGB` (`docs/so_floatscan_output.txt`).
