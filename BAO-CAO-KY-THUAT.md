# Báo cáo kỹ thuật — Chroisen (크로이센) LGT → J2ME, Việt hoá v1

Mỗi mục gồm: nguyên nhân hoặc bối cảnh → cách làm → cách kiểm tra. Bằng chứng RE nằm trong `kro/re/formats.md` và `kro/re/map.tsv`; mỗi địa chỉ có độ tin cậy H/M/L.
Nguyên tắc chung:
- Bytecode/mã ARM thật được ưu tiên hơn kết quả decompile.
- Chỉ dùng API CLDC 1.1/MIDP 2.0.
- Không cấp phát trong vòng vẽ.
- Mọi jar đều qua preverify và `j2me_scan`.

## 1. Engine (kroj)

**Nguồn:** bản sao `xenj` (bản port Zenonia/HL5 mới nhất). Đã bỏ các lớp riêng của HL5/Zenonia.

**Thành phần:**
- `Cpu`: ARMv5TE, gồm ARM và Thumb.
- `Memory`: trang 64 KB, `int[]`.
- `LgtRuntime`: bảng WIPI-C của LGT.
- `GameCanvas`, `Sound`: vỏ MIDP.

**Layout:** `binary.mod` dùng cùng layout ELF với HL5. Gồm `.text` tại 0x1000, `.data` tại 0x1400000, `.bss` tại 0x1500000.

**Test:** `RunHeadless` chạy từ màn tiêu đề → tạo nhân vật → vào chơi.

## 2. HLE API ảnh (mới)

**Bối cảnh:** game dùng ảnh PNG đóng gói trong `image/*.dat`. Binary không có bộ giải PNG, nên phải dùng các hàm ảnh của nền tảng:
- `MC_grpCreateImage` (0xe9);
- `GetImageFrameBuffer` (0xc9);
- `DrawImage` (0xd5);
- `DestroyImage` (0xeb).

**Cách làm (`LgtRuntime.grpCreateImage/DestroyImage/DrawImage`):**
- Giải PNG qua `Host.decodeImage`: trên MIDP dùng `Image.createImage(byte[],..)` + `getRGB`; trong harness dùng ImageIO.
- Ghi ra framebuffer RGB565 trong bộ nhớ khách.
- Điểm trong suốt được ghi bằng màu khoá **0xFFF7**. Game tự phân tích PLTE (0x26590) và quy đổi màu về chỉ số bảng màu; giá trị 0xFFF7 được cứng ở 0x3a184.
- `DestroyImage` giải phóng đủ 5 khối nhớ.

**Kiểm tra:**
- Ảnh chụp các màn khớp với ảnh gốc.
- Số lần create bằng số lần destroy (log `svc:`: `e9 = eb`).

## 3. Chữ tiếng Việt (KroText)

**Bối cảnh:** game tự vẽ chữ bằng `data/font.dat` (Johab, ô 10x12).
- Hàm đo: `0x28e14`.
- Vòng vẽ: `0x296d8`. Vòng này bỏ qua byte dẫn không phải Hangul tại `0x297e8` (`cmp r2,#0xa4`).

**Cách làm:**
- **Mã hoá:** mỗi từ tiếng Việt thành 2 byte (lead 0xC9.., trail 0xA1..), là chỉ số vào `vi/dict.txt`. Nhờ vậy mọi đoạn xử lý 2 byte kiểu EUC-KR của game vẫn chạy đúng.
- **Thay hàm đo:** `0x28e14` được thay trọn bằng HLE. Với chữ Hàn, kết quả giống hệt bản gốc (đã so bằng `HL4_MEASURECHECK`).
- **Vá vòng vẽ:**
  - Lệnh tại `0x297e8` được vá thành BL tới stub; stub vẽ chữ cái NAM.P `f12` vào ô 10x12.
  - Sau đó gọi lại chính hàm glyph `0x292ec` của game, nên cắt, alpha và viền vẫn do game xử lý.
  - Dấu cách trong chuỗi Việt rút còn 3 px (BL tại `0x29aa0`).

**Kiểm tra:** `HL4_TEXTLOG` và ảnh chụp các menu (trạng thái, túi đồ, mô tả đồ, cửa hàng, hệ thống).

## 4. Pipeline dịch

**Trích xuất:** `kro/vi/extract.py` → `catalog.json`, gồm 7509 mục:
- chuỗi binary `B:addr`;
- bảng u16 `T:file:i:field`;
- `D:` Describ;
- `E:` sự kiện.

