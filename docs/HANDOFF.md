# HANDOFF: hướng dẫn cho phiên làm việc sau

Cập nhật: 2026-10-17. **Bàn giao giai đoạn mới nhất: `docs/HANDOFF_E6.md`** (trạng thái E6, phần chưa kiểm chứng, bản chạy thử, việc tiếp theo); §4–§5 dưới đây là ghi chép cũ của E5. Nhánh làm việc: `ccr-f53d26cc-ct4qk8` (push bằng `git push -u origin ccr-f53d26cc-ct4qk8`, không tạo PR khi chưa được yêu cầu).
Đọc trước: `NOTES.md` (quyết định, phạm vi, trạng thái) và `docs/phaseE.md` (kiến trúc + cách kiểm chứng Phase E).

## 1. Dự án là gì, đang ở đâu

Viết tay một game Java ME (CLDC 1.1 / MIDP 2.0, 240×320 dọc, không float, heap ≥ 2 MB) tái hiện 영웅서기4-환영의가면.
Nguồn sự thật: `binary.mod` của bản LGT-Android 2011 (ELF ARM, 2388 hàm có tên) và thư mục `res/` của nó.
Không phải launcher, emulator hay transcode máy móc. Emulator ARM (`tools/ref_game.py`) chỉ dùng để **kiểm chứng**.

**Tiến độ đo được** (`python3 tools/port_cover.py`):

- Java dẫn chiếu 649 / 2113 hàm trong phạm vi, tức **≈ 30,7%**.
- Còn 114 chỗ `later` trong mã: nhánh chưa port, chạm tới thì ném `IllegalStateException`.
- Khoảng 25k dòng Java.

| Mốc | Trạng thái | Bằng chứng |
|---|---|---|
| Toolchain, scanner, DES, GBM, font HF, sprite CIF, palette, blit/fx | xong | `docs/phaseC_*`, `docs/phaseD_selftest.txt` (0 lệch) |
| Logo → Load → Title → hộp đồng ý | xong | trace A, hash từng tick khớp |
| Vào game: scene, map, NPC, cutscene mở đầu (fade, camera, SKIP) | xong | `trace_C`: tick 501–606 khớp hash khung + RNG + back-buffer |
| Hội thoại cutscene (khung trượt, mặt, tên, chữ chạy) | xong (commit `f2eb960`) | `trace_C`: tick 501–699 khớp |
| Phím trong game / event, điều khiển hero | xong phần trace chạm tới | trace E–K |
| Quái/chiến đấu/hạt/hồi sinh/game over (E6, một phần: xem `docs/phaseE.md` §3b) | một phần | trace K khớp tới tick 5300, trace L (hero chết, popup game over) tới 5496, trace M (rơi đồ, nhặt đồ, level-up) trọn 9000 |
| Story-mode UI (E4), menu/túi đồ/shop/save/âm thanh/đóng gói (E8) | chưa | — |
| Build: JAR preverify, scanner 0 reject | đạt | `python3 tools/j2me_build.py build` |
| Thiết bị thật / KVM thật | **chưa xác minh** | — |

Các module lớn còn thiếu: HERO 29/156, MONSTER 20/148, CHAR 30/113, GMenu 5/78, SUMMON 4/48, Boss 2/36, GameMenuFrame 0/14, NpcUi 0/20, SM (âm thanh) 0/15.

## 2. Dựng lại môi trường trong container mới

`work/` bị gitignore (tài nguyên gốc không được commit). Container mới **không có** `work/`, nên phải làm lại:

1. Xin người dùng tải lên 2 file gốc. Script kiểm SHA-256 trước khi dùng:
   - `영웅서기4_KTF_wipiX호환.zip` (`49ade895…5c42`)
   - `hl4_android.zip` (`45adb3ee…0a93`)
2. Chạy `tools/setup_env.sh <KTF zip> <hl4_android.zip> [--ghidra]`. Script làm các việc sau:
   - `pip install unicorn==2.1.4 capstone==5.0.9`
   - lấy các jar build (ECJ 4.6.1, ProGuard 6.2.2, stub CLDC/MIDP, MicroEmulator 2.0.4) từ `HL4_project_2026-09-27j.zip` đã commit và kiểm hash
   - dựng `work/orig` bằng `tools/inventory.py`, tạo symlink `work/res`, build JAR
   - với `--ghidra`: tải Ghidra 11.3.2 rồi sinh `work/ghidra/decomp_and2011.c` cho `tools/cfunc.py` (chỉ để đọc, mất khoảng 10 phút)
3. Python và JVM cần `LC_ALL=C.UTF-8` (đường dẫn có tiếng Hàn). Binary nằm ở
   `"work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod"`. Địa chỉ Ghidra = địa chỉ thật + 0x10000.
4. Golden vector và golden trace của bản gốc **đã có trong repo** (`golden/`, chỉ chứa hash, số lần gọi RNG, dump trạng thái game; không chứa tài nguyên gốc). `setup_env.sh` tự chép sang `work/golden`. Chỉ phải sinh trace **mới** (§4); trace mới sinh xong thì chép vào `golden/` và commit.

