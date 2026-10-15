# Phase B — Luồng chính (AND2011)

Địa chỉ = vaddr ELF AND2011 `binary.mod`. Dump tái lập: `python3 tools/and_dump.py <binary.mod> - <hàm…>`.
Xref bộ nhớ toàn cục: `python3 tools/and_xref.py build <binary.mod> work/cache/xref.tsv` rồi `… q work/cache/xref.tsv '<regex>'`.
Bảng hỗ trợ (đều tái lập bằng `tools/and_*.py`):
- `symbols_and2011.tsv` — 2388 hàm.
- `callgraph_and2011.tsv` — 7487 cạnh: BL trực tiếp + 775 tail-call `B`. 0 nhánh trỏ lệch đầu hàm.
- `imports_and2011.tsv` — 105 import, import nào cũng có nơi gọi.
- `fptrs_and2011.tsv` — 77 slot con trỏ trong data, 96 trong GOT.
- `objects_and2011.tsv` — 633 biến.
- `reach_and2011.txt` — **1942 hàm sống / 446 hàm chết**.

Hàm "getter" lá (`MainFrame_Get→mainFrame`, `MP_getHero→_pool`, `MP_getBag→_itemBag`, `MP_getArmsBag→_armsBag`, `UM_TURN_*`)
được bộ chú thích tự nhận diện, nên các truy cập kiểu `bl MainFrame_Get; str rX,[r0,#k]` cũng nằm trong xref.

## 0. Hàm sống / chết (`tools/and_reach.py`)
- Gốc = `init` + 158 hàm bị lấy địa chỉ (con trỏ trong data/GOT, hoặc nạp địa chỉ hàm qua GOT/GOTOFF trong code).
- 446 hàm không đến được từ gốc. Ví dụ: `nFPS_*` (debug FPS), `Boss_AI*`, `make_Warp_array`, `_sin`, `_sqrt`, `UM_RandSeed`,
  `auth_netCONNECTcb`, nhiều `MX_dsp*`/`MX_fnt*`/`_midas_*`, 17 hàm `Touch_*`.
  Nhãn **INFERRED dead**: không thấy BL/B, không có con trỏ, không có chỗ nạp địa chỉ. Các hàm này **không port**.
- Float còn sống: `make_Lenz_array`, `MGHEO_sqrt`, `DM_ConvertHSBtoRGB`. Đã chết: `make_Warp_array`, `_sin`, `_sqrt`.

## 1. Điểm vào nền tảng (CONFIRMED)
- `_export_table` → `init` @0x220c4: 3× `dlet_set_property`, rồi `wipic_event_handler(cfunc)` (tail 0x22124).
- `cfunc` (.data 0xe0bec) = `{startClet, pauseClet, resumeClet, destroyClet, paintClet, handleCletEvent}`.
  `paintClet` @0x266bc rỗng; mọi việc vẽ nằm trong `timerCB`.

## 2. Khởi động và xác thực (CONFIRMED)
`startClet` @0x31f34: `MC_miscBackLight`, `MC_mdaSetMuteState`, `CUSTOM_onStartClet`, rồi `clsidAuth(type)`.
- `CUSTOM_onStartClet` @0x26b78 đọc `PHONEMODEL`. Nếu là `"LG-LU9400"` thì bật `internalTimerNanoCheck`. Sau đó `MC_knlDefTimer` và `MC_knlSetTimer`.
- `clsidAuth` → `clsidAuthDo` @0x31f10 (`b auth_startApp`) → `auth_startApp` gán `internalAuthStatus = 0`
  → `gamerating_close` → `__knlGR_release`, **`onStartApp`**, rồi gán `internalAuthStatus = 1` (0x31ed8). Ở bước này không kiểm tra gì.
- Màn mật khẩu/hết hạn `internalAuthCB` @0x31f70 (so với mốc **2011-04-25 02:36:47 UTC** = `0x4db4de3f` @0x32050; có `%dDay %dHours`, `internalAuthSysPwd/UserPwd/FailCnt`)
  chỉ chạy khi `internalAuthStatus == 5` (`clsidAuthHandle` 0x32a40).
  Giá trị 5 chỉ được ghi trong `auth_netCONNECTcb` (0x31cf4), mà hàm này **chết** ⇒ cơ chế khoá thời hạn **không bao giờ kích hoạt**. Không port.
