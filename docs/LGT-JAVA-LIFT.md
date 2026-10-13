# Hướng mới: lift mã ARM của game WIPI-Java (LGT) về bytecode JVM

Trạng thái: **đang nghiên cứu** (nhánh `ccr-6632a5f4-e1agyq`). Chưa có jar chạy được trên S60.

## 1. Phát hiện (có bằng chứng từ binary)
- Game thử nghiệm: LGT, AID `0002A4B1` (Legend of Master), MClass `Lm`, vendor K-NetP.
- `binary.mod` là ELF32 ARM **chế độ ARM** (không phải Thumb) do trình biên dịch Java→ARM của LGT sinh ra.
  Đây là **WIPI-Java biên dịch sẵn**, không phải WIPI-C như HL4/HL5/Zenonia. Nó gọi bảng runtime Java `0x64`,
  không phải bảng WIPI-C. Engine cũ (`hl4j/hl5j/xenj`) không có lớp Java ABI này nên không khởi động được game.
- Quy mô: 590 hàm, 242.595 lệnh ARM (≈ 1 MB mã). Tập lệnh rất hẹp: `ldr 30%`, `mov 27%`, `b 14%`, `bx 8,5%`,
  `cmp 7,7%`; gần như không có mul-long/adc/sbc (adc/sbc ≈ 1 lệnh). Chỉ một hàm helper Thumb được game gọi
  (`0xf4a61` = irem).
- 23 lớp (`Lm,a..v`), 597 chuỗi, bảng import: 416 field-instance, 47 virtual, 3 interface, 104 non-virtual.
- 4 hàm > 8000 lệnh (lớn nhất 13.528): rủi ro giới hạn 64 KB bytecode/method của JVM.

## 2. Vì sao engine cũ không phù hợp cho S60
Engine cũ thông dịch ARM bằng Java (≈66 MIPS thông dịch / ≈600 MIPS AOT chỉ trên HotSpot desktop), cần 2–8 triệu
lệnh khách mỗi khung hình và mảng `int[]` 64 KB/trang. Trên S60 (JIT CLDC-HI, heap nhỏ) không khả thi.
J2ME Loader chạy được vì có ART/JIT mạnh; điều đó không chứng minh chạy trên KVM/CLDC thật.

## 3. Hướng đã chọn
Dịch tĩnh ("static recompilation") mã ARM về **bytecode JVM thật** (CLDC), kèm lớp tương thích `org.kwis.*` → MIDP,
rồi preverify bằng ProGuard `-microedition`. Khác với quy tắc cũ "không viết lại logic": đây là biên dịch *cơ học*
(không viết lại logic game), nhưng không còn chạy ARM trong thông dịch viên.
Mô hình dịch: mức thanh ghi có suy luận kiểu (mỗi thanh ghi → local int/ref), trạng thái trừu tượng cho
`Payload/VTable/Table`, kiểm tra NPE/AIOOBE/ArithExc → `athrow` tường minh. Xem `lgtlift/`.

## 4. Giới hạn / chưa kiểm chứng
- Chưa biết model S60 (3rd/5th edition) cũng như heap, giới hạn jar, hành vi JIT với method rất lớn → cần probe MIDlet.
- Slot vtable cố định của lớp lõi (`String/Vector/Random/...`) chỉ có một phần trong dữ liệu của `wie`; phần còn lại
  phải suy từ bằng chứng.
- Dùng dữ liệu ABI từ dự án [wie](https://github.com/dlunch/wie) (tham khảo). Theo yêu cầu của wie: *This project is
  based on [wie](https://github.com/dlunch/wie), created by Inseok Lee.*

## 5. Mã trong repo
- `lgtlift/`: decoder ARM, ảnh nhị phân, metadata lớp/import (`python3 meta.py <binary.mod>`), các script thống kê.
- `tools-re/lomre/`: script khám phá ban đầu (disassembly có chú thích). Không chứa dữ liệu game; đường dẫn
  `binary.mod` truyền qua tham số/biến.
