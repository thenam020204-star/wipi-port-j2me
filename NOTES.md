# NOTES — Tái triển khai 영웅서기4-환영의가면 (Heroes Lore 4) KTF WIPI → J2ME

Nhãn bằng chứng: **CONFIRMED** (offset/trace) · **INFERRED** (nêu căn cứ) · **UNKNOWN**.
Chi tiết layout: `docs/binary_layout.md`. Manifest/hash: `docs/manifest_*.tsv`.

## Tham số đích (người dùng đã duyệt 2026-10-15)
| Tham số | Giá trị | Căn cứ |
|---|---|---|
| Profile | MIDP 2.0 / CLDC 1.1 (logic vẫn tránh float) | người dùng chọn |
| Độ phân giải nội bộ | 240×320 | CONFIRMED `__adf__` `DisplaySize:240*320` |
| JAR / heap | JAR không giới hạn (dự kiến ~3 MB), heap ≥ 2 MB | người dùng chọn; BSS KTF 387872 B + tài nguyên |
| Thiết bị | lớp S60v3 / S40 đời cao, 240×320 | INFERRED |
| Nguồn sự thật | **AND2011** `영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod` (sha256 `995b59cc…`) + tài nguyên `res/` của jar đó; KTF/LGT 2010 chỉ đối chiếu | người dùng: "bản nào mới nhất và đầy đủ thì port bản đó" (2026-10-15) |

## Quyết định nguồn: AND2011 (2026-10-15)
| Bản | Ngày build binary | Ver/ID | Ghi chú |
|---|---|---|---|
| KTF `client.bin387872` | 2009-12-29 | 01.00.03, AID 010100D4 | raw Thumb, không symbol |
| LGT `binary.mod` | 2010-01-18 | 01.00.01, AID 0002D74B | ELF EXEC, không symbol |
| **AND2011 `binary.mod`** | **2011-04-18** | 01.00.00, `android.lgt.wipi.App00035956` | ELF DYN, 2388 hàm có tên |

CONFIRMED:
- APK chỉ là launcher: `classes.dex` ghi `jarfile` ra `…/files/00035956.jar` rồi phát `android.intent.action.LAUNCH_WIPI` (trình chạy WIPI của LG U+). Mã chạy thật là `binary.mod`.
- `binary.mod` giống hệt `lib/armeabi/libbinary.so` ở mọi section; thêm trailer `RAPT` 80 byte.
- Độ đầy đủ: so với LGT 2010 chỉ thiếu `CM/TITLE_BM`, `CM/TITLE_CIF`. So với KTF chỉ thiếu `client.bin` và 5 file cảnh:
  `e0183`/`n0162` = màn debug/QA (nhảy level, "사운드 테스트", lối tắt boss cuối); `e0184` gần rỗng; `n0163` = bản trùng thoại của `e0163`
  (AND2011/LGT đều có `e0163`); `e0185` UNKNOWN.
- Có thêm: 91 file (OGG song song MMF, biến thể `GRADE_*`, `kickbert`), UI cảm ứng (`Touch_*`), `&SKIP`. 128 file dữ liệu khác KTF.

## Phạm vi port (mặc định, chưa phải quyết định không thể đảo)
- Chỉ port đường phím. **Không port UI cảm ứng** (bàn phím ảo, `Touch_*`). Đây là thay đổi hành vi có chủ đích vì máy đích J2ME dùng bàn phím.
- Âm thanh lấy từ nguồn MMF (SMAF) như bản WIPI. OGG không dùng được trên J2ME.
- Bỏ mạng/xác thực/thanh toán (`clsidAuth*`, `_midas_*`, `MC_net*`) để chạy offline. Mỗi điểm cắt sẽ ghi lại tại đây.

## Đầu vào & bất biến
- Bản gốc chỉ đọc; mọi giải nén nằm trong `work/` (gitignored — không commit/phân phối lại tài nguyên gốc).
- `docs/manifest_inputs.tsv`: SHA-256 của các zip đầu vào. KTF upload `49ade895…5c42` == `HL4_project/KTF.zip` (CONFIRMED).
- Android: `hl4_android.zip` (`45adb3ee…0a93`, upload 2) → `영웅서기4.apk` + `kickass`.
- Tái lập: `python3 -I tools/inventory.py --ktf <KTF zip> --ref HL4_project_2026-09-27j.zip --android <android zip> --out work/orig --docs docs`
  (đã chạy 2 lần, manifest giống hệt).