- `onStartApp` @0x336d8: `DM_Initialize`, `DM_initLCDEffect`, `FR_Load`, `HF_Initialize`, `Item_dataPreLoad`, `loadCommonMenuImage`, …;
  gán `mainFrame+0x30 = mainFrame+0x34 = −100` (0x33738); `MainFrame_Initialize`; `MyTimer_changeDefaultFps(1)` (0x33764).
- `MainFrame_Initialize` → `MainFrame_certification` @0x89010: đọc `PHONENUMBER`, kiểm tra save tồn tại (`MX_sdIsExist` → `isExistSaveData`),
  giải mã/so file `CERTIDATA` (`UM_FileDecoding`/`UM_FileEncoding`). Kết quả → cờ `mainFrame+0x4e`. Đây là cơ chế gắn save với số máy (INFERRED).
  Bản offline sẽ cấp `PHONENUMBER` cố định.

## 3. Game loop / timing (CONFIRMED)
- `MainFrame_ShowNotify` → `MyTimer_setTimer` @0xa7938: `CUSTOM_knlDefTimer(&_mTimer+0x18, timerCB)`, hẹn 50 ms (0xa796c).
- `timerCB` @0xa7994, mỗi lần chạy = 1 khung:
  1. `MainFrame_KeyPressed` → `MainFrame_KeyReleased` → `MainFrame_TouchNotify` → `MainFrame_Process` → `MainFrame_Render` → `MainFrame_EndProcess`
  2. → `SM_PlayResEx` → (vẽ pad ảo) → `CUSTOM_grpFlushLcd`
  3. → gán `mainFrame+0x30 = +0x34 = −100` (0xa7a3c–0xa7a48) → `MyTimer_setTimerAfterPaint`
- `MyTimer_setTimerAfterPaint` @0xa78dc hẹn one-shot với độ trễ 64-bit `_mTimer+0x10`, **sau** khi đã vẽ xong.
  Trễ mặc định 1 ms; `MyTimer_setFrameRateLoad` = 10 ms; `MyTimer_setFrameRateMenu` = 1 ms (hàm này chết).
  `MyTimer_setFrameRate` @0xa7880 rỗng.
- ⇒ Logic chạy theo khung; tốc độ phụ thuộc máy. Nhịp chuẩn để tái hiện là **UNKNOWN**, sẽ chốt ở Phase E/F.
- `MainFrame_Process` @0x88f9c: `checkAndPlayBGM` → `nBASE_Process()` → gán `mainFrame+0x1c = +0x18` (phím khung trước), `+0x18 = 0`.

## 4. Input (CONFIRMED)
- Đường đi: `handleCletEvent` @0x32bb4 → `clsidAuthHandle` @0x32a18 → `onHandleEvent` @0x330d4.
  Khi byte `[GOT+0x24c]+0x3c ≠ 0` thì đi vào `imeLocal_onHandleEvent` thay vì `onHandleEvent` (INFERRED: chế độ nhập chữ).
- Mã sự kiện: 0x1f6 = nhấn → `mainFrame+0x30 = key`; 0x1f7 = nhả → `mainFrame+0x34 = key`; 0x5064/0x5065/0x5066 = cảm ứng (không port).
  Bỏ qua mọi sự kiện khi `LoadFrame_isLoading()`. Khi `nTouchRotate == 1`, mã phím đi qua `touch_keyChange`.
- **Ngữ nghĩa bắt cạnh**: −100 = "không phím". Ở tick kế tiếp:
  - `MainFrame_KeyPressed` gọi `nBASE_KeyPressed(key)` nếu `+0x30 ≠ −100`, và gán `+0x14 = 1`, `+0x18 = key`.
  - `MainFrame_KeyReleased` gọi `nBASE_KeyReleased(key)` nếu `+0x34 ≠ −100`, gán `+0x14 = 0`, rồi reset `+0x34`.
  - Cuối tick, cả hai bị reset.
  - ⇒ Mỗi sự kiện tạo đúng một lần gọi. Không có tự lặp phím trong game. Nếu nhiều lần nhấn rơi vào cùng một tick, chỉ lần cuối được giữ.
- Bảng phím (giá trị CONFIRMED qua các phép so sánh; tên theo quy ước WIPI là INFERRED):

