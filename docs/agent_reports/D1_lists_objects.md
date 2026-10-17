# Báo cáo module D1 "object pool & lists"

Worktree: /home/user/wipi-port-j2me/.claude/worktrees/agent-ab2f626e10b34807e (đã `git reset --hard 2cbe906`). Không commit/push.

## Module / hàm được giao
LinkedList_addFirstObject, addLastObject, getSize, isEmpty, releaseAll, tempRelease; MP_InitNetItem, MP_POSITION_init, MP_getArmsBag, MP_getArmsEquip, MP_getBag, MP_getEquip, MP_getHero, MP_getNew, MP_getObj, MP_getPickUpItem, MP_getSummon, MP_getX, MP_getY, MP_release; OBJ_destroy, getCreateMapIdx, getEventIdx, getPtr, getVisible, initReleaseTypeCreateMapIdx, next, return, setCreateMapIdx, setEventIdx, setPosition, setReleaseType, setVisible; MAPOBJ_getOpacityType, return, set, setTrans, setVisible; DropItem_draw, process, return, set. (42 hàm)

## Đã port (tên gốc @addr -> Lớp.method)
LList:
- LinkedList_getSize @0x86960 -> LList.getSize()
- LinkedList_tempRelease @0x87070 -> LList.tempRelease()
- LinkedList_releaseAll @0x8719c -> LList.releaseAll() (static; 7 LinkedList_release rồi, theo tail call PointerList_releaseAll = PointerList_initAll @0x87154, 3 `PList.release()` của _npcList/_monsterList/_targetEffectList; viết inline vì PointerList_* thuộc agent khác)
Pool:
- MP_getObj @0x9d250 -> Pool.getObj(Obj pos)  (position = header +0x10..0x17 của một Obj)
- MP_POSITION_init @0x9d1cc -> Pool.positionInit(o, type, idx, ptr, flag)  (ptr không lưu: con trỏ tự thân = chính `o`)
- MP_InitNetItem @0x9d1e4 -> Pool.initNetItem(idx)  (KHÔNG phải network: chỉ ghi lại header {item, 12, 1, idx} của ô arms/net item idx; đã port và kiểm đủ)
- MP_getHero @0x9dab4 -> Pool.getHero(); MP_getSummon @0x9dad0 -> getSummon(); MP_getPickUpItem @0x9db78 -> getPickUpItem(i)
- MP_getBag @0x827a8 -> getBag() (G.itemBag); MP_getArmsBag @0x829bc -> getArmsBag(); MP_getEquip @0x827c4 -> getEquip(i); MP_getArmsEquip @0x82a18 -> getArmsEquip(i)
- MP_getX @0x9db34 / MP_getY @0x9daf0 -> Pool.getX/getY(Obj pos)  (-1 nếu type != 3)
- MP_release @0x9d24c -> Pool.release() (rỗng, bản gốc chỉ `bx lr`)
Obj (static, nhận Obj): OBJ_getPtr @0xb9258 getPtr, OBJ_next @0xb9264 next, OBJ_setPosition @0xb923c, OBJ_getVisible @0xb9248, OBJ_setVisible @0xb9250, OBJ_setReleaseType @0xb928c, OBJ_setCreateMapIdx @0xb929c, OBJ_initReleaseTypeCreateMapIdx @0xb92a4, OBJ_getCreateMapIdx @0xb92c4, OBJ_setEventIdx @0xb9310, OBJ_getEventIdx @0xb9408, OBJ_destroy @0xb9368 -> Obj.destroy, OBJ_return @0xb93b8 -> Obj.ret.
MapObjDraw: MAPOBJ_set @0x9d18c -> MapObjDraw.set(o), MAPOBJ_return @0x9d150 -> ret(o), MAPOBJ_setTrans @0x9c3ac -> setTrans, MAPOBJ_setVisible @0x9c590 -> setVisible.
DropItems: DropItem_set @0xd16cc -> DropItems.set(o), DropItem_return @0xd1684 -> DropItems.ret(o). Nhánh nước của DropItem_draw (xem dưới) cũng port mới.

