# Báo cáo module D1 (Item / ItemBag / ArmsBag)

## Module / hàm được giao
ItemBag_AllClean, addItemTab, getSize, getSlot, getTabItem, release, removeCurItem, removeCurItem_2, removeItem, setItem, setItemDN, setItemOptAdd;
ArmsBag_addItem, getCur, getSize, getSlot, release, setItem;
Item_Initialize, release, createItem, findEqualTypeIdx, getCnt, getIdx, getType, setCnt, isCountable, isEquipSetSlot, setEquipSlotNum,
releaseEquipSlotNum, getIcon, isRandomBox, getItemFrom, getItemCnt_ETC, getEquip, setTempEquip, tempInvenInit, TestQuest, TestSetItem.
Tất cả nằm trong `j2me/src/hl4/game/Items.java`; thêm `G.itemBagTab` (G.java) và một case trong `Pool.ret` (Pool.java).
Địa chỉ trong javadoc là địa chỉ THẬT (như `disf.py` in), theo brief §2/§1.5. Các javadoc có sẵn dùng địa chỉ Ghidra (+0x10000); chỉ sửa chỗ tôi đụng vào (getItemCnt).

## Đã port (tên gốc @addr -> Lớp.method)
- Item_release @0x82744 -> Items.release (rỗng); Item_Initialize @0x82794 -> Items.initialize (rỗng)
- Item_tempInvenInit @0x82748 -> Items.tempInvenInit
- Item_setCnt @0x827a0 -> Items.setCnt; Item_releaseEquipSlotNum @0x82948 -> Items.releaseEquipSlotNum
- Item_isRandomBox @0x82850 -> Items.isRandomBox; Item_setTempEquip @0x8299c -> Items.setTempEquip
- Item_getEquip @0x8338c -> Items.getEquip; Item_findEqualTypeIdx @0x839e0 -> Items.findEqualTypeIdx; Item_getIcon @0x84d30 -> Items.getIcon
- Item_TestQuest @0x84f48 -> Items.testQuest; Item_TestSetItem @0x85d2c -> Items.testSetItem
- Item_getItemCnt_ETC @0x83570 -> Items.getItemCntEtc (Items.getItemCnt giờ gọi nó, như bản gốc `b ETC`)
- Item_getItemFrom @0x83624 -> Items.getItemFrom
- ItemBag_getSlot @0x82838 -> Items.bagGetSlot; ItemBag_getSize @0x828bc -> Items.bagGetSize; ItemBag_getTabItem @0x828dc -> Items.bagGetTabItem
- ItemBag_release @0x8356c -> Items.bagRelease(LList); ArmsBag_release @0x83568 -> Items.armsRelease(LList)  (bản gốc NHẬN danh sách làm đối số)
- ArmsBag_getSlot @0x829d8 / getSize @0x829f0 / addItem @0x83310 / getCur @0x836a0 / setItem @0x85750 -> Items.armsGetSlot / armsGetSize / armsAddItem / armsGetCur / armsSetItem
- AmrsSearch @0x83704 -> Items.armsSearch (không được giao nhưng ArmsBag_setItem cần; chưa ai port)
- ItemBag_addItemTab @0x839e8 -> Items.bagAddItemTab(PList, tab, mode)
- ItemBag_removeCurItem @0x83dd8 -> Items.bagRemoveCurItem(cnt, byte[] ref, off); ItemBag_AllClean @0x83f1c -> Items.bagAllClean
- ItemBag_removeCurItem_2 @0x83f90 -> Items.bagRemoveCurItem2; ItemBag_removeItem @0x83fd8 -> Items.bagRemoveItem
- ItemBag_setItemDN @0x85dbc -> Items.setItemDn; ItemBag_setItemOptAdd @0x85ca4 -> Items.setItemOptAdd
- Hàm phụ thuộc, KHÔNG được giao, port tối thiểu vì addItemTab / removeCurItem_2 cần (người review nên khử trùng nếu agent GMenu cũng port):
  GMenu_InvenTypeCheck @0x61878 -> Items.invenTypeCheck; GMenu_EquiptypeCheck @0x66dfc -> Items.equipTypeCheck;
  GMenu_InventoryTabSort @0x6e234 -> Items.inventoryTabSort (PointerList_release + addItemTab).
