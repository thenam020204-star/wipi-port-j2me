# Phase D — Dự án Java ME (CLDC 1.1 / MIDP 2.0): toolchain, bộ quét tương thích, lõi dữ liệu/đồ hoạ

Mục tiêu: dựng **dự án Java độc lập** (không launcher/emulator/dịch máy) với toolchain tái lập được, cổng kiểm tương thích J2ME,
và chuyển sang Java mọi định dạng/thuật toán **đã kiểm vi sai ở Phase C**, mỗi cái được kiểm lại bằng golden vector sinh từ mô hình Python
(đã khớp với hàm gốc). Chạy emulator ARM chỉ là công cụ kiểm; sản phẩm là mã Java thuần.

## 1. Cấu trúc
```
j2me/src/hl4/            Main (MIDlet), GameCanvas (hiển thị + vòng lặp + phím)
  sys/   Des, Bytes, Res         giải mã tài nguyên (DES biến thể), đọc little-endian đúng độ rộng/dấu, nạp tài nguyên từ JAR
  gfx/   Gbm, Frame, Blit        ảnh GBM; khung RGB565; DM_DrawImg (+ DM_DrawScreen565)
         Fx                      DM_DrawImgEx, DM_DrawImgRotate(Ex), bảng trộn, HSB/palette/tint (RGB565, chỉ số nguyên)
         Cif, Sprite, SpriteOut  chỉ mục CIF; SPR_drawSpriteNPC (ghép mảnh -> lời gọi DM_*)
         HnfFont                 ghép glyph Hàn/Latin (bố cục chữ: Phase E)
  data/  MapData, Exd, AiData, Scn, Pal, Text      parser dữ liệu (map, EXD, AI, container cảnh, PAL, MrgText/LinkedText)
j2me/test/hl4/test/      bộ kiểm chạy trên JVM máy tính (không thuộc sản phẩm)
tools/j2me_build.py      biên dịch + quét + preverify + đóng JAR/JAD
tools/j2me_scan.py       bộ quét tương thích bytecode
tools/j2me_golden.py     sinh golden vector (từ mô hình Python đã kiểm)
tools/j2me_test.py       chạy toàn bộ kiểm thử, ghi docs/phaseD_selftest.txt
tools/gen_des_java.py    sinh Des.java từ bảng đã kiểm của tools/hl4des.py
tools/j2me_stage_res.py  gom tài nguyên gốc vào JAR (cục bộ; không commit tài nguyên gốc)
```

## 2. Toolchain (tái lập)
| Bước | Công cụ | Ghi chú |
|---|---|---|
| Biên dịch | ECJ 4.6.1 `-1.3 -target 1.1`, `-bootclasspath cldc-1.1-stub + midp-2.0-stub` | chỉ thấy API của CLDC 1.1/MIDP 2.0 ⇒ tham chiếu ngoài profile là **lỗi biên dịch**; class version 45.3 |
| Quét tương thích | `tools/j2me_scan.py` | xem §3; vi phạm nghiêm trọng ⇒ build bị **từ chối** |
| Preverify | ProGuard 6.2.2 `-microedition` (không shrink/optimize/obfuscate) | thêm StackMap; version vẫn 45.3; so sánh trước/sau: chỉ cộng thuộc tính StackMap (bảng ở `docs/phaseD_selftest.txt`) |
| Đóng gói | `jar` + JAD (`MIDlet-Jar-Size` tự tính) + `hl4.sha256` | tài nguyên gốc chỉ ở `work/` (gitignored) |
Các jar công cụ (ECJ, ProGuard, stub API, MicroEmulator) là phần mềm bên thứ ba lấy từ gói tham chiếu cũ, đặt ở `work/tools/j2me/` — chỉ dùng để build/kiểm, không phân phối.
Chạy: `python3 tools/j2me_build.py build --res work/resjar_full` (sau `python3 tools/j2me_stage_res.py`), kiểm: `python3 tools/j2me_test.py`.
Lưu ý khi chạy JVM trên thư mục tài nguyên tên Hàn: cần `LC_ALL=C.UTF-8` (đã đặt trong `j2me_test.py`).

## 3. Bộ quét tương thích (`tools/j2me_scan.py`)
Đọc trực tiếp file class (constant pool + bytecode). **REJECT**: version lớp lạ; mọi `Class/Field/Method/InterfaceMethod` không thuộc stub CLDC 1.1/MIDP 2.0 hoặc app
(tra cả lớp cha/interface); `invokedynamic`; mọi lệnh/hằng/descriptor/mảng `float`/`double` (trừ lớp được liệt kê — hiện **không có**); mảng hằng > 64 KB.
**WARN**: cấp phát trong `paint/render/draw/update/run/process`, đệ quy trực tiếp, `synchronized`, `Thread.sleep`/`Timer`, số ép kiểu hẹp (đếm).
Kiểm âm tính (đã chạy): lớp dùng `ArrayList`, `String.isEmpty`, `float`, `double`, mảng 100000 byte, đệ quy, class version 52 ⇒ 14 REJECT, exit 1.
Bản hiện tại: 20 lớp, 0 reject, 1 warn (cấp phát trong `paint` của khung demo).