## Phase A — Inventory (HOÀN THÀNH)
### Đã biết (CONFIRMED)
- KTF zip: `010100D4.jar` (1845 entry, 36 thư mục), `__adf__` (EUC-KR: AID 010100D4, PID PD008712, Ver 01.00.03, MClass Clet, MaxThread 5), `__class__`, 3 icon PNG.
- Jar KTF: 1809 file, **tên không có phần mở rộng**, định dạng xác định theo nội dung: 41 SMAF (`MMMD`), 1 Thumb image, 1767 nhị phân riêng.
  Hậu tố tên: `_scn`(350) `_PAL`(196) `_CIF`(148) `_EXD`(117) `_MMF`(41) `_BM`(30) `_DAT`(26). Các đuôi `.ada .gcs .gmi …` trong đề bài **không tồn tại** ở bản này.
- `client.bin387872` **không phải ELF** (đề bài giả định sai): image Thumb tự relocate; image ở file 0x1104; GOT img+0x88bb4;
  BSS 387872 B = hậu tố tên file (loader kiểm tra); trả về descriptor `WIPI_exe` tại img+0x881a8. Chi tiết + offset: `docs/binary_layout.md` §1.
- LGT `binary.mod`: ELF32 ARM EXEC không symbol, có 12030 relocation ABS32 (§2).
- **Android `libbinary.so`: ELF ARM không strip, 2388 hàm có tên** (`Boss_AI`, `BattleProc_*`, `CHAR_*`, `DM_Draw*`, `EVT_*`, `SCENE_Change`, `Cos100`…) (§3).
- Float chỉ trong 6 hàm khởi tạo/toán (`make_Lenz_array`, `make_Warp_array`, `_sin`, `_sqrt`, `MGHEO_sqrt`, `DM_ConvertHSBtoRGB`) trên `.so`.
- Ghép chuỗi KTF↔.so: 252 vị trí literal KTF được gắn với 155 tên hàm `.so` (`docs/xmatch_strings_ktf_so.tsv`).
- `kickass` (5785 B) trong gói Android = file save thật (thư mục chứa `./Hero4Sms`, …) — mẫu để kiểm thử định dạng save (bản Android dùng `./Hero4Game`, `./Hero4Slot`, `./Hero4OptSave`; KTF dùng `kickass`/`chicken`).

### Suy luận (INFERRED)
- `.so` Android được port từ nhánh LGT (phụ thuộc `liblgt_system.so`; 1774/1892 tài nguyên trùng LGT, 1674 trùng KTF). Cùng mã C với KTF ở phần lớn logic (155 hàm khớp qua chuỗi; project cũ cũng nói "cùng mã C").
- **Khác biệt dữ liệu KTF vs Android: 128 tài nguyên khác hash** (vd. `E/AI0xx`, `E/_BSDAT_*`, `E/_ESDAT_*`, `CM/GRADE_*`) ⇒ cân bằng/AI có thể khác giữa phiên bản; hành vi phải lấy từ KTF, `.so` chỉ cấp tên + cấu trúc.
- Bản Android có thêm chuỗi/tính năng không có trong KTF (cường hoá `%d/10강화`, `&SKIP`, đường save `./Hero4*`) ⇒ phiên bản mới hơn; không được mang các tính năng này sang.
- KTF `Clet` là lớp vỏ WIPI-Java biên dịch AOT (metadata `Clet$CletCard`, `org/kwis/msp/lcdui/*`) gọi mã C game.
- Logic game là số nguyên ⇒ CLDC 1.0/1.1 đều làm được; float chỉ cần cho tạo bảng (tính trước, kiểm bit-exact).

### Chưa biết (UNKNOWN)
- 8 byte header @file 0x04 của KTF; ý nghĩa đầy đủ các trường descriptor/ExeInterface; section `.raptor` của LGT.
- Danh sách hàm KTF, ranh giới code/data, call graph; API WIPI-C/Java mà KTF thực sự gọi (qua bảng interface nào).
- Định dạng các tài nguyên `_BM _CIF _PAL _EXD _scn _DAT …` (project cũ có mô tả `_BM` — phải tự xác minh).
- Khác biệt mã KTF vs `.so` ở từng hàm.