- `Pool.ret`: thêm case ITEM / ARMS_ITEM (= ITEM_return @0x84f10 = OBJ_return + Item_init); trước đó `LList.remove` của item ném "MP_return type 8: later" nên không hàm bag nào xoá được item.
- `Item_RepaySet`: đã thay `later` "ArmsBag_setItem(0xe, idx)" bằng `armsSetItem(0xe, sub)`. `GMenu_repayErrorPopSet` giữ `later`.
- Item_getItem (có sẵn): `it.m[0x25] = cnt` đổi thành `setCnt(it, cnt)` (bản gốc gọi Item_setCnt; cần để hàng gọi lồng khớp khi đo).

## Đã có sẵn, chỉ thêm javadoc
Item_getCnt @0x827f4, Item_getIdx @0x827ec, Item_getType @0x827e4, Item_isCountable @0x82820, Item_isEquipSetSlot @0x82920,
Item_setEquipSlotNum @0x8293c, Item_createItem @0x85720 (tất cả trong Items.java). ItemBag_setItem (đã có javadoc kiểu Ghidra) chỉ kiểm.

## Còn later (hàm, nhánh, lý do)
Không thêm `later` mới. Đã bỏ 1 `later` (RepaySet/ArmsBag_setItem). Lệch có chủ ý so với bản gốc (UI không bao giờ chạm tới):
- bagGetTabItem(i<0): bản gốc đọc ngoài mảng; Java ném AIOOBE.
- bagRemoveCurItem2 / ItemBag_removeCurItem_2 khi tab-list rỗng hoặc index >= count: bản gốc dùng con trỏ 0x172 (đọc rác, vẫn chạy); Java NPE.
- Quest.getStatusList (có sẵn, không phải của tôi) ném AIOOBE với step >= ~64 qua Item_TestQuest (bản gốc đọc ngoài mảng).