## 4. Kiểm chứng (docs/phaseD_selftest.txt) — mỗi mục: Java == mô hình Python == hàm gốc (Phase C)
| Module Java | Golden (sinh từ) | Kết quả |
|---|---|---|
| `Des` (giải mã + mã hoá lại) | 409 file DES (`hl4des.py`) | 0 lệch (giải mã đúng hash, mã hoá lại trả đúng byte gốc) |
| `Blit` (`DM_DrawImg`) | 253.726 ca (1/7 của 1.776.085 ca tự kiểm; ảnh tổng hợp + thật; CRC-32 khung 240×400) | 0 lệch |
| `MapData`, `Exd`, `AiData`, `Scn`, `Pal`, `Text` | 816 file thật (97 map, 117 EXD, 43 AI, 345 scn, 196 PAL, 6 MrgText, 12 LinkedText); băm toàn bộ cấu trúc | 0 lệch |
| `Sprite` (`SPR_drawSpriteNPC`) | 59.907 lời gọi: 148 CIF thật + 60 CIF tổng hợp, trạng thái ngẫu nhiên; băm chuỗi lời gọi DM_* + số khung mới | 0 lệch |
| `Fx` | bảng trộn, HSB (≈50 nghìn mẫu), 6000 ca palette, 3900 ca tint theo giờ, 3999 ca `DrawImgEx`, 4000 ca vẽ co giãn (CRC-32 khung) | 0 lệch |
| `HnfFont` | 2350 âm tiết + 82 ký tự Latin, băm mọi dòng glyph | 0 lệch |
Các bài kiểm cũng chạy lại trên lớp **đã preverify** (SpriteTest, FxTest) — cùng kết quả. Golden được cache ở `work/golden/` (xoá để sinh lại).

## 5. Hiển thị & vòng lặp (`GameCanvas`)
- Khung nội bộ **240×320 RGB565** (`short[]` 150 KB). Bản gốc dùng khung 240×400 (240×320 vùng game + bàn phím ảo — `phaseB_display.md`); mọi hàm vẽ ở đây **cắt thêm theo khung** (khác biệt có chủ đích, không đổi hình hiển thị).
- Trình bày: RGB565 → ARGB theo dải 32 hàng (`int[240*32]` = 30 KB, không cấp phát mỗi khung), `Graphics.drawRGB`, tự cắt theo màn hình (một số stack MIDP không cắt `drawRGB`), canh giữa.
- Phím: `keyPressed/keyReleased` (cạnh) — bảng phím thật là Phase E. Vòng lặp hiện là khung thử (vẽ `tdf/TITLE_BM`); nhịp thật (`timerCB` @0xa7994) ở Phase E.
- Kiểm bằng MicroEmulator 2.0.4 (Xvfb, màn 240×320): MIDlet khởi động từ JAD, nạp `TITLE_BM` từ JAR qua `Res`, hiển thị đúng ảnh; **J2ME Loader ≠ Nokia KVM** nên đây chưa phải bằng chứng chạy máy thật.

## 6. Ngân sách bộ nhớ (ước lượng, chưa đo trên KVM)
- Cố định: khung 150 KB + 30 KB dải trình bày + bảng trộn 16 KB + `Des` (< 1 KB) ≈ 200 KB.
- Tài nguyên: một map ≤ 6 KB; tileset ≤ 3 × 7 KB; ảnh 4/8 bpp giữ nguyên dạng đóng gói trong mảng của file (không giải nén ra); palette mỗi ảnh ≤ 512 B (bản làm việc chỉ tạo khi bị đổi — copy-on-write).
- Còn lại cho logic/sprite/âm thanh trong heap ≥ 2 MB: chưa lập ngân sách đầy đủ (Phase E, đo bằng `Runtime.freeMemory` trên emulator cấu hình heap thấp).
- JAR đầy đủ tài nguyên không-âm-thanh: 1.769 file, 3,75 MB gốc → **2,22 MB** nén (`work/build/hl4.jar`; chưa tính âm thanh).

## 7. Khác biệt có chủ đích / hành vi không xác định của bản gốc (ghi để Phase E rà lại)
1. Khung 240×320 thay vì 240×400 (§5).
2. Hiệu ứng trộn `effect` ngoài 0..16 (bản gốc đọc vượt bảng 16 chế độ; 4 mảnh CIF dùng effect 17): Java coi như effect 0. **UNKNOWN** nội dung bản gốc đọc được — cần đo bằng emulator khi gặp trong cảnh thật.
3. Điểm ảnh nguồn ngoài ảnh bị bỏ qua (bản gốc đọc bộ nhớ lân cận); plan có W hoặc H = 0 không vẽ (bản gốc dùng ô stack chưa khởi tạo — UB).
4. Sprite: chỉ số ảnh ngoài mảng set, set rỗng, hoặc `anim` ngoài `[0, nAnim)` ⇒ không vẽ (bản gốc đọc rác); không CIF thật nào vi phạm (chỉ số ảnh ≤ 52, set 0..7).
5. `DM_LoadResGbmMergeBlending` với slot không xác định (≠ −1, 0..6) để nguyên palette (bản gốc dùng biến chưa khởi tạo).
6. Cảm ứng, mạng, thanh toán không port (NOTES.md "Phạm vi port").

## 8. Chưa làm (chuyển sang Phase E/F)
Bảng phím + nhịp khung thật; vẽ bản đồ (ô, đối tượng, camera); bố cục chữ (`HF_DrawTextAnchorH2Color`, mã điều khiển); các loader `Item_*/NpcUi*/FR_*` + VM `IT_*`/`EVT_*`;
logic chiến đấu/AI (`EnemyAIParsing_*`, `Boss_AI`, `BattleProc_*`); save (`SaveGameData_Net` + DES, định dạng nội dung); âm thanh (MIDI/WAV từ MMF, quyết định Phase F);
golden trace toàn game bằng emulator ARM (RNG tất định đã xác nhận ở Phase B) và chạy thiết bị thật ("chưa xác minh").