| Mã | Ý nghĩa trong game | Bằng chứng |
|---|---|---|
| −1/−2/−3/−4 | lên/xuống/trái/phải | `HERO_Match_Number_key` @0x76a74 ánh xạ `2→−1, 8→−2, 4→−3, 6→−4` |
| −5 | OK (`5→−5`) — nhặt đồ / `Event_CheckOKKey` | 0x76ab8, `MainGameFrame_GamePlayKeyPressed` 0x8ca70 |
| −16 | CLR — mở menu game (`MainFrame_changeFrame(1)`, khi HP > 0) | 0x8cb0c–0x8cb3c |
| −7 | phím mềm phải — `NpcUi_Initialize(0, 0xb)` (khi `_map+0x375 ≠ 0`) | 0x8caa4, 0x8cb9c–0x8cbc0 |
| −8 | vào quick-save (`MainGameFrame_initQuickSave`) | 0x8ca98, 0x8cb6c–0x8cb98 |
| −6 | bị bỏ qua trong `HERO_Key_press` | 0x7adfc |
| `0 1 3 7 9` | dùng skill theo ô (`HERO_check_skilluse`), `5` = slot OK | `HERO_Key_press` 0x7aec8–0x7aff4 |
| `#` (0x23) | đổi bộ skill (`hero+0x20c ^= 1`) | 0x7af24 |
| `*` (0x2a) | bật/tắt `_game+3` (UNKNOWN chức năng; có thể là bản đồ nhỏ) | 0x8caac–0x8cacc |

## 5. Frame (màn hình trạng thái) (CONFIRMED cơ chế)
- 9 slot con trỏ toàn cục `nBASE_{Process 0x101168, Render 0x1011c4, KeyPressed 0x10115c, KeyReleased 0x1011bc, EndProcess 0x101160,
  ShowNotify 0x1011c8, HideNotify 0x101164, TouchPressed 0x1011cc, TouchReleased 0x1011c0}` do `MainFrame_ElementAt` @0x88b1c gán.
- `MainFrame_changeFrame(n)` gán `mainFrame+0x38 = n`. `MainFrame_EndProcess` gọi `nBASE_EndProcess()` rồi `changeFrame(0)`.
- Mỗi frame (`Logo/Title/MainMenu/Load/MainGame/GameMenu/TS`) có bộ `_ShowNotify/_HideNotify/_Process/_Render/_KeyPressed/_KeyReleased/_EndProcess`.
  Menu con dùng bảng con trỏ: `gMenuPaint/gMenuKey` (8), `mMenuPaint/mMenuKey/mMenuProc` (7), `NpcUiPaint/NpcUiKey/NpcUiProc` (11).
- Việc tiếp theo: bảng chuyển frame (ai gọi `MainFrame_ElementAt` với bộ hàm nào; `_nextFrame`).

## 6. RNG (CONFIRMED)
- `UM_Random` @0xc8f2c = additive feedback kiểu BSD `random()` TYPE_3: `*fptr += *rptr; kết quả = *fptr >> 1`, 31 word, cách nhau 3.
  `fptr/rptr` quay vòng trong `_randtbl[1..31]` (`_randtbl` @0xe1f90, `_fptr` @0xe2010, `_rptr` @0xe2014).
- Trạng thái đầu = bảng BSD gốc ứng với seed 1 (`00000003 9a319039 32d9c024 …`, giống `C.19.18250` @0xd99cc).
  `UM_RandSeed` **chết** ⇒ dãy số **tất định** từ khi khởi động. 10 giá trị đầu (mô phỏng): 2078917053, 143302914, 1027100827, …
- `UM_Rand(a,b) = a + UM_Random() % (b−a+1)` (chia không dấu); `UM_RandRatio(p) = UM_Rand(0,100) ≤ p`.
- Hệ quả: golden trace chỉ cần cùng chuỗi input và cùng số lần gọi RNG; không cần seed.

## 7. Âm thanh (CONFIRMED)
- Bản 2011 chỉ phát **OGG**: `/SND/BGM_%02d.ogg`, `/SND/EFF_%02d.ogg`, MIME `audio/OGG` (0xbf0d0). Binary không còn chuỗi `MMF` nào.
  Jar vẫn chứa `*_MMF` cùng chỉ số (20 BGM + 22 EFF) nhưng code 2011 không dùng. `BGM_11_bak` cũng không dùng.