## 3. Công cụ hằng dùng

| Lệnh | Việc |
|---|---|
| `python3 tools/disf.py NAME…` | disassembly có chú thích (GOT, `_event+off`, tên lời gọi). **Disassembly thắng pseudo-C.** Dừng ở literal pool đầu tiên. |
| `python3 tools/disr.py <hex> <hex>` | disassembly theo khoảng địa chỉ, đi qua được literal pool (dùng cho phần sau pool của hàm lớn như `EVT_OnFunction`) |
| `python3 tools/cfunc.py <bin> NAME` | pseudo-C Ghidra; chỉ để đọc nháp, hay sai ở vòng lặp, signed/unsigned, đối số stack |
| `tools/jt.sh hl4.test.GameTraceTest work/golden/trace_C.txt <end> 500 [tick:key…] [--raw t1,t2 dir] [--state t1,t2 out] [--ppm dir]` | chạy bản Java và so từng tick với trace gốc; dừng ở tick lệch đầu tiên |
| `python3 tools/e_trace.py <bin> work/res <out> <ticks> tick:key…` (env `SNAP=500`) | sinh golden trace của bản gốc |
| `python3 tools/e_raw.py …`, `tools/raw2png.py a.raw out.png` / `raw2png.py side a b out` | dump khung RGB565 của bản gốc / Java và xem ảnh |
| `python3 tools/e_state.py`, `tools/e_callers.py`, `tools/and_xref.py`, `tools/stw.py` | so trạng thái `_event`/struct, tìm caller, xref, ghi bộ nhớ |
| `python3 tools/e_cover.py <bin> work/res out.tsv t0 t1 keys…` | hàm nào của bản gốc chạy trong khoảng tick, để biết cần port gì **trước khi** chạm tới |
| `python3 tools/port_cover.py [--list]` | % hàm đã port theo module |
| `python3 tools/j2me_build.py build|compile|test` | build JAR/JAD đã preverify, scanner, chạy test |

## 4. Golden trace

Cả hai trace snapshot tại tick 500. Lúc đó Java bỏ qua title + story menu, gọi thẳng `GameStart.newGame` với seed lấy từ dòng `# seed` của trace.

- Token kịch bản (chung cho `tools/ref_game.py` và `GameTraceTest`): `t:KEY` (bấm+nhả), `t:pKEY`, `t:rKEY`, `t:wSCENE/X/Y/W` (warp, móc test), `t:H` (**fixture test**, không phải hành vi gốc: HP hero +0xa4 := HP max +0xa8 trước tick t, áp dụng giống hệt ở cả hai phía — dùng cho trace M để hero sống qua nhiều lần giết).
- `trace_C.txt`: 700 tick, kịch bản `80:-5 100:-5 250:-5 300:-5 340:-5 380:-5 420:-5 460:-5 500:-5 540:-5`.
- `trace_D.txt`: 1320 tick; kịch bản như trên cộng thêm OK (-5) tại 720, 780, …, 1260 (mỗi 60 tick) để qua chuỗi thoại.

Lệnh sinh:
```
B="work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod"
LC_ALL=C.UTF-8 SNAP=500 nohup python3 tools/e_trace.py "$B" work/res work/golden/trace_D.txt 1320 80:-5 100:-5 250:-5 300:-5 340:-5 380:-5 420:-5 460:-5 500:-5 540:-5 720:-5 780:-5 840:-5 900:-5 960:-5 1020:-5 1080:-5 1140:-5 1200:-5 1260:-5 &
```

Hiện trạng khi chạy Java:

- `trace_C`: khớp 501..699 nếu **không** truyền phím 540. Phím 540 trong cutscene không đổi hình ở bản gốc, nhưng Java sẽ ném `MainGameFrame_KeyPressed: later`.
- `trace_D`: lệch ở 723, do phím 720 chưa được xử lý.

## 5. Việc tiếp theo (theo thứ tự)