## Công cụ
- capstone 5.0.x (pip; objdump hệ thống không có target ARM), pyelftools (pip; đọc ELF/symtab/relocation).
- `tools/inventory.py` · `tools/binlayout.py` · `tools/so_floatscan.py` · `tools/xmatch_strings.py`.
- Toolchain J2ME có sẵn trong `HL4_project/tools/` (cldcapi11, midpapi20, ecj, ProGuard=preverify, microemulator) — dùng làm **công cụ** ở Phase D.
- Emulator đối chứng cho golden trace (Phase F): interpreter ARM của project cũ (`hl4j`, RunHeadless) hoặc `wie` — chỉ để đối chứng, không làm runtime.

## Phase B — tiến độ (AND2011)
Chi tiết + địa chỉ: `docs/phaseB_display.md`, `docs/phaseB_flows.md`.
### Đã biết (CONFIRMED)
- Vùng game = **240×320** khi frame là 240×400 (keysize 80 @0xc8c48); phần dư là bàn phím ảo. Bố cục tính theo `_lcdG+0x54/0x58`. Màu RGB565.
- Điểm vào: `init` → `wipic_event_handler(cfunc)`; `cfunc = {startClet, pauseClet, resumeClet, destroyClet, paintClet(rỗng), handleCletEvent}`.
- Khởi động không kết nối mạng: `clsidAuth → auth_startApp → gamerating_close → onStartApp`, rồi `status = 1`.
  Màn mật khẩu/hết hạn (mốc 2011-04-25) chỉ chạy khi `status == 5`; giá trị 5 chỉ được ghi bởi hàm chết ⇒ không bao giờ kích hoạt.
- Game loop = `timerCB` @0xa7994: KeyPressed → KeyReleased → TouchNotify → Process → Render → EndProcess → SM_PlayResEx → FlushLcd → reset phím → hẹn lại.
  Timer one-shot đặt lại sau khi vẽ; trễ mặc định 1 ms (lúc tải màn 10 ms). Logic chạy theo khung.
- Input bắt cạnh: mỗi sự kiện nhấn/nhả tạo đúng một lần gọi handler ở tick kế tiếp, rồi bị reset. Không tự lặp phím. Bảng phím: `phaseB_flows.md` §4.
- Frame: 9 slot con trỏ `nBASE_*`, gán qua `MainFrame_ElementAt`. Chuyển cảnh qua màn tải (`LoadFrame_setState`, kéo dài ≥ 11 tick, có tiêu thụ RNG để chọn mẹo);
  bảng `_nextFrame` 1 = Logo, 2 = Title, 3 = MainMenu, 4/6 = MainGame, 7 = NpcUi. Chuyển trực tiếp: `MainFrame_changeFrame` (`mainFrame+0x38`).
- RNG: BSD `random()` TYPE_3, không bao giờ seed lại ⇒ dãy số tất định từ khi khởi động.
- Âm thanh: một kênh. Hiệu ứng ngắt BGM; BGM phát lại từ đầu sau 30 khung. Bản 2011 chỉ phát **OGG**; các file MMF cùng chỉ số vẫn nằm trong jar.
- Save: `SaveGameData_Net` tuần tự hoá → DES → MIDAS SD. Mọi file logic `./Hero4Game_N`, `./Hero4Slot_N`, … nằm trong một file `kickass`,
  header v2 = `FF 56 30 00` + 40 mục × {name[16], u16 cap, u16 size}.
- Call graph 7487 cạnh (gồm tail-call). 1942 hàm sống / 446 hàm chết (`docs/reach_and2011.txt`).
- Float còn sống: `make_Lenz_array`, `MGHEO_sqrt`, `DM_ConvertHSBtoRGB`.
### Thay đổi hành vi đã dự kiến (sẽ ghi lại khi triển khai)
- Âm thanh lấy từ MMF thay vì OGG 2011 (J2ME không phát được OGG) ⇒ âm sắc có thể khác.
- Không port cảm ứng/pad ảo; chặn NpcUi chế độ 11 (mạng/cửa hàng) và các gói mạng; `PHONENUMBER` cố định cho `CERTIDATA`.
### Chưa biết
- Nhịp khung thực trên máy. Chức năng phím `*` (`_game+3`).
- File `kickass` mẫu không khớp layout MIDAS v2 (header chỉ có `./Hero4Sms`, dữ liệu ở 0x285…).
- Định dạng nội dung save. Pipeline vẽ chi tiết (GBM/sprite).

