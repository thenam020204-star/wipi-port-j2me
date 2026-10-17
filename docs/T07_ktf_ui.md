# T0.7 — Giao diện bàn phím lấy từ KTF: báo cáo phân tích (2026-10-17)

Quyết định nền: logic + dữ liệu AND2011, giao diện bàn phím (HUD, phím mềm, ô tắt, menu) theo KTF (`NOTES.md`). Ảnh `docs/ref_ktf/` chỉ dùng để nhận diện màn hình.
Công cụ mới: `tools/ktf_code.py` (mô hình code KTF: tách hàm, tìm theo hằng số / chuỗi, disassembly, caller), `tools/ktf_match.py` (ghép tên KTF↔AND2011 theo đồ thị gọi → `docs/ktf_match.tsv`).
Địa chỉ KTF viết `img+X` (offset trong image, `docs/binary_layout.md` §1); AND2011 là địa chỉ thật.

## (a) LGT 2010 có cùng HUD với KTF không?

**Cùng cấu trúc, chưa chắc cùng tọa độ** (INFERRED từ code, chưa chạy LGT).
- LGT `binary.mod` cũng là Thumb (không phải ARM như AND2011). Tìm theo dấu vân tay của hàm vẽ ô tắt (`DM_FillRectAlphaRGB(..,0xd,0xd,0x96,0xb,0xb,0xb)`):
  LGT `0x3f878` được `0x3fdd0` gọi **6 lần** (6 ô), hàm icon skill `0x3f62c` được cả hai gọi — y hệt KTF (`img+0x43140` × 6 trong `img+0x43680`, `img+0x42f38`).
- So dãy hằng số tức thời: hàm ô tắt khớp 86%, icon skill 78%, nhưng **thanh dưới chỉ khớp 61%**: LGT có các hằng 125, 131, 136, 157, 165, 185, 197, 217 mà KTF không có (KTF có 135, 150, 240, 248, 249) ⇒ có thể khác vị trí/kích thước một phần; và offset struct hero khác (KTF đọc `+0x298/+0x2b0/+0x2b4`, LGT `+0x29c/+0x2b8`).
- Kết luận dùng được: **nguồn giao diện là KTF** (đúng ảnh người dùng gửi). LGT chỉ dùng để đối chiếu khi một đoạn KTF khó đọc; không lấy tọa độ từ LGT.
- Muốn chắc chắn (a) bằng hình: cần HLE cho LGT trong bộ chạy tham chiếu (binary Thumb, import khác) — không làm ở T0, để mở.

## (b) Phần giao diện khác AND2011 (bản dọc, bàn phím)

So ảnh AND2011 ở tick 5250 trace M (`tools/e_raw.py`) với `docs/ref_ktf/00_hud_vi.png`, `09_hud_shop.jpg`, rồi kiểm bằng code:

| Thành phần | AND2011 dọc (đang port) | KTF (đích) | Code |
|---|---|---|---|
| Thanh dưới | không có ô tắt; chỉ icon buff/thể lực bên trái (`MainGameFrame_drawGameBottomUI`) | 6 ô tắt 1/3/5/7/9/0 + icon skill/vật phẩm/guardian/arms, số lượng, lớp phủ hồi chiêu; thanh HP (đỏ) / SP (xanh) dưới cùng | KTF `drawGameBottomUI2` `img+0x43680` (1243 lệnh Thumb), ô `img+0x43140` |
| HP/SP/EXP hero | thanh góc trên-trái kèm "LV" (`MainGameFrame_drawGameBottomUI_Touch(3)`, dùng ảnh skin TOUCH) | không có ở góc trên; HP/SP ở thanh dưới | KTF: trong `img+0x43680` (cần đọc) |
| Thanh tên/cấp quái | giữa-trên, sát thanh hero | giữa-trên, khung riêng ("LV03 Tân binh") | KTF `drawMonsterInfo` `img+0x4445c` → `drawInfo` `img+0x4419c` |
| Nhãn phím mềm | không có (bàn phím ảo cảm ứng) | góc trên-phải "우상키 / SHOP" ("Phím phải SHOP" bản Việt) | **chưa tìm thấy** (không nằm trong `drawBottomBar`/`drawMonsterInfo`; ứng viên: các lời gọi còn lại trong `drawGame` `img+0x44f44`, xem (d)) |
| Menu trong game (Status, Inventory, Equipment, Skill, Quest, System, Option) | khung TOUCH skin (`TF_setFrame`) | khung riêng, tab ◀1/3▶, ◀7/9▶ | chưa ghép (GMenu_*: chỉ vài hàm có hạt giống chuỗi, xem `docs/ktf_match.tsv`) |
| Minimap | `_lcdG+0x54 − _map+0x170 − 8`, y 0x18 | `... − 0x23` (KTF `drawBottomBar` `img+0x44ecc`) | khác hằng ⇒ vị trí khác |

## (c) Ảnh UI của KTF có trong tài nguyên AND2011 không?

