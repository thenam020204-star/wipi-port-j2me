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
| Nguồn sự thật | KTF `client.bin387872`; LGT `binary.mod` + Android `libbinary.so` để đối chiếu | người dùng chọn |

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

## Trạng thái (Definition of Done)
| Hạng mục | Trạng thái | Bằng chứng |
|---|---|---|
| Đã phân tích | Phase A xong (inventory + layout) | `docs/manifest_*.tsv`, `docs/binlayout_output.txt`, `docs/binary_layout.md` |
| Đã triển khai | chưa | — |
| Đã build | chưa | — |
| Chạy emulator | chưa | — |
| Đối chiếu bản gốc | chưa | — |
| Chạy thiết bị thật | chưa xác minh | — |

## Việc tiếp theo (Phase B)
1. Phân tích hàm KTF: tìm prologue Thumb (`push {…,lr}`), duyệt BL, dựng danh sách hàm + call graph (`tools/ktf_funcs.py`).
2. Lan truyền tên `.so` → KTF: neo bằng chuỗi (đã có 155), rồi khớp theo call graph (callee/caller đã biết, hằng số, kích thước) — mỗi tên có điểm tin cậy HIGH/MEDIUM/LOW; lưu `docs/symbols_ktf.tsv` (img+X ↔ tên ↔ bằng chứng).
3. Giải bảng interface WIPI KTF (descriptor img+0x881a8, `WIPIC_knlInterface`…) để liệt kê API được gọi.
4. Xác định game loop / timer / input / render / audio / save trên KTF (neo: `nFPS_Render`, `SCENE_Change`, `loadTitleImage`, `kickass`).
5. Xác minh float trên KTF (tìm các hàm tương ứng 6 hàm float của `.so`).