## Phase C — tiến độ (AND2011)
Chi tiết: `docs/phaseC_formats.md`. Mọi định dạng dưới đây được **kiểm vi sai với chính hàm gốc** chạy trong emulator đối chứng
(`tools/and_emu.py` + `tools/and_hle.py`, Unicorn — chỉ là công cụ kiểm thử).
| Định dạng | Kết quả |
|---|---|
| DES biến thể (S1[3][10] = 2, nhánh giải mã bỏ `swap32bit`) | 408/408 file khớp (giải mã + mã hoá) |
| Ảnh GBM (container, 4/8 bpp, khoá màu 0xF81F) | 4069/4069 ảnh khớp từng pixel |
| Font HNF + ghép âm tiết Hàn | 2350 âm tiết + 82 ASCII khớp từng pixel |
| Text MrgText / LinkedText | 6 + 12 file khớp |
| Chỉ mục sprite CIF | 148/148 file khớp cấu trúc |
| Ghép sprite (mảnh CIF → chuỗi lời gọi DM_*) | 14.400 ca ngẫu nhiên (phủ 100% code `SPR_drawSpriteNPC`) + 55.107 ca trên 148 CIF thật khớp; chi tiết `docs/phaseC_sprite.md` |
| Pha màu/blend/co giãn/palette/nạp ảnh theo giờ (`dm_fx`) | 960.498 ca kế hoạch cắt + 5.433 ca toàn khung + 40.000 ca HSB + 4.000 ca palette + 4.413 ca nạp ảnh khớp; `docs/phaseC_fx.md` |
| Palette `H4/PAL` | 196 file / 1.568 ca khớp; `docs/phaseC_pal.md` |
| Map `MAP/M` | 97/97 map khớp (hai lớp, chuỗi, 16.358 đối tượng, 930 mục quái); `docs/phaseC_map.md` |
| Thuộc tính sprite `H4/EXD` | 117/117 file khớp (1.321 cell); `docs/phaseC_exd.md` |
| Hình học `DM_DrawImg` (neo, lật/xoay, cắt biên; 9 mode của `DM_DrawScreen565`) | 1.776.085 ca khớp từng byte của khung 240×400 + 907.137 ca kế hoạch cắt; chi tiết `docs/phaseC_blit.md` |
| Âm thanh SMAF (`SND/*.mmf`) → MIDI/WAV | 42 file MMF phân tích đúng; ADPCM == ffmpeg 22/22; MIDI == bộ chuyển tham chiếu (trừ 646 CC7 do tham chiếu gán sai kênh); `docs/phaseC_audio.md` (cách đóng gói: quyết định ở Phase F) |
| Khung bảng F1 (E, HDAT, ITM, NPC) + bố cục 20 loader đo từ loader gốc | 70 file đúng khung; xem `docs/phaseC_tables.md`, `docs/phaseC_tables_layout.txt` (đệm DES ≤ 7 byte `00` CONFIRMED) |
| Script AI `E/AI###` | 43/43 file khớp `EnemyAIParsing_LoadData` gốc; `docs/phaseC_tables.md` §3 |
| Container cảnh `MAP/SC/*_scn` | 345/345 file khớp `IT_Initialize` gốc; `docs/phaseC_tables.md` §6 |
Công cụ phụ trợ: Ghidra 11.3.2 (tải vào `work/tools/`, không commit) cho mã giả C của 2344 hàm (`work/ghidra/decomp_and2011.c`, base 0x10000). Mã giả chỉ để đọc; **disassembly thắng** (đã gặp một chỗ Ghidra gộp nhầm hai nhánh góc xoay).
Chưa làm / UNKNOWN (chi tiết `docs/phaseC_tables.md` §5): bố cục từng trường của các loader `Item_*`/`Arms_*`, `NpcUi*`, `LoadPartQuestData`, `Guardian_loadBasicData`
(khung F1 đã CONFIRMED; sẽ kiểm bằng golden dump khi chuyển loader sang Java ở Phase E); bytecode công thức `FR/*`; VM `IT_*`/`EVT_*` diễn giải thân `_scn`;
ý nghĩa tên của các trường (offset cấu trúc chưa gán tên); `DM_Draw*RGB`/mosaic/Lenz; vẽ ô bản đồ (INFERRED, kiểm khi dựng bộ vẽ map); `ITM/_ITM_EMPTY`, `DAT/_DAT_DES` (không có nơi gọi).
Quyết định cho Phase F: âm thanh — MIDI (từ MMF, 1 tick = 1 ms) hay WAV (từ OGG của bản 2011 / ADPCM của MMF) tuỳ giới hạn thiết bị; mặc định đề xuất: MIDI cho BGM + WAV 8 kHz cho hiệu ứng.