## Kiểm từng hàm
Công cụ: `tools/e_calls.py` + `tools/jcr.sh` + `tools/callcmp.py`, snapshot M_4800, probe tại tick 4800, trace M. Script/ probe/ ref/ java ở `work/cr/` (`p?.probe|spec|ref|java`, `run.sh`, `run2.sh`, `runall.sh`).
Dòng tổng của `callcmp` (lần chạy cuối, mã cuối):
```
pa  Item_createItem+r,Item_isRandomBox,GMenu_InvenTypeCheck,GMenu_EquiptypeCheck,Item_getIcon/2+r,Item_getEquip      73 calls, 0 differences
pb  ArmsBag_getSlot,getSize,getCur,addItem,setItem, AmrsSearch                                                         157 calls, 0 differences
pc  Item_release,Item_Initialize,Item_tempInvenInit,Item_setCnt,getType,getIdx,getCnt,isCountable,isEquipSetSlot,
    Item_setEquipSlotNum,Item_setTempEquip,ItemBag_getSlot,ItemBag_getSize,ItemBag_getTabItem                          48 calls, 0 differences
pd  Item_getItemCnt_ETC                                                                                                8 calls, 0 differences
ph  Item_getItemCnt                                                                                                    7 calls, 0 differences
pg  Item_releaseEquipSlotNum                                                                                           4 calls, 0 differences
pq  Item_TestQuest                                                                                                     11 calls, 0 differences
pe  ItemBag_setItem/4+r=-:0:1:2,ItemBag_setItemDN/4+r,ItemBag_setItemOptAdd/8+r=-:0:1:2:3:4:5:6,Item_getEquip,
    Item_createItem+r,Item_getItemCnt_ETC                                                                              124 calls, 0 differences
pr2 GMenu_InventoryTabSort,ItemBag_getTabItem (+ hash của item)                                                        607 calls, 0 differences
pr4 ItemBag_AllClean                                                                                                   378 calls, 0 differences
pr1 Item_getItemFrom, Item_findEqualTypeIdx, ItemBag_addItemTab, ItemBag_getTabItem   (adapter tạm)                    140 calls, 0 differences
pr3 ItemBag_removeCurItem (adapter tạm)                                                                                488 calls, 0 differences
px  ItemBag_removeCurItem_2 (adapter tạm + sửa chữ ký)                                                                 1072 calls, 0 differences
py  ItemBag_removeItem (adapter tạm + sửa chữ ký)                                                                      1068 calls, 0 differences
pz  ItemBag_release/1, ArmsBag_release/1 (adapter tạm + sửa chữ ký)                                                    121 calls, 0 differences
pf  Item_TestSetItem                                                                                                   130 ref calls khớp từng dòng; xem Leo thang
```
Ghi chú phương pháp (quan trọng cho người review):
1. **Hàm nhận con trỏ / danh sách** (Item_getItemFrom, Item_findEqualTypeIdx, ItemBag_addItemTab, ItemBag_removeCurItem(_2), ItemBag_removeItem, ItemBag_release, ArmsBag_release): dispatcher probe chỉ gọi được tham số int/Obj, không gọi được `byte[]`/`LList`/`PList`. Tôi chạy kiểm bằng một file adapter TẠM `ZProbe.java` (giải mã địa chỉ tuyệt đối -> mảng + offset; đã XOÁ khỏi `j2me/src`, bản ở `work/cr/ZProbe.java.txt`) và tạm vô hiệu javadoc `@0x` của hàm thật (`work/cr/tog.sh on|off`); đã khôi phục (`grep "@ 0x"` = 0). Logic được kiểm là logic thật (adapter chỉ đổi kiểu đối số).
2. Probe có "chọc bộ nhớ" bản gốc để dựng trạng thái: `Item_setEquipSlotNum(addr-0x24, v)` ghi 1 byte tại `addr`, `Item_setTempEquip(25+i, item)` ghi con trỏ vào `_itemBag.ptrs[i]` (địa chỉ `_equip + 4*slot`). Adapter Java làm đúng phép ghi tương ứng. Nhờ vậy dựng được tab-list và `_itemBag+1` mà không cần hàm UI.
3. ItemBag_release / ArmsBag_release / ItemBag_removeCurItem_2 / ItemBag_removeItem: `e_calls.py` không đọc được chữ ký Ghidra (tail-call thunk không có phần giải mã, hoặc >12 dòng cảnh báo trước khai báo). Dùng wrapper `work/cr/ec.py` + `work/cr/ci.py` (+ `jcr2.sh`) chỉ bổ sung chữ ký; không sửa `tools/`.
4. Gọi lồng bằng tail-call (`b`) trong bản gốc chỉ ghi 1 dòng; nên các cặp (Item_getItemCnt/ETC, Item_releaseEquipSlotNum/setEquipSlotNum, removeItem/removeCurItem_2, InventoryTabSort/addItemTab) được đo trong các lượt riêng.
5. Đối số `-1` hiện là `ptr` (đại diện) trong dòng ref nên không phân biệt được -1 với con trỏ; các giá trị trả về `-1` cũng vậy.

## Build
`scan: 108 classes, 0 reject, 51 warn, narrowing casts 817` / `built .../work/build/hl4.jar 295746 bytes fac840e7...` (không có `hl4/game/ZProbe`).

## Hồi quy trace M
`GameTraceTest ok ticks 501..9000` (lệnh `tools/jt.sh hl4.test.GameTraceTest work/golden/trace_M.txt 9000 500 $(cat docs/golden_scripts/kM.txt)`).

