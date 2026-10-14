# Báo cáo kỹ thuật — Chroisen (크로이센) LGT → J2ME, Việt hoá v1 + v2

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
- **Sau v2 (§12):** 13865 mục đã dịch, 0 chưa dịch; từ điển 5218/6298 mã.
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
| `jardiff.py` bản Hàn → bản Việt | class giống hệt, tài nguyên đổi: binary.mod, 49 file data, 3 file ảnh, + `vi/dict.txt`, `vi/font.bin`  (`release/jardiff_ko_vi1.txt`) |
| `jardiff.py` jar WIPI gốc → bản Việt | +37 class (không StackMapTable, version ≤ 48), +51 tài nguyên (âm thanh mid/wav, icon, vi), ~99 đổi  (`release/jardiff_goc_vi1.txt`) |
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

## 12. Việt hoá v2 — thoại, quest, kịch bản sự kiện

**Phạm vi (dịch tay bằng `dp`, không dịch máy, không subagent):**

| Nhóm | Nguồn | Số đơn vị dịch |
|---|---|---|
| Kịch bản sự kiện | `EV00–EV21.dat` | 3493 |
| Kịch bản quest | `QUEST1.dat` | 2162 |
| Thoại NPC + bảng cảnh báo hầm ngục | trường kịch bản của `DIAL.dat` | 401 |
| Tên + mô tả quest | `QUEST0.dat`, `QUEST00.dat` | 559 |

Xưng hô theo `kro/vi/dp/w/*/dp_work/vai.md` (ví dụ Rene – Sebastian: ta – con; Sebastian – Innis: tôi – tiểu thư; phản diện: ta – ngươi). Tên riêng theo `kro/vi/names.tsv`, thuật ngữ theo `thuat-ngu.tsv` và tên đồ/quái đã có ở v1 (vd. "Tấm thép", "Đuôi Scorpion", "Hồng ngọc thô").

**Định dạng kịch bản (`kro/vi/script.py`):**
- `EV##.dat`: 10 × (u32 off, u32 len), offset tính từ byte 80; `QUEST1.dat`: u32 n + n × (u32 len, u32 off), offset tính từ byte 4 + 8n.
- Văn bản có thẻ `<NAME x> <n> <t> <EMO a b c> <DELAY n> <BALLOON ..> <TYPING ..> <SCENE ..> <SELECT ..>`. Đơn vị dịch = đoạn chữ giữa các thẻ ngắt; thẻ nằm giữa câu giữ trong đơn vị.
- Tham số thẻ được chép vào bộ đệm 40 byte trên stack **không kiểm biên** (`sp+0x4bc`) → `build_vi` chặn mọi tham số mã hoá > 39 byte.
- Tham số `<NAME>` không dịch theo câu mà lấy từ `names.tsv`, vì hàm tìm chân dung (`0x232b0`) `strcmp` nó với bảng tên 9 byte ở `0x1400050`. `build_vi` kiểm hai bên cùng mã.
- Đoạn mở đầu `EV00[0]` (chữ tự cuộn) được ngắt lại theo bề rộng 170 px (`PARA_W`).

**Vá mã DIAL (`kro/re/patches.txt`, `0x28b04`):**
- Bản gốc lấy độ dài kịch bản của bản ghi DIAL từ một byte u8 → kịch bản > 254 byte bị cắt.
- Bản vá tính độ dài = `reclen − L1 − 6` (u16) từ chính bảng offset. Mã thay thế cùng độ dài, phần dư lấp `nop` (`c046`).
- Vùng `0x28a00–0x28bff` không nằm trong vùng AOT nào (đã kiểm: `LgtAot.java` không có địa chỉ này), nên mã vá luôn chạy bằng trình thông dịch. Sinh lại AOT từ binary đã vá cho ra `LgtAot.java` **giống hệt** bản v1.