## Phase D — tiến độ (dự án Java ME)
Chi tiết: `docs/phaseD.md`, kết quả kiểm: `docs/phaseD_selftest.txt`. Dự án `j2me/` (ECJ `-1.3 -target 1.1` + stub CLDC 1.1/MIDP 2.0 + preverify ProGuard), bộ quét `tools/j2me_scan.py` (reject API ngoài profile, float, mảng lớn).
Đã chuyển sang Java và kiểm bằng golden vector sinh từ mô hình Python đã khớp hàm gốc: DES (409 file), `DM_DrawImg` (253.726 ca), parser map/EXD/AI/scn/PAL/text (816 file),
`SPR_drawSpriteNPC` (59.907 lời gọi), `DM_DrawImgEx`/co giãn/palette/HSB/tint (≈17 nghìn dòng golden), glyph HNF (2432 ký tự) — **0 lệch**, cả trên lớp đã preverify.
MIDlet khung chạy được trong MicroEmulator 2.0.4 (240×320, JAR+JAD, ảnh `TITLE_BM` từ JAR). Chưa chạy KVM/thiết bị thật.

## Phase E — tiến độ (2026-10-16)
Chi tiết: `docs/phaseE.md`. **Bàn giao cho phiên sau: `docs/HANDOFF.md`.**

- Đo bằng `tools/port_cover.py`: 433 / 2113 hàm trong phạm vi (≈ 20,5%); còn 124 nhánh `later`.
- Golden trace toàn game `trace_F`: tick 501–8100 khớp hash khung + số lần gọi RNG + hash back-buffer từng tick.
  Đoạn này gồm vào game, toàn bộ cutscene mở đầu (chuyển cảnh, popup, danh sách quest), rồi chế độ chơi đứng yên với phím OK mỗi 50 tick (làng, không di chuyển).
- Chế độ chơi đã port: va chạm vùng sự kiện (`Map_Check_Collision_Hero_Eventzone`, `_Move`, `OKKey`), hồi HP/SP và đếm lùi (`HERO_process`), `HERO_HK_endprocess`,
  phím nhấn/nhả (`HERO_Key_press`/`Release`), bước đi (`HERO_set_move`), va chạm ô của hero (`..._MoveFlag_hero`, chưa đối chiếu), HUD khung trạng thái, tiêu đề bản đồ.
- Chặn kế tiếp: đối chiếu đi bộ (trace G), rồi chiến đấu (`HERO_check_skilluseHK`, quái, thả đồ), menu game (`GameMenuFrame`), NpcUi.

## Trạng thái (Definition of Done)
| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Đã phân tích | Phase A–C xong; Phase E phân tích theo luồng chạy (tới hộp thoại cutscene + đường phím đã đọc pseudo-C) | `docs/phaseB_*.md`, `docs/phaseC_*`, `docs/HANDOFF.md` §5 |
| Đã triển khai | một phần (≈ 20,5% hàm): lõi dữ liệu/đồ hoạ, Logo/Load/Title, scene/map/NPC/hero, cutscene, hội thoại, phím + đi bộ + HUD cơ bản; chưa: chiến đấu, menu, save, âm thanh | `j2me/src`, `tools/port_cover.py` |
| Đã build | JAR/JAD preverify, scanner 0 reject (92 lớp) | `tools/j2me_build.py build` |
| Chạy emulator | MicroEmulator 2.0.4 (khung demo); golden trace trên JVM máy tính | `docs/phaseD_selftest.txt` |
| Đối chiếu bản gốc | trace A (300 tick) + trace F tick 501–8100 khớp từng tick | `tools/e_trace.py` ↔ `GameTraceTest` |
| Chạy thiết bị thật | chưa xác minh | — |

## Việc tiếp theo
Xem `docs/HANDOFF.md` §5. Tóm tắt:
1. Phím trong game/event.
2. Các lệnh script/popup/narration mà `trace_D` chạm tới (lấy danh sách bằng `e_cover.py` trước, port theo cụm).
3. Play mode hero + HUD.
4. E6 chiến đấu, E4 story menu, E8 menu/save/âm thanh/đóng gói.