**Dịch tay:** bằng `dp`, không dùng dịch máy hay subagent, theo `thuat-ngu.tsv` và `vai.md`.
- Tên đồ được sinh có quy tắc (`itgen.py`/`it_lex.py`), sau đó soát tay.

**Đóng gói:** `build_vi.py` → overlay:
- dựng lại bảng offset u16;
- ngắt dòng sẵn cho `Describ`, vì game ngắt theo số byte;
- giữ mã màu `|NN`/`|cNN`, `%d %s`.

**Ô chuỗi trong binary:**
- Độ dài tối đa của một ô = khoảng tới chuỗi kế tiếp.
- Bản dịch dài quá ô thì dùng chế độ **gộp cụm**: cả cụm từ thành 1 mã (35 mục).

**Chuỗi dùng chung đuôi (24 chỗ):** trình biên dịch gộp đuôi chuỗi, nên đuôi chuỗi ngoài *là* chuỗi trong. Cách xử lý:
- chép bản dịch của phần đuôi ra sau NUL của chuỗi ngoài;
- sửa mọi word căn 4 byte (literal pool) đang trỏ địa chỉ cũ;
- danh sách ở `tr/B_inner_id.tsv`.
- Đã kiểm: **không lệnh nào được thực thi** nằm trong các byte `.text` bị đổi (đối chiếu với profile).

**Kết quả:**
- 6666 mục đã dịch (gồm 9 chuỗi bổ sung bên dưới).
- 843 chưa dịch, toàn bộ là phạm vi v2: DIAL 278, QUEST0 213, QUEST00 352, E 10. Đã kiểm chéo theo câu Hàn.
- Từ điển: 3956/5076 mã.
- 1 cảnh báo có chủ đích: `TitleN 0` dùng `[Chưa có danh hiệu]` thay `<…>`, vì `<>` là cú pháp thẻ.

**Kiểm tra:** `build_vi` báo mã điều khiển lệch, ô tràn, ký tự ngoài font. Cùng với đó là ảnh chụp trong game.

**Chuỗi bị sót (đã sửa):**
- Phát hiện khi chơi thử: nhãn "종류" vẫn tiếng Hàn ở màn tìm ký gửi.
- Nguyên nhân: bộ trích xuất chỉ nhận chuỗi có byte NUL đứng trước. Một số chuỗi nằm ngay sau bảng dữ liệu nên bị bỏ qua.
- Quét độ phủ toàn rodata (mọi cặp Hangul không thuộc chuỗi nào) tìm ra 9 chuỗi thật:

  | Chuỗi | Ghi chú |
  |---|---|
  | 필드 | |
  | 투구 | |
  | 종류 | ô đầu của mảng `char[5]` |
  | 포션 패키지 | |
  | [포션 패키지] | |
  | 입구 | |
  | 도움말 | |
  | 배경음크기 | |
  | 맵퀘스트 제목 길이 오류 | thông báo debug |

  Mỗi chuỗi có đúng 1 tham chiếu literal pool. Phần còn lại của kết quả quét là dữ liệu nhị phân tình cờ trùng mã Hangul.
- Sửa:
  - Thêm danh sách `EXTRA_B` trong `extract.py`. Ô của mỗi chuỗi dừng ở mốc 4 byte, không lấn sang byte 0 có thể là dữ liệu.
  - Bản dịch đặt trong `tr/B_extra.tsv`.
  - Đã kiểm ô kế bên (`등급` → "Phẩm") vẫn nguyên.

**Nhãn Cài đặt tràn (đã sửa):** nhãn trong màn Cài đặt được canh phải sát cột giá trị. Ba nhãn bị rút gọn để không đè lên cột (≤ 78 px, bằng "Rung màn hình"):

| Gốc | Trước | Sau |
|---|---|---|
| 효과음크기 | Âm lượng hiệu ứng | Âm hiệu ứng |
| 적자동추적 | Tự theo dõi địch | Tự nhắm địch |
| 크리티컬효과 | Hiệu ứng chí mạng | Hiệu ứng Crit |

"Crit" khớp với chữ trên bảng chỉ số. Đã chụp lại màn hình kiểm tra.

## 5. Ảnh có chữ + logo

**Định dạng:** `kro/img/datpack.py` đóng gói lại byte-chính-xác với 112 container.

**Vẽ lại:** `mk_images.py` dùng pixeltext với font NAM.P:
- `title[5]`, `title[6]` (dải menu, 13c), `title[11]` (nút lớp);
- `uiinner[7,9,16,19,20,22,23,25,26,29]`.
- PNG giữ bảng màu + tRNS 0/255, khớp màu chính xác.

