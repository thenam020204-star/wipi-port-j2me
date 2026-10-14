# Chroisen (크로이센, Softmaru, LGT WIPI AID 0002CCCB v01.00.03) — định dạng & kiến trúc

Nguồn bằng chứng: `binary.mod` (bytecode ARM/Thumb thật, `kroj/tools/tdis.py`, `xref.py`), chạy động bằng `RunHeadless`
(`HL4_SVCLOG`, `HL4_TEXTLOG`, `HL4_CALL`), file dữ liệu. Độ tin cậy: **H**=chứng minh nhiều đường, **M**=khả năng cao, **L**=suy luận.

## 1. Gói
| Thành phần | Nội dung |
|---|---|
| `LGT_____.zip` | `0002CCCB.jar` + `app_info` (AID/PID/MClass) |
| jar | `binary.mod` (ELF ARM), `data/`, `image/`, `sound/` (gốc jar, không có `res/`) |
| `binary.mod` | ELF32 LE ARM: `.text` @0x1000 (0x5dfd7, file off 0x34) — **chủ yếu Thumb** (lệnh ARM ~2,6 %); `.data` @0x1400000 (0x414); `.bss` @0x1500000 (0x108f8); entry 0x56474; rodata (chuỗi EUC-KR) từ ~0x56480 (H) |
| Màn hình | 240x320, framebuffer RGB565 do game tự blit; vòng lặp sự kiện: start 0x10c1, paint 0x10bd, event 0x1069 (H) |

## 2. API nền tảng (WIPI-C LGT)
Bảng import 0x1FB hàm (+ bảng stdlib). Engine `kroj` (từ `xenj`) giải lệnh ARM/Thumb và gọi HLE: đồ hoạ (`MC_grp*`), file/db (`MC_dbOpen…`), âm thanh (`MC_mda*`, MMF/SMAF),
mạng (`MC_net*`), timer. Mới cho game này: **API ảnh** `MC_grpCreateImage` (0xe9: buf,off,len → ảnh PNG), `GetImageFrameBuffer` (0xc9), `DrawImage` (0xd5), `DestroyImage` (0xeb) — HLE trong `LgtRuntime` (H).
- Game tự phân tích PLTE của PNG (0x26590) rồi đổi điểm ảnh RGB565 đã giải mã ngược về chỉ số màu ⇒ điểm trong suốt phải là **0xFFF7** (cứng ở 0x3a184, `[0x1506600]=0xf81f`) (H, thử động).
- Phím: OZ = OK (-5) (H, thử động: -5 tua prologue, -10 không làm gì); SEND (-10) = lưu nhanh theo văn bản trợ giúp; '#' bỏ qua hội thoại; '*' minimap.
- Timer game: SetTimer 20/50/10 ms; `HL4-FPS` mặc định 27.

## 3. Ảnh (`image/*.dat`)
`u32 n` + n × (`u32 size`, `u32 off` tính từ đầu vùng dữ liệu = 4+8n) + PNG bảng màu (type 3, tRNS chỉ 0/255). Ô rỗng (size 0) giữ offset chạy. `body/illust/wing/charsel` = PNG trần. Round-trip byte-chính-xác trên 112 container (`kro/img/datpack.py`) (H).
Ảnh có chữ Hàn (đã vá, `kro/img/mk_images.py`):
- `title.dat`: [5] tiêu đề bảng xếp hạng (46x144, 6 hàng x24); [6] dải menu 74x336 (21 ô x16, trắng trên đen, đen = trong suốt khi vẽ); [11] nút lớp nhân vật 61x155 (5 ô x31, có quầng sáng).
- `uiinner.dat`: [7] nhãn quest 50x39 (3 hàng x13); [9] lọc lớp 27x78 (6 hàng x13); [16] nghề/chuyển/thức tỉnh/SP 24x48 (4x12); [19] thanh tab 234x13 (6 ô x39); [20] nút hành động 45x168 (14x12); [22] chỉ số 19x70 (5x14); [23] chỉ số phụ 33x70 (5x14); [25] "#:bỏ" 24x9; [26] gợi ý túi 48x52 (4x13); [29] "tái sinh" 18x13.
- `logo.dat`: [0] WINEMOBILE, [1] ALLSTAR, [2] 1x1 (dò màu trong suốt), [3] **Softmaru → logo NAMP** (vẽ giữa màn hình theo kích thước ảnh: hàm 0x20d4 trừ nửa w/h từ tâm [0x1509f6c]; nên ảnh 240x320 phủ toàn màn), [4] "사행성" (H).
Không có chữ: `uidefault/uibattle/eximg/WMap/effect/icon/charsel` (đã xem từng bảng).