## Đã có sẵn, chỉ thêm / sửa javadoc
- LinkedList_addFirstObject @0x86904, LinkedList_addLastObject @0x86934, LinkedList_isEmpty @0x86968 -> LList (đối chiếu disassembly: đúng)
- MP_getNew @0x9dab0 (tail call MP_search @0xad444) -> Pool.getNew (thêm: type ngoài khoảng / không có pool (0, 11, 13, 15, 17+) trả null thay vì NPE/AIOOBE)
- MAPOBJ_getOpacityType @0x9c3b4 -> ObjDraw.opacityType (file ObjDraw, chỉ thêm 1 dòng javadoc)
- DropItem_process @0xd140c -> DropItems.process; DropItem_draw @0xd12f0 -> DropItems.draw

## Còn later
Không còn `later` trong phạm vi giao. Lưu ý:
- DropItem_draw nhánh ô nước (tile property 7): trước đây `throw later`; nay đã port từ disassembly (SPR_drawSprite(commonEffectSpr, anim 0x16, &spr+0x6c, x+8, y+10, 0, 0) rồi SPR_repeatFrame(spr, 0x16, 0)). KHÔNG kiểm được: trace M không có drop trên ô nước (và map ở tick 4800 không có ô thuộc tính 7; quét 100x100 ô). Phần này chỉ dựa trên đọc disassembly, người review nên coi là chưa được kiểm tự động.
- MP_getObj với idx ngoài pool: bản gốc trả con trỏ rác, Java trả null (không xảy ra trong game).

## Kiểm từng hàm
Công cụ riêng (không sửa tools/): bản sao `work/cr/callrec_inst.py` + `work/cr/jcr.sh` (thêm: biến POSWILD ghi đối số "position" là `*`, và CR_PATCH chạy một script chỉnh bản sao nguồn), adapter TẠM `work/cr/CrAdapter.java` (chỉ chép vào bản sao nguồn của jcr, KHÔNG bao giờ vào j2me/src; javadoc LList.* tương ứng bị tắt trong bản sao). Tất cả script ở work/cr/: gen_probe.py (sinh probe), runB.sh/runAll.sh (probe ref+Java+so), runC.sh/runD.sh (lời gọi tự nhiên), filt.py.
1. Probe tick 4800 (snapshot work/snap/M_4800.snap), `work/cr/runAll.sh`:
   - Lượt 1 (mọi hàm trừ 3 hàm "callee của tail call": OBJ_setCreateMapIdx, OBJ_setVisible, OBJ_return): `callcmp: 249 ref calls, 249 java calls, 0 differences`
   - Lượt 2 (OBJ_setCreateMapIdx, OBJ_setVisible, OBJ_return): `callcmp: 29 ref calls, 29 java calls, 0 differences`
   Chỉ so lời gọi mức trên cùng của probe (tick 4801, depth 0); hash g/e/h/m và hash từng object đối số đều khớp. Phủ: mọi loại pool cho MP_getObj/getNew (cả type không hợp lệ), cờ free của position, MP_InitNetItem 0..5 và 69, danh sách trống / size lệch (addFirst/addLast không đổi size, tempRelease làm size âm), release type 1 (permanent) và khác 1, OBJ_*/MAPOBJ_* với đối số vượt độ rộng (u8/s8/s16), OBJ_return/destroy trên object có byte +0x1e/+0x1f khác 0, DropItem_process mọi nhánh (nhấp nháy ở 0x173, xoá ở 0x191 cả khi node ngoài danh sách và trong danh sách, tràn s16), DropItem_set/return, LinkedList_releaseAll (cuối cùng, trên trạng thái thật).
   - Kiểm tra độ nhạy: hoàn nguyên Obj.objSet về w32 (cũ) thì lượt 1 và 2 báo lệch ngay ở OBJ_destroy/OBJ_return; sau khi sửa lệch biến mất.
2. Lời gọi tự nhiên trace M, ticks 4800..9000: `SNAPLOAD=work/snap/M_4800.snap python3 tools/e_calls.py ... 4800 9000 DropItem_process,DropItem_draw,MAPOBJ_getOpacityType ...` rồi `tools/jcr.sh ... trace_M.txt 9000 500 ...` (work/cr/runC.sh, runD.sh): `callcmp: 1757 ref calls, 1757 java calls, 0 differences; DropItem_draw=877 DropItem_process=880` (MAPOBJ_getOpacityType không được trace M gọi trong 500..9000, chỉ kiểm bằng probe: 18 giá trị trên các ngưỡng 0x4d/0x74/0x9b/0xc2/0xe9/0xff).
   Nhật ký chạy ref 500..4800 cũng không có lời gọi nào của ba hàm này.