**Logo:** `logo.dat[3]` (Softmaru) được thay bằng logo NAMP 240x320, lượng tử về 250 màu. Hàm `0x20d4` vẽ ảnh ở tâm màn hình trừ nửa w/h, nên ảnh 240x320 phủ kín màn.

**Sửa cuối — `uiinner[26]` (gợi ý túi đồ và popup cửa hàng):**
- Nguyên nhân: hàng 4 của ảnh gốc là **hai nửa 24 px** "#닫기" | "#펴기". Game chỉ vẽ nửa trái hoặc nửa phải tuỳ trạng thái; bằng chứng là ảnh chụp popup cửa hàng cash hiện đúng 24 px trái.
- Bản trước vẽ "#:Đóng" trải cả 48 px, nên trong game bị cắt thành "#Đón".
- Ngoài ra, ở khoảng cách chữ 0 px dấu ":" dính vào "#".
- Sửa:
  - Hàng 4 vẽ thành hai nửa "#Ẩn" | "#Mở", canh trên để dấu của chữ hoa không bị cắt.
  - Ba hàng còn lại bỏ ":" (giống gốc hàng 3) và dùng khoảng cách chữ 1 px: "#Phá túi", "*Xếp túi", "*Bộ đồ".

**Kiểm tra:** script tự báo nhãn tràn ô, cộng với ảnh chụp trong game.

## 6. Bản offline — cash shop miễn phí

**RE:**
- Máy trạng thái mạng `0x41b00` giữ trạng thái ở `[0x15081c4]` và loại yêu cầu ở `[0x1508330]`.
- Kết quả trả về qua `0x3f71c(type, status)`.
- Mua cash là loại 3 và 30, chung nhánh `0x3f9fa`. Game **đã cộng đồ vào túi trước khi gửi**; trả lời chỉ để chốt giao dịch, thất bại thì hoàn tác.

**Cách làm:**
- `KroGame.netAllow()` chỉ cho loại 3/30.
- `LgtRuntime` khi đó giả kết nối, ghi và đọc.
- `netReply()` trả lời tại chỗ:
  - loại ≤ 0x19: chuỗi '0';
  - loại 30: 12 byte, với [7]=0 và [8]=1. Giá trị [8]=0 nghĩa là "hết hạn mức tháng".
- Mọi loại khác thất bại như mất mạng (xếp hạng, hộp quà, ký gửi, sự kiện).

**Kiểm tra (chạy bằng class lấy từ jar):**
- Kịch bản mua gói "flash sale": log `MC_netConnect -> ok (offline server)`, `Write 36 / Read 12`, rồi thông báo mua thành công.
- Game tự lưu `save0.dat`: 49 byte ở vùng đồ thay đổi.
- Khởi động lại từ save đó và mở túi: đủ đồ đã mua; lúc tải **không có** kết nối mạng.

## 7. AOT (Thumb → Java)

**Sinh mã:**
- Profile chơi ngẫu nhiên 700 s sau đoạn mở đầu: `kroj/profiles/kro_prof1.txt`.
- `kroj/tools/regen_aot.sh` → `kroj/aot/hl4/aot/LgtAot.java`: 503 vùng, phủ 98,9 % số lệnh đã profile.
- Các vùng bị hook/vá được loại trừ (`28e14`, `297e8`, `29aa0`); runtime cũng `aotBlock` các chỗ này.
- Literal được đọc từ bộ nhớ lúc chạy, nên chuỗi dịch không ảnh hưởng mã AOT.

**Bit-exact:** cùng kịch bản phím 300 s, so trình thông dịch với AOT. Kết quả trùng khớp:
- số lệnh 21.636.050.818;
- `svclog` (hash qua 4,2 triệu lần gọi host);
- 15/15 khung hình.

**Tốc độ:** 107 → 965 MIPS (×9) trên máy dev.

**Rủi ro:** `LgtAot.class` nặng ~1 MB (một class, giống bản Zenonia). KVM nạp nguyên class vào heap. Nếu máy nào không nạp được thì build `NOAOT=1` (chậm hơn nhiều).

**Kiểm tra lại sau bản sửa §8:** xem §10.

## 8. Rò rỉ heap khách ở `MC_knlGetResourceID` (đã sửa)

**Nguyên nhân:**
- Mỗi lần game lấy ID tài nguyên, HLE cấp một chuỗi tên mới trong heap khách và không bao giờ giải phóng.
- Game gọi 1930 lần/700 s, khoảng 46 KB, và tăng mãi theo thời gian chơi.
- Trong WIPI-C, ID này chỉ là handle mờ: game không giải phóng và không ghi vào nó. Đã kiểm bằng log suốt 700 s: không có `MC_knlFree` nào trên handle, nội dung handle không đổi.