1. **Đường phím** `MainGameFrame_KeyPressed` @0x8cc20 với `_game[0]==0`. Event_Key (Ghidra 0x69b1c, địa chỉ thật 0x59b1c) đã đọc pseudo-C nhưng **chưa kiểm bằng disassembly**:
   - Ánh xạ mã phím: 0x3b35..0x3b37 và 0x3b07/0x3b08 thành -5; 0x3b6b thành 0x23 (`#` = SKIP); 0x3cd1/14999 giữ nguyên.
   - `e[0x206]` (đang skip) thì return. Sau đó `PopopBox_key(_evtBox, key)`; nếu `_evtBox[0x2c]` thì return.
   - Phím 0x23 khi state `0x200` ∉ {0x32, 0x3c, 0x2f} bật skip:
     - `e[0x207]=1`, `e[0x208]=0` (hoặc 5 nếu `e[0x5ef]==0 || e[0x5ee]==0`), `e[0x206]=1`, `e[0x209]=5`
     - `e[0x5ef]=-1`, `e[0x5ec]=0`, `e[0x608]=0`
   - State 0x2e/0x30 với phím -5 hoặc 0x35 (slot = `e[0x205]`):
     - chữ chưa hiện hết (`+0x24c < +0x248`): lặp `talkBoxTick` tới khi trả 0;
     - đã hết đoạn cuối (`+0x250 <= +0x24c`): đặt `+0x23f=1`, sang lệnh tiếp;
     - còn lại: nếu `+0x240==0` thì `+0x244 = +0x248` và `+0x240 = 1` (trang sau).
   - State 0x32 (popup) và nhánh `GameMenuFrame` (0x515 / `_game[0x5d]`): để `later`.

   Khi không có event, cần `HERO_Key_press`, `ItemBag_pickUp`, `Event_CheckOKKey`, mở game menu bằng phím -8: port khi kịch bản tới đó.
   `MainGameFrame_KeyReleased` (pseudo-C 97 dòng) cũng cần, vì Engine gửi press và release trong cùng tick.
   Đích: `trace_D` khớp xa nhất có thể, rồi `trace_C` khớp khi có phím 540.
2. Lệnh script còn `later` mà `trace_D` chạm tới: dùng `tools/e_cover.py` cho khoảng 700..1320 để lấy danh sách **trước**, port cả cụm một lượt
   (`Event_Popup`/`PopupReset`/`Draw_Popup`, `Event_Init_Narration`/`Draw_Narration` cho lệnh 0x30 và `e[0x5dc]`, `Event_Set_SubTitle`/`Draw_SubTitle`, …).
3. Hết cutscene là tới điều khiển hero ở play mode. `Hero_draw` play-mode, HUD `MainGameFrame_drawBottomBar`, `MainGameFrame_drawMonsterInfo` đang `later`.
   Cần trace mới có phím di chuyển (-1/-2/-3/-4).
4. Sau đó: E6 (quái, chiến đấu, skill, AI, particle, drop), E4 (story menu), E8 (menu game, save RMS, âm thanh MMF, đóng gói, tick thật trên máy).

**Cách làm cho nhanh** (rút kinh nghiệm phiên này):

- Chạy `e_cover.py` cho cả khoảng tick sắp tới, port trọn cụm hàm, rồi mới chạy trace. Không sửa từng blocker một.
- Viết nháp từ pseudo-C; chỉ đọc kỹ disassembly ở vòng lặp, signed/unsigned, bảng hằng (`C.xx.yyyy` copy lên stack), đối số stack và chỗ hash lệch.
  Ví dụ đã gặp: pseudo-C sai thứ tự mảng vị trí ở `Event_Draw_Talk`; `HF_GetByteLenPrintable` đo bằng font 1 nhưng vẽ bằng font 0;
  `Event_ScriptWindow_Close` đóng xong ngay trong một lần gọi.
- Sinh trace bản gốc ở nền với kịch bản dài, làm song song.

## 6. Mô hình code Java (để viết tiếp cho đồng nhất)

- **State-in-bytes**: các struct gốc là `byte[]` với offset gốc (`G.game[296]`, `G.map`, `G.event[2388]`, `G.gameMenu`, `G.slotInfo`, `Opt.v`). Truy cập bằng `Bytes.s32/w32/s16/w16`.
  Con trỏ heap của bản gốc được giữ trong mảng Java song song, và `_event` chỉ nhận một giá trị khác 0 thay chỗ.
  Ví dụ `Dialog.text/name`, `Faces.spr`. `tools/e_state.py` phải mask các ô con trỏ này.
- Object pool `Obj/Char/SObj`: offset header (+8 x, +0xc y, +0x14 type, +0x18 visible, …) ghi ở đầu `Obj.java`.
- Javadoc mỗi hàm ghi `TênGốc @0xaddr` (địa chỉ thật). `tools/port_cover.py` đếm tiến độ từ chỗ này, nên **luôn ghi**.
- Nhánh chưa port: `throw new IllegalStateException("<TênGốc>: later")`. Không viết stub giả rồi báo là xong.
- Kỷ luật J2ME: không cấp phát trong `paint`/game loop (buffer tĩnh như `Dialog.nameBuf`), không float, không API ngoài CLDC/MIDP (scanner sẽ reject), giữ nguyên kiểu byte/short/int như bản gốc.
- Render: back buffer `Gfx.back` cuộn theo camera, `Gfx.scr` là khung hiển thị. Thứ tự trong `MainGameFrame.drawGame()` đã khớp bản gốc.

## 7. Lệch có chủ đích và chưa biết (giữ nguyên nhãn)

Xem `NOTES.md` và `docs/phaseE.md` §4. Tóm tắt:

- Lệch có chủ đích: 240×320 dọc từ màn logo; không có touch/pointer/bàn phím ảo; âm thanh chưa gắn; bỏ mạng/MIDAS.
- UNKNOWN: blend effect 17, chu kỳ tick thật, layout một số loader, MAPOBJ opacity `p6&7==7` (đang coi là 0xff).
- Chưa chạy trên thiết bị thật / KVM thật.
