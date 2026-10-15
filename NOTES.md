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

## Trạng thái (Definition of Done)
| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Đã phân tích | Phase A xong; Phase B: symbol, call graph (sống/chết), màn hình, loop, input/phím, frame, RNG, audio, save (chuỗi gọi + container), xác thực, bảng chuyển frame xong; còn pipeline vẽ | `docs/manifest_*.tsv`, `docs/binlayout_output.txt`, `docs/phaseB_*.md`, `docs/*_and2011.tsv` |
| Đã triển khai | chưa | — |
| Đã build | chưa | — |
| Chạy emulator | chưa | — |
| Đối chiếu bản gốc | chưa | — |
| Chạy thiết bị thật | chưa xác minh | — |

## Việc tiếp theo
1. Phase B (phần còn lại): pipeline vẽ `DM_*`/`SPR_*`;
   đối chiếu `kickass` mẫu với thư viện MIDAS của KTF/LGT 2010.
2. Emulator đối chứng: ARM interpreter + HLE cho 105 import có tên (tham khảo runtime LGT của project cũ). Ghi golden trace cho Phase F,
   gồm đo nhịp khung và kiểm chứng dãy RNG.
3. Phase C: giải mã tài nguyên (DES `MX_desDecrypt`; định dạng `_BM _CIF _PAL _EXD _scn _DAT`); script chuyển MMF → MIDI/WAV.
4. Ghép tên AND2011 → KTF vẫn giữ để đối chiếu (`docs/xmatch_strings_ktf_so.tsv`).