**Sửa:** `Runtime.knlGetResourceId` giữ bảng tên → handle, mỗi tên chỉ cấp một lần.

**Phát hiện phụ — lỗi có sẵn của game, không sửa:**
- Sau bản sửa, quỹ đoạn chơi ngẫu nhiên lệch dần so với trước.
- Đã truy ra nguyên nhân: vòng lặp ô bản đồ `0x638c–0x645e` đọc **vượt biên** hai mảng thuộc tính bản đồ. Mỗi mảng là khối heap 416 byte, game đọc tới chỉ số 416–418+, tức sang khối heap nằm kế bên.
- Bằng chứng:
  - instrument tại `0x63a4`/`0x63ae`;
  - đổi sang heap xoá trắng không làm đổi hành vi (vậy không phải bộ nhớ chưa khởi tạo).
- Kết luận: kết quả của vòng này phụ thuộc bố cục heap. Trên máy thật cũng vậy (chỉ ảnh hưởng số ô trang trí ngẫu nhiên). Đây là lỗi của game gốc nên giữ nguyên hành vi, không vá.

## 9. Bộ nhớ

**Heap khách** (bộ nhớ game cấp phát): đỉnh 1,1–1,4 MB trong nhà, 4,8–6,6 MB sau khi đi nhiều bản đồ (chơi ngẫu nhiên 700 s).
- Đo bằng `HL4_HEAPLOG`. Heap tăng dần rồi đứng yên, vì game giữ sprite theo định dạng riêng.
- Ảnh PNG được giải phóng đúng (create = destroy).

**Trang bộ nhớ đã map:** 6,6–8,4 MB `int[]`.

**Ước tính heap Java trên máy thật:**
- trang bộ nhớ ~7–9 MB;
- class AOT ~1 MB;
- framebuffer, âm thanh.

⇒ **cần ≥ 16 MB**. Máy Nokia S60 v3/v5 và Sony Ericsson đời cao là phù hợp; S40 (2–4 MB) không đủ.

Ghi chú: harness desktop cần `-Xmx32m`, vì nó còn nạp sẵn toàn bộ zip + overlay (~13 MB `byte[]`, không có trên máy thật). Đã xác định bằng `jmap -histo`.

## 10. Kiểm chứng bản phát hành (class + tài nguyên lấy từ `CHROISEN_LGT_vi1.jar`)

| Kiểm tra | Kết quả |
|---|---|
| `j2me_scan.py` (37 class, đối chiếu stub CLDC 1.1 + MIDP 2.0, StackMap, version) | 0 vi phạm |
| `jardiff.py` bản Hàn → bản Việt | class giống hệt, tài nguyên đổi: binary.mod, 49 file data, 3 file ảnh, + `vi/dict.txt`, `vi/font.bin` |
| `jardiff.py` jar WIPI gốc → bản Việt | +37 class (không StackMapTable, version ≤ 48), +51 tài nguyên (âm thanh mid/wav, icon, vi), ~99 đổi |
| Mua cash → tự lưu → khởi động lại → túi đồ | xem `release/kiem-chung.txt` |
| Chơi ngẫu nhiên 700 s (AOT) | xem `release/kiem-chung.txt` |
| AOT vs thông dịch 300 s | xem `release/kiem-chung.txt` |

**Chưa kiểm được ở đây:**
- J2ME Loader (Android/ART, không phải chuẩn KVM);
- KEmulator;
- máy thật;
- FreeJ2ME/MicroEmulator bằng cửa sổ: anh đã yêu cầu dừng phần tự động hoá giả lập.

Nếu lỗi lưu game trên máy thật thì build với `HL4_SAVEDBG=1`, rồi chụp dòng chẩn đoán gửi lại.

## 11. Phân loại rủi ro J2ME

| Hạng mục | Đánh giá |
|---|---|
| API | chỉ CLDC 1.1/MIDP 2.0, không reflection/Android/Java SE (`j2me_scan`) |
| Preverify | ProGuard `-microedition`, có StackMap, không StackMapTable |
| Class lớn (`LgtAot` ~1 MB) | rủi ro với KVM giới hạn kích thước class; có bản `NOAOT` dự phòng |
| Heap ≥ 16 MB | không chạy trên máy heap nhỏ |
| `Image.createImage(byte[])` + `getRGB` khi giải PNG | MIDP 2.0 chuẩn |