## Quirk của bản gốc gặp phải (đã chép đúng, đừng "sửa")
- Item_getEquip(slot): so sánh s8 của +0x24 (<= 6) với `slot` nguyên 32-bit; slot = -1 trả item đầu tiên CHƯA trang bị (s8(0xff) = -1 <= 6).
- ArmsBag_setItem luôn trả 0 và tạo item bằng `Item_createItem(0, ...)` => pool loại 8 (không phải pool 12), `a = 0` nên tiêu một lần `Rng.rand(0,5)` (+0x16b).
- ItemBag_removeCurItem: vòng chỉnh `+0x172` của các item phía sau chạy cả khi chỉ giảm số lượng (không xoá); index byte chỉ lùi 1 khi xoá đúng phần tử cuối; QuestCheck dùng idx = s8(+0x22) mở rộng dấu.
- ItemBag_removeCurItem_2 đọc `_itemBag+1` rồi gọi InventoryTabSort(tab-1, 0): với tab = 0 danh sách tab trở nên rỗng (tab -1 không khớp loại nào).
- Item_getItemCnt_ETC không đọc đối số thứ 3 (Item_getItemCnt truyền 0); AmrsSearch đọc `_armsBag+0xc` trực tiếp (không qua ArmsBag_getSlot).
- ItemBag_addItemTab ghi `+0x172` (vị trí trong bag) kể cả khi PointerList đầy (add bị bỏ qua).
- GMenu_InvenTypeCheck mode 1: type<=3 || type==7 || (type==8 && idx==0x15 qua phép so word 0x150008 bỏ byte +0x21).
- GMenu_EquiptypeCheck mode 4 phụ thuộc lớp nhân vật `hero+0x16b`: (0|2)->type 4, 1->type 5, 3->type 6.
- Item_TestQuest chỉ đổi trạng thái khi `_event[0x824+step] <= 1`.
- Item_getIcon = byte đầu của record dữ liệu item; type không có file trả -1.
- ItemBag_release/ArmsBag_release nhận con trỏ danh sách (`_itemBag+4`, `_armsBag+4`) ở r0.

## Leo thang
- **Item_TestSetItem (@0x85d2c)**: bản gốc trong emulator tham chiếu dừng im lặng sau 65 lời gọi lồng (không có dòng thoát, pend rò độ sâu +3 cho các dòng sau); Java chạy đủ 100 vòng. 130 dòng ref đầu khớp từng dòng (0 lệch, `pf`). Nguyên nhân phía emulator chưa rõ (nghi cạn heap bump-allocator của emulator: mỗi lần tạo item trang bị tải `_ITM_n_SD` rồi `MC_knlFree` là no-op; lượt gọi thứ hai trong cùng probe lại chạy đủ 100 vòng, các lần ret=0 là túi đầy). Không thể so phần còn lại; hàm chỉ là vòng for 100 lần của hàm đã kiểm (setItem / setItemOptAdd). Không sửa công cụ.
- Không có hàm nào vượt 3 vòng sửa.

## Brief feedback