- Chỉ **một kênh** (`_pClip`):
  - `SM_PlayEffSound(idx, loop)` @0xbefb0 (khi volume > 0): `_timeToPlayEffect = 30`, `SM_PlayRes` dừng âm hiện tại,
    xếp `_clipToPlay = idx` (≤ 26) và `_clipToRepeat = loop`.
  - `SM_PlayResEx` (cuối mỗi tick) tạo clip và phát.
  - `checkAndPlayBGM` (đầu `MainFrame_Process`) đếm ngược `_timeToPlayEffect`. Về 0 (và hiệu ứng không lặp) thì phát lại BGM **từ đầu**.
    Khi đang ở menu tuỳ chọn (`mainFrame+0x4d` hoặc `_isOptMenu`) thì giữ ở 1.
- Hệ quả cho J2ME: OGG không dùng được. Lấy âm từ `*_MMF` cùng chỉ số (chuyển sang MIDI/WAV, có script tái lập) và ghi nhận âm sắc có thể khác OGG 2011.
  Mô hình một kênh và các bộ đếm theo khung phải giữ nguyên.

## 8. Save (CONFIRMED chuỗi gọi; định dạng nội dung ở Phase C)
- `SaveData_Game` → `SaveGameData` (tail `b` → `SaveGameData_Net(…, 0, 0)` tuần tự hoá toàn bộ) → `SaveGameWrite("./Hero4Game")`
  → `SaveSlotInfo` (16 B) → `SaveGameWrite("./Hero4Slot")`.
  `SaveGameWrite` ghép tên `"%s_%d"` với số slot `_gameMenu+0x16`. `SaveGameBase` sao chép → `MX_desEncrypt` → `UM_FileWrite` → `MX_sdSaveData`.
- Nhiều hàm `*_save` cũ chỉ còn là stub rỗng (`MainGameData_save`, `HeroData_save`, `MainGameFrame_save`: 4 byte).
- Vùng chứa MIDAS SD: **một file vật lý `kickass`** (`_midas_funcSdOpen` @0x2b7f8) chứa mọi file logic.
  - Header v2 = magic `FF 56 30 00` + 40 mục × 20 B (0x320). Header cũ = 20 mục × 20 B (0x190), không có magic (@0x301f8–0x302cc).
  - Mục = `name[16]` (tối đa 15 ký tự, `_midas_funcSdMakeName`) + `u16 capacity` + `u16 size`.
  - Dữ liệu bắt đầu ngay sau header; mục thứ k nằm ở `hdr + Σ capacity(0..k−1)` (`_midas_funcSdLoadData` 0x300c4–0x30144).
  - Ghi luôn dùng header v2 (`_midas_funcSdWriteHeader` 0x308d0–0x30924).
- **UNKNOWN**: file `kickass` mẫu trong gói Android (5785 B) không khớp layout này. Header chỉ có mục `./Hero4Sms` (cap 0x100, size 8),
  các mục còn lại bằng 0, nhưng có dữ liệu ở 0x285, 0x385, 0x485 (1328 B), 0xb85 (2608 B). Có thể file đến từ bản khác
  hoặc từ một phiên bản thư viện MIDAS khác. Cần đối chiếu ở Phase C.
- Save gắn với số máy qua `CERTIDATA` (§2).

## 9. Mạng / thanh toán — điểm cắt (INFERRED, xác minh khi triển khai)
- Không có kết nối nào khi khởi động (xem §2).
- `−7` trong lúc chơi mở `NpcUi` chế độ 11 = `NpcUiNet_*` (cửa hàng/mạng). `SaveGameData_Net` ngoài đường save còn được gọi từ `NpcUi_ProcRecvPacket_SINGLE_MAN` (gói nhận).
- Bản offline: chặn chế độ NpcUi 11 và các gói mạng. Ghi lại đây là thay đổi hành vi.

## 10. Render (neo; chi tiết ở Phase C/E)
- Offscreen RGB565 `_lcdG+0x10` (w×h vùng game); `_lcdG+0x54/0x58` = w/h; clip `DM_SetCliptoGBM`.
- Ảnh dùng định dạng "GBM" (`DM_LoadResGbm565`, `DM_DrawGbm4Bit`, palette `DM_ChangePalette`); sprite qua `SPR_drawSprite`.
- Xuất màn qua `CUSTOM_grpFlushLcd` @0x2fd94 → `MC_grpFlushLcd`.