**Mở rộng không gian mã từ:**
- v1: lead `0xC9–0xFE` × trail `0xA1–0xFE` = 5076 mã.
- v2: thêm lead `0xA2, 0xA3, 0xA5–0xAF` (bỏ `0xA1` = hàng dấu cách/dấu câu và `0xA4` = jamo mà game tự vẽ) → tối đa **6298** mã. Cùng một bảng trong `build_vi.py XLEADS` và `KroText`.
- Đang dùng **5218 / 6298**.

**Dấu câu tách khỏi mã từ:**
- Trước: "từ!" "từ..." là mã riêng → từ điển phình. Giờ mã từ chỉ chứa chữ cái; `([\"'.,!?:;)]~` là byte ASCII riêng và KroText vẽ chúng bằng glyph VnFont khi nằm trong chuỗi tiếng Việt (để cùng nét với chữ).
- Áp dụng cho kịch bản, đoạn mở đầu, mọi mục `T:` (bảng u16) và `D:` (Describ). Chuỗi trong binary (`B:`) vẫn gắn liền vì ô chật và có chuỗi dùng chung đuôi.
- Kết quả: từ điển 5123 → 4700 trước khi dịch phần còn lại.

**Dấu ba chấm `` ` ``:**
- Hàm ngắt dòng hộp thoại (`0x24a24`) chỉ kéo **một** byte đuôi lên dòng trên, nên "..." có thể bị tách sang đầu dòng sau.
- `build_vi` đổi mọi chuỗi ≥ 2 dấu chấm thành một byte `` ` `` (không xuất hiện trong văn bản gốc); KroText vẽ nó thành 3 dấu chấm và đo bằng 3 × bề rộng dấu chấm.
- Font không có `…` và dấu nháy đơn: bản dịch dùng `...` và `"`.

**Lỗi nguồn giữ nguyên / sửa trong bản dịch:**
- `QUEST1` 2050–2052: lời Jason nằm dưới thẻ tên Sebastian → dịch theo đúng chữ, không đổi thẻ.
- `QUEST1` 1731 ghi Upper Slasher ở chỗ đáng là Upper Assassin; 1882 ghi 보글링 ở chỗ đáng là Druid → bản dịch theo nghĩa đúng.
- `QUEST0` "미지막인사" (lỗi gõ của 마지막) → "Lời chào cuối".

**Soát chữ Hàn còn sót** (quét mọi file `data/` của overlay tìm cụm ≥ 2 âm tiết Hangul KS X 1001):
- `IT11` mục 1 và 21: tên "얋은두건" / "고급얋은두건" dùng âm tiết **얋 (0x9DF9) nằm ngoài EUC-KR** (chỉ có trong CP949/UHC; chắc là lỗi gõ của 얇은). Bộ trích xuất chỉ nhận chuỗi EUC-KR hợp lệ nên bỏ sót từ v1. Sửa: `extract.py` nhận chuỗi CP949 trong bảng u16 (`valid949`), `build_vi.py` so chuỗi gốc bằng CP949; dịch "Thin Hood" / "Cao cấp Thin Hood" (`tr/IT_extra.tsv`). Catalog chỉ thêm đúng 2 mục, không mục nào đổi.
- `DIAL` (69 cụm): trường nhãn đầu mỗi bản ghi (vd. "삶이란", "선호무기"). Loader `0x289fc` bỏ qua trường này, không hiển thị → giữ nguyên.
- `EV04` (1 câu): bản gốc có dấu `<` lạc: `<도저히 못 참겠군!!<n>`. RE vòng thoại `0x245f0` (HIGH): ở trạng thái chữ, `<` (`0x248d2`) chuyển sang đọc thẻ; trong thẻ chỉ `>` `/` và dấu cách được xử lý (`0x24644–0x2465a`), `<` thứ hai là ký tự thường. Vậy cả đoạn thành thẻ lạ tên "도저히" với tham số "못 참겠군!!<n" (13 byte < 40), không khớp 69 tên thẻ (`0x248c2`) nên bị bỏ qua. **Game gốc không bao giờ hiện câu này** → giữ nguyên byte để giữ đúng hành vi.
- `Describ0` mục 0 (`//0 /set align`, màn thông tin nhà phát hành) và mục 11 (`//11 /box`, hộp "thông tin kiểm duyệt"): tiêu đề có kèm lệnh nên `extract.py` (regex `^//N$`) bỏ sót từ v1. Sửa: các dòng chữ Hàn trong mục có lệnh được dịch **từng dòng** (ID mới `L:file:dòng`, `tr/L_extra.tsv`), giữ nguyên dòng lệnh `/mo` và số liệu.
- **Lỗi v1 phát hiện kèm (đã sửa):** khi đóng gói Describ, `build_vi.py` chỉ coi `//N` trơn là tiêu đề. Mục 10 đã dịch bật cờ bỏ dòng gốc, dòng `//11 /box` không tắt cờ → **cả nội dung mục 11 bị xoá** trong bản vi1 (đã kiểm `CHROISEN_LGT_vi1.jar`: sau `//11 /box` không còn dòng nào). Sửa: mọi `//N` (có hoặc không có lệnh) đều kết thúc mục trước. Kiểm: danh sách dòng tiêu đề của mọi `Describ*.dat` trùng bản gốc; mục 11 đủ 16 dòng như gốc.
- Các cặp Hangul lẻ còn lại trong `IT##` là byte nhị phân trùng mã (không có cụm ≥ 2 âm tiết).