So hash `docs/manifest_ktf.tsv` ↔ `docs/manifest_android.tsv` (cùng tên):
- `CM/` 39 file: **35 giống hệt**; khác 4: `CM/GRADE_BM`, `CM/GRADE_V_BM`, `CM/_CM_CMEFFSPR`, `CM/_CM_GRADE` (ảnh xếp hạng tuổi; AND2011 thêm `GRADE_*_S/1/2`).
- `GMenu/` 5 file: 3 giống; khác `GMenu/_GMENU_TXT`, `GMenu/_NPCUI_TXT` (bảng chữ menu/NPC UI — AND2011 có thêm chuỗi). `FT/` 6/6 giống. `tdf/` khác `TITLE_BM`, `_tdf_HELP`, `_tdf_MENU_TXT`.
- KTF không có file nào thiếu trong AND2011 ngoài 5 cảnh `MAP/SC/*` (đã biết, `NOTES.md`).
- ⇒ **Ảnh dùng cho HUD/menu của KTF đều có sẵn, giống từng byte trong jar AND2011** (trừ ảnh xếp hạng). Cần lưu ý: chữ menu của KTF nằm trong `_GMENU_TXT` bản KTF, khác bản AND2011 ⇒ khi port menu KTF phải xác định chỉ số chuỗi theo bảng nào (đề xuất: dùng bảng AND2011, ánh xạ chỉ số theo nội dung chuỗi, ghi lệch vào `docs/known_diffs.md`).

## (d) Hàm vẽ tương ứng trong KTF

Xác định bằng hạt giống chuỗi + trình tự lời gọi giống hệt AND2011 (mỗi dòng đã đọc disassembly KTF):

| KTF | AND2011 | Căn cứ |
|---|---|---|
| `img+0x44f44` | `MainGameFrame_drawGame` @0x8c2ec | gọi `drawBottomBar`, `drawMonsterInfo`, `DropItem_drawInfos`, `MainFrame_Get`×2, `Event_Draw_Event`, `Event_Draw_SubTitle`, `Event_Draw_Skip_Effect` đúng thứ tự AND2011 |
| `img+0x44ecc` | `MainGameFrame_drawBottomBar` | `MP_getHero`, `Event_isEvent`, `PickUpItem_drawItems` (`img+0x44e70`), `drawGameBottomUI` (`img+0x440e8`), minimap nếu `_game[3]` (`img+0x4c490`) |
| `img+0x440e8` | `MainGameFrame_drawGameBottomUI` | chỉ gọi `img+0x43680` |
| `img+0x43680` | `MainGameFrame_drawGameBottomUI2` | hạt giống chuỗi; **thanh dưới KTF**: 6 lời gọi ô tắt, icon skill |
| `img+0x43140` | `MainGameFrame_drawSkillSlotIcon` | hạt giống chuỗi; số lượng vật phẩm, phủ `0x96/0xb` |
| `img+0x42f38` | `MainGameFrame_drawSkillIcon` | icon skill + phủ hồi chiêu (cùng hằng) |
| `img+0x4445c` | `MainGameFrame_drawMonsterInfo` | `Event_isEvent`, `MONSTER_isDie`, `drawInfo`, `initMonsterInfo` |
| `img+0x4419c` | `MainGameFrame_drawInfo` | thanh tên/cấp/HP quái |
| `img+0x428dc` | `MainGameFrame_initMonsterInfo` | |
| `img+0x44d80` | `DropItem_drawInfos` | vị trí trong `drawGame` |
| `img+0x42d50` | `MainGameFrame_drawGameOver` | hạt giống chuỗi |
| `img+0x79f70` | `__aeabi_idiv` (bản runtime riêng của KTF) | |

Còn phải tìm: nhãn phím mềm góc trên-phải; khung menu trong game (GameMenuFrame/GMenu_Paint*); tab và con trỏ menu. `docs/ktf_match.tsv` hiện ghép tự động 228/1873 hàm KTF (hạt giống chuỗi + lan truyền khi khoảng giữa hai lời gọi đã biết có cùng số hàm); bộ lan truyền còn yếu ở hàm bị inline khác nhau — cải thiện khi bắt đầu port HUD.

## Ghi chú cho việc port HUD (Giai đoạn 1, mục 4b-8, **D3**)

1. Code KTF đọc struct theo **offset KTF** (khác AND2011 ở nhiều chỗ, ví dụ hero). Mỗi offset phải ánh xạ sang offset AND2011 bằng cách ghép hàm truy cập (ví dụ `HERO_getCurHp` hai bản) — không được chép thẳng.
2. Tọa độ lấy từ disassembly KTF (`python3 tools/ktf_code.py dis 0x43680`), không đo từ ảnh.
3. Kiểm chứng: chưa có bộ chạy tham chiếu cho KTF ⇒ không có golden trace cho phần HUD này. Đề xuất: (i) dựng HLE tối thiểu để chạy riêng hàm `img+0x43680` trên trạng thái hero chép từ snapshot AND2011 (đã ánh xạ offset) và so ảnh; hoặc (ii) chấp nhận kiểm bằng mắt với ảnh `docs/ref_ktf/` + ghi `docs/known_diffs.md`. Cần người dùng chọn khi tới mục này.
4. Đây là **lệch có chủ đích** so với AND2011: golden trace AND2011 sẽ lệch hash khung ở vùng HUD sau khi đổi ⇒ cần cờ build/test giữ HUD AND2011 cho hồi quy trace (đề xuất: `Hud.style` = AND2011 trong test, KTF trong bản phát hành).