## 4. Chữ
`data/font.dat` — 3 phần: bảng mã Johab 2350 chữ (→ *0x15008e8), bảng 0x480 (→ *0x15002a0), 0x300c glyph (→ *0x15008d8) (H).
Số liệu: ASCII advance 6 (0x15008d6), Hangul advance 10 (0x15006c2), ô 10x12 (0x15006c4 / 0x15008e5), byte/glyph 0x15008d5. Game **tự vẽ chữ**.
Hàm: đo `0x28e14(str, mode)` (mode 1 = rộng thô, còn lại = rộng vẽ; mã `|cNN` đếm chữ số thứ 2), vẽ chuỗi `0x296d8(str,x,y,fg,c2,align)` (stack: x[sp+0x24], y[sp+0x20], fg[sp+0x10], c2[sp+8], str[sp+0x28]),
glyph `0x292ec(x,y,bitmap,fg,c2)` (cắt/alpha/gradient/viền 4 hướng), ASCII `0x28ed8`, độ dài hiển thị `0x29db0`, nạp/ngắt Describ `0x29e3c` (ngắt khi byte*5 ≥ rộng-15), ngắt thoại `0x24a18`. Mã màu `|NN`, `|cNN`, `|`.
Lead 0xB0–0xC8 = Hangul; lead không phải Hangul bị bỏ qua tại 0x297e8 (`cmp r2,#0xa4`) ⇒ **điểm móc chữ Việt** (H).
**Mã hoá chữ Việt:** mỗi *từ* (kể cả dấu câu dính) = 2 byte (lead 0xC9.., trail 0xA1..), chỉ số vào `vi/dict.txt` (≤ 5076 mã); dấu cách và số giữ ASCII (dấu cách rút còn 3 px trong chuỗi Việt).
Font: NAM.P `f12.bin` (12 hàng) vẽ từng chữ cái vào ô 10x12 rồi gọi lại `0x292ec` của game (nên cắt/alpha/viền vẫn do game xử lý) — `KroText.java`.

## 5. Văn bản
| Loại | Định dạng | Ghi chú |
|---|---|---|
| `DIAL, QUEST0/00, IT##, SKILL, GameTip, TitleN, 490–492, MD0` | bảng u16: n×(u16 len, u16 off), off[0]=4n; bản ghi chứa trường nhị phân + chuỗi có tiền tố u8 (L=len+1, NUL ở p+L) | dựng lại offset sau khi dịch (<64 KB) |
| `Describ%d.dat` | văn bản, mục `//N`, CRLF, mã màu | game tự ngắt theo số byte ⇒ `build_vi` ngắt sẵn |
| `EV##.dat`, `QUEST1.dat` | bảng u32 (off,len) + kịch bản có thẻ `<AUTOSCROLL> <NAME x> <n> <t> <EMO a b c> <ENDEVENT>` | giai đoạn 2 |
| chuỗi trong `binary.mod` | EUC-KR trong rodata; ô = tới chuỗi kế (≤ mốc 4 byte); một số chuỗi *dùng chung đuôi* | đuôi được chép riêng và trỏ lại (xem §7) |

## 6. Mạng / bản offline
Máy chủ 222.231.57.145:59196 (chuỗi 0x5d454). Máy trạng thái 0x41b00: [0x15081c4] = 0 rỗi,1 connect,2 chờ,3 socket,4 chờ kết nối,5 gửi (0x40f00),6 nhận (0x40d04) → kết quả `0x3f71c(type, 1)`, lỗi `0x40c64(type)` → `0x3f71c(type, 2)`. Loại yêu cầu ở [0x1508330]; tham số phụ [0x1502d14+4].
Mua cash (loại 3 và 30, cùng nhánh 0x3f9fa): game **đã chuyển đồ vào túi trước khi gửi**; trả lời thành công chỉ để chốt (thất bại → hoàn tác). Trả lời loại 30 dài 12 byte: [7]=0, [8]=1 (0 = hết hạn mức tháng, 2/3 = từ chối khác); loại 3: 1 byte '0'.
⇒ Bản offline: `KroGame.netAllow()` chỉ cho loại 3/30, `netReply()` trả lời tại chỗ; mọi yêu cầu khác thất bại như mất mạng (xếp hạng, hộp quà, ký gửi, sự kiện…).

## 7. Chuỗi dùng chung đuôi trong binary
24 chuỗi trong `binary.mod` kết thúc đúng chỗ chuỗi khác bắt đầu (tail merge của trình biên dịch). `build_vi.py` chép bản dịch của phần đuôi ra sau NUL của chuỗi ngoài (`tr/B_inner_id.tsv`, ID `B:addr+k`) và sửa mọi con trỏ (từ literal pool) tới địa chỉ cũ.