## Build
`scan: 108 classes, 0 reject, 50 warn, narrowing casts 824` / `built .../work/build/hl4.jar 297503 bytes b1d61a06...`

## Hồi quy trace M
`GameTraceTest ok ticks 501..9000` (lệnh jt.sh trong work/cr/regress.sh).

## Sửa ngoài file giao (liệt kê từng chỗ)
1. Maps.java `dropItemInit` (DropItem_init @0xd1490): `mm[0x20] = 0` -> `Bytes.w16(mm, 0x20, 0)` (bản gốc `strh`). LỖI CÓ SẴN: byte cao của bộ đếm tuổi drop không bị xoá khi DropItem_set/return; lộ ra qua probe DropItem_process (+0x21 ref 00, java 01).
2. Maps.java `tempReleaseList`: vòng lặp inline thay bằng `G.objList.tempRelease()` (LinkedList_tempRelease). Hành vi giống hệt.
3. ObjDraw.java: chỉ thêm javadoc `MAPOBJ_getOpacityType` cho `opacityType`.
4. Obj.java `objSet` (OBJ_set @0xb9318 ghi địa chỉ Ghidra 0xc9318 trong javadoc cũ, nay thêm địa chỉ thật): `Bytes.w32(m, MAPIDX, 0xffff)` ghi cả +0x1e/+0x1f -> `Bytes.w16` (bản gốc `strh` ở +0x1c). LỖI CÓ SẴN. Đồng thời VISIBLE/SORTY ghi bằng w8 (kết quả byte giống cũ). Hồi quy M vẫn khớp.
5. Pool.java: initSlots/ret dùng `MapObjDraw.set/ret`, `DropItems.set/ret` (thay hai đoạn lặp giống hệt), `getNew` thêm kiểm tra type.
6. docs/ghidra_sig_fixes.tsv: thêm `LinkedList_isEmpty /1+r`, `MP_getNew /2`, `MAPOBJ_set /2`, `DropItem_set /2` (Ghidra sai: các hàm *_set nhận (o, pos) vì OBJ_set đọc r1 = pos do MP_Initialize truyền; Ghidra ghi 1 đối số).
Không có hàm "port thêm" nào ngoài danh sách giao.

## Quirk của bản gốc gặp phải (đã chép đúng)
- OBJ_destroy và OBJ_return có cùng thân; cả hai chỉ đặt lại header (kể cả cờ free = 1), không đụng +0x1c..+0x1f ngoài halfword create-map index.
- MAPOBJ_set/DropItem_set/MAPOBJ_return/DropItem_return: set gọi OBJ_set trước rồi *_init (x=y=0 sau đó init ghi lại; DropItem_init ghi y-8 = -8); return thì *_init trước rồi OBJ_return (x=y=0 cuối).
- LinkedList_isEmpty: `1 - size` không dấu, size âm cũng cho 0 ("không rỗng"); addFirst/addLast KHÔNG đổi size nên tempRelease có thể làm size âm (kiểm trong probe).
- LinkedList_tempRelease: duyệt từ đuôi, kiểm byte release type có dấu (!= 1); đoạn cuối "head/tail không phải 1 thì đặt null" thực tế không bao giờ chạy.
- MP_getNew/MP_search ghi position {0, 0x11, 1, 0xffff} khi hết chỗ; MP_search không có case cho type 11, 13, 15 (và PICKUP type 14 *có* case trong MP_search nhưng MP_getObj lại không có case 14 -> 0).
- MP_getObj: hero/summon bỏ qua idx; idx đọc `ldrh` nhưng nhân bằng `smulbb` (có dấu); MP_getX/Y dùng `ldrsh`.
- DropItem_process trả giá trị rác (r0 còn lại từ LinkedList_remove khi tail-call, không tail-call thì r0 bất kỳ); Ghidra ghi `int`: Java void, callcmp bỏ qua vì ret Java là `-`.
- DropItem_draw tính tile property trước khi kiểm visible (không ảnh hưởng).

## Leo thang
Không có hàm nào qua 3 vòng sửa. Chưa kiểm được: nhánh nước của DropItem_draw (không có dữ liệu), và hình vẽ của DropItem_draw ngoài hash khung của trace M (khớp 501..9000).