**Khung tên quest (soát hình trong game):**
- Cửa sổ Quest vẽ tên quest **căn giữa** (tâm ~x147) trong ô viền x=96..202, không cắt chữ. Tên dài tràn đè lên nhãn "NV chính" (vd. "Thực tế là tu luyện" 108 px bắt đầu ở x=93).
- Đo trên ảnh chụp (bản Hàn `qko` và bản Việt `vis`): chữ phải ≤ 99 px để bắt đầu ≥ x=98. `build_vi` báo lỗi khi tên `T:QUEST0/00:*:31` rộng hơn `QUEST_TITLE_W = 99`.
- Rút gọn 72 tên bằng `dp fix` (vd. "Đời là tu luyện", "Hội đàm cấp cao", lớp nghề viết tắt "G. Assassin 1", "C. Berserker 1"; hầm ngục "Deep Desert D.").
- Bảng dịch tra theo **chữ Hàn** dùng chung mọi file. Bốn tên trùng chữ Hàn với tên đồ/hầm ngục (황제의 마법서, 딥샌드던전, 딥데저트던전, 딥블루마운틴) đặt riêng theo ID trong `tr/QUEST_title_id.tsv`. Nhờ vậy tên đồ và bảng 492/B vẫn giữ tên đầy đủ.

**Ngắt dòng Describ (lỗi có từ v1, đã sửa):**
- Triệu chứng ở Trợ giúp → Thông tin game:
  - "đến |06công phép" hiện thành "06công" (mất `|`, mất màu);
  - mất dòng trống trước "Kỹ năng debuff" và dòng sau thụt một khoảng.
- RE trình vẽ trang `0x2a3f4` (vòng `0x2a79c–0x2a8f0`, HIGH):
  - mỗi byte thêm vào dòng thì gọi `text_measure` (`0x2a8a6`);
  - khi đo ≥ `W − 15` thì ngắt dòng, bỏ một CRLF, rồi **kéo byte ASCII kế tiếp lên dòng đã đầy**;
  - byte đó có thể là `|` của mã màu ở đầu dòng sau, hoặc CR của dòng trống.
- Lượt đếm dòng `0x29e3c` ngắt khi `số byte × 5 ≥ W − 15`.
- W là tham số stack 0 của `0x29e3c` ở từng nơi gọi:

| File | W (nơi gọi) |
|---|---|
| Describ0 | 160 (`0x382aa`, `0x393f2`); 154 (`0x37e56`) |
| Describ1 | 140 (`0x3695a`) |
| Describ3 | 150 (`0x3405a`) |
| Describ4 | 160 (`0x39048`) |
| Describ5 | 160 (`0x41f6`, `0x38578`) |

  Đo lúc chạy, trang thông tin có `[0x150e568+0x18] = 160`.
