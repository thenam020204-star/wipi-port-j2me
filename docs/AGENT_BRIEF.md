# Bản giao việc cho agent port (T0.5)

Đọc hết file này trước khi làm. Không cần đọc tài liệu khác trừ khi bản giao việc của bạn chỉ tới. Mọi đường dẫn tính từ gốc repo.
Bối cảnh 3 dòng: ta **viết tay** lại bằng Java ME game WIPI 영웅서기4 (Heroes Lore 4). Nguồn sự thật là `binary.mod` bản Android 2011 (ARM, có tên hàm).
Bản gốc chạy được trong một emulator kiểm chứng (`tools/ref_game.py`) để so từng hàm / từng tick với bản Java. **Emulator chỉ để kiểm, không phải sản phẩm.**

## 1. Luật không được phá (bản nộp vi phạm bị loại)

1. **Java ME thật**: CLDC 1.1 / MIDP 2.0. Không `float`/`double`, không API ngoài profile (không `java.util.ArrayList`, `String.format`, generics, autoboxing, `enum`, varargs, for-each, `StringBuilder`, `Math.floorMod`...). `tools/j2me_build.py build` có bộ quét, phải **0 reject**.
2. **Không cấp phát trong vòng lặp game / `paint`**: không `new`, không nối `String` trong đường gọi mỗi tick. Dùng buffer tĩnh như code hiện có.
3. **Giữ đúng kiểu và độ rộng của bản gốc**: struct gốc là `byte[]` với offset gốc; đọc/ghi bằng `Bytes.s8/u8/s16/u16/s32/w8/w16/w32` đúng độ rộng mà disassembly dùng (`ldrb` = u8, `ldrsb` = s8, `ldrh`/`ldrsh` = 16 bit, `ldr` = 32 bit). Phép cắt bit/dấu phải giống hệt (`(byte)`, `(short)`, `& 0xff`).
4. **Disassembly thắng pseudo-C.** Pseudo-C Ghidra chỉ để đọc nháp; nó hay sai ở vòng lặp, signed/unsigned, số đối số, đối số trên stack, bảng hằng `C.x.y` chép lên stack.
5. **Mỗi hàm Java tương ứng một hàm gốc có javadoc mở đầu bằng tên gốc và địa chỉ thật**: `/** TênGốc(a, b) @0x92c48: mô tả ngắn */`. Công cụ đếm tiến độ và công cụ kiểm từng hàm (§4) tìm hàm qua dòng này. Thiếu dòng này = hàm coi như chưa port.
6. **Nhánh chưa port**: `throw new IllegalStateException("TênGốc: <nhánh>: later");`. Không viết stub giả rồi báo là xong. Không đoán.
7. **Không đổi chữ ký (descriptor) của method đã có**, không đổi tên lớp/method đã có, không sửa file ngoài phạm vi được giao trừ chỗ gọi tối thiểu. Không sửa `tools/`, `golden/`, `docs/` trừ báo cáo của bạn.
8. **Trước khi viết một hàm, tìm xem đã có bản port chưa**: `grep -rn "TênGốc" j2me/src` và tìm cả method trùng chức năng không có javadoc (ví dụ `Items.getCnt` là `Item_getCnt` nhưng thiếu `@0x`: khi đó **thêm javadoc** cho method có sẵn, không viết bản thứ hai). Đã có hai bản trùng (`MONSTER_addHp`, `MONSTER_lookHero`, `BattleProc_getSkillDamage`) — đừng tạo thêm.

## 2. Môi trường

- Luôn `export LC_ALL=C.UTF-8` (đường dẫn có tiếng Hàn). Binary: `B="work/orig/android/영웅서기4.apk!/res/raw/jarfile.mp3!/binary.mod"`.
- Nếu bạn chạy trong **git worktree** riêng (agent song song): chạy một lần `tools/agent_env.sh <đường dẫn checkout chính>` để có `work/` (đầu vào dùng chung qua symlink, build riêng). Không chạy `tools/setup_env.sh`.
- Địa chỉ Ghidra = địa chỉ thật + 0x10000. Javadoc ghi **địa chỉ thật** (như `tools/disf.py` in ra).

## 3. Công cụ đọc bản gốc

| Lệnh | Việc |
|---|---|
| `python3 tools/disf.py TÊN…` | disassembly có chú thích (GOT, `_event+off`, tên hàm được gọi). Dừng ở literal pool đầu tiên |
| `python3 tools/disr.py <hex từ> <hex tới>` | disassembly theo khoảng địa chỉ (phần sau literal pool của hàm lớn) |
| `python3 tools/cfunc.py TÊN…` | pseudo-C Ghidra (nháp) |
| `python3 tools/port_cover.py --list` | hàm đã có javadoc `@0x` trong Java |
| `grep -n "TÊN" docs/callgraph_and2011.tsv` | ai gọi / gọi ai |

Mô hình code (xem các file trong `j2me/src/hl4/game/` làm mẫu, ví dụ `Items.java`, `MonDamage.java`):
- Global gốc = mảng trong `hl4.game.G` (`G.game`, `G.event`, `G.map`, ...). Object pool: `Obj`/`Char` (`o.m` = byte[] của struct, header ở đầu `Obj.java`; `Pool.hero`, `Pool.pools[type][i]`).
- Con trỏ của bản gốc được giữ bằng tham chiếu Java (trường song song), ô con trỏ trong `byte[]` để 0.
- Danh sách liên kết: `LList`, danh sách con trỏ: `PList`.

## 4. Kiểm từng hàm (bắt buộc trước khi nộp)