## Brief feedback
- §4 không nói rằng `e_calls.py ... 4800 4800` ghi cả lời gọi TỰ NHIÊN của tick 4800 (tick 4800 chạy trước probe, probe ghi ở tick 4801) nên file ref lẫn lời gọi mà bản Java không bao giờ tạo (Java không gọi MP_getHero, LinkedList_getSize...). Phải lọc theo tick 4801 (work/cr/filt.py). Nên ghi rõ, hoặc cho e_calls ghi probe với tick riêng / có `--ticks`.
- Lời gọi lồng nhau: ref ghi cả lời gọi lồng của hàm gốc (OBJ_return -> OBJ_setEventIdx...), còn Java phẳng hơn/khác; dấu hiệu "độ sâu" chỉ đúng khi mọi hàm cha cũng được ghi. Mô tả "tail call chỉ một dòng" thiếu hệ quả: hàm cha mất dòng khi callee cũng được ghi (initRelease -> setCreateMapIdx, MAPOBJ_setVisible -> OBJ_setVisible, DropItem_return -> OBJ_return), nên phải chia lượt theo tập hàm VÀ chia file probe (nếu không, lời gọi con của hàm cha không được ghi sẽ lộ ra ở depth 0). Nên có công cụ lọc "chỉ lời gọi probe cấp cao nhất" (CALLER=1 hiện LỖI với lời gọi probe: `g.elf.name(RET_MAGIC)` trả None -> TypeError).
- §4: tham số `LList`/`Obj`-có-vị-trí: dispatcher sinh `(LList) O(..)` làm biên dịch hỏng cả gói (không chỉ "không gọi được") nên mọi javadoc LList.* trong spec phải tắt; adapter phải nhận selector kiểu Obj. Gợi ý cách chuẩn: selector là object type 14 (PICKUP, không thuộc family nên không sinh cột `o=`), chỉ có 5 object (idx 0..4); `!poke`/đối số `oT.i` của e_calls KHÔNG hiểu type 14 (KeyError) nên không poke được selector; poke "position" phải dùng object family (tôi dùng sprobj type 16).
- Đối số kiểu "con trỏ tới position (header obj+0x10)" (MP_getObj, MP_POSITION_init, MP_getX/Y): e_calls in `ptr` (không căn stride) nhưng Java ghi `oT.i` và thêm cột `o=` mà ref không có -> `o` lệch; tôi phải thêm POSWILD trong bản sao công cụ. Nên cho spec cú pháp "đối số này là con trỏ trong object" hoặc để callcmp bỏ qua `o` khi đối số ref là `ptr`.
- Probe cho hàm được gọi qua con trỏ nội bộ phải truyền địa chỉ số: nên liệt kê trong brief bảng địa chỉ thật của pool/global (tôi lấy từ POOLS của e_calls: pool=0x1017b0, offset từng loại từ MP_getObj) và nói `I(a[i])` của ProbeDispatch chỉ parse số thập phân (hex trong dòng probe làm Java hỏng nhưng ref vẫn chạy).
- §1.7 / §4 không nêu trường hợp Ghidra ghi sai số đối số của hàm *_set (nhận cả pos). Nên liệt kê sẵn trong ghidra_sig_fixes (CHAR_set, Effect_set, ITEM_set, SprObject_set, Tile_set nhiều khả năng cùng lỗi: MP_Initialize gọi chúng với 2 đối số; tôi chỉ kiểm và sửa MAPOBJ_set, DropItem_set).
- §3 mô hình: không nói "position" (MP_POSITION) là header +0x10..0x17 của chính object; tôi phải suy ra từ MP_InitNetItem/MP_search. Một câu sẽ tiết kiệm nhiều thời gian. Cũng không nói MP_InitNetItem không liên quan network (tên gây hiểu nhầm; đó là pool arms item type 12).
- `docs/AGENT_BRIEF.md` yêu cầu `tools/agent_env.sh` in dòng built: đã chạy ổn. Hạn chế shell (heredoc/`$(...)`) chặn nhiều lệnh; ghi chú trong §2 đúng, nên thêm rằng `python3 - <<'EOF'` đơn giản vẫn chạy được còn `time`/`$(cat ..)` trong lệnh dài thì bị chặn: hãy dùng file script.
- Thời gian ref: 500..4800 mất vài phút, 4800..9000 vài phút: nên nói để agent đặt chạy nền.