- Bản v1 ngắt sẵn ở 150 px cho mọi file. Vì vậy dòng 145–150 px bị ngắt lần nữa lúc vẽ.
- Sửa: `build_vi` ngắt theo từng file, mỗi dòng ≤ `W − 16` px và ≤ `(W − 16) / 5` byte (`DESCRIB_BOX`; Describ2 không thấy nơi gọi hằng số nên dùng 150). Dòng nào vượt (kể cả dòng `L:`) thì dừng build.
- Ô kiểm duyệt (`//11 /box`): cột nhãn rộng ~62 px, nên rút nhãn thành "Ngày SX", "Phân loại", "Đăng ký".
- Kiểm bằng ảnh `vis4/vis5`: mã màu đúng, dòng trống còn, ô kiểm duyệt không đè.

**Thay đổi class:** chỉ `hl4/lgt/KroText.class` (`release/jardiff_vi1_vi2.txt`): thêm `XLEAD0/1`, `BASE`, `ELL`, `VNP`, `vnAscii[]`, `ellW`, hàm `glyph()`. Mảng chỉ cấp trong constructor; `draw/glyph/measure` **không cấp phát** (đã quét `javap -c`). `selfCheck0` (StringBuffer) chỉ chạy lúc khởi động như v1.

**Kiểm tra:** `dp check` OK cho cả 4 nhóm (cảnh báo thuật ngữ còn lại đều là dương tính giả: câu tách dòng, 전직 = "trước đây", 강화 = "tăng cường"); `build_vi` 0 lỗi (ô tràn, tham số thẻ > 39 byte, mô tả quest > 254 byte, `<NAME>` lệch bảng tên); kết quả chạy ở §13.

## 13. Kiểm chứng bản vi2 (jar cuối `e0f6efc3…`, class + tài nguyên lấy từ jar)

Kết quả đầy đủ trong `release/kiem-chung-vi2.txt`. Bộ chạy: `fbuy`, `fmonkey`, `fA`, `fI`, rồi `fload`.

| Kiểm tra | Kết quả |
|---|---|
| AOT vs thông dịch, 300 s, cùng kịch bản phím | 21 417 374 087 lệnh ở cả hai; svclog trùng (4,1 triệu+ lần gọi host); 6/6 khung hình trùng |
| Mua cash → tự lưu → khởi động lại → mở túi | kết nối máy chủ offline, `save0.dat` đổi 49 byte; lúc tải lại 0 lần `netConnect`; túi đồ hiện đủ |
| Chơi ngẫu nhiên 700 s (AOT) | 51,4 tỉ lệnh; heap khách đỉnh 4 150 264 byte; 12 yêu cầu mạng bị từ chối như mất mạng |
| Exception / hàm WIPI chưa cài | 0 ở cả 5 lần chạy |
| `j2me_scan` + preverify | 37 class, 0 vi phạm, 0 cảnh báo StackMap |
| `jardiff` vi1 → vi2 | chỉ `KroText.class` đổi; class version > 48 / StackMapTable: 0 |

**Kịch bản phím cash dời so với v1:** thoại mở đầu v2 dài hơn nên đoạn mở đầu kết thúc ở ~118 s thay vì ~105 s. Nếu giữ phím menu như v1 thì phím rơi vào lúc còn thoại, không mua được (lần chạy đầu thất bại vì lý do này, không phải lỗi game). Đã thêm OK mỗi 1,2 s từ 105–118,2 s và dời phím menu +14 s.

**Soát hình** (ảnh chụp headless):
- chữ tự cuộn mở đầu;
- thoại EV00 có bong bóng;
- cửa sổ Quest (tên + mô tả);
- túi đồ, kỹ năng, menu, cài đặt;
- Trợ giúp: điều khiển, cốt truyện, 9 trang thông tin game;
- ô kiểm duyệt.

**Không kiểm được ở đây:** J2ME Loader, KEmulator, máy thật, âm thanh thật, FPS trên máy.