Một hàm chỉ được coi là xong khi **so với hàm gốc chạy trong emulator cho 0 lệch**. Hai cách, cùng định dạng kết quả:

`mkdir -p work/cr` trước.

**(a) Hàm được game gọi trong một trace** (ghi mọi lời gọi tự nhiên):
```
S=Item_getItemCnt,ItemBag_setItem          # danh sách hàm (SPEC, xem đầu tools/e_calls.py)
SNAPLOAD=work/snap/M_4800.snap python3 tools/e_calls.py "$B" work/res work/cr/ref.txt 4800 5400 $S $(cat docs/golden_scripts/scM.txt)
tools/jcr.sh $S work/cr/ref.txt work/cr/java.txt hl4.test.GameTraceTest work/golden/trace_M.txt 5401 500 $(cat docs/golden_scripts/kM.txt)
python3 tools/callcmp.py work/cr/ref.txt work/cr/java.txt
```
**(b) Hàm chưa trace nào gọi tới** (probe: gọi trực tiếp với đối số bạn chọn, trên trạng thái game thật ở tick T):
```
# work/cr/probe.txt: mỗi dòng 'TÊN arg arg ..' (số nguyên, hoặc object pool 'o<type>.<idx>': o2.0 = hero, o3.k = quái k, o8.k = item k)
SNAPLOAD=work/snap/M_4800.snap PROBE=work/cr/probe.txt python3 tools/e_calls.py "$B" work/res work/cr/ref.txt 4800 4800 $S $(cat docs/golden_scripts/scM.txt)
tools/jcr.sh $S work/cr/ref.txt work/cr/java.txt hl4.test.GameTraceTest work/golden/trace_M.txt 5401 500 $(cat docs/golden_scripts/kM.txt) --probe 4800 work/cr/probe.txt
python3 tools/callcmp.py work/cr/ref.txt work/cr/java.txt
```
- Probe gọi theo thứ tự trên **cùng một trạng thái** (lời gọi trước có thể đổi trạng thái cho lời gọi sau, hai phía như nhau). Chọn đối số phủ các nhánh (giá trị biên, mỗi loại item, chỉ số ngoài khoảng nếu bản gốc chịu được). Nhánh `later` thì đừng probe vào.
- Mỗi dòng kết quả so: tên, đối số, giá trị trả về (theo độ rộng chữ ký Ghidra), hash `_game`/`_event`/hero/quái và hash của từng object đối số **sau** lời gọi.
- Lệch hash: chạy lại cả hai phía với `CALLDUMP=h,g,e,o` (biến môi trường cho cả `e_calls.py` và `jcr.sh`) để `callcmp` in ra **byte** lệch (offset trong struct). `CALLER=1` thêm cột hàm gọi (phía gốc).
- Chữ ký Ghidra sai số đối số / trả về void sai: sửa bằng SPEC `TÊN/N` (ghi N đối số), `TÊN+r` (ghi giá trị trả về), `TÊN=j0:j1:-` (đối số gốc i ↔ tham số Java j_i). Kiểm bằng disassembly trước khi sửa.
- Snapshot có sẵn: `work/snap/M_500.snap`, `M_4500.snap`, `M_4800.snap` (trace M), `work/snap/N/snap_4500.snap`, `snap_8000.snap` (trace N). Tạo thêm: `python3 tools/e_snap.py "$B" work/res work/snap/X <tick> $(cat docs/golden_scripts/scM.txt)` (kịch bản luôn truyền **đầy đủ** `scX.txt`).
- Phía Java phải đạt cùng trạng thái ở tick T: trace dùng cho `--probe` phải là trace Java đang khớp tới T (M khớp trọn 501..9000; N khớp tới 4737).

## 5. Tự kiểm trước khi nộp

1. `python3 tools/j2me_build.py build` → `scan: … 0 reject`, có dòng `built …hl4.jar`.
2. Kiểm từng hàm §4 cho **mọi** hàm bạn port: `callcmp: … 0 differences`.
3. Hồi quy nhanh: `tools/jt.sh hl4.test.GameTraceTest work/golden/trace_M.txt 9000 500 $(cat docs/golden_scripts/kM.txt)` → `GameTraceTest ok ticks 501..9000`. (Không chạy nhiều `jt.sh` song song trong cùng một checkout.)
4. Đọc lại diff của mình: không float, không `new` trong đường mỗi tick, kiểu đọc/ghi đúng độ rộng, javadoc `TênGốc @0x…` đủ.

## 6. Nộp bài

Không commit/push (người review làm). Nộp **một báo cáo** `work/agent_reports/<module>.md` (và nhắc nó trong tin nhắn cuối) gồm:
```
Module / hàm được giao:
Đã port (tên gốc @addr -> Lớp.method):
Đã có sẵn, chỉ thêm javadoc:
Còn later (hàm, nhánh, lý do):
Kiểm từng hàm: lệnh đã chạy + dòng tổng của callcmp (số lời gọi, 0 differences)
Build: dòng scan + built
Hồi quy trace M: dòng kết quả
Quirk của bản gốc gặp phải (đã chép đúng, đừng "sửa"):
Leo thang (nếu có):
```

## 7. Leo thang

Một hàm không qua kiểm sau **3 vòng sửa** thì dừng hàm đó (để `later`), ghi vào mục *Leo thang*: tên hàm, input (dòng probe / tick), giá trị gốc vs Java (dòng `callcmp` + byte lệch), giả thuyết của bạn. Làm tiếp hàm khác. Không sửa test, công cụ hay golden để cho qua.