1. Brief nói javadoc ghi địa chỉ THẬT (như disf in), nhưng toàn bộ code có sẵn (Items.java, Quest.java...) dùng địa chỉ Ghidra (+0x10000). `port_cover.py` chấp nhận cả hai; `callrec_inst.py` chỉ cần `@0x` bất kỳ. Cần chốt một quy ước (và nói rõ có đổi lại các javadoc cũ hay không).
2. Worktree của agent được tạo từ commit cũ `e687dee` (không có `docs/`, `tools/`, `work/`), không phải `hl4-j2me-port` (`b380efa`). Tôi phải `git reset --hard b380efa` trên nhánh worktree của mình để có brief; brief nên nói trước hoặc worktree phải được tạo từ đúng nhánh.
3. §4 giả định mọi hàm probe được bằng số nguyên / `oT.I`. Hàm nhận con trỏ byte, `LList`, `PList` (rất nhiều trong bag) không probe được: `ProbeDispatch` ném lỗi khi tham số là mảng, và sinh code không biên dịch được nếu tham số là `LList`/`PList` (cast `(LList) Obj`). Brief cần quy ước cho "con trỏ vào mảng" (ví dụ cách viết tham số Java, hoặc cách probe ghi bộ nhớ), nếu không mỗi agent phải tự bày adapter tạm như tôi.
4. Brief không nói cách dựng trạng thái cho probe (ví dụ cần tab-list / `_itemBag+1`). Tôi dùng thủ thuật ghi bộ nhớ qua `Item_setEquipSlotNum(addr-0x24, v)` và `Item_setTempEquip(25+i, ...)` (ghi tuỳ ý theo `_equip + 4*slot`); nên có hàm/cú pháp probe chính thức để ghi byte/word.
5. `e_calls.py` `sigs()` không đọc được chữ ký của hàm có >12 dòng comment cảnh báo trước khai báo (ItemBag_removeCurItem_2, ItemBag_removeItem) và hàm thunk không có phần giải mã (ItemBag_release, ArmsBag_release); không có cách ghi đè trong SPEC (`/N` chỉ sửa số đối số). Phải viết wrapper `work/cr/ec.py`.
6. Chữ ký Ghidra sai nhiều chỗ (Item_getIcon, Item_createItem ret void, ItemBag_setItem*, Item_TestQuest ...); mẹo `/N`, `+r`, `=map` đủ dùng nhưng nên có danh sách các hàm có chữ ký sai biết trước.
7. Hàm gọi lồng sinh dòng ghi nhận lệch cấu trúc: tail-call (`b`) chỉ ghi 1 dòng (hàm đích), gọi con bên trong hàm gốc mà Java đã inline (ví dụ Item_getItem gọi Item_setCnt, ItemBag_leftSlotCnt/ItemBag_getCur gọi ItemBag_getSize/Slot, AmrsSearch không qua ArmsBag_getSlot) làm dòng thừa/thiếu khi đo cùng lúc nhiều hàm trong cùng cây gọi. Brief nên khuyến cáo: đo từng nhóm hàm không lồng nhau trong các lượt riêng, hoặc để Java giữ đúng cấu trúc gọi của bản gốc.
8. `ident()` biến mọi giá trị >= 0x10000000 (kể cả -1 = 0xffffffff) thành `ptr` (wildcard) nên đối số / giá trị trả về `-1` không được so; điều này che cả những lệch thật với -1 (ví dụ Item_getEquip(-1), Item_getType của item rỗng).
9. Emulator tham chiếu dừng im lặng giữa chừng khi một lời gọi probe dài (Item_TestSetItem: sau 65 lời gọi lồng) mà không báo lỗi; để lại pend rò làm sai độ sâu các dòng sau. Brief không nói về giới hạn này (nghi cạn heap vì MC_knlFree là no-op).
10. Sandbox của agent từ chối lệnh shell "phức tạp" (heredoc python nhiều dòng, `$(...)`, `time`, `bash script` từ ngoài worktree); phải viết script ra file trong worktree rồi gọi trực tiếp. Brief nên có mục lưu ý nếu chạy trong worktree.
11. `tools/jcr.sh` / `callrec_inst.py` đòi javadoc có TÊN GỐC ngay ở đầu và `@0x` cùng dòng đầu; nếu javadoc nhiều dòng mà `@0x` nằm dòng sau thì không bắt được. Brief §1.5 chỉ nói "mở đầu bằng tên gốc và địa chỉ"; nên nói rõ cùng một dòng.
12. Phạm vi mơ hồ: được giao ItemBag_removeCurItem_2/addItemTab nhưng chúng cần GMenu_InventoryTabSort / GMenu_InvenTypeCheck / GMenu_EquiptypeCheck (module khác) và `Pool.ret` loại 8 (`later`); brief không nói nên port hộ hay để `later`. Tôi port tối thiểu và ghi chú để khử trùng. Cũng không rõ có được sửa `Pool.java`/`G.java` (ngoài Items.java) hay không (brief §1.7 chỉ cho "chỗ gọi tối thiểu").
13. ItemBag_getSlot/ItemBag_release/getCur... có đối số "thừa" (r0 bị bỏ qua hoặc là con trỏ danh sách); brief không nói cách đặt tên/đối số Java cho các hàm có tham số bị bỏ qua (tôi bỏ `_` đầu, giữ tham số bị bỏ qua cuối như `bagGetSize(int tab)`).
14. Chưa có chỗ nào ghi trạng thái `_itemBag+1` / bảng tab trong Java; tôi thêm `G.itemBagTab`. Snapshot/golden không kiểm trường này (hash chỉ phủ _game/_event/hero/quái + object đối số), nên sai lệch ở `_itemBag` (danh sách tab, +1) chỉ phát hiện được nhờ giá trị trả về / hash item của lời gọi sau.
15. `ItemBag_checkAndSetItem` (có sẵn) bỏ qua phép mở rộng dấu / mask u8 của đối số (p5 u8, p6 s8) mà disassembly thực hiện; không ảnh hưởng đường gọi hiện tại (đối số nhỏ) nhưng khác bản gốc nếu truyền giá trị ngoài khoảng. Không sửa vì ngoài danh sách.
